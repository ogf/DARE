package es.uvigo.ei.sing.dare.entities;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang.Validate;
import org.joda.time.DateTime;

public class ExecutionResult {

    public static ExecutionResult create(String code, Robot robot,
            String... resultLines) {
        return new ExecutionResult(code, robot.getCode(), 0, resultLines);
    }

    public static ExecutionResult create(String code,
            PeriodicalExecution parent, String... resultLines) {
        return new ExecutionResult(code, null, 0, resultLines);
    }

    private final String code;

    /**
     * It's null if it's contained inside a PeriodicalExecution
     */
    private final String optionalRobotCode;

    private final DateTime creationTime;

    private final long executionTimeMilliseconds;

    private final List<String> resultLines;

    private ExecutionResult(String code, String createdFromCode,
            long executionTimeMilliseconds, String[] resultLines) {
        this(code, new DateTime(), createdFromCode, executionTimeMilliseconds,
                resultLines);
    }

    public ExecutionResult(String code, DateTime creationTime,
            String optionalRobotCode, long executionTimeMilliseconds,
            String[] resultLines) {
        Validate.notNull(code);
        Validate.notNull(creationTime);
        Validate.isTrue(executionTimeMilliseconds >= 0);
        Validate.notNull(resultLines);
        this.code = code;
        this.creationTime = creationTime;
        this.optionalRobotCode = optionalRobotCode;
        this.executionTimeMilliseconds = executionTimeMilliseconds;
        this.resultLines = Collections.unmodifiableList(Arrays
                .asList(resultLines));
    }

    public String getCode() {
        return code;
    }

    public String getOptionalRobotCode() {
        return optionalRobotCode;
    }

    public DateTime getCreationTime() {
        return creationTime;
    }

    public long getExecutionTimeMilliseconds() {
        return executionTimeMilliseconds;
    }

    public List<String> getResultLines() {
        return resultLines;
    }

    public ExecutionResult withExecutionTime(long executionTime) {
        return new ExecutionResult(this.code, this.optionalRobotCode,
                executionTime, this.resultLines.toArray(new String[0]));
    }

}
