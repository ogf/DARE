package es.uvigo.ei.sing.dare.resources;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)  
public class ExecutionResult {
	
	@XmlElement(name="line")
	private final List<String> lines;
	
	public ExecutionResult(){
		this(new ArrayList<String>());
	}
	
	public ExecutionResult(String... lines){
		this(Arrays.asList(lines));
	}

	public ExecutionResult(Collection<? extends String> lines) {
		this.lines = new ArrayList<String>(lines);
	}

	public List<String> getLines() {
		return lines;
	}

}
