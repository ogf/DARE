package es.uvigo.ei.sing.dare.entities;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang.Validate;
import org.joda.time.DateTime;

public class ExecutionResult {

    public static ExecutionResult create(String code, Robot robot,
            List<String> inputs,
            String... resultLines) {
        return new ExecutionResult(code, robot.getCode(), 0, inputs,
                resultLines);
    }

    public static ExecutionResult create(String code,
            PeriodicalExecution parent, List<String> inputs,
            String... resultLines) {
        return new ExecutionResult(code, null, 0, inputs, resultLines);
    }

    private final String code;

    /**
     * It's null if it's contained inside a PeriodicalExecution
     */
    private final String optionalRobotCode;

    private final DateTime creationTime;

    private final List<String> inputs;

    private final long executionTimeMilliseconds;

    private final List<String> resultLines;

    private ExecutionResult(String code, String createdFromCode,
            long executionTimeMilliseconds, List<String> inputs,
            String[] resultLines) {
        this(code, new DateTime(), createdFromCode, executionTimeMilliseconds,
                inputs, resultLines);
    }

    public ExecutionResult(String code, DateTime creationTime,
            String optionalRobotCode, long executionTimeMilliseconds,
            List<String> inputs, String[] resultLines) {
        Validate.notNull(code);
        Validate.notNull(creationTime);
        Validate.isTrue(executionTimeMilliseconds >= 0);
        Validate.notNull(inputs);
        Validate.notNull(resultLines);
        this.code = code;
        this.creationTime = creationTime;
        this.optionalRobotCode = optionalRobotCode;
        this.executionTimeMilliseconds = executionTimeMilliseconds;
        this.inputs = Collections
                .unmodifiableList(new ArrayList<String>(inputs));
        this.resultLines = Collections.unmodifiableList(Arrays
                .asList(resultLines));
    }

    public String getCode() {
        return code;
    }

    public String getOptionalRobotCode() {
        return optionalRobotCode;
    }

    public List<String> getInputs() {
        return inputs;
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
                executionTime, this.inputs,
                this.resultLines.toArray(new String[0]));
    }

}
