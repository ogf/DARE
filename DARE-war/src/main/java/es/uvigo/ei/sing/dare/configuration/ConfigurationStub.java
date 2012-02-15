package es.uvigo.ei.sing.dare.configuration;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import es.uvigo.ei.sing.dare.domain.ExecutionFailedException;
import es.uvigo.ei.sing.dare.domain.ExecutionTimeExceededException;
import es.uvigo.ei.sing.dare.domain.IBackend;
import es.uvigo.ei.sing.dare.domain.Maybe;
import es.uvigo.ei.sing.dare.domain.MinilanguageProducer;
import es.uvigo.ei.sing.dare.domain.TimeTracker;
import es.uvigo.ei.sing.dare.domain.TimeTracker.IExecutionResultBuilder;
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

    public static final String INPUT_THAT_ALWAYS_TIMEOUTS = "it always timeouts";

    public static final String INPUT_THAT_ALWAYS_CAUSES_ERROR = "it always cause error";

    static {
        PERIODICAL_EXECUTION_WITH_RESULT.receiveLastResult(ExecutionResult
                .create("test-result", PERIODICAL_EXECUTION_WITH_RESULT,
                        PERIODICAL_EXECUTION_WITH_RESULT.getInputs(),
                        "result-line-1",
                        "result-line-2"));
    }

    private static final PeriodicalExecution[] existent = {
            EXISTENT_PERIODICAL_EXECUTION, PERIODICAL_EXECUTION_WITH_RESULT };

    private ExecutorService executor = Executors.newCachedThreadPool();

    private MinilanguageProducer producer = new MinilanguageProducer(4);

    private final IBackend store = new IBackend() {

        private Map<String, Robot> robotsByCode = new HashMap<String, Robot>();

        private Map<String, PeriodicalExecution> periodicalsByCode = new HashMap<String, PeriodicalExecution>();

        private Map<String, ExecutionResult> previousResultsByCode = new HashMap<String, ExecutionResult>();

        private Map<String, Future<ExecutionResult>> executions = new HashMap<String, Future<ExecutionResult>>();

        {
            robotsByCode.put(robotExample.getCode(), robotExample);
            for (PeriodicalExecution each : existent) {
                periodicalsByCode.put(each.getCode(), each);
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
        public String submitExecution(final Robot robot,
                final List<String> inputs) {
            save(robot);
            return enqueRobotExection(robot, inputs);
        }

        private String enqueRobotExection(final Robot robot,
                final List<String> inputs) {
            String resultCode = UUID.randomUUID().toString();
            Future<ExecutionResult> future = executor.submit(resultCreation(
                    resultCode, robot, inputs));
            executions.put(resultCode, future);
            return resultCode;
        }

        @Override
        public String submitExecutionForExistentRobot(String existentRobotCode,
                List<String> inputs) {
            Robot robot = find(existentRobotCode);
            if (robot == null) {
                return null;
            }
            return enqueRobotExection(robot, inputs);
        }

        private Callable<ExecutionResult> resultCreation(final String code,
                final Robot robot, final List<String> inputs) {
            return new Callable<ExecutionResult>() {

                @Override
                public ExecutionResult call() throws Exception {
                    IExecutionResultBuilder resultBuilder = new IExecutionResultBuilder() {

                        @Override
                        public ExecutionResult build()
                                throws ExecutionTimeExceededException,
                                ExecutionFailedException {
                            if (hasTimeoutInput(inputs)) {
                                throw new ExecutionTimeExceededException(100l);
                            } else if (hasInputCausingErrors(inputs)) {
                                throw new ExecutionFailedException(
                                        INPUT_THAT_ALWAYS_CAUSES_ERROR);
                            }
                            final String[] result = robot.execute(inputs);
                            return ExecutionResult.create(code, robot, inputs,
                                    result);
                        }

                    };
                    return TimeTracker.trackTime(resultBuilder);
                }
            };
        }

        private boolean hasTimeoutInput(Collection<? extends String> inputs) {
            return inputs.contains(INPUT_THAT_ALWAYS_TIMEOUTS);
        }

        private boolean hasInputCausingErrors(final List<String> inputs) {
            return inputs.contains(INPUT_THAT_ALWAYS_CAUSES_ERROR);
        }

        @Override
        public Maybe<ExecutionResult> retrieveExecution(String executionCode)
                throws ExecutionTimeExceededException, ExecutionFailedException {
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
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof ExecutionTimeExceededException) {
                        throw (ExecutionTimeExceededException) e.getCause();
                    }
                    if (e.getCause() instanceof ExecutionFailedException) {
                        throw (ExecutionFailedException) e.getCause();
                    }
                    throw new RuntimeException(e.getCause());
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

        @Override
        public void close() throws IOException {
        }

        @Override
        public void deleteExecution(String code) {
            executions.remove(code);
        }

        @Override
        public void deleteRobot(String code) {
            robotsByCode.remove(code);
        }

        @Override
        public void deletePeriodical(String code) {
            periodicalsByCode.remove(code);
        }

    };

    public IBackend getBackend() {
        return store;
    }

    @Override
    public ExecutorService getRobotParserExecutor() {
        return executor;
    }

    @Override
    public MinilanguageProducer getMinilanguageProducer() {
        return producer;
    }

}
