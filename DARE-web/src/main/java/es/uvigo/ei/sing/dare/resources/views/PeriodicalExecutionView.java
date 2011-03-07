package es.uvigo.ei.sing.dare.resources.views;

import java.net.URI;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
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

    @XmlElementWrapper
    @XmlElement(name = "input")
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

    public JSONObject asJSON() {
        JSONObject result = new JSONObject();
        try {
            result.put("code", code);
            result.put("creationDateMillis", creationDateMillis);
            result.put("robot", robot.toString());
            result.put("periodUnit", periodUnit);
            result.put("periodAmount", periodAmount);
            result.put("inputs", new JSONArray(inputs));
            result.put("lastExecutionResult",
                    lastExecutionResult == null ? JSONObject.NULL
                            : lastExecutionResult.toString());
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return result;
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
