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
public class ExecutionResultView {

    private final URI createdFrom;

    @XmlElementWrapper
    @XmlElement(name = "line")
    private final List<String> resultLines;

    private final long executionTime;

    /**
     * The number of milliseconds since 1970-01-01T00:00:00Z
     */
    private final long date;

    public ExecutionResultView(){
        this(null, new DateTime(), 0, new ArrayList<String>());
    }

    public ExecutionResultView(URI createdFrom, DateTime creationTime,
            String... lines) {
        this(createdFrom, creationTime, -1, lines);
    }

    public ExecutionResultView(URI createdFrom, DateTime creationTime,
            long milliseconds, String... lines) {
        this(createdFrom, creationTime, milliseconds, Arrays.asList(lines));
    }

    public ExecutionResultView(URI createdFrom, DateTime creationTime,
            Collection<? extends String> lines) {
        this(createdFrom, creationTime, -1, lines);
    }

    public ExecutionResultView(URI createdFrom, DateTime creationTime,
            long executionTime, Collection<? extends String> resutLines) {
        this.createdFrom = createdFrom;
        this.resultLines = new ArrayList<String>(resutLines);
        this.executionTime = executionTime;
        this.date = creationTime.getMillis();
    }

    public JSONObject asJSON() {
        JSONObject result = new JSONObject();
        try {
            result.put("createdFrom", createdFrom.toString());
            result.put("resultLines", resultLines);
            result.put("executionTime", executionTime);
            result.put("date", date);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    public static ExecutionResultView fromJSON(JSONObject object){
        try {
            return new ExecutionResultView(URI.create(object
                    .getString("createdFrom")),
                    new DateTime(object.getLong("date")),
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

    public URI getCreatedFrom() {
        return createdFrom;
    }

    public List<String> getResultLines() {
        return Collections.unmodifiableList(resultLines);
    }

    public long getExecutionTime() {
        return executionTime;
    }

    public DateTime getDate() {
        return new DateTime(date);
    }

}
