package entity;

import java.time.LocalDateTime;

public class RoutingDecision {

    private int decisionId;
    private int requestId;
    private int agentId;
    private LocalDateTime routedAt;
    private int waitSeconds;

    public RoutingDecision() {
    }

    public RoutingDecision(int decisionId, int requestId,
                           int agentId, LocalDateTime routedAt,
                           int waitSeconds) {
        setDecisionId(decisionId);
        setRequestId(requestId);
        setAgentId(agentId);
        setRoutedAt(routedAt);
        setWaitSeconds(waitSeconds);
    }

    public int getDecisionId() {
        return decisionId;
    }

    public void setDecisionId(int decisionId) {
        if (decisionId <= 0) {
            throw new IllegalArgumentException("Decision ID must be positive.");
        }
        this.decisionId = decisionId;
    }

    public int getRequestId() {
        return requestId;
    }

    public void setRequestId(int requestId) {
        if (requestId <= 0) {
            throw new IllegalArgumentException("Request ID must be positive.");
        }
        this.requestId = requestId;
    }

    public int getAgentId() {
        return agentId;
    }

    public void setAgentId(int agentId) {
        if (agentId <= 0) {
            throw new IllegalArgumentException("Agent ID must be positive.");
        }
        this.agentId = agentId;
    }

    public LocalDateTime getRoutedAt() {
        return routedAt;
    }

    public void setRoutedAt(LocalDateTime routedAt) {
        if (routedAt == null) {
            throw new IllegalArgumentException("Routed time cannot be null.");
        }
        this.routedAt = routedAt;
    }

    public int getWaitSeconds() {
        return waitSeconds;
    }

    public void setWaitSeconds(int waitSeconds) {
        if (waitSeconds < 0) {
            throw new IllegalArgumentException("Wait seconds cannot be negative.");
        }
        this.waitSeconds = waitSeconds;
    }
}