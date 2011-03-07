package es.uvigo.ei.sing.dare.resources;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

import java.net.URI;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.w3c.dom.Document;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.ClientResponse.Status;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.filter.LoggingFilter;
import com.sun.jersey.core.util.MultivaluedMapImpl;

import es.uvigo.ei.sing.dare.entities.Robot;
import es.uvigo.ei.sing.dare.entities.RobotTest;
import es.uvigo.ei.sing.dare.resources.views.ExecutionResultView;
import es.uvigo.ei.sing.dare.resources.views.RobotJSONView;
import es.uvigo.ei.sing.dare.resources.views.RobotXMLView;
import es.uvigo.ei.sing.dare.util.XMLUtil;

@RunWith(JUnit4.class)
public class RobotResourceTest {

    private WebResource robotResource;

    private Client client;

    private URIPoller poller;

    public RobotResourceTest() {
        client = new Client();
        client.addFilter(new LoggingFilter());
        robotResource = client.resource(
                RobotResourceExecutionTest.APPLICATION_URI).path("robot");
        poller = new URIPoller(client, MediaType.APPLICATION_JSON_TYPE);
    }

    @Test
    public void aRobotCanBeCreatedFromAMinilanguage() {
        postRobotCreation("url");
    }

    @Test
    public void ifTheMinilanguageIsWrongABadRequestErrorStatusIsReturned() {
        ClientResponse response = postRobotCreationReturningResponse("wrong");
        assertThat(response.getStatus(),
                equalTo(Status.BAD_REQUEST.getStatusCode()));
    }

    @Test
    public void aRobotCanBeCreatedFromAXML() {
        Document robot = RobotTest.buildValidRobot();
        postRobotCreation(robot);
    }

    @Test
    public void ifTheRobotHasAnInvalidXMLAnErrorStatusIsReturned() {
        Document invalidRobot = RobotTest.buildInvalidRobot();
        ClientResponse response = postRobotCreation(ClientResponse.class,
                invalidRobot);
        assertThat(response.getStatus(),
                equalTo(Status.BAD_REQUEST.getStatusCode()));
    }

    @Test
    public void afterCreatingARobotItCanBeViewedUsingTheReturnedURI() {
        URI uri = postRobotCreation("url");
        RobotXMLView robot = getRobot(MediaType.TEXT_XML_TYPE,
                RobotXMLView.class, uri);
        assertThat(robot.getCode(), notNullValue());
        assertThat(robot.getCreationDate(), notNullValue());
        Document fromRootDocumentElement = XMLUtil
                .fromRootDocumentElement(robot.getRobot());
        Robot recreated = Robot.createFromXML(fromRootDocumentElement);
        assertNotNull(recreated);
    }

    @Test
    public void afterCreatingARobotItCanBeRetrievedASJSON() {
        URI uri = postRobotCreation("url");
        RobotJSONView robot = getRobot(MediaType.APPLICATION_JSON_TYPE,
                RobotJSONView.class, uri);
        assertThat(robot.getCode(), notNullValue());
        assertThat(robot.getCreationDate(), notNullValue());
        Robot recreated = Robot.createFromMinilanguage(robot
                .getRobotInMinilanguage());
        assertNotNull(recreated);
    }

    @Test
    public void theCreationDateIsReturnedAsALong() throws JSONException {
        URI uri = postRobotCreation("url");
        JSONObject jsonResponse = getRobot(MediaType.APPLICATION_JSON_TYPE,
                JSONObject.class, uri);
        jsonResponse.getLong("creationDateMillis");
    }

    @Test
    public void aCreatedRobotCanBeExecuted() {
        URI uri = postRobotCreation("url");
        RobotJSONView robot = getRobot(MediaType.APPLICATION_JSON_TYPE,
                RobotJSONView.class, uri);
        String robotCode = robot.getCode();

        MultivaluedMap<String, String> map = new MultivaluedMapImpl();
        map.add("input", "http://www.google.com");
        map.add("input", "http://www.twitter.com");
        ClientResponse response = robotResource.path(robotCode).path("execute")
                .type(MediaType.APPLICATION_FORM_URLENCODED_TYPE)
                .post(ClientResponse.class, map);
        ExecutionResultView result = ExecutionResultView.fromJSON(poller
                .retrieve(JSONObject.class, response.getLocation()));

        assertNotNull(result);
        assertThat(result.getExecutionTime(), greaterThan(0l));
        assertThat(result.getResultLines().isEmpty(), is(false));
    }

    @Test
    public void fromARobotAPeriodicalExecutionCanBeCreated() {
        URI uri = postRobotCreation("url");
        WebResource periodicalCreationResource = client.resource(uri).path(
                "periodical");
        ClientResponse response = periodicalCreationResource.post(
                ClientResponse.class,
                new MultivaluedMapImpl() {
                    {
                        add("period", "12h");
                        add("input", "http://www.google.com");
                    }
                });

        ClientResponse periodicalExecutionCreated = client.resource(
                response.getLocation()).get(ClientResponse.class);

        assertThat(periodicalExecutionCreated.getStatus(),
                equalTo(Status.OK.getStatusCode()));
    }

    @Test
    public void aWrongPeriodImpliesABadRequestResponse() {
        URI uri = postRobotCreation("url");
        WebResource periodicalCreationResource = client.resource(uri).path(
                "periodical");
        ClientResponse response = periodicalCreationResource.post(
                ClientResponse.class, new MultivaluedMapImpl() {
                    {
                        add("period", "foo");
                        add("input", "http://www.google.com");
                    }
                });
        assertThat(response.getStatus(),
                equalTo(Status.BAD_REQUEST.getStatusCode()));
    }

    private URI postRobotCreation(String robotInMinilanguage) {
        return postRobotCreationReturningResponse(robotInMinilanguage)
                .getLocation();
    }

    private ClientResponse postRobotCreationReturningResponse(
            String robotInMinilanguage) {
        MultivaluedMap<String, String> map = new MultivaluedMapImpl();
        map.add("minilanguage", robotInMinilanguage);
        return robotResource.path("create")
                .type(MediaType.APPLICATION_FORM_URLENCODED_TYPE)
                .post(ClientResponse.class, map);
    }

    private URI postRobotCreation(Document robot) {
        return URI.create(postRobotCreation(String.class, robot));
    }

    private <T> T postRobotCreation(Class<T> type, Document robot) {
        return robotResource.path("create")
                .type(MediaType.APPLICATION_XML_TYPE).post(type, robot);
    }

    private <T> T getRobot(MediaType mediaType, Class<T> type, URI uriToRobot) {
        return client.resource(uriToRobot).accept(mediaType).get(type);
    }

}
