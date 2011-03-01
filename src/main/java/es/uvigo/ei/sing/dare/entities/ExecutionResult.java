package es.uvigo.ei.sing.dare.entities;

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
public class ExecutionResult {

    private final URI createdFrom;

    @XmlList
    private final List<String> lines;

    private final long executionTime;

    /**
     * The number of milliseconds since 1970-01-01T00:00:00Z
     */
    private final long date;

    public ExecutionResult(){
        this(null, 0, new ArrayList<String>());
    }

    public ExecutionResult(URI createdFrom, String... lines) {
        this(createdFrom, -1, lines);
    }

    public ExecutionResult(URI createdFrom, long milliseconds, String... lines) {
        this(createdFrom, milliseconds, Arrays.asList(lines));
    }

    public ExecutionResult(URI createdFrom, Collection<? extends String> lines) {
        this(createdFrom, -1, lines);
    }

    public ExecutionResult(URI createdFrom, long executionTime,
            Collection<? extends String> lines) {
        this.createdFrom = createdFrom;
        this.lines = new ArrayList<String>(lines);
        this.executionTime = executionTime;
        this.date = new DateTime().getMillis();
    }

    public ExecutionResult withExecutionTime(long executionTime) {
        return new ExecutionResult(this.createdFrom, executionTime, this.lines);
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
