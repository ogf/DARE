package es.uvigo.ei.sing.dare.backend;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import es.uvigo.ei.sing.dare.entities.ExecutionPeriod;
import es.uvigo.ei.sing.dare.entities.ExecutionPeriod.Unit;
import es.uvigo.ei.sing.dare.entities.PeriodicalExecution;
import es.uvigo.ei.sing.dare.entities.Robot;

public class ConfigurationStub extends Configuration {

    public static final String EXISTENT_PERIODICAL_EXECUTION_CODE = "abc";

    private static final IStore store = new IStore() {

        private Map<String, Robot> robotsByCode = new HashMap<String, Robot>();

        @Override
        public PeriodicalExecution findPeriodicalExecution(String code) {
            if (code.equals(EXISTENT_PERIODICAL_EXECUTION_CODE)) {
                return new PeriodicalExecution(
                        Robot.createFromMinilanguage("url"),
                        ExecutionPeriod.create(1, Unit.DAYS),
                        Arrays.asList("www.google.com"));
            }
            return null;
        }

        @Override
        public void save(Robot robot) {
            robotsByCode.put(robot.getCode(), robot);
        }

        @Override
        public Robot find(String code) {
            return robotsByCode.get(code);
        }
    };

    private static IStore stubExecutionsStore() {
        return store;
    }


    public ConfigurationStub() {
        super(stubExecutionsStore());
    }

}
