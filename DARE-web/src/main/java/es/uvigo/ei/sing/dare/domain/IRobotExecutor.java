package es.uvigo.ei.sing.dare.domain;

import java.util.List;

import es.uvigo.ei.sing.dare.entities.Robot;

public interface IRobotExecutor {

    public String submitExecution(Robot robot, List<String> inputs);

}
