package es.uvigo.ei.sing.dare.resources.views;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlList;
import javax.xml.bind.annotation.XmlRootElement;

import org.joda.time.DateTime;

@XmlRootElement(name = "result")
@XmlAccessorType(XmlAccessType.FIELD)
public class ExecutionResultView {

    private final URI createdFrom;

    @XmlList
    private final List<String> lines;

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
            long executionTime,
            Collection<? extends String> lines) {
        this.createdFrom = createdFrom;
        this.lines = new ArrayList<String>(lines);
        this.executionTime = executionTime;
        this.date = creationTime.getMillis();
    }

    public URI getCreatedFrom() {
        return createdFrom;
    }

    public List<String> getLines() {
        return Collections.unmodifiableList(lines);
    }

    public long getExecutionTime() {
        return executionTime;
    }

    public DateTime getDate() {
        return new DateTime(date);
    }

}
