package exception;

public class AgentNotFoundException extends Exception {
    private static final long serialVersionUID = 1L;

    public AgentNotFoundException() {
        super("Agent was not found.");
    }

    public AgentNotFoundException(String message) { super(message); }

    public AgentNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
