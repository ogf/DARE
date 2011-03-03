package es.uvigo.ei.sing.dare.entities;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang.Validate;
import org.joda.time.DateTime;

public class ExecutionResult {

    public static ExecutionResult create(String code, Robot robot,
            String[] resultLines) {
        return new ExecutionResult(code, robot.getCode(), 0, resultLines);
    }

    private final String code;

    private final String robotCode;

    private final DateTime creationTime;

    private final long executionTimeMilliseconds;

    private final List<String> resultLines;

    private ExecutionResult(String code, String robotCode,
            long executionTimeMilliseconds,
            String[] resultLines) {
        Validate.notNull(code);
        Validate.notNull(robotCode);
        Validate.notNull(resultLines);
        Validate.isTrue(executionTimeMilliseconds >= 0);

        this.code = code;
        this.robotCode = robotCode;
        this.creationTime = new DateTime();
        this.executionTimeMilliseconds = executionTimeMilliseconds;
        this.resultLines = Collections.unmodifiableList(Arrays
                .asList(resultLines));
    }

    public String getCode() {
        return code;
    }

    public String getRobotCode() {
        return robotCode;
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
        return new ExecutionResult(this.code, this.robotCode, executionTime,
                this.resultLines.toArray(new String[0]));
    }

}
