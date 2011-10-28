package es.uvigo.ei.sing.dare.domain;

import es.uvigo.ei.sing.dare.entities.ExecutionResult;

public class TimeTracker {

    public interface IExecutionResultBuilder {
        ExecutionResult build() throws ExecutionTimeExceededException;
    }

    public static ExecutionResult trackTime(IExecutionResultBuilder builder)
            throws ExecutionTimeExceededException {
        long start = System.currentTimeMillis();
        ExecutionResult built = builder.build();
        long elapsed = System.currentTimeMillis() - start;
        return built.withExecutionTime(elapsed);
    }

}
