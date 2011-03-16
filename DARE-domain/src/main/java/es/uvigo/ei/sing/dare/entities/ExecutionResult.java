package es.uvigo.ei.sing.dare.entities;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang.Validate;
import org.joda.time.DateTime;

public class ExecutionResult {

    public static ExecutionResult create(String code, Robot robot,
            String... resultLines) {
        return new ExecutionResult(code, Type.ROBOT, robot.getCode(), 0, resultLines);
    }

    public static ExecutionResult create(String code,
            PeriodicalExecution parent, String... resultLines) {
        return new ExecutionResult(code, Type.PERIODICAL, parent.getCode(), 0,
                resultLines);
    }

    public enum Type {
        ROBOT, PERIODICAL;
    }

    private final String code;

    private final Type type;

    private final String createdFromCode;

    private final DateTime creationTime;

    private final long executionTimeMilliseconds;

    private final List<String> resultLines;

    private ExecutionResult(String code, Type type, String createdFromCode,
            long executionTimeMilliseconds, String[] resultLines) {
        this(code, new DateTime(), type, createdFromCode,
                executionTimeMilliseconds, resultLines);
    }

    public ExecutionResult(String code, DateTime creationTime, Type type,
            String createdFromCode, long executionTimeMilliseconds,
            String[] resultLines) {
        Validate.notNull(code);
        Validate.notNull(creationTime);
        Validate.notNull(type);
        Validate.notNull(createdFromCode);
        Validate.isTrue(executionTimeMilliseconds >= 0);
        Validate.notNull(resultLines);
        this.code = code;
        this.creationTime = creationTime;
        this.createdFromCode = createdFromCode;
        this.type = type;
        this.executionTimeMilliseconds = executionTimeMilliseconds;
        this.resultLines = Collections.unmodifiableList(Arrays
                .asList(resultLines));
    }

    public String getCode() {
        return code;
    }

    public Type getType() {
        return type;
    }

    public String getCreatedFromCode() {
        return createdFromCode;
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
        return new ExecutionResult(this.code, this.type, this.createdFromCode,
                executionTime, this.resultLines.toArray(new String[0]));
    }

}
