
package entity;

public class Skill {

    private int skillId;
    private String skillName;

    // Default constructor
    public Skill() {
    }

    // Parameterized constructor
    public Skill(int skillId, String skillName) {
        setSkillId(skillId);
        setSkillName(skillName);
    }

    // Getters and Setters
    public int getSkillId() {
        return skillId;
    }

    public void setSkillId(int skillId) {
        if (skillId < 0) {
            throw new IllegalArgumentException(
                "Skill ID cannot be negative"
            );
        }
        this.skillId = skillId;
    }

    public String getSkillName() {
        return skillName;
    }

    public void setSkillName(String skillName) {
        if (skillName == null || skillName.trim().isEmpty()
                || skillName.length() > 50) {
            throw new IllegalArgumentException(
                "Skill name must contain 1 to 50 characters"
            );
        }
        this.skillName = skillName;
    }
}
