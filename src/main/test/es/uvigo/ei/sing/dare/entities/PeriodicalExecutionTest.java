package es.uvigo.ei.sing.dare.entities;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import es.uvigo.ei.sing.dare.entities.ExecutionPeriod.Unit;

public class PeriodicalExecutionTest {

    private Robot robot = Robot.createFromMinilanguage("url");

    private ExecutionPeriod examplePeriod = ExecutionPeriod.create(20,
            Unit.HOURS);

    private String[] exampleInputs = { "www.google.com", "www.twitter.com" };

    private PeriodicalExecution periodicalExecution = robot.createPeriodical(
            examplePeriod, exampleInputs);

    @Test(expected = IllegalArgumentException.class)
    public void thePeriodMustBeNotNull() {
        robot.createPeriodical(null, exampleInputs);
    }

    @Test(expected = IllegalArgumentException.class)
    public void theInputsArrayMustBeNotNull() {
        robot.createPeriodical(examplePeriod, (String[]) null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void theInputsListMustBeNotNull() {
        robot.createPeriodical(examplePeriod, (List<String>) null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void allTheInputsMustBeNotNull() {
        robot.createPeriodical(examplePeriod, new String[] { "www.google.com",
                null });
    }

    @Test
    public void thePeriodicalExecutionHasItsOwnCode() {
        assertThat(periodicalExecution.getCode(), not(equalTo(robot.getCode())));
    }

    @Test
    public void thePeriodicalExecutionHasACreationTime(){
        assertNotNull(periodicalExecution.getCreationTime());
    }

    @Test
    public void aPeriodicalExecutionContainsTheRobotThatCreatedIt() {
        assertThat(periodicalExecution.getRobot().getCode(),
                equalTo(robot.getCode()));
    }

    @Test
    public void thePeriodicalExecutionHasTheInputsWithWhichItWasCreated() {
        assertThat(periodicalExecution.getInputs(),
                equalTo(Arrays.asList(exampleInputs)));
    }

    @Test
    public void theProvidedListOfInputsIsCopiedToAvoidUndesirableSideEffects() {
        List<String> inputs = new ArrayList<String>();
        inputs.add("www.google.com");
        periodicalExecution = robot.createPeriodical(examplePeriod, inputs);
        inputs.add("www.yahoo.com");
        assertThat(periodicalExecution.getInputs(), not(equalTo(inputs)));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void theInputsOfAPeriodicalExecutionCannotBeModified() {
        periodicalExecution.getInputs().add("www.yahoo.com");
    }

    @Test
    public void thePeriodicalExecutionHasThePeriodWithWhichItWasCreated() {
        assertThat(periodicalExecution.getExecutionPeriod().getAmount(),
                equalTo(examplePeriod.getAmount()));
        assertThat(periodicalExecution.getExecutionPeriod().getUnitType(),
                equalTo(examplePeriod.getUnitType()));
    }

    @Test
    public void initiallyTheLastExecutionResultIsNull() {
        assertThat(periodicalExecution.getLastExecutionResult(), nullValue());
    }

}
