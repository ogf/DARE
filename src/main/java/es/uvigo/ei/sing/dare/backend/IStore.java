package es.uvigo.ei.sing.dare.backend;

import es.uvigo.ei.sing.dare.entities.ExecutionResult;
import es.uvigo.ei.sing.dare.entities.PeriodicalExecution;
import es.uvigo.ei.sing.dare.entities.Robot;

public interface IStore {

    void save(Robot robot);

    /**
     * Finds a robot with the specified code. If not found returns
     * <code>null</code>
     *
     * @param code
     * @return
     */
    Robot find(String code);

    /**
     * Returns a fulfilled {@link ExecutionResult} if it has been completed,
     * i.e., a {@link Maybe} with a value. Otherwise a {@link Maybe} without an
     * associated value.
     *
     * @param executionCode
     *            the code for the associated execution
     * @return <code>null</code> if there is no execution for executionCode.
     */
    Maybe<ExecutionResult> retrieveExecution(String executionCode);

    void save(PeriodicalExecution periodicalExecution);

    /**
     * Finds a periodical execution with the specified code. If not found
     * returns <code>null</code>.
     *
     * @param code
     * @return
     */
    PeriodicalExecution findPeriodicalExecution(String code);

}
