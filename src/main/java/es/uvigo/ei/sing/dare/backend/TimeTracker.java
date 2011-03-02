package es.uvigo.ei.sing.dare.backend;

import es.uvigo.ei.sing.dare.entities.ExecutionResult;

public class TimeTracker {

    public interface IExecutionResultBuilder {
        ExecutionResult build();
    }

    public static ExecutionResult trackTime(IExecutionResultBuilder builder) {
        long start = System.currentTimeMillis();
        ExecutionResult built = builder.build();
        long elapsed = System.currentTimeMillis() - start;
        return built.withExecutionTime(elapsed);
    }

}
