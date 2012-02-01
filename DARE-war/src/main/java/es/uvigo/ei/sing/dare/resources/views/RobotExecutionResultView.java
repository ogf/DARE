package es.uvigo.ei.sing.dare.resources.views;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
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

@XmlRootElement(name = "result")
@XmlAccessorType(XmlAccessType.FIELD)
public class RobotExecutionResultView extends ExecutionResultView {

    private final URI createdFrom;

    @XmlElementWrapper
    @XmlElement(name = "input")
    private final List<String> inputs;

    public RobotExecutionResultView(){
        this(null, new DateTime(), 0, new ArrayList<String>());
    }

    public RobotExecutionResultView(URI createdFrom, DateTime creationTime,
            Collection<? extends String> inputs,
            String... lines) {
        this(createdFrom, creationTime, -1, inputs, lines);
    }

    public RobotExecutionResultView(URI createdFrom, DateTime creationTime,
            long milliseconds, Collection<? extends String> inputs,
            String... lines) {
        this(createdFrom, creationTime, milliseconds, inputs, Arrays
                .asList(lines));
    }

    public RobotExecutionResultView(URI createdFrom, DateTime creationTime,
            Collection<? extends String> inputs,
            Collection<? extends String> lines) {
        this(createdFrom, creationTime, -1, inputs, lines);
    }

    public RobotExecutionResultView(URI createdFrom, DateTime creationTime,
            long executionTime, Collection<? extends String> inputs,
            Collection<? extends String> resultLines) {
        super(creationTime, executionTime, resultLines);
        this.createdFrom = createdFrom;
        this.inputs = new ArrayList<String>(inputs);
    }

    public JSONObject asJSON() {
        JSONObject result = super.asJSON();
        try {
            result.put("createdFrom", createdFrom.toString());
            result.put("inputs", inputs);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    public static RobotExecutionResultView fromJSON(JSONObject object){
        ExecutionResultView common = ExecutionResultView
                .fromJSON(object);
        return new RobotExecutionResultView(extractGetCreatedFrom(object),
                common.getDate(), common.getExecutionTime(),
                extractInputs(object), common.getResultLines());
    }

    private static Collection<? extends String> extractInputs(JSONObject object) {
        try {
            JSONArray jsonArray = object.getJSONArray("inputs");
            List<String> result = new ArrayList<String>();
            for (int i = 0; i < jsonArray.length(); i++) {
                result.add(jsonArray.getString(i));
            }
            return result;
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    private static URI extractGetCreatedFrom(JSONObject object) {
        URI createdFrom;
        try {
            createdFrom = URI.create(object.getString("createdFrom"));
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return createdFrom;
    }


    public URI getCreatedFrom() {
        return createdFrom;
    }

    public List<String> getInputs() {
        return Collections.unmodifiableList(inputs);
    }

}
