
package dao;

import entity.*;
import exception.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class QueueFlowServiceImpl implements IQueueFlowService {

    public static final long DEFAULT_SLA_TARGET_SECONDS = 30;

    private final List<Agent> agents = new ArrayList<>();
    private final List<Skill> skills = new ArrayList<>();
    private final List<AgentSkill> mappings = new ArrayList<>();
    private final List<Request> requests = new ArrayList<>();
    private final List<RoutingDecision> decisions = new ArrayList<>();

    private final Map<Integer, String> pauseReasons = new HashMap<>();
    private final Set<Integer> activeRequestIds = new HashSet<>();

    private final AtomicInteger requestIds = new AtomicInteger(1);
    private final AtomicInteger mappingIds = new AtomicInteger(1);
    private final AtomicInteger decisionIds = new AtomicInteger(1);

    // Add a skill
    public synchronized void addSkill(Skill skill) {
        Objects.requireNonNull(skill, "Skill cannot be null");

        boolean exists = skills.stream()
                .anyMatch(s -> s.getSkillId() == skill.getSkillId());

        if (!exists) {
            skills.add(skill);
        }
    }

    // Register an agent and map skills
    @Override
    public synchronized void RegisterAgent(Agent agent, List<Integer> skillIds) {

        Objects.requireNonNull(agent, "Agent cannot be null");

        if (skillIds == null) {
            skillIds = Collections.emptyList();
        }

        boolean agentExists = agents.stream()
                .anyMatch(a -> a.getAgentId() == agent.getAgentId());

        if (agentExists) {
            throw new IllegalArgumentException("Agent already registered.");
        }

        for (Integer skillId : skillIds) {

            if (skillId == null) {
                continue;
            }

            boolean skillExists = skills.stream()
                    .anyMatch(s -> s.getSkillId() == skillId);

            if (!skillExists) {
                throw new IllegalArgumentException(
                        "Skill ID " + skillId + " does not exist.");
            }
        }

        agents.add(agent);

        for (Integer skillId : skillIds) {
            if (skillId == null) {
                continue;
            }

            mappings.add(new AgentSkill(
                    mappingIds.getAndIncrement(),
                    agent.getAgentId(),
                    skillId,
                    3
            ));
        }
    }

    // Update agent availability
    @Override
    public synchronized void UpdateAgentAvailability(
            int agentId, String status) throws AgentNotFoundException {

        Agent agent = findAgent(agentId);

        if (status == null ||
                !(status.equalsIgnoreCase("Available")
                || status.equalsIgnoreCase("Busy")
                || status.equalsIgnoreCase("Offline"))) {

            throw new IllegalArgumentException("Invalid agent status.");
        }

        if ("Available".equalsIgnoreCase(status)
                && hasActiveAssignment(agentId)) {
            throw new IllegalStateException(
                    "An agent with an active request cannot be marked Available.");
        }

        agent.setStatus(status);

        if (!status.equalsIgnoreCase("Offline")) {
            pauseReasons.remove(agentId);
        }
    }

    // Submit a new request
    @Override
    public synchronized Request SubmitRequest(
            int customerId,
            int requiredSkillId,
            String priority,
            String channel) {

        boolean skillExists = skills.stream()
                .anyMatch(skill -> skill.getSkillId() == requiredSkillId);
        if (!skillExists) {
            throw new IllegalArgumentException(
                    "Skill ID " + requiredSkillId + " does not exist.");
        }

        if (priority == null ||
                !(priority.equalsIgnoreCase("High")
                || priority.equalsIgnoreCase("Medium")
                || priority.equalsIgnoreCase("Low"))) {

            throw new IllegalArgumentException("Invalid priority.");
        }

        if (channel == null ||
                !(channel.equalsIgnoreCase("Call")
                || channel.equalsIgnoreCase("Chat"))) {

            throw new IllegalArgumentException("Invalid request channel.");
        }

        int requestId = requestIds.getAndIncrement();

        LocalDateTime receivedAt = LocalDateTime.now();

        Request request;

        if (channel.equalsIgnoreCase("Call")) {
            request = new CallRequest(
                    requestId,
                    customerId,
                    requiredSkillId,
                    priority,
                    receivedAt,
                    "Queued"
            );
        } else {
            request = new ChatRequest(
                    requestId,
                    customerId,
                    requiredSkillId,
                    priority,
                    receivedAt,
                    "Queued"
            );
        }

        requests.add(request);

        return request;
    }

    // Find the best available agent
    @Override
    public synchronized Agent RouteRequest(int requestId)
            throws NoAgentsAvailableException {

        Request request = findRequest(requestId);

        if (!isRoutable(request)) {
            throw new IllegalArgumentException(
                    "Only queued or escalated requests can be routed.");
        }

        Agent bestAgent = agents.stream()

                .filter(a -> "Available".equalsIgnoreCase(a.getStatus()))
                .filter(a -> !hasActiveAssignment(a.getAgentId()))

                .filter(a -> mappings.stream().anyMatch(m ->
                        m.getAgentId() == a.getAgentId()
                        && m.getSkillId() == request.getRequiredSkillId()))

                .max(Comparator
                        .comparingInt((Agent agent) ->
                                mappings.stream()
                                .filter(m ->
                                        m.getAgentId() == agent.getAgentId()
                                        && m.getSkillId() == request.getRequiredSkillId())
                                .mapToInt(AgentSkill::getProficiencyLevel)
                                .max()
                                .orElse(0))
                        .thenComparingInt((Agent agent) -> -agent.getAgentId()))

                .orElseThrow(() -> new NoAgentsAvailableException(
                        "No available agent has the required skill."));

        assignInternally(request, bestAgent);

        return bestAgent;
    }

    // Manually assign an agent
    @Override
    public synchronized void AssignAgent(int requestId, int agentId)
            throws AgentNotFoundException, NoAgentsAvailableException {

        Request request = findRequest(requestId);
        Agent agent = findAgent(agentId);

        if (!isRoutable(request)) {
            throw new IllegalArgumentException(
                    "Only queued or escalated requests can be assigned.");
        }

        if (!"Available".equalsIgnoreCase(agent.getStatus())
                || hasActiveAssignment(agentId)) {
            throw new NoAgentsAvailableException(
                    "Selected agent is not available.");
        }

        boolean hasRequiredSkill = mappings.stream()
                .anyMatch(m ->
                        m.getAgentId() == agentId
                        && m.getSkillId() == request.getRequiredSkillId());

        if (!hasRequiredSkill) {
            throw new NoAgentsAvailableException(
                    "Agent does not have the required skill.");
        }

        assignInternally(request, agent);
    }

    // Common assignment logic
    private void assignInternally(Request request, Agent agent) {

        if (!activeRequestIds.add(request.getRequestId())) {
            throw new IllegalStateException(
                    "Request already has an active assignment.");
        }

        agent.setStatus("Busy");
        request.setStatus("Assigned");

        int waitingTime = (int) Math.max(
                0,
                Duration.between(
                        request.getReceivedAt(),
                        LocalDateTime.now()
                ).getSeconds()
        );

        RoutingDecision decision = new RoutingDecision(
                decisionIds.getAndIncrement(),
                request.getRequestId(),
                agent.getAgentId(),
                LocalDateTime.now(),
                waitingTime
        );

        decisions.add(decision);
    }

    // Complete a request and release its assigned agent
    @Override
    public synchronized void CompleteRequest(int requestId)
            throws AgentNotFoundException {

        Request request = findRequest(requestId);

        if (!"Assigned".equalsIgnoreCase(request.getStatus())
                || !activeRequestIds.contains(requestId)) {
            throw new IllegalArgumentException(
                    "Only assigned requests can be completed.");
        }

        RoutingDecision decision = decisions.stream()
                .filter(d -> d.getRequestId() == requestId)
                .max(Comparator.comparingInt(
                        RoutingDecision::getDecisionId))
                .orElseThrow(() -> new IllegalStateException(
                        "No routing decision found for this request."));

        Agent agent = findAgent(decision.getAgentId());

        request.setStatus("Resolved");
        activeRequestIds.remove(requestId);
        agent.setStatus("Available");
    }

    // Escalate a request
    @Override
    public synchronized void EscalateRequest(int requestId, String reason) {

        Request request = findRequest(requestId);

        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Escalation reason cannot be empty.");
        }

        if ("Escalated".equalsIgnoreCase(request.getStatus())) {
            return;
        }

        if (!"Queued".equalsIgnoreCase(request.getStatus())
                || Duration.between(request.getReceivedAt(), LocalDateTime.now())
                        .compareTo(Duration.ofSeconds(
                                DEFAULT_SLA_TARGET_SECONDS)) <= 0) {
            throw new IllegalArgumentException(
                    "Only queued requests that exceeded the SLA can be escalated.");
        }

        request.setPriority("High");
        request.setStatus("Escalated");

        System.out.println(
                "Request " + requestId + " escalated. Reason: " + reason);
    }

    // Pause an agent
    @Override
    public synchronized void PauseAgent(
            int agentId, String reason) throws AgentNotFoundException {

        Agent agent = findAgent(agentId);

        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Pause reason cannot be empty.");
        }

        agent.setStatus("Offline");
        pauseReasons.put(agentId, reason);
    }

    // Find agent by ID
    private Agent findAgent(int id) throws AgentNotFoundException {

        return agents.stream()
                .filter(a -> a.getAgentId() == id)
                .findFirst()
                .orElseThrow(() -> new AgentNotFoundException(
                        "Agent " + id + " was not found."));
    }

    // Find request by ID
    private Request findRequest(int id) {

        return requests.stream()
                .filter(r -> r.getRequestId() == id)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Request " + id + " was not found."));
    }

    private boolean hasActiveAssignment(int agentId) {
        return decisions.stream().anyMatch(decision ->
                decision.getAgentId() == agentId
                        && activeRequestIds.contains(decision.getRequestId()));
    }

    private boolean isRoutable(Request request) {
        String status = request.getStatus();
        return !activeRequestIds.contains(request.getRequestId())
                && ("Queued".equalsIgnoreCase(status)
                || "Escalated".equalsIgnoreCase(status));
    }

    // Return copies of stored collections
    public synchronized List<Agent> getAgents() {
        return new ArrayList<>(agents);
    }

    public synchronized List<Request> getRequests() {
        return new ArrayList<>(requests);
    }

    public synchronized List<Skill> getSkills() {
        return new ArrayList<>(skills);
    }

    public synchronized List<RoutingDecision> getDecisions() {
        return new ArrayList<>(decisions);
    }

    public synchronized Map<Integer, String> getPauseReasons() {
        return new HashMap<>(pauseReasons);
    }
}
