package es.uvigo.ei.sing.dare.resources.views;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
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
            List<String> inputs, ExecutionResultView lastExecution) {
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

    private final ExecutionResultView lastExecutionResult;

    // Empty constructor for JAXB. DO NOT USE!
    public PeriodicalExecutionView() {
        this(null, 0, null, null, 0, null, null);
    }

    private PeriodicalExecutionView(String code, long creationTimeMilliseconds,
            URI robot, String unit, int amount, List<String> inputs,
            ExecutionResultView lastExecutionResult) {
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
                            : lastExecutionResult.asJSON());
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    public static PeriodicalExecutionView fromJSON(JSONObject object) {
        try {
            String code = object.getString("code");
            DateTime creationTime = new DateTime(
                    object.getLong("creationDateMillis"));
            URI robot = new URI(object.getString("robot"));
            ExecutionPeriod period = ExecutionPeriod
                    .parse(object.getString("periodAmount")
                            + object.getString("periodUnit"));
            List<String> inputs = asList(object.getJSONArray("inputs"));
            ExecutionResultView lastExecution = ExecutionResultView
                    .fromJSON(object.isNull("lastExecutionResult") ? null
                            : object.getJSONObject("lastExecutionResult"));
            return PeriodicalExecutionView.create(code, creationTime, robot,
                    period, inputs, lastExecution);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private static List<String> asList(JSONArray jsonArray) {
        try {
            List<String> result = new ArrayList<String>();
            for (int i = 0; i < jsonArray.length(); i++) {
                result.add(jsonArray.getString(i));
            }
            return result;
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
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

    public ExecutionResultView getLastExecutionResult() {
        return lastExecutionResult;
    }

}
