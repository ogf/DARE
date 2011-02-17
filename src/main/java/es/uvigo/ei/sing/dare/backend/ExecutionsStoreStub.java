package es.uvigo.ei.sing.dare.backend;

import es.uvigo.ei.sing.dare.entities.PeriodicalExecution;

public class ExecutionsStoreStub implements IExecutionsStore {

    public static final String EXISTENT_PERIODICAL_EXECUTION_CODE = "abc";

    @Override
    public PeriodicalExecution findPeriodicalExecution(String code) {
        if (code.equals(EXISTENT_PERIODICAL_EXECUTION_CODE)) {
            return new PeriodicalExecution();
        }
        return null;
    }

}
