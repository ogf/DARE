package es.uvigo.ei.sing.dare.client;

import java.net.URI;
import java.util.Collection;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriBuilder;

import org.apache.commons.lang.Validate;
import org.codehaus.jettison.json.JSONObject;
import org.w3c.dom.Document;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.filter.GZIPContentEncodingFilter;
import com.sun.jersey.api.client.filter.LoggingFilter;
import com.sun.jersey.core.util.MultivaluedMapImpl;

import es.uvigo.ei.sing.dare.configuration.Configuration;
import es.uvigo.ei.sing.dare.resources.views.PeriodicalExecutionView;
import es.uvigo.ei.sing.dare.resources.views.RobotExecutionResultView;
import es.uvigo.ei.sing.dare.resources.views.RobotJSONView;

public class DARE {


    public static DARE build(String url, int port) {
        return build(url, port, true);
    }

    public static DARE build(String url, int port, boolean withLog) {
        return build(url, port, MediaType.APPLICATION_JSON_TYPE, withLog);
    }

    public static DARE build(String url, int port, MediaType mediaType,
            boolean withLog) {
        URI uri = UriBuilder.fromUri(url).port(port).build();
        return new DARE(buildClient(withLog), uri, mediaType);
    }

    private static Client buildClient(boolean withLog) {
        Client result = new Client();
        result.addFilter(new GZIPContentEncodingFilter());
        if (withLog) {
            result.addFilter(new LoggingFilter());
        }
        return result;
    }

    private final Client client;
    private final URI baseURI;
    private final MediaType mediaType;

    public DARE(Client client, URI baseURI, MediaType mediaType) {
        Validate.notNull(client);
        Validate.notNull(baseURI);
        Validate.notNull(mediaType);
        this.client = client;
        this.baseURI = baseURI;
        this.mediaType = mediaType;
    }

    private URIPoller cachedPoller = null;

    private URIPoller getPoller() {
        if (cachedPoller != null) {
            return cachedPoller;
        }
        return cachedPoller = new URIPoller(client, mediaType);
    }

    private WebResource getBaseRobot() {
        return client.resource(baseURI).path(Configuration.ROBOT_BASE_PATH);
    }

    private WebResource getPeriodicalExecutionBase() {
        return client.resource(baseURI).path(
                Configuration.PERIODICAL_EXECUTION_BASE_PATH);
    }

    public <T> T doGet(URI uri, Class<T> type) {
        return doGet(uri, type, mediaType);
    }

    public <T> T doGet(URI uri, Class<T> returnType, MediaType mediaType) {
        if (MediaType.APPLICATION_JSON_TYPE.isCompatible(mediaType)) {
            if (PeriodicalExecutionView.class.equals(returnType)) {
                return returnType.cast(PeriodicalExecutionView.fromJSON(doGet(
                        uri, JSONObject.class, mediaType)));
            }
            if (RobotExecutionResultView.class.equals(returnType)) {
                return returnType.cast(RobotExecutionResultView.fromJSON(doGet(
                        uri, JSONObject.class)));
            }
        }
        URIPoller poller = getPoller();
        return poller.retrieve(uri, returnType, mediaType);
    }

    public <T> T getRobot(String robotCode, Class<T> type, MediaType mediaType) {
        return doGet(getBaseRobot().getUriBuilder().path("{code}").build(robotCode), type,
                mediaType);
    }

    public <T> T getRobot(String robotCode, Class<T> type) {
        return getRobot(robotCode, type, mediaType);
    }

    public RobotJSONView getRobot(String robotCode) {
        return getRobot(robotCode, RobotJSONView.class,
                MediaType.APPLICATION_JSON_TYPE);
    }

    public <T> T getPeriodicalExecution(String periodicalExecutionCode,
            Class<T> type) {
        return getPeriodicalExecution(periodicalExecutionCode, type, mediaType);
    }

    public <T> T getPeriodicalExecution(String periodicalExecutionCode, Class<T> type,
            MediaType mediaType) {
        return doGet(getPeriodicalExecutionBase().getUriBuilder()
                        .path("result/{code}").build(periodicalExecutionCode),
                type);
    }

    public PeriodicalExecutionView getPeriodicalExecution(String code) {
        return getPeriodicalExecution(code, PeriodicalExecutionView.class);
    }

