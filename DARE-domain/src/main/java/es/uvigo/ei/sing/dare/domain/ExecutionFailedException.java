package es.uvigo.ei.sing.dare.domain;

@SuppressWarnings("serial")
public class ExecutionFailedException extends Exception {

    public ExecutionFailedException(String message) {
        super(message);
    }

    public ExecutionFailedException(Exception cause) {
        super(cause.getMessage(), cause);
    }

}
