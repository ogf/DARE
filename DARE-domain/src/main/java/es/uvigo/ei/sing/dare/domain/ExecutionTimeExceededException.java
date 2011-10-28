package es.uvigo.ei.sing.dare.domain;

@SuppressWarnings("serial")
public class ExecutionTimeExceededException extends Exception {

    public ExecutionTimeExceededException(long maxTimeSeconds) {
        super("The execution took more than the maximum allowed: "
                + maxTimeSeconds + " seconds");
    }
}
