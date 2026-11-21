
package stream;

import dao.QueueFlowServiceImpl;
import entity.Request;
import entity.Skill;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class RequestProducer implements Runnable {

    private final QueueFlowServiceImpl service;
    private final int customerStart;
    private final int count;
    private final BlockingQueue<Request> output;
    private final Semaphore queueSlots;
    private final long intervalMillis;

    public RequestProducer(
            QueueFlowServiceImpl service,
            int customerStart,
            int count,
            BlockingQueue<Request> output) {
        this(service, customerStart, count, output, null, 0);
    }

    // Constructor
    public RequestProducer(
            QueueFlowServiceImpl service,
            int customerStart,
            int count,
            BlockingQueue<Request> output,
            Semaphore queueSlots) {
        this(service, customerStart, count, output, queueSlots, 0);
    }

    public RequestProducer(
            QueueFlowServiceImpl service,
            int customerStart,
            int count,
            BlockingQueue<Request> output,
            Semaphore queueSlots,
            long intervalMillis) {

        this.service = service;
        this.customerStart = customerStart;
        this.count = count;
        this.output = output;
        this.queueSlots = queueSlots;
        if (intervalMillis < 0) {
            throw new IllegalArgumentException(
                    "Request interval cannot be negative.");
        }
        this.intervalMillis = intervalMillis;
    }

    // Generate random customer requests
    @Override
    public void run() {
        List<Skill> skills = service.getSkills();
        if (skills.isEmpty() && count > 0) {
            throw new IllegalStateException(
                    "At least one skill must be registered before producing requests.");
        }

        String[] priorities = {
                "Low", "Medium", "High"
        };

        String[] channels = {
                "Call", "Chat"
        };

        for (int i = 0; i < count; i++) {
            boolean slotAcquired = false;
            try {
                if (queueSlots != null) {
                    queueSlots.acquire();
                    slotAcquired = true;
                }

                int customerId = customerStart + i;

                int skillId = skills.get(
                        ThreadLocalRandom.current().nextInt(skills.size()))
                        .getSkillId();

                String priority =
                        priorities[ThreadLocalRandom.current()
                                .nextInt(priorities.length)];

                String channel =
                        channels[ThreadLocalRandom.current()
                                .nextInt(channels.length)];

                // Create and register request
                Request request = service.SubmitRequest(
                        customerId,
                        skillId,
                        priority,
                        channel
                );

                if (queueSlots == null) {
                    output.put(request);
                } else if (!output.offer(request)) {
                    throw new IllegalStateException(
                            "Reserved request queue capacity was unavailable.");
                }
                slotAcquired = false;

                System.out.println(
                        "Request generated: ID="
                        + request.getRequestId()
                        + ", Customer=" + customerId
                        + ", Skill=" + skillId
                        + ", Priority=" + priority
                        + ", Channel=" + channel
                );
                if (intervalMillis > 0 && i + 1 < count) {
                    TimeUnit.MILLISECONDS.sleep(intervalMillis);
                }
            } catch (InterruptedException e) {
                if (slotAcquired) {
                    queueSlots.release();
                }
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                        "Request producer was interrupted.", e);
            } catch (RuntimeException e) {
                if (slotAcquired) {
                    queueSlots.release();
                }
                throw e;
            }
        }
    }
}