    public <T> T getExecutionResult(String executionResultCode, Class<T> type) {
        URI uri = UriBuilder.fromUri(baseURI)
                .path(Configuration.EXECUTION_RESULT_BASE_URL).path("{code}")
                .build(executionResultCode);
        return getExecutionResult(uri, type);
    }

    private <T> T getExecutionResult(URI location, Class<T> returnType) {
        return doGet(location, returnType);
    }

    public RobotExecutionResultView getExecutionResult(
            String executionResultCode) {
        return getExecutionResult(executionResultCode,
                RobotExecutionResultView.class);
    }

    public ClientResponse createRobotAndReturnLocation(String robotInMinilanguage) {
        MultivaluedMap<String, String> map = new MultivaluedMapImpl();
        map.add("minilanguage", robotInMinilanguage);
        return getCreateRobotResource()
                .type(MediaType.APPLICATION_FORM_URLENCODED_TYPE)
                .post(ClientResponse.class, map);
    }

    public ClientResponse createRobotAndReturnLocation(Document robot) {
        return getCreateRobotResource()
                .type(MediaType.APPLICATION_XML_TYPE)
                .post(ClientResponse.class, robot);
    }

    public RobotJSONView createRobot(Document robot) {
        ClientResponse response = createRobotAndReturnLocation(robot);
        return robotFromResponseWithLocation(response);
    }

    public RobotJSONView createRobot(String robotMinilanguage) {
        ClientResponse response = createRobotAndReturnLocation(robotMinilanguage);
        return robotFromResponseWithLocation(response);
    }

    private RobotJSONView robotFromResponseWithLocation(ClientResponse response) {
        checkSuccess(response);
        return doGet(response.getLocation(), RobotJSONView.class,
                MediaType.APPLICATION_JSON_TYPE);
    }

    private PeriodicalExecutionView getPeriodicalFromResponseWithLocation(
            ClientResponse response) {
        checkSuccess(response);
        return doGet(response.getLocation(), PeriodicalExecutionView.class);
    }

    private void checkSuccess(ClientResponse response)
            throws UniformInterfaceException {
        if (response.getClientResponseStatus().getStatusCode() >= 300) {
            throw new UniformInterfaceException(response);
        }
    }

    private WebResource getCreateRobotResource() {
        return getBaseRobot().path("create");
    }

    public ClientResponse createPeriodicalExecutionAndReturnLocation(URI robotResource,
            String period, Collection<? extends String> inputs) {
        return createPeriodicalExecutionAndReturnLocation(robotResource, period,
                inputs.toArray(new String[0]));
    }

    public ClientResponse createPeriodicalExecutionAndReturnLocation(RobotJSONView robot,
            String period, Collection<? extends String> inputs) {
        return createPeriodicalExecutionAndReturnLocation(getRobotURI(robot), period, inputs);
    }

    public ClientResponse createPeriodicalExecutionAndReturnLocation(RobotJSONView robot,
            final String period, final String... inputs) {
        return createPeriodicalExecutionAndReturnLocation(getRobotURI(robot), period, inputs);
    }

    public ClientResponse createPeriodicalExecutionAndReturnLocation(String robotCode,
            String period, Collection<? extends String> inputs) {
        return createPeriodicalExecutionAndReturnLocation(getRobotURI(robotCode), period, inputs);
    }

    public ClientResponse createPeriodicalExecutionAndReturnLocation(String robotCode,
            final String period, final String... inputs) {
        return createPeriodicalExecutionAndReturnLocation(getRobotURI(robotCode), period, inputs);
    }

    @SuppressWarnings("serial")
    public ClientResponse createPeriodicalExecutionAndReturnLocation(URI robotResource,
            final String period, final String... inputs) {
        WebResource periodicalCreationResource = client.resource(robotResource).path(
                "periodical");
        ClientResponse response = periodicalCreationResource.post(
                ClientResponse.class, new MultivaluedMapImpl() {
                    {
                        add("period", period);
                        for (String each : inputs) {
                            add("input", each);
                        }
                    }
                });
        return response;
    }

