package es.uvigo.ei.sing.dare.backend;

import es.uvigo.ei.sing.dare.entities.PeriodicalExecution;

public interface IStore {

    /**
     * Finds a periodical execution with the specified code. If not found
     * returns <code>null</code>.
     *
     * @param code
     * @return
     */
    PeriodicalExecution findPeriodicalExecution(String code);

}
