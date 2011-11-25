package es.uvigo.ei.sing.dare.resources.views;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.joda.time.DateTime;

@XmlRootElement(name = "result")
@XmlAccessorType(XmlAccessType.FIELD)
public class RobotExecutionResultView extends ExecutionResultView {

    private final URI createdFrom;

    public RobotExecutionResultView(){
        this(null, new DateTime(), 0, new ArrayList<String>());
    }

    public RobotExecutionResultView(URI createdFrom, DateTime creationTime,
            String... lines) {
        this(createdFrom, creationTime, -1, lines);
    }

    public RobotExecutionResultView(URI createdFrom, DateTime creationTime,
            long milliseconds, String... lines) {
        this(createdFrom, creationTime, milliseconds, Arrays.asList(lines));
    }

    public RobotExecutionResultView(URI createdFrom, DateTime creationTime,
            Collection<? extends String> lines) {
        this(createdFrom, creationTime, -1, lines);
    }

    public RobotExecutionResultView(URI createdFrom, DateTime creationTime,
            long executionTime, Collection<? extends String> resutLines) {
        super(creationTime, executionTime, resutLines);
        this.createdFrom = createdFrom;
    }

    public JSONObject asJSON() {
        JSONObject result = super.asJSON();
        try {
            result.put("createdFrom", createdFrom.toString());
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    public static RobotExecutionResultView fromJSON(JSONObject object){
        ExecutionResultView common = ExecutionResultView
                .fromJSON(object);
        return new RobotExecutionResultView(extractGetCreatedFrom(object), common.getDate(),
                common.getExecutionTime(), common.getResultLines());
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

}
