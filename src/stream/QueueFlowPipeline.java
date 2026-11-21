
package stream;

import dao.QueueFlowServiceImpl;
import entity.*;
import analytics.QueueAnalytics;
import exception.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class QueueFlowPipeline implements AutoCloseable {

    private static final int QUEUE_CAPACITY = 100;

    private final QueueFlowServiceImpl service;

    // Bounded queue with maximum capacity of 100 requests
    private final BlockingQueue<Request> queue =
            new ArrayBlockingQueue<>(QUEUE_CAPACITY);
    private final Semaphore queueSlots = new Semaphore(QUEUE_CAPACITY);

    // Worker threads for concurrent processing
    private final ExecutorService workers =
            Executors.newFixedThreadPool(4);

    private final ExecutorService notifications =
            Executors.newFixedThreadPool(2);

    private final ExecutorService producers =
            Executors.newFixedThreadPool(2);

    // Scheduler for periodic SLA checking
    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(2);

    private final QueueAnalytics analytics = new QueueAnalytics();
    private final AtomicLong maximumAssignmentLatencyMillis =
            new AtomicLong();
    private final ConcurrentMap<Integer, Long> assignmentLatencyMillis =
            new ConcurrentHashMap<>();
    private final AtomicBoolean started = new AtomicBoolean();
    private final Set<Integer> waitingForAgentIds =
            ConcurrentHashMap.newKeySet();

    // Constructor
    public QueueFlowPipeline(QueueFlowServiceImpl service) {
        this.service = Objects.requireNonNull(service);
    }

    // Start scheduled SLA monitoring
    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        scheduler.scheduleWithFixedDelay(
                this::consumeContinuously,
                0,
                100,
                TimeUnit.MILLISECONDS
        );
        scheduler.scheduleAtFixedRate(
                this::slaSweep,
                15,
                15,
                TimeUnit.SECONDS
        );
    }

    // Generate requests asynchronously
    public Future<?> produceAsync(int customerStart, int count) {

        return producers.submit(
                new RequestProducer(
                        service,
                        customerStart,
                        count,
                        queue,
                        queueSlots
                )
        );
    }

    public Future<?> produceAtRateAsync(
            int customerStart, int count, long intervalMillis) {
        if (intervalMillis <= 0) {
            throw new IllegalArgumentException(
                    "Request interval must be greater than zero.");
        }

        return producers.submit(
                new RequestProducer(
                        service,
                        customerStart,
                        count,
                        queue,
                        queueSlots,
                        intervalMillis
                )
        );
    }

    // Process requests from the queue
    public synchronized void consumeAvailable() {
        List<Request> batch = new ArrayList<>();
        queue.drainTo(batch);
        if (batch.isEmpty()) {
            return;
        }

        List<CompletableFuture<Boolean>> processing = new ArrayList<>();
        for (Request request : batch) {
            processing.add(CompletableFuture.supplyAsync(
                    () -> processRequest(request), workers));
        }

        try {
            CompletableFuture.allOf(
                    processing.toArray(new CompletableFuture<?>[0])).join();
        } catch (CompletionException | CancellationException e) {
            // Inspect each result below so failed requests can be retained.
        }

        List<Request> retryRequests = new ArrayList<>();
        RuntimeException processingFailure = null;
        for (int i = 0; i < processing.size(); i++) {
            try {
                if (!processing.get(i).join()) {
                    retryRequests.add(batch.get(i));
                }
            } catch (CompletionException | CancellationException e) {
                retryRequests.add(batch.get(i));
                if (processingFailure == null) {
                    processingFailure = new IllegalStateException(
                            "Request " + batch.get(i).getRequestId()
                                    + " could not be processed.",
                            e.getCause());
                }
            }
        }

        for (Request request : retryRequests) {
            if (!queue.offer(request)) {
                throw new IllegalStateException(
                        "Could not return request " + request.getRequestId()
                                + " to the bounded queue.");
            }
        }

        if (processingFailure != null) {
            throw processingFailure;
        }
    }

    private void consumeContinuously() {
        try {
            consumeAvailable();
        } catch (RuntimeException e) {
            System.err.println("Queue consumer failed: " + e.getMessage());
        }
    }

    private boolean processRequest(Request request) {
        try {
            Agent assigned = service.RouteRequest(request.getRequestId());
            waitingForAgentIds.remove(request.getRequestId());
            long latencyMillis = Math.max(0,
                    Duration.between(request.getReceivedAt(),
                            LocalDateTime.now()).toMillis());
            assignmentLatencyMillis.put(
                    request.getRequestId(), latencyMillis);
            maximumAssignmentLatencyMillis.accumulateAndGet(
                    latencyMillis, Math::max);
            queueSlots.release();

            try {
                CompletableFuture<Void> agentNotification =
                        CompletableFuture.runAsync(
                                () -> System.out.println(
                                        assigned.getFirstName()
                                                + " notified for request "
                                                + request.getRequestId()),
                                notifications);

                CompletableFuture<Void> customerNotification =
                        CompletableFuture.runAsync(
                                () -> System.out.println(
                                        "Customer event: connecting you now "
                                                + "(request "
                                                + request.getRequestId() + ")"),
                                notifications);

                CompletableFuture.allOf(
                        agentNotification, customerNotification).join();
            } catch (CompletionException | RejectedExecutionException
                     | CancellationException e) {
                System.err.println("Notification failed for assigned request "
                        + request.getRequestId() + ": " + e.getMessage());
            }
            return true;
        } catch (NoAgentsAvailableException e) {
            if (waitingForAgentIds.add(request.getRequestId())) {
                System.out.println("Request " + request.getRequestId()
                        + " remains queued: " + e.getMessage());
            }
            return false;
        }
    }

    public int getQueueSize() {
        return queue.size();
    }

    public int getQueueRemainingCapacity() {
        return queue.remainingCapacity();
    }

    public long getMaximumAssignmentLatencySeconds() {
        return TimeUnit.MILLISECONDS.toSeconds(
                maximumAssignmentLatencyMillis.get());
    }

    public long getMaximumAssignmentLatencyMillis() {
        return maximumAssignmentLatencyMillis.get();
    }

    public Map<Integer, Long> getAssignmentLatenciesMillis() {
        return new HashMap<>(assignmentLatencyMillis);
    }

    public void runSlaSweep() {
        slaSweep();
    }

    // Check for SLA-breaching requests every 15 seconds
    private void slaSweep() {

        List<Request> breachingRequests =
                analytics.GetSlaBreachingRequests(
                        service.getRequests(),
                        LocalDateTime.now(),
                        QueueFlowServiceImpl.DEFAULT_SLA_TARGET_SECONDS
                );

        for (Request request : breachingRequests) {

            try {
                service.EscalateRequest(
                        request.getRequestId(),
                        "SLA wait target exceeded"
                );

            } catch (IllegalArgumentException e) {

                System.out.println(
                        "SLA escalation skipped: " + e.getMessage()
                );
            }
        }
    }

    // Concurrent dashboard by skill
    public void printConcurrentDashboard() {

        List<Skill> skills = service.getSkills();
        List<Request> requests = service.getRequests();

        List<CompletableFuture<String>> tasks = new ArrayList<>();

        for (Skill skill : skills) {

            CompletableFuture<String> task =
                    CompletableFuture.supplyAsync(() -> {

                        List<Request> queuedRequests =
                                requests.stream()
                                        .filter(request ->
                                                request.getRequiredSkillId()
                                                == skill.getSkillId())
                                        .filter(request ->
                                                "Queued".equalsIgnoreCase(
                                                        request.getStatus())
                                                || "Escalated".equalsIgnoreCase(
                                                        request.getStatus()))
                                        .toList();

                        long queueDepth = queuedRequests.size();

                        double averageWait =
                                queuedRequests.stream()
                                        .mapToLong(request ->
                                                Math.max(
                                                        0,
                                                        Duration.between(
                                                                request.getReceivedAt(),
                                                                LocalDateTime.now()
                                                        ).getSeconds()
                                                )
                                        )
                                        .average()
                                        .orElse(0);

                        return "Skill " + skill.getSkillName()
                                + ": depth=" + queueDepth
                                + ", average wait="
                                + String.format("%.1f", averageWait)
                                + " sec";

                    }, workers);

            tasks.add(task);
        }

        // Wait for all dashboard calculations
        CompletableFuture.allOf(
                tasks.toArray(new CompletableFuture<?>[0])
        ).join();

        // Display results
        tasks.forEach(task ->
                System.out.println(task.join())
        );
    }

    // Shut down executors
    @Override
    public void close() {
        producers.shutdown();
        try {
            awaitShutdown(producers);
            scheduler.shutdownNow();
            awaitShutdown(scheduler);
            workers.shutdown();
            awaitShutdown(workers);
            notifications.shutdown();
            awaitShutdown(notifications);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            producers.shutdownNow();
            scheduler.shutdownNow();
            workers.shutdownNow();
            notifications.shutdownNow();
        }

        System.out.println("QueueFlow pipeline stopped.");
    }

    private static void awaitShutdown(ExecutorService executor)
            throws InterruptedException {
        if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
            executor.shutdownNow();
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                System.err.println("An executor did not stop within 4 seconds.");
            }
        }
    }
}
