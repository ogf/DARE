package es.uvigo.ei.sing.dare.backend;

import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import es.uvigo.ei.sing.dare.backend.TimeTracker.IExecutionResultBuilder;
import es.uvigo.ei.sing.dare.entities.ExecutionPeriod;
import es.uvigo.ei.sing.dare.entities.ExecutionPeriod.Unit;
import es.uvigo.ei.sing.dare.entities.ExecutionResult;
import es.uvigo.ei.sing.dare.entities.PeriodicalExecution;
import es.uvigo.ei.sing.dare.entities.Robot;

public class ConfigurationStub extends Configuration {

    public static final String EXISTENT_PERIODICAL_EXECUTION_CODE = "abc";

    private ExecutorService executor = Executors.newCachedThreadPool();

    private Map<String, Future<ExecutionResult>> executions = new HashMap<String, Future<ExecutionResult>>();

    private final IStore store = new IStore() {

        private Map<String, Robot> robotsByCode = new HashMap<String, Robot>();

        private Map<String, PeriodicalExecution> periodicalsByCode = new HashMap<String, PeriodicalExecution>();

        @Override
        public void save(Robot robot) {
            robotsByCode.put(robot.getCode(), robot);
        }

        @Override
        public Robot find(String code) {
            return robotsByCode.get(code);
        }

        @Override
        public Maybe<ExecutionResult> retrieveExecution(String executionCode) {
            Future<ExecutionResult> future = executions.get(executionCode);
            if (future == null) {
                return null;
            }
            if (future.isDone()) {
                try {
                    return Maybe.value(future.get());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            return Maybe.none();
        }

        @Override
        public PeriodicalExecution findPeriodicalExecution(String code) {
            if (periodicalsByCode.containsKey(code)) {
                return periodicalsByCode.get(code);
            }
            if (code.equals(EXISTENT_PERIODICAL_EXECUTION_CODE)) {
                return new PeriodicalExecution(
                        Robot.createFromMinilanguage("url"),
                        ExecutionPeriod.create(1, Unit.DAYS),
                        Arrays.asList("www.google.com"));
            }
            return null;
        }

        @Override
        public void save(PeriodicalExecution periodicalExecution) {
            periodicalsByCode.put(periodicalExecution.getCode(),
                    periodicalExecution);
        }

    };

    private IRobotExecutor robotExecutor = new IRobotExecutor() {

        @Override
        public String submitExecution(final URI createdFrom, final Robot robot,
                final List<String> inputs) {
            String code = UUID.randomUUID().toString();
            Future<ExecutionResult> future = executor
                    .submit(resultCreation(createdFrom, robot, inputs));
            executions.put(code, future);
            return code;
        }

        private Callable<ExecutionResult> resultCreation(final URI createdFrom,
                final Robot robot, final List<String> inputs) {
            return new Callable<ExecutionResult>() {

                @Override
                public ExecutionResult call() throws Exception {
                    IExecutionResultBuilder resultBuilder = new IExecutionResultBuilder() {

                        @Override
                        public ExecutionResult build() {
                            final String[] result = robot.execute(inputs);
                            return new ExecutionResult(createdFrom, result);
                        }
                    };
                    return TimeTracker.trackTime(resultBuilder);
                }
            };
        }
    };

    public IStore getStore() {
        return store;
    }

    @Override
    public IRobotExecutor getRobotExecutor() {
        return robotExecutor;
    }
}
