package es.uvigo.ei.sing.dare.backend;

import java.util.Arrays;

import es.uvigo.ei.sing.dare.entities.ExecutionPeriod;
import es.uvigo.ei.sing.dare.entities.ExecutionPeriod.Unit;
import es.uvigo.ei.sing.dare.entities.PeriodicalExecution;
import es.uvigo.ei.sing.dare.entities.Robot;

public class ExecutionsStoreStub implements IExecutionsStore {

    public static final String EXISTENT_PERIODICAL_EXECUTION_CODE = "abc";

    @Override
    public PeriodicalExecution findPeriodicalExecution(String code) {
        if (code.equals(EXISTENT_PERIODICAL_EXECUTION_CODE)) {
            return new PeriodicalExecution(Robot.createFromMinilanguage("url"),
                    ExecutionPeriod.create(1, Unit.DAYS),
                    Arrays.asList("www.google.com"));
        }
        return null;
    }

}
