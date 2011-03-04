package es.uvigo.ei.sing.dare.resources.views;

import java.net.URI;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.joda.time.DateTime;

import es.uvigo.ei.sing.dare.entities.ExecutionPeriod;
import es.uvigo.ei.sing.dare.entities.ExecutionPeriod.Unit;

@XmlRootElement(name = "periodical-execution")
@XmlAccessorType(XmlAccessType.FIELD)
public class PeriodicalExecutionView {

    public static PeriodicalExecutionView create(String code,
            DateTime creationTime, URI robot, ExecutionPeriod executionPeriod,
            List<String> inputs, URI lastExecution) {
        return new PeriodicalExecutionView(code, creationTime.getMillis(),
                robot, executionPeriod.getUnitType().asString(),
                executionPeriod.getAmount(), inputs, lastExecution);
    }

    private final String code;

    private final long creationDateMillis;

    private final URI robot;

    private final String periodUnit;

    private final int periodAmount;

    @XmlElement(name = "inputs")
    private final List<String> inputs;

    private final URI lastExecutionResult;

    // Empty constructor for JAXB. DO NOT USE!
    public PeriodicalExecutionView() {
        this(null, 0, null, null, 0, null, null);
    }

    private PeriodicalExecutionView(String code, long creationTimeMilliseconds,
            URI robot, String unit, int amount, List<String> inputs,
            URI lastExecutionResult) {
        this.code = code;
        this.creationDateMillis = creationTimeMilliseconds;
        this.robot = robot;
        this.periodUnit = unit;
        this.periodAmount = amount;
        this.inputs = inputs;
        this.lastExecutionResult = lastExecutionResult;
    }

    public String getCode() {
        return code;
    }

    public long getCreationTimeMillis() {
        return creationDateMillis;
    }

    public URI getRobot() {
        return robot;
    }

    public ExecutionPeriod getExecutionPeriod() {
        return ExecutionPeriod.create(periodAmount, Unit.parseUnit(periodUnit));
    }

    public List<String> getInputs() {
        return inputs;
    }

    public URI getLastExecutionResult() {
        return lastExecutionResult;
    }

}
