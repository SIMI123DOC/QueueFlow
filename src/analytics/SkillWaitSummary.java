
package analytics;

public final class SkillWaitSummary {

    private final int skillId;
    private final double averageWaitSeconds;
    private final int queueDepth;

    // Constructor
    public SkillWaitSummary(
            int skillId,
            double averageWaitSeconds,
            int queueDepth) {

        this.skillId = skillId;
        this.averageWaitSeconds = averageWaitSeconds;
        this.queueDepth = queueDepth;
    }

    // Get skill ID
    public int getSkillId() {
        return skillId;
    }

    // Get average waiting time
    public double getAverageWaitSeconds() {
        return averageWaitSeconds;
    }

    // Get queue depth
    public int getQueueDepth() {
        return queueDepth;
    }

    // Display summary
    @Override
    public String toString() {
        return "Skill " + skillId
                + ": depth=" + queueDepth
                + ", average wait="
                + String.format("%.1f", averageWaitSeconds)
                + " sec";
    }
}
