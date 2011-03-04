package es.uvigo.ei.sing.dare.backend;

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

    private static final Robot robotExample = Robot
            .createFromMinilanguage("url");

    private static final ExecutionPeriod oneDay = ExecutionPeriod.create(1,
            Unit.DAYS);

    public static final PeriodicalExecution EXISTENT_PERIODICAL_EXECUTION = new PeriodicalExecution(
            robotExample, oneDay, Arrays.asList("http://www.google.com"))
            .withHarcodedCode("test");

    public static final PeriodicalExecution PERIODICAL_EXECUTION_WITH_RESULT = new PeriodicalExecution(
            robotExample, oneDay, Arrays.asList("http://www.google.com"))
            .withHarcodedCode("test-with-periodical");
    static {
        PERIODICAL_EXECUTION_WITH_RESULT.receiveLastResult(ExecutionResult
                .create("test-result", PERIODICAL_EXECUTION_WITH_RESULT,
                        "result-line-1",
                        "result-line-2"));
    }

    private static final PeriodicalExecution[] existent = {
            EXISTENT_PERIODICAL_EXECUTION, PERIODICAL_EXECUTION_WITH_RESULT };

    private ExecutorService executor = Executors.newCachedThreadPool();

    private Map<String, Future<ExecutionResult>> executions = new HashMap<String, Future<ExecutionResult>>();

    private final IStore store = new IStore() {

        private Map<String, Robot> robotsByCode = new HashMap<String, Robot>();

        private Map<String, PeriodicalExecution> periodicalsByCode = new HashMap<String, PeriodicalExecution>();

        private Map<String, ExecutionResult> previousResultsByCode = new HashMap<String, ExecutionResult>();

        {
            for (PeriodicalExecution each : existent) {
                periodicalsByCode.put(each.getCode(), each);
                robotsByCode.put(each.getRobot().getCode(), each.getRobot());
                ExecutionResult lastExecutionResult = each
                        .getLastExecutionResult();
                if (lastExecutionResult != null) {
                    previousResultsByCode.put(lastExecutionResult.getCode(),
                            lastExecutionResult);
                }
            }
        }

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
            if (previousResultsByCode.containsKey(executionCode)) {
                return Maybe.value(previousResultsByCode.get(executionCode));
            }

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
        public String submitExecution(final Robot robot,
                final List<String> inputs) {
            String code = UUID.randomUUID().toString();
            Future<ExecutionResult> future = executor.submit(resultCreation(
                    code, robot, inputs));
            executions.put(code, future);
            return code;
        }

        private Callable<ExecutionResult> resultCreation(final String code,
                final Robot robot,
                final List<String> inputs) {
            return new Callable<ExecutionResult>() {

                @Override
                public ExecutionResult call() throws Exception {
                    IExecutionResultBuilder resultBuilder = new IExecutionResultBuilder() {

                        @Override
                        public ExecutionResult build() {
                            final String[] result = robot.execute(inputs);
                            return ExecutionResult.create(code, robot, result);
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
