package es.uvigo.ei.sing.dare.domain;

@SuppressWarnings("serial")
public class ExecutionTimeExceededException extends Exception {

    public ExecutionTimeExceededException(String message) {
        super(message);
    }

    public ExecutionTimeExceededException(long maxTimeSeconds) {
        super("The execution took more than the maximum allowed: "
                + maxTimeSeconds + " seconds");
    }
}
