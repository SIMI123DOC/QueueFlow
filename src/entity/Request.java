
package entity;

import java.time.LocalDateTime;

public abstract class Request {

    private int requestId;
    private int customerId;
    private int requiredSkillId;
    private volatile String priority;
    private String channel;
    private volatile LocalDateTime receivedAt;
    private volatile String status;

    // Default constructor
    public Request() {
        this.status = "Queued";
        this.receivedAt = LocalDateTime.now();
    }

    // Parameterized constructor
    public Request(int requestId, int customerId,
                   int requiredSkillId, String priority,
                   String channel, LocalDateTime receivedAt,
                   String status) {

        setRequestId(requestId);
        setCustomerId(customerId);
        setRequiredSkillId(requiredSkillId);
        setPriority(priority);
        setChannel(channel);
        setReceivedAt(receivedAt);
        setStatus(status);
    }

    // Abstract method
    public abstract int getRoutingWeight();

    // Getters and Setters
    public int getRequestId() {
        return requestId;
    }

    public void setRequestId(int requestId) {
        if (requestId <= 0) {
            throw new IllegalArgumentException(
                "Request ID must be positive"
            );
        }
        this.requestId = requestId;
    }

    public int getCustomerId() {
        return customerId;
    }

    public void setCustomerId(int customerId) {
        if (customerId <= 0) {
            throw new IllegalArgumentException(
                "Customer ID must be positive"
            );
        }
        this.customerId = customerId;
    }

    public int getRequiredSkillId() {
        return requiredSkillId;
    }

    public void setRequiredSkillId(int requiredSkillId) {
        if (requiredSkillId <= 0) {
            throw new IllegalArgumentException(
                "Required Skill ID must be positive"
            );
        }
        this.requiredSkillId = requiredSkillId;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        if (priority == null || priority.trim().isEmpty()) {
            throw new IllegalArgumentException(
                "Priority cannot be empty"
            );
        }
        this.priority = priority;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        if (channel == null || channel.trim().isEmpty()) {
            throw new IllegalArgumentException(
                "Channel cannot be empty"
            );
        }
        this.channel = channel;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(LocalDateTime receivedAt) {
        if (receivedAt == null) {
            throw new IllegalArgumentException(
                "Received date cannot be null"
            );
        }
        this.receivedAt = receivedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        if (status == null || status.trim().isEmpty()) {
            throw new IllegalArgumentException(
                "Status cannot be empty"
            );
        }
        this.status = status;
    }
}
