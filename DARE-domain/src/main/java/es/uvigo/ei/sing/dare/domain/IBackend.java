package es.uvigo.ei.sing.dare.domain;

import java.io.Closeable;
import java.util.List;

import es.uvigo.ei.sing.dare.entities.ExecutionResult;
import es.uvigo.ei.sing.dare.entities.PeriodicalExecution;
import es.uvigo.ei.sing.dare.entities.Robot;

/**
 * Represents the back-end of the application. It's responsible of storing the
 * information and allowing to retrieve it. It also dispatches execution
 * petitions.
 */
public interface IBackend extends Closeable {

    void save(Robot robot);

    /**
     * Finds a robot with the specified code. If not found returns
     * <code>null</code>
     * @param code
     * @return <code>null</code> if not found. Otherwise the robot.
     */
    Robot find(String code);

    /**
     * Submits an execution with the specified robot. The robot provided is
     * created. It doesn't execute immediately, so it's not blocking.
     * {@link retrieveExecution} must be used to retrieve the result of the
     * execution afterwards.
     *
     * @param robot
     *            the robot to be executed
     * @param inputs
     *            the inputs for the execution
     * @return an executionCode that can be used later to retrieve the
     *         {@link ExecutionResult}
     * @see IBackend#retrieveExecution(String)
     */
    public String submitExecution(Robot robot, List<String> inputs);

    /**
     * The same behavior as {@link IBackend#submitExecution(Robot, List)} but
     * receives the code of an existent robot.
     *
     * @param existentRobotCode
     * @param inputs
     * @return <code>null</code> if the robot doesn't exist
     * @see IBackend#submitExecution(Robot, List)
     * @see IBackend#retrieveExecution(String)
     */
    public String submitExecutionForExistentRobot(String existentRobotCode, List<String> inputs);

    /**
     * Returns a fulfilled {@link ExecutionResult} if it has been completed,
     * i.e., a {@link Maybe} with a value. Otherwise a {@link Maybe} without an
     * associated value.
     *
     * @param executionCode
     *            the code for the associated execution. It's the handle
     *            resulting of submitting an execution.
     * @return <code>null</code> if there is no execution for executionCode.
     * @see IBackend#submitExecution
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
