package es.uvigo.ei.sing.dare.resources.views;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

import org.apache.commons.lang.Validate;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.joda.time.DateTime;

@XmlAccessorType(XmlAccessType.FIELD)
public class ExecutionResultView {

    @XmlElementWrapper
    @XmlElement(name = "line")
    private final List<String> resultLines;

    private final long executionTime;

    /**
     * The number of milliseconds since 1970-01-01T00:00:00Z
     */
    private final long creationDateMillis;

    public ExecutionResultView() {
        this(new DateTime(), 0, Collections.<String> emptyList());
    }

    public ExecutionResultView(DateTime creationTime, long executionTime,
            Collection<? extends String> resutLines) {
        Validate.isTrue(executionTime >= 0);
        this.resultLines = new ArrayList<String>(resutLines);
        this.executionTime = executionTime;
        this.creationDateMillis = creationTime.getMillis();
    }

    public List<String> getResultLines() {
        return Collections.unmodifiableList(resultLines);
    }

    public long getExecutionTime() {
        return executionTime;
    }

    public DateTime getDate() {
        return new DateTime(creationDateMillis);
    }

    public static ExecutionResultView fromJSON(JSONObject object) {
        try {
            return new ExecutionResultView(new DateTime(
                    object.getLong("creationDateMillis")),
                    object.getLong("executionTime"),
                    asList(object.getJSONArray("resultLines")));
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    private static List<String> asList(JSONArray jsonArray) {
        List<String> result = new ArrayList<String>();
        try {
            for (int i = 0; i < jsonArray.length(); i++) {
                result.add(jsonArray.getString(i));
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    public JSONObject asJSON() {
        JSONObject result = new JSONObject();
        try {
            result.put("resultLines", resultLines);
            result.put("executionTime", executionTime);
            result.put("creationDateMillis", creationDateMillis);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return result;
    }

}
