
import entity.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Main {

    public static void main(String[] args) {

        // 1. Create agents
        Agent agent1 = new Agent(1, "John", "David", "Available");
        Agent agent2 = new Agent(2, "Mary", "Joseph", "Available");

        // 2. Create skills
        Skill skill1 = new Skill(1, "Java");
        Skill skill2 = new Skill(2, "Python");

        // 3. Map agents to their skills
        List<AgentSkill> agentSkills = new ArrayList<>();

        agentSkills.add(new AgentSkill(1, 1, 1, 4));
        agentSkills.add(new AgentSkill(2, 2, 2, 5));

        // 4. Create a mixed list of requests
        List<Request> requests = new ArrayList<>();

        requests.add(new ChatRequest(
                2, 102, 2, "Medium",
                LocalDateTime.now(), "Queued"
        ));

        requests.add(new CallRequest(
                1, 101, 1, "High",
                LocalDateTime.now(), "Queued"
        ));

        // 5. Sort requests by routing weight (highest first)
        requests.sort(
                Comparator.comparingInt(Request::getRoutingWeight).reversed()
        );

        // 6. Create available agent pool
        List<Agent> agents = new ArrayList<>();
        agents.add(agent1);
        agents.add(agent2);

        // 7. Route requests to matching available agents
        System.out.println("===== QUEUEFLOW ROUTING DEMO =====");

        int decisionId = 1;

        for (Request request : requests) {

            Agent assignedAgent = null;

            for (Agent agent : agents) {

                if (!agent.getStatus().equalsIgnoreCase("Available")) {
                    continue;
                }

                boolean hasRequiredSkill = false;

                for (AgentSkill mapping : agentSkills) {

                    if (mapping.getAgentId() == agent.getAgentId()
                            && mapping.getSkillId() == request.getRequiredSkillId()) {

                        hasRequiredSkill = true;
                        break;
                    }
                }

                if (hasRequiredSkill) {
                    assignedAgent = agent;
                    break;
                }
            }

            if (assignedAgent != null) {

                assignedAgent.setStatus("Busy");

                RoutingDecision decision = new RoutingDecision(
                        decisionId++,
                        request.getRequestId(),
                        assignedAgent.getAgentId(),
                        LocalDateTime.now(),
                        0
                );

                System.out.println("\nRequest ID: " + request.getRequestId());
                System.out.println("Channel: " + request.getChannel());
                System.out.println("Routing Weight: " + request.getRoutingWeight());
                System.out.println("Assigned Agent: "
                        + assignedAgent.getFirstName());
                System.out.println("Agent Status: " + assignedAgent.getStatus());
                System.out.println("Decision ID: " + decision.getDecisionId());

            } else {

                System.out.println("\nRequest ID: " + request.getRequestId()
                        + " could not be routed.");
            }
        }

        System.out.println("\n===== FINAL AGENT STATUS =====");

        for (Agent agent : agents) {
            System.out.println(
                    agent.getFirstName() + " - " + agent.getStatus()
            );
        }

       // 8. Test validation rules
System.out.println("\n===== VALIDATION TEST =====");

try {
    AgentSkill invalidMapping = new AgentSkill(3, 1, 1, 6);
    System.out.println("Invalid mapping created.");
} catch (IllegalArgumentException e) {
    System.out.println("Validation error: " + e.getMessage());
}

try {
    agent1.setStatus("OnBreak");
    System.out.println("Invalid status accepted.");
} catch (IllegalArgumentException e) {
    System.out.println("Validation error: " + e.getMessage());
}

System.out.println("\nPolymorphism and routing demo completed!");
    }
}
