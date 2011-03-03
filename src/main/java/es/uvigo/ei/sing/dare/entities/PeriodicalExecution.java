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

    private final Robot robot;

    private final ExecutionPeriod executionPeriod;

    private final List<String> inputs;

    private ExecutionResult lastExecution;

    public PeriodicalExecution(Robot robot, ExecutionPeriod executionPeriod,
            List<String> inputs) {
        this(new DateTime(), robot, executionPeriod, inputs);
    }

    private PeriodicalExecution(DateTime creationTime, Robot robot,
            ExecutionPeriod executionPeriod, List<String> inputs) {
        Validate.notNull(creationTime);
        Validate.notNull(robot);
        Validate.notNull(inputs);
        this.code = UUID.randomUUID().toString();
        this.creationTime = creationTime;
        this.robot = robot;
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

    public Robot getRobot() {
        return robot;
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

}
