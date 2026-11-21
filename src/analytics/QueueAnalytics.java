
package analytics;

import entity.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class QueueAnalytics {

    // 1. Get agents based on their status
    public List<Agent> GetAgentsByStatus(
            List<Agent> agents, String status) {

        if (agents == null || status == null) {
            return Collections.emptyList();
        }

        return agents.stream()
                .filter(Objects::nonNull)
                .filter(agent ->
                        status.equalsIgnoreCase(agent.getStatus()))
                .collect(Collectors.toList());
    }

    // 2. Get queue depth for each skill
    public Map<Integer, Long> GetQueueDepthBySkill(
            List<Request> requests, List<Skill> skills) {

        if (requests == null || requests.isEmpty()
                || skills == null || skills.isEmpty()) {
            return Collections.emptyMap();
        }

        Set<Integer> skillIds = skills.stream()
                .filter(Objects::nonNull)
                .map(Skill::getSkillId)
                .collect(Collectors.toSet());

        return requests.stream()
                .filter(Objects::nonNull)
                .filter(QueueAnalytics::isWaiting)
                .filter(request -> skillIds.contains(
                        request.getRequiredSkillId()))
                .collect(Collectors.groupingBy(
                        Request::getRequiredSkillId,
                        TreeMap::new,
                        Collectors.counting()
                ));
    }

    // 3. Get requests that have exceeded the SLA time
    public List<Request> GetSlaBreachingRequests(
            List<Request> requests,
            LocalDateTime now,
            long slaTargetSeconds) {

        if (requests == null || now == null || slaTargetSeconds < 0) {
            return Collections.emptyList();
        }

        return requests.stream()
                .filter(Objects::nonNull)
                .filter(QueueAnalytics::isWaiting)
                .filter(request ->
                        Duration.between(
                                request.getReceivedAt(),
                                now
                        ).compareTo(Duration.ofSeconds(slaTargetSeconds)) > 0)
                .collect(Collectors.toList());
    }

    // 4. Find skills with the highest average waiting time
    public List<SkillWaitSummary> GetWorstWaitSkills(
            List<Skill> skills,
            List<Request> requests,
            int topN) {

        if (skills == null || skills.isEmpty()
                || requests == null || topN <= 0) {
            return Collections.emptyList();
        }

        LocalDateTime now = LocalDateTime.now();
        Set<Integer> skillIds = skills.stream()
                .filter(Objects::nonNull)
                .map(Skill::getSkillId)
                .collect(Collectors.toSet());

        Map<Integer, List<Request>> groupedRequests =
                requests.stream()
                        .filter(Objects::nonNull)
                        .filter(QueueAnalytics::isWaiting)
                        .filter(request -> skillIds.contains(
                                request.getRequiredSkillId()))
                        .collect(Collectors.groupingBy(
                                Request::getRequiredSkillId
                        ));

        return groupedRequests.entrySet()
                .stream()
                .map(entry -> {

                    int skillId = entry.getKey();

                    List<Request> skillRequests = entry.getValue();

                    double averageWait = skillRequests.stream()
                            .mapToLong(request ->
                                    Math.max(
                                            0,
                                            Duration.between(
                                                    request.getReceivedAt(),
                                                    now
                                            ).getSeconds()
                                    ))
                            .average()
                            .orElse(0);

                    return new SkillWaitSummary(
                            skillId,
                            averageWait,
                            skillRequests.size()
                    );
                })
                .sorted(
                        Comparator.comparingDouble(
                                SkillWaitSummary::getAverageWaitSeconds
                        ).reversed()
                        .thenComparingInt(SkillWaitSummary::getSkillId)
                )
                .limit(topN)
                .collect(Collectors.toList());
    }

    private static boolean isWaiting(Request request) {
        String status = request.getStatus();
        return "Queued".equalsIgnoreCase(status)
                || "Escalated".equalsIgnoreCase(status);
    }
}
