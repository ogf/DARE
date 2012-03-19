package es.uvigo.ei.sing.dare.resources;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.util.Arrays;
import java.util.UUID;

import javax.ws.rs.core.MediaType;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.w3c.dom.Document;

import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.ClientResponse.Status;

import es.uvigo.ei.sing.dare.client.DARE;
import es.uvigo.ei.sing.dare.entities.Robot;
import es.uvigo.ei.sing.dare.entities.RobotTest;
import es.uvigo.ei.sing.dare.resources.views.PeriodicalExecutionView;
import es.uvigo.ei.sing.dare.resources.views.RobotExecutionResultView;
import es.uvigo.ei.sing.dare.resources.views.RobotJSONView;
import es.uvigo.ei.sing.dare.resources.views.RobotXMLView;
import es.uvigo.ei.sing.dare.util.XMLUtil;

@RunWith(JUnit4.class)
public class RobotResourceTest {

    private DARE dare;

    public RobotResourceTest() {
        dare = RobotResourceExecutionTest
                .buildClientWithLoggingAndCaching(MediaType.APPLICATION_JSON_TYPE);
    }

    @Test
    public void aRobotCanBeCreatedFromAMinilanguage() {
        RobotJSONView robot = dare.createRobot("url");
        assertNotNull(robot);
    }

    @Test
    public void ifTheMinilanguageIsWrongABadRequestErrorStatusIsReturned() {
        ClientResponse response = dare.createRobotAndReturnLocation("wrong");
        assertThat(response.getStatus(),
                equalTo(Status.BAD_REQUEST.getStatusCode()));
    }

    @Test
    public void ifTheEvaluationOfMinilanguageTakesMoreThanOneSecondAnErrorIsReturned() {
        ClientResponse response = dare.createRobotAndReturnLocation("sleep(2); url");
        assertThat(response.getStatus(),
                equalTo(Status.INTERNAL_SERVER_ERROR.getStatusCode()));
        String errorMessage = response.getEntity(String.class);
        assertNotNull(errorMessage);
        assertTrue(errorMessage.matches(".*Max time.*exceeded.*"));
    }

    @Test
    public void aRobotCanBeCreatedFromAXML() {
        Document robot = RobotTest.buildValidRobot();
        URI uri = URI.create(dare.createRobotAndReturnLocation(robot).getEntity(String.class));
        assertThat(uri, notNullValue());
    }

    @Test
    public void ifTheRobotHasAnInvalidXMLAnErrorStatusIsReturned() {
        Document invalidRobot = RobotTest.buildInvalidRobot();
        ClientResponse response = dare.createRobotAndReturnLocation(invalidRobot);
        assertThat(response.getStatus(),
                equalTo(Status.BAD_REQUEST.getStatusCode()));
    }

    @Test
    public void afterCreatingARobotItCanBeViewedUsingTheReturnedURI() {
        URI uri = postRobotCreation("url");
        RobotXMLView robot = dare.doGet(uri, RobotXMLView.class, MediaType.TEXT_XML_TYPE);
        assertThat(robot.getCode(), notNullValue());
        assertThat(robot.getCreationDate(), notNullValue());
        Document fromRootDocumentElement = XMLUtil
                .fromRootDocumentElement(robot.getRobot());
        Robot recreated = Robot.createFromXML(fromRootDocumentElement);
        assertNotNull(recreated);
        Robot recreatedFromMinilanguage = Robot.createFromMinilanguage(robot
                .getRobotInMinilanguage());
        assertNotNull(recreatedFromMinilanguage);
    }

    @Test
    public void afterCreatingARobotItCanBeRetrievedASJSON() {
        RobotJSONView robot = dare.createRobot("url");
        assertThat(robot.getCode(), notNullValue());
        assertThat(robot.getCreationDate(), notNullValue());
        Robot recreated = Robot.createFromMinilanguage(robot
                .getRobotInMinilanguage());
        assertNotNull(recreated);
        Robot recreatedFromXML = Robot.createFromXML(robot.getRobotXML());
        assertNotNull(recreatedFromXML);
    }

    @Test
    public void theCreationDateIsReturnedAsALong() throws JSONException {
        URI uri = postRobotCreation("url");
        JSONObject jsonResponse = dare.doGet(uri, JSONObject.class, MediaType.APPLICATION_JSON_TYPE);
        jsonResponse.getLong("creationDateMillis");
    }

    @Test
    public void aCreatedRobotCanBeExecuted() {
        RobotJSONView robot = dare.createRobot("url");
        String robotCode = robot.getCode();
        RobotExecutionResultView result = dare.executeRobot(robotCode,
                "http://www.google.com", "http://www.twitter.com");
        assertNotNull(result);
        assertThat(result.getExecutionTime(), greaterThan(0l));
        assertThat(result.getResultLines().isEmpty(), is(false));
    }

    @Test
    public void executingANotCreatedRobotReturnsNotFound() {
        String robotCode = UUID.randomUUID().toString();
        ClientResponse response = dare.executeRobot(robotCode,
                ClientResponse.class, "http://www.google.com",
                "http://www.twitter.com");
        assertThat(response.getStatus(),
                equalTo(Status.NOT_FOUND.getStatusCode()));
    }

    @Test
    public void fromARobotAPeriodicalExecutionCanBeCreated() {
        RobotJSONView robot = dare.createRobot("url");
        PeriodicalExecutionView response = dare.createPeriodicalExecution(
                robot, "12h",
                "http://www.google.com");
        assertNotNull(response);
        assertThat(response.getInputs(),
                equalTo(Arrays.asList("http://www.google.com")));
        RobotJSONView robotRetrieved = dare.doGet(response.getRobot(),
                RobotJSONView.class);
        assertThat(robot.getCode(), equalTo(robotRetrieved.getCode()));
    }

    @Test
    public void aWrongPeriodImpliesABadRequestResponse() {
        RobotJSONView robot = dare.createRobot("url");
        ClientResponse response = dare.createPeriodicalExecutionAndReturnLocation(robot, "foo",
                "http://www.google.com");
        assertThat(response.getStatus(),
                equalTo(Status.BAD_REQUEST.getStatusCode()));
    }

    private URI postRobotCreation(String robotInMinilanguage) {
        return dare.createRobotAndReturnLocation(robotInMinilanguage)
                .getLocation();
    }

}
