package entity;

import java.time.LocalDateTime;

public class CallRequest extends Request {

    public CallRequest() {
        super();
    }

    public CallRequest(int requestId, int customerId,
                       int requiredSkillId, String priority,
                       LocalDateTime receivedAt, String status) {

        super(requestId, customerId, requiredSkillId,
              priority, "Call", receivedAt, status);
    }

    @Override
    public int getRoutingWeight() {
        return 3;
    }
}