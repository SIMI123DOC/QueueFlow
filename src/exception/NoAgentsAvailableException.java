package exception;

public class NoAgentsAvailableException extends Exception {
    private static final long serialVersionUID = 1L;

    public NoAgentsAvailableException() {
        super("No agents are available.");
    }

    public NoAgentsAvailableException(String message) { super(message); }

    public NoAgentsAvailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
