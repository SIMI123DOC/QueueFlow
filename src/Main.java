import analytics.QueueAnalytics;
import analytics.SkillWaitSummary;
import dao.QueueFlowServiceImpl;
import entity.Agent;
import entity.Request;
import entity.RoutingDecision;
import entity.Skill;
import exception.AgentNotFoundException;
import exception.NoAgentsAvailableException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import stream.QueueFlowPipeline;

public class Main {

    public static void main(String[] args) {
        QueueFlowServiceImpl service = new QueueFlowServiceImpl();
        service.addSkill(new Skill(1, "Java"));
        service.addSkill(new Skill(2, "Python"));

        service.RegisterAgent(new Agent(1, "John", "David", "Available"),
                Arrays.asList(1));
        service.RegisterAgent(new Agent(2, "Mary", "Joseph", "Available"),
                Arrays.asList(2));

        System.out.println("===== QUEUEFLOW SPRINT 3 DEMO =====");

        Request call = service.SubmitRequest(101, 1, "High", "Call");
        Request chat = service.SubmitRequest(102, 2, "Medium", "Chat");

        try {
            List<Request> mixedRequests =
                    new ArrayList<>(Arrays.asList(call, chat));
            mixedRequests.sort(Comparator.comparingInt(
                    Request::getRoutingWeight).reversed());
            for (Request request : mixedRequests) {
                Agent assigned = service.RouteRequest(request.getRequestId());
                System.out.println(request.getChannel() + " request "
                        + request.getRequestId() + " (routing weight "
                        + request.getRoutingWeight() + ") routed to "
                        + assigned.getFirstName());
            }

            service.CompleteRequest(call.getRequestId());
            System.out.println("Completed call " + call.getRequestId()
                    + "; agent released.");
        } catch (AgentNotFoundException | NoAgentsAvailableException e) {
            System.out.println("Service error: " + e.getMessage());
        }

        QueueAnalytics analytics = new QueueAnalytics();
        System.out.println("\n===== ANALYTICS =====");
        System.out.println("Available: "
                + analytics.GetAgentsByStatus(service.getAgents(), "Available")
                        .stream().map(Agent::getFirstName).toList());

        Request slaRequest = service.SubmitRequest(103, 1, "Low", "Chat");
        slaRequest.setReceivedAt(LocalDateTime.now().minusSeconds(31));

        Map<Integer, Long> depth =
                analytics.GetQueueDepthBySkill(service.getRequests(), service.getSkills());
        System.out.println("Queue depth: " + depth);

        List<Request> breaching = analytics.GetSlaBreachingRequests(
                service.getRequests(), LocalDateTime.now(),
                QueueFlowServiceImpl.DEFAULT_SLA_TARGET_SECONDS);
        System.out.println("SLA breaches: " +
                breaching.stream().map(Request::getRequestId).toList());

        List<SkillWaitSummary> worst =
                analytics.GetWorstWaitSkills(service.getSkills(), service.getRequests(), 2);
        System.out.println("Worst waits: " + worst);

        System.out.println("\n===== CUSTOM EXCEPTIONS =====");
        try {
            service.UpdateAgentAvailability(999, "Available");
        } catch (AgentNotFoundException e) {
            System.out.println("Handled: " + e.getMessage());
        }

        try {
            service.RouteRequest(999);
        } catch (IllegalArgumentException | NoAgentsAvailableException e) {
            System.out.println("Handled: " + e.getMessage());
        }

        System.out.println("\n===== SIMULATED STREAM AND CONCURRENT DASHBOARD =====");
        try (QueueFlowPipeline pipeline = new QueueFlowPipeline(service)) {
            pipeline.start();

            Future<?> producer = pipeline.produceAsync(200, 4);
            producer.get();
            pipeline.consumeAvailable();
            pipeline.printConcurrentDashboard();
            pipeline.runSlaSweep();

            System.out.println("Bounded queue capacity: "
                    + (pipeline.getQueueSize()
                    + pipeline.getQueueRemainingCapacity()));
            System.out.println("Maximum assignment latency: "
                    + pipeline.getMaximumAssignmentLatencyMillis() + " ms");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Pipeline demonstration interrupted.");
        } catch (java.util.concurrent.ExecutionException e) {
            System.out.println("Pipeline error: " + e.getMessage());
        }

        System.out.println("\n===== LOAD SHAPE DEMONSTRATION =====");
        QueueFlowServiceImpl loadService = new QueueFlowServiceImpl();
        loadService.addSkill(new Skill(1, "Java"));
        loadService.addSkill(new Skill(2, "Python"));
        for (int i = 1; i <= 40; i++) {
            loadService.RegisterAgent(
                    new Agent(i, "Agent " + i, "Worker", "Available"),
                    Arrays.asList(1, 2));
        }

        try (QueueFlowPipeline loadPipeline = new QueueFlowPipeline(loadService)) {
            loadPipeline.start();
            long intervalMillis = TimeUnit.MINUTES.toMillis(1) / 100;
            Future<?> producer = loadPipeline.produceAtRateAsync(
                    1000, 100, intervalMillis);
            Set<Integer> completedRequests = new HashSet<>();
            long deadline = System.nanoTime()
                    + TimeUnit.SECONDS.toNanos(70);
            boolean producerResultChecked = false;
            while (completedRequests.size() < 100
                    && System.nanoTime() < deadline) {
                for (RoutingDecision decision : loadService.getDecisions()) {
                    if (completedRequests.add(decision.getRequestId())) {
                        loadService.CompleteRequest(decision.getRequestId());
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
            if (!producerResultChecked || completedRequests.size() != 100) {
                throw new IllegalStateException(
                        "The paced 100-request load did not finish in 70 seconds.");
            }
            System.out.println("100-request load: assigned="
                    + loadService.getDecisions().size()
                    + ", still queued=" + loadPipeline.getQueueSize()
                    + ", latency samples="
                    + loadPipeline.getAssignmentLatenciesMillis().size()
                    + ", maxAssignmentLatency="
                    + loadPipeline.getMaximumAssignmentLatencyMillis()
                    + " ms (acceptance limit: < 5000 ms; arrival interval: "
                    + intervalMillis + " ms)");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Load demonstration interrupted.", e);
        } catch (java.util.concurrent.ExecutionException
                 | AgentNotFoundException e) {
            throw new IllegalStateException(
                    "Load demonstration failed.", e);
        }

        System.out.println("\nSprint 3 in-memory demo completed.");
    }
}
