import analytics.QueueAnalytics;
import dao.QueueFlowServiceImpl;
import entity.Agent;
import entity.ChatRequest;
import entity.Request;
import entity.RoutingDecision;
import entity.Skill;
import exception.AgentNotFoundException;
import exception.NoAgentsAvailableException;
import stream.QueueFlowPipeline;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class Sprint3AcceptanceTest {

    public static void main(String[] args)
            throws InterruptedException, ExecutionException,
            AgentNotFoundException, NoAgentsAvailableException {
        testServiceExceptions();
        testAnalyticsEdgeCases();
        testMainAnalyticsConsistency();
        testDuplicateAssignmentProtection();
        testContinuousConsumer();
        testScheduledSlaSweep();
        testConcurrentNotificationFanOut();
        testLoadLatency();
        System.out.println("Sprint 3 acceptance checks passed.");
    }

    private static void testServiceExceptions()
            throws AgentNotFoundException, NoAgentsAvailableException {
        QueueFlowServiceImpl service = new QueueFlowServiceImpl();
        service.addSkill(new Skill(1, "Java"));

        expect(AgentNotFoundException.class,
                () -> service.UpdateAgentAvailability(99, "Available"));
        expect(IllegalArgumentException.class,
                () -> service.RouteRequest(99));

        Agent agent = new Agent(1, "Alex", "Agent", "Available");
        service.RegisterAgent(agent, Arrays.asList(1));
        service.UpdateAgentAvailability(1, "Available");

        Request request = service.SubmitRequest(1, 1, "High", "Call");
        Agent assigned = service.RouteRequest(request.getRequestId());
        check(assigned.getAgentId() == 1,
                "A valid request should be routed to the available agent.");
        request.setStatus("Queued");
        agent.setStatus("Available");
        expect(IllegalArgumentException.class,
                () -> service.RouteRequest(request.getRequestId()));
        request.setStatus("Assigned");
        agent.setStatus("Busy");

        Request waiting = service.SubmitRequest(2, 1, "Low", "Chat");
        expect(NoAgentsAvailableException.class,
                () -> service.RouteRequest(waiting.getRequestId()));
        expect(IllegalStateException.class,
                () -> service.UpdateAgentAvailability(1, "Available"));
        agent.setStatus("Available");
        expect(NoAgentsAvailableException.class,
                () -> service.RouteRequest(waiting.getRequestId()));
        service.CompleteRequest(request.getRequestId());
        request.setStatus("Assigned");
        expect(IllegalArgumentException.class,
                () -> service.CompleteRequest(request.getRequestId()));
        request.setStatus("Resolved");
        waiting.setReceivedAt(LocalDateTime.now().minusSeconds(31));
        service.EscalateRequest(waiting.getRequestId(), "SLA exceeded");
        check(service.RouteRequest(waiting.getRequestId()).getAgentId() == 1,
                "An escalated request must remain eligible for routing.");

        QueueFlowServiceImpl invalidRegistration = new QueueFlowServiceImpl();
        expect(IllegalArgumentException.class,
                () -> invalidRegistration.RegisterAgent(
                        new Agent(2, "Casey", "Agent", "Available"),
                        Arrays.asList(99)));
        check(invalidRegistration.getAgents().isEmpty(),
                "A failed registration must not leave a partial agent.");
    }

    private static void testAnalyticsEdgeCases() {
        QueueAnalytics analytics = new QueueAnalytics();
        check(analytics.GetAgentsByStatus(
                Collections.emptyList(), "Available").isEmpty(),
                "Empty agent lists should produce no results.");
        check(analytics.GetQueueDepthBySkill(
                Collections.emptyList(), Collections.emptyList()).isEmpty(),
                "Empty request lists should produce no queue depths.");
        check(analytics.GetSlaBreachingRequests(
                Collections.emptyList(), LocalDateTime.now(), 30).isEmpty(),
                "Empty request lists should produce no SLA breaches.");
        check(analytics.GetWorstWaitSkills(
                Collections.emptyList(), Collections.emptyList(), 3).isEmpty(),
                "Empty inputs should produce no wait summaries.");

        QueueFlowServiceImpl service = new QueueFlowServiceImpl();
        service.addSkill(new Skill(1, "Java"));
        service.addSkill(new Skill(2, "Python"));
        Request oldJava = service.SubmitRequest(1, 1, "High", "Call");
        oldJava.setReceivedAt(LocalDateTime.now().minusSeconds(40));
        Request recentPython = service.SubmitRequest(2, 2, "Low", "Chat");
        recentPython.setReceivedAt(LocalDateTime.now().minusSeconds(10));
        Request assignedJava = service.SubmitRequest(3, 1, "Medium", "Chat");
        assignedJava.setStatus("Assigned");
        Request unknownSkill = new ChatRequest(4, 4, 99, "Low",
                LocalDateTime.now().minusSeconds(60), "Queued");
        Request escalatedPython = new ChatRequest(5, 5, 2, "High",
                LocalDateTime.now().minusSeconds(45), "Escalated");

        Map<Integer, Long> depths = analytics.GetQueueDepthBySkill(
                Arrays.asList(oldJava, recentPython, assignedJava,
                        unknownSkill, escalatedPython),
                service.getSkills());
        check(depths.get(1) == 1L && depths.get(2) == 2L,
                "Queue depth should count queued and escalated known skills.");
        check(analytics.GetSlaBreachingRequests(
                Arrays.asList(oldJava, recentPython, assignedJava,
                        unknownSkill, escalatedPython),
                LocalDateTime.now(), 30).size() == 3,
                "Only requests over the SLA should be reported.");
        check(analytics.GetWorstWaitSkills(service.getSkills(),
                Arrays.asList(oldJava, recentPython, assignedJava,
                        unknownSkill, escalatedPython),
                1).get(0).getSkillId() == 1,
                "Worst-wait skills should be sorted by average wait.");
    }

    private static void testMainAnalyticsConsistency()
            throws AgentNotFoundException, NoAgentsAvailableException {
        QueueFlowServiceImpl service = new QueueFlowServiceImpl();
        service.addSkill(new Skill(1, "Java"));
        service.addSkill(new Skill(2, "Python"));
        service.RegisterAgent(new Agent(1, "John", "David", "Available"),
                Arrays.asList(1));
        service.RegisterAgent(new Agent(2, "Mary", "Joseph", "Available"),
                Arrays.asList(2));

        Request call = service.SubmitRequest(101, 1, "High", "Call");
        Request chat = service.SubmitRequest(102, 2, "Medium", "Chat");
        service.RouteRequest(call.getRequestId());
        service.RouteRequest(chat.getRequestId());
        service.CompleteRequest(call.getRequestId());

        Request waiting = service.SubmitRequest(103, 1, "Low", "Chat");
        waiting.setReceivedAt(LocalDateTime.now().minusSeconds(31));

        QueueAnalytics analytics = new QueueAnalytics();
        Map<Integer, Long> depths = analytics.GetQueueDepthBySkill(
                service.getRequests(), service.getSkills());
        check(depths.equals(Collections.singletonMap(1, 1L)),
                "The displayed depth must include the same queued request as SLA analytics.");
        check(analytics.GetSlaBreachingRequests(
                service.getRequests(), LocalDateTime.now(), 30)
                        .stream().anyMatch(
                                request -> request.getRequestId()
                                        == waiting.getRequestId()),
                "The aged queued request should appear in SLA breaches.");
        check(analytics.GetWorstWaitSkills(
                service.getSkills(), service.getRequests(), 1)
                        .get(0).getQueueDepth() == 1,
                "Worst-wait depth should match queue-depth analytics.");
    }

    private static void testDuplicateAssignmentProtection()
            throws InterruptedException, ExecutionException {
        QueueFlowServiceImpl service = new QueueFlowServiceImpl();
        service.addSkill(new Skill(1, "Java"));
        service.RegisterAgent(new Agent(1, "Alex", "One", "Available"),
                Arrays.asList(1));
        service.RegisterAgent(new Agent(2, "Blair", "Two", "Available"),
                Arrays.asList(1));
        Request request = service.SubmitRequest(1, 1, "High", "Call");

        ExecutorService callers = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> first = callers.submit(() -> tryRoute(
                    service, request, ready, start));
            Future<Boolean> second = callers.submit(() -> tryRoute(
                    service, request, ready, start));
            ready.await();
            start.countDown();
            check(first.get() ^ second.get(),
                    "Exactly one concurrent route may assign a request.");
            check(service.getDecisions().size() == 1,
                    "A request must have at most one routing decision.");
        } finally {
            callers.shutdownNow();
        }
    }

    private static boolean tryRoute(
            QueueFlowServiceImpl service,
            Request request,
            CountDownLatch ready,
            CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            service.RouteRequest(request.getRequestId());
            return true;
        } catch (NoAgentsAvailableException | IllegalArgumentException e) {
            return false;
        }
    }

    private static void testContinuousConsumer()
            throws InterruptedException, ExecutionException {
        QueueFlowServiceImpl service = new QueueFlowServiceImpl();
        service.addSkill(new Skill(1, "Java"));
        service.RegisterAgent(new Agent(1, "Taylor", "Agent", "Available"),
                Arrays.asList(1));

        try (QueueFlowPipeline pipeline = new QueueFlowPipeline(service)) {
            pipeline.start();
            pipeline.produceAsync(10, 1).get();
            long deadline = System.nanoTime()
                    + TimeUnit.SECONDS.toNanos(3);
            while (service.getDecisions().isEmpty()
                    && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            check(service.getDecisions().size() == 1,
                    "The started pipeline should consume without a manual drain.");
        }
    }

    private static void testScheduledSlaSweep()
            throws InterruptedException {
        QueueFlowServiceImpl service = new QueueFlowServiceImpl();
        service.addSkill(new Skill(1, "Java"));
        Request oldRequest = service.SubmitRequest(1, 1, "Low", "Chat");
        oldRequest.setReceivedAt(LocalDateTime.now().minusSeconds(
                QueueFlowServiceImpl.DEFAULT_SLA_TARGET_SECONDS + 1));

        try (QueueFlowPipeline pipeline = new QueueFlowPipeline(service)) {
            pipeline.start();
            long deadline = System.nanoTime()
                    + TimeUnit.SECONDS.toNanos(18);
            while (!"Escalated".equalsIgnoreCase(oldRequest.getStatus())
                    && System.nanoTime() < deadline) {
                Thread.sleep(50);
            }
            check("Escalated".equalsIgnoreCase(oldRequest.getStatus()),
                    "The automatic SLA sweep should run after 15 seconds.");
        }
    }

    private static void testConcurrentNotificationFanOut()
            throws InterruptedException, ExecutionException {
        QueueFlowServiceImpl service = new QueueFlowServiceImpl();
        service.addSkill(new Skill(1, "Java"));
        service.RegisterAgent(new Agent(1, "Taylor", "Agent", "Available"),
                Arrays.asList(1));

        PrintStream originalOut = System.out;
        ConcurrentNotificationPrintStream capturedOut =
                new ConcurrentNotificationPrintStream();
        try (QueueFlowPipeline pipeline = new QueueFlowPipeline(service)) {
            System.setOut(capturedOut);
            try {
                pipeline.start();
                pipeline.produceAsync(10, 1).get();
                check(capturedOut.awaitNotifications(3, TimeUnit.SECONDS),
                        "Both routing notifications should be emitted.");
                check(capturedOut.eventTypes.containsAll(
                                Arrays.asList("agent", "customer")),
                        "The agent and customer should each be notified.");
                check(capturedOut.startedConcurrently.get(),
                        "Both notification tasks should run concurrently.");
            } finally {
                System.setOut(originalOut);
            }
        } finally {
            capturedOut.close();
        }
    }

    private static void testLoadLatency()
            throws InterruptedException, ExecutionException,
            AgentNotFoundException {
        QueueFlowServiceImpl service = new QueueFlowServiceImpl();
        service.addSkill(new Skill(1, "Java"));
        service.addSkill(new Skill(2, "Python"));
        for (int i = 1; i <= 40; i++) {
            service.RegisterAgent(
                    new Agent(i, "Agent " + i, "Worker", "Available"),
                    Arrays.asList(1, 2));
        }

        try (QueueFlowPipeline pipeline = new QueueFlowPipeline(service)) {
            pipeline.start();
            Future<?> producer = pipeline.produceAtRateAsync(
                    1000, 100, TimeUnit.MINUTES.toMillis(1) / 100);
            Set<Integer> completedRequests = new HashSet<>();
            long deadline = System.nanoTime()
                    + TimeUnit.SECONDS.toNanos(70);
            boolean producerResultChecked = false;
            while (completedRequests.size() < 100
                    && System.nanoTime() < deadline) {
                for (RoutingDecision decision : service.getDecisions()) {
                    if (completedRequests.add(decision.getRequestId())) {
                        service.CompleteRequest(decision.getRequestId());
                    }
                }
                if (!producerResultChecked && producer.isDone()) {
                    producer.get();
                    producerResultChecked = true;
                }
                if (completedRequests.size() < 100) {
                    Thread.sleep(10);
                }
            }
            if (!producerResultChecked) {
                producer.get();
                producerResultChecked = true;
            }
            check(producerResultChecked,
                    "The 100-request producer should finish within 70 seconds.");
            check(completedRequests.size() == 100,
                    "All 100 requests should be assigned and completed.");
            long latencyMillis =
                    pipeline.getMaximumAssignmentLatencyMillis();
            Map<Integer, Long> assignmentLatencies =
                    pipeline.getAssignmentLatenciesMillis();
            check(service.getDecisions().size() == 100,
                    "The 40 simulated agents should process all 100 requests.");
            check(assignmentLatencies.size() == 100,
                    "Latency should be recorded for every successful assignment.");
            check(assignmentLatencies.values().stream()
                            .allMatch(latency -> latency >= 0
                                    && latency < 5000),
                    "Every assignment latency should be non-negative and below 5 seconds.");
            check(pipeline.getQueueSize() == 0,
                    "The continuous consumer should drain every assigned request.");
            check(pipeline.getQueueSize()
                            + pipeline.getQueueRemainingCapacity() == 100,
                    "The request queue must remain bounded at 100 items.");
            check(latencyMillis < 5000,
                    "Assignment latency must be below 5 seconds; was "
                            + latencyMillis + " ms.");
            System.out.println("Load acceptance: assigned=100, queued=0, "
                    + "maximum assignment latency=" + latencyMillis + " ms.");
        }
    }

    private static final class ConcurrentNotificationPrintStream
            extends PrintStream {

        private final CountDownLatch notificationStarts =
                new CountDownLatch(2);
        private final CountDownLatch notifications =
                new CountDownLatch(2);
        private final Set<String> eventTypes =
                ConcurrentHashMap.newKeySet();
        private final AtomicBoolean startedConcurrently =
                new AtomicBoolean(true);

        private ConcurrentNotificationPrintStream() {
            super(new ByteArrayOutputStream(), true);
        }

        @Override
        public void println(String message) {
            if (message.startsWith("Taylor notified for request ")) {
                eventTypes.add("agent");
                recordNotification();
            } else if (message.startsWith(
                    "Customer event: connecting you now ")) {
                eventTypes.add("customer");
                recordNotification();
            }
            super.println(message);
        }

        private void recordNotification() {
            notificationStarts.countDown();
            try {
                if (!notificationStarts.await(2, TimeUnit.SECONDS)) {
                    startedConcurrently.set(false);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                startedConcurrently.set(false);
            } finally {
                notifications.countDown();
            }
        }

        private boolean awaitNotifications(long timeout, TimeUnit unit)
                throws InterruptedException {
            return notifications.await(timeout, unit);
        }
    }

    private static void expect(
            Class<? extends Exception> expected,
            ThrowingRunnable action) {
        try {
            action.run();
        } catch (Exception e) {
            check(expected.isInstance(e),
                    "Expected " + expected.getSimpleName() + " but got "
                            + e.getClass().getSimpleName());
            return;
        }
        throw new AssertionError(
                "Expected " + expected.getSimpleName() + " to be thrown.");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
