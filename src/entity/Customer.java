
package entity;

public class Customer {

    private int customerId;
    private String firstName;
    private String lastName;
    private String tier;

    // Default constructor
    public Customer() {
        this.tier = "Standard";
    }

    // Parameterized constructor
    public Customer(int customerId, String firstName,
                    String lastName, String tier) {

        setCustomerId(customerId);
        setFirstName(firstName);
        setLastName(lastName);
        setTier(tier);
    }

    // Getters and Setters
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

    public String getTier() {
        return tier;
    }

    public void setTier(String tier) {
        if (tier == null || tier.trim().isEmpty()) {
            throw new IllegalArgumentException(
                "Customer tier cannot be empty"
            );
        }
        this.tier = tier;
    }
}
