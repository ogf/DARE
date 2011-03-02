package es.uvigo.ei.sing.dare.backend;

import java.net.URI;
import java.util.List;

import es.uvigo.ei.sing.dare.entities.Robot;

public interface IRobotExecutor {

    public String submitExecution(URI createdFrom, Robot robot,
            List<String> inputs);

}
