package es.uvigo.ei.sing.dare.entities;

import static es.uvigo.ei.sing.dare.util.StringUtil.quote;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.Validate;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.joda.time.DateTime;
import org.joda.time.Period;

public class ExecutionPeriod {

    public enum Unit {
        DAYS("day", "days", "d") {
            @Override
            public Period byAmount(int amount) {
                return Period.days(amount);
            }
        },
        HOURS("hours", "hour", "h") {
            @Override
            public Period byAmount(int amount) {
                return Period.hours(amount);
            }
        },
        MINUTES("minutes", "minute", "m") {
            @Override
            public Period byAmount(int amount) {
                return Period.minutes(amount);
            }
        };

        public static Unit parseUnit(String unit) {
            Validate.notNull(unit);
            unit = unit.trim();
            for (Unit each : Unit.values()) {
                if (each.isMatchedBy(unit)) {
                    return each;
                }
            }
            throw new IllegalArgumentException(quote(unit)
                    + " is not a valid unit");
        }

        private final String[] possibleRepresentations;

        private Unit(String... possibleRepresentations) {
            this.possibleRepresentations = possibleRepresentations;
        }

        private boolean isMatchedBy(String unit) {
            for (String each : possibleRepresentations) {
                if (each.equalsIgnoreCase(unit)) {
                    return true;
                }
            }
            return false;
        }

        public abstract Period byAmount(int amount);
    }

    private static Pattern periodSpecificationPattern = Pattern
            .compile("\\s*(-?\\d+)\\s*(\\w+)\\s*");

    public static ExecutionPeriod parse(String specification) {
        Validate.notNull(specification);

        Matcher matcher = periodSpecificationPattern.matcher(specification);
        boolean matches = matcher.matches();
        Validate.isTrue(matches, quote(specification) + " doesn't match: "
                        + periodSpecificationPattern.pattern());

        String amount = matcher.group(1);
        String unit = matcher.group(2);
        return new ExecutionPeriod(Integer.parseInt(amount),
                Unit.parseUnit(unit));
    }

    public static ExecutionPeriod create(int amount, Unit unit) {
        return new ExecutionPeriod(amount, unit);
    }

    private final int amount;

    private final Unit unit;

    public ExecutionPeriod(int amount, Unit unit) {
        Validate.isTrue(amount > 0);
        Validate.notNull(unit);
        this.amount = amount;
        this.unit = unit;
    }

    public int getAmount() {
        return amount;
    }

    public Unit getUnitType() {
        return unit;
    }

    private Period getPeriod() {
        return unit.byAmount(amount);
    }

    public DateTime calculateNextExecution(DateTime lastExecutionTime) {
        return lastExecutionTime.plus(getPeriod());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ExecutionPeriod) {
            ExecutionPeriod other = (ExecutionPeriod) obj;
            return new EqualsBuilder().append(this.amount, other.amount)
                    .append(this.unit, other.unit).isEquals();
        }
        return false;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(amount).append(unit).toHashCode();
    }

}
