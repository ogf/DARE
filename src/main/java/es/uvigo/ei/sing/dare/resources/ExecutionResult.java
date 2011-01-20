package es.uvigo.ei.sing.dare.resources;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlList;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "result")
@XmlAccessorType(XmlAccessType.FIELD)
public class ExecutionResult {

    @XmlList
    private final List<String> lines;

    private final long executionTime;

    public ExecutionResult(){
        this(0, new ArrayList<String>());
    }

    public ExecutionResult(long milliseconds, String... lines) {
        this(milliseconds, Arrays.asList(lines));
    }

    public ExecutionResult(long executionTime,
            Collection<? extends String> lines) {
        this.lines = new ArrayList<String>(lines);
        this.executionTime = executionTime;
    }

    public List<String> getLines() {
        return lines;
    }

    public long getExecutionTime() {
        return executionTime;
    }

}
