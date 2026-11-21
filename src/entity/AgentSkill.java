
package entity;

public class AgentSkill {

    private int agentSkillId;
    private int agentId;
    private int skillId;
    private int proficiencyLevel;

    // Default constructor
    public AgentSkill() {
    }

    // Parameterized constructor
    public AgentSkill(int agentSkillId, int agentId,
                      int skillId, int proficiencyLevel) {
        setAgentSkillId(agentSkillId);
        setAgentId(agentId);
        setSkillId(skillId);
        setProficiencyLevel(proficiencyLevel);
    }

    // Getters and Setters
    public int getAgentSkillId() {
        return agentSkillId;
    }

    public void setAgentSkillId(int agentSkillId) {
        if (agentSkillId < 0) {
            throw new IllegalArgumentException(
                "Agent Skill ID cannot be negative"
            );
        }
        this.agentSkillId = agentSkillId;
    }

    public int getAgentId() {
        return agentId;
    }

    public void setAgentId(int agentId) {
        if (agentId <= 0) {
            throw new IllegalArgumentException(
                "Agent ID must be positive"
            );
        }
        this.agentId = agentId;
    }

    public int getSkillId() {
        return skillId;
    }

    public void setSkillId(int skillId) {
        if (skillId <= 0) {
            throw new IllegalArgumentException(
                "Skill ID must be positive"
            );
        }
        this.skillId = skillId;
    }

    public int getProficiencyLevel() {
        return proficiencyLevel;
    }

    public void setProficiencyLevel(int proficiencyLevel) {
        if (proficiencyLevel < 1 || proficiencyLevel > 5) {
            throw new IllegalArgumentException(
                "Proficiency level must be between 1 and 5"
            );
        }
        this.proficiencyLevel = proficiencyLevel;
    }
}
