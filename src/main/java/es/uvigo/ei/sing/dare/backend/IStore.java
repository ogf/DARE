package es.uvigo.ei.sing.dare.backend;

import es.uvigo.ei.sing.dare.entities.PeriodicalExecution;
import es.uvigo.ei.sing.dare.entities.Robot;

public interface IStore {

    /**
     * Finds a periodical execution with the specified code. If not found
     * returns <code>null</code>.
     *
     * @param code
     * @return
     */
    PeriodicalExecution findPeriodicalExecution(String code);

    void save(Robot robot);

    /**
     * Finds a robot with the specified code. If not found returns
     * <code>null</code>
     *
     * @param code
     * @return
     */
    Robot find(String code);


}