    public PeriodicalExecutionView createPeriodicalExecution(URI robotResource,
            final String period, final String... inputs) {
        ClientResponse response = createPeriodicalExecutionAndReturnLocation(
                robotResource, period, inputs);
        return getPeriodicalFromResponseWithLocation(response);
    }

    public PeriodicalExecutionView createPeriodicalExecution(URI robotResource,
            String period, Collection<? extends String> inputs) {
        return createPeriodicalExecution(robotResource, period,
                inputs.toArray(new String[0]));
    }

    public PeriodicalExecutionView createPeriodicalExecution(
            RobotJSONView robot, String period,
            Collection<? extends String> inputs) {
        return createPeriodicalExecution(getRobotURI(robot), period, inputs);
    }

    public PeriodicalExecutionView createPeriodicalExecution(
            RobotJSONView robot, final String period, final String... inputs) {
        return createPeriodicalExecution(getRobotURI(robot), period, inputs);
    }

    public PeriodicalExecutionView createPeriodicalExecution(String robotCode,
            String period, Collection<? extends String> inputs) {
        return createPeriodicalExecution(getRobotURI(robotCode), period, inputs);
    }

    public PeriodicalExecutionView createPeriodicalExecution(String robotCode,
            final String period, final String... inputs) {
        return createPeriodicalExecution(getRobotURI(robotCode), period, inputs);
    }

    public <T> T doExecution(String robotInMinilanguage, Class<T> returnType,
            Collection<? extends String> inputs) {
        return doExecution(robotInMinilanguage, returnType,
                inputs.toArray(new String[0]));
    }

    public <T> T doExecution(String robotInMinilanguage, Class<T> returnType,
            String... inputs) {
        MultivaluedMap<String, String> request = fromInputs(inputs);
        request.add("robot", robotInMinilanguage);
        ClientResponse response = getBaseRobot().path("execute")
                .type(MediaType.APPLICATION_FORM_URLENCODED)
                .post(ClientResponse.class, request);
        return pollForExecutionResult(response, returnType);
    }

    public RobotExecutionResultView doExecution(String robotInMinilanguage,
            String... inputs) {
        return doExecution(robotInMinilanguage, RobotExecutionResultView.class,
                inputs);
    }

    private <T> T pollForExecutionResult(
            ClientResponse executionResultCreatedResponse, Class<T> returnType) {
        if (executionResultCreatedResponse.getClientResponseStatus().getStatusCode() >= 300) {
            if (ClientResponse.class.equals(returnType)) {
                return returnType.cast(executionResultCreatedResponse);
            }
            throw new UniformInterfaceException(executionResultCreatedResponse);
        }
        URI location = executionResultCreatedResponse.getLocation();
        return getExecutionResult(location, returnType);
    }

    public <T> T executeRobot(String robotCode, Class<T> returnType,
            Collection<? extends String> inputs) {
        return executeRobot(robotCode, returnType,
                inputs.toArray(new String[0]));
    }

    public <T> T executeRobot(RobotJSONView robot, Class<T> returnType,
            String... inputs) {
        return executeRobot(getRobotURI(robot), returnType, inputs);
    }

    public <T> T executeRobot(String robotCode, Class<T> returnType,
            String... inputs) {
        return executeRobot(getRobotURI(robotCode), returnType, inputs);
    }

    public <T> T executeRobot(URI robotURI, Class<T> returnType,
            String... inputs) {
        MultivaluedMap<String, String> map = fromInputs(inputs);
        ClientResponse response = client.resource(robotURI).path("execute")
                .type(MediaType.APPLICATION_FORM_URLENCODED_TYPE)
                .post(ClientResponse.class, map);
        return pollForExecutionResult(response, returnType);
    }

    private URI getRobotURI(RobotJSONView robot) {
        return getRobotURI(robot.getCode());
    }

    private URI getRobotURI(String robotCode) {
        return getBaseRobot().getUriBuilder().path("{code}").build(robotCode);
    }

    public RobotExecutionResultView executeRobot(String robotCode,
            String... inputs) {
        return executeRobot(robotCode, RobotExecutionResultView.class, inputs);
    }

    MultivaluedMap<String, String> fromInputs(String... inputs) {
        MultivaluedMap<String, String> map = new MultivaluedMapImpl();
        for (String input : inputs) {
            map.add("input", input);
        }
        return map;
    }
}
