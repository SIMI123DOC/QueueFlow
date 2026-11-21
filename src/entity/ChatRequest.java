package entity;

import java.time.LocalDateTime;

public class ChatRequest extends Request {

    public ChatRequest() {
        super();
    }

    public ChatRequest(int requestId, int customerId,
                       int requiredSkillId, String priority,
                       LocalDateTime receivedAt, String status) {

        super(requestId, customerId, requiredSkillId,
              priority, "Chat", receivedAt, status);
    }

    @Override
    public int getRoutingWeight() {
        return 2;
    }
}