package es.uvigo.ei.sing.dare.entities;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import org.apache.commons.lang.Validate;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.junit.Test;

import es.uvigo.ei.sing.dare.entities.ExecutionPeriod.Unit;


public class ExecutionPeriodTest {

    @Test
    public void anExecutionPeriodIsComposedOfAnAmountAndAnUnit() {
        ExecutionPeriod executionInterval = ExecutionPeriod.create(2,
                Unit.MINUTES);
        assertThat(executionInterval.getAmount(), equalTo(2));
        assertThat(executionInterval.getUnitType(), equalTo(Unit.MINUTES));
    }

    @Test(expected = IllegalArgumentException.class)
    public void theAmountMustBeGreaterThanZero() {
        ExecutionPeriod.create(0, Unit.HOURS);
    }

    @Test(expected = IllegalArgumentException.class)
    public void theAmountMustBeNotNegative() {
        ExecutionPeriod.create(-1, Unit.HOURS);
    }

    @Test(expected = IllegalArgumentException.class)
    public void theUnitIsRequired() {
        ExecutionPeriod.create(100, null);
    }

    @Test
    public void anExecutionPeriodCanBeSpecifiedWithAString() {
        String[] equivalent = { "20m", "20   m", "20 m", "20 m ", "20m ",
                "20 minute", "20 minutes", "20minutes", "20 MINUTES" };
        for (String each : equivalent) {
            ExecutionPeriod period = ExecutionPeriod.parse(each);
            String errorMessage = "error parsing: " + each;
            assertThat(errorMessage, period.getAmount(), equalTo(20));
            assertThat(errorMessage, period.getUnitType(),
                    equalTo(Unit.MINUTES));
        }
    }

    @Test
    public void notAllTheStringsCanBeParsed() {
        String[] invalid = { null, "minutes 20", "20 minutos", "a 20 m",
                "20@minutes" };
        for (String each : invalid) {
            try {
                ExecutionPeriod.parse(each);
                fail("\"" + each + "\" should be an invalid string");
            } catch (IllegalArgumentException e) {
                // ok
            }
        }
    }

    @Test
    public void throwExceptionIfTheUnitSpecificationIsInvalid() {
        String[] invalid = { null, "MI NUTES", "hor", "sec" };
        for (String each : invalid) {
            try {
                Unit.parseUnit(each);
                fail("should throw IllegalArgumentException for: " + each);
            } catch (IllegalArgumentException e) {
                // ok
            }
        }
    }

    @Test
    public void itCanTellWhenShouldBeExecutedNext() {
        ExecutionPeriod period = ExecutionPeriod.create(20, Unit.MINUTES);
        DateTime lastExecutionTime = new LocalDate(
                2010, 1, 1).toDateTimeAtStartOfDay();
        Validate.isTrue(lastExecutionTime.getMinuteOfHour() == 0);

        DateTime nextExecution = period
                .calculateNextExecution(lastExecutionTime);
        assertThat(nextExecution.getMinuteOfHour(), equalTo(20));
    }

}
