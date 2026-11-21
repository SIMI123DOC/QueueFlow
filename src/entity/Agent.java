
package entity;

public class Agent {

    private int agentId;
    private String firstName;
    private String lastName;
    private String status;

    // Default constructor
    public Agent() {
        this.status = "Offline";
    }

    // Parameterized constructor
    public Agent(int agentId, String firstName,
                 String lastName, String status) {

        setAgentId(agentId);
        setFirstName(firstName);
        setLastName(lastName);
        setStatus(status);
    }

    // Getters and Setters
    public int getAgentId() {
        return agentId;
    }

    public void setAgentId(int agentId) {
        if (agentId < 0) {
            throw new IllegalArgumentException(
                "Agent ID cannot be negative"
            );
        }
        this.agentId = agentId;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        if (firstName == null || firstName.trim().isEmpty()
                || firstName.length() > 50) {
            throw new IllegalArgumentException(
                "First name must contain 1 to 50 characters"
            );
        }
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        if (lastName == null || lastName.trim().isEmpty()
                || lastName.length() > 50) {
            throw new IllegalArgumentException(
                "Last name must contain 1 to 50 characters"
            );
        }
        this.lastName = lastName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        if (status == null ||
            !(status.equalsIgnoreCase("Available") ||
              status.equalsIgnoreCase("Busy") ||
              status.equalsIgnoreCase("Offline"))) {

            throw new IllegalArgumentException(
                "Status must be Available, Busy or Offline"
            );
        }

        this.status = status;
    }
}
