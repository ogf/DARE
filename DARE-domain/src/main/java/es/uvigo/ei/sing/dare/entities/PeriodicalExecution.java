package es.uvigo.ei.sing.dare.entities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.apache.commons.lang.Validate;
import org.joda.time.DateTime;

public class PeriodicalExecution {

    private final String code;

    private final DateTime creationTime;

    private final String robotCode;

    private final ExecutionPeriod executionPeriod;

    private final List<String> inputs;

    private ExecutionResult lastExecution;

    public PeriodicalExecution(Robot robot,
            ExecutionPeriod executionPeriod,
            List<String> inputs) {
        this(new DateTime(), robot.getCode(), executionPeriod, inputs);
    }

    private PeriodicalExecution(DateTime creationTime, String robotCode,
            ExecutionPeriod executionPeriod, List<String> inputs) {
        this(UUID.randomUUID().toString(), creationTime, robotCode,
                executionPeriod, inputs);
    }

    public PeriodicalExecution(String code, DateTime creationTime,
            String robotCode,
            ExecutionPeriod executionPeriod, List<String> inputs) {
        Validate.notNull(code);
        Validate.notNull(creationTime);
        Validate.notNull(robotCode);
        Validate.notNull(inputs);
        this.code = code;
        this.creationTime = creationTime;
        this.robotCode = robotCode;
        this.inputs = Collections
                .unmodifiableList(new ArrayList<String>(inputs));
        this.executionPeriod = executionPeriod;
    }

    public String getCode() {
        return code;
    }

    public DateTime getCreationTime() {
        return creationTime;
    }

    public String getRobotCode() {
        return robotCode;
    }

    public List<String> getInputs() {
        return inputs;
    }

    public ExecutionPeriod getExecutionPeriod() {
        return executionPeriod;
    }

    public ExecutionResult getLastExecutionResult() {
        return lastExecution;
    }

    public void receiveLastResult(ExecutionResult last) {
        this.lastExecution = last;
    }

    public PeriodicalExecution withHarcodedCode(String hardcodedCode) {
        return new PeriodicalExecution(hardcodedCode, creationTime, robotCode,
                executionPeriod, inputs);
    }

}
