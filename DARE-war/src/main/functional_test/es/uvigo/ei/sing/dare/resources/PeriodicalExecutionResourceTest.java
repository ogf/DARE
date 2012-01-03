package es.uvigo.ei.sing.dare.resources;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.assertThat;

import java.net.URI;

import javax.ws.rs.core.MediaType;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.ClientResponse.Status;
import com.sun.jersey.api.client.WebResource;

import es.uvigo.ei.sing.dare.configuration.ConfigurationStub;
import es.uvigo.ei.sing.dare.entities.PeriodicalExecution;
import es.uvigo.ei.sing.dare.resources.views.ExecutionResultView;
import es.uvigo.ei.sing.dare.resources.views.PeriodicalExecutionView;
import es.uvigo.ei.sing.dare.resources.views.RobotXMLView;

@RunWith(JUnit4.class)
public class PeriodicalExecutionResourceTest {

    private Client client;

    private WebResource periodicalExecution;

    private WebResource periodicalExecutionResult;

    public PeriodicalExecutionResourceTest() {
        client = RobotResourceExecutionTest.buildClientWithLoggingAndCaching();
        periodicalExecution = client.resource(
                RobotResourceExecutionTest.APPLICATION_URI).path(
                PeriodicalExecutionResource.BASE_PATH);
        periodicalExecutionResult = periodicalExecution.path("result");
    }

    @Test
    public void ifNotAssociatedPeriodicalResultReturn404() {
        ClientResponse clientResponse = periodicalExecution.path("" + 2000)
                .get(ClientResponse.class);
        assertThat(clientResponse.getStatus(),
                equalTo(Status.NOT_FOUND.getStatusCode()));
    }

    @Test
    public void ifPeriodicalResultExistsMustReturn200Code() {
        ClientResponse response = periodicalExecutionResult
                .path(ConfigurationStub.EXISTENT_PERIODICAL_EXECUTION.getCode())
                .get(ClientResponse.class);
        assertThat(response.getStatus(), equalTo(Status.OK.getStatusCode()));
    }

    @Test
    public void aPeriodicalExecutionIsMappedToAPeriodicalExecutionView() {
        final PeriodicalExecution existent = ConfigurationStub.EXISTENT_PERIODICAL_EXECUTION;
        PeriodicalExecutionView response = retrievePeriodicalFromServer(existent);

        assertThat(response.getCode(), equalTo(existent.getCode()));
        assertThat(response.getCreationTimeMillis(), greaterThan(0l));
        assertThat(response.getExecutionPeriod(),
                equalTo(existent.getExecutionPeriod()));
        assertThat(response.getInputs(), equalTo(existent.getInputs()));
        assertThat(response.getLastExecutionResult(), nullValue());
    }

    @Test
    public void theRobotFromThePeriodicalExecutionCanBeRetrieved() {
        final PeriodicalExecution existent = ConfigurationStub.EXISTENT_PERIODICAL_EXECUTION;
        PeriodicalExecutionView response = retrievePeriodicalFromServer(existent);
        URI robot = response.getRobot();
        RobotXMLView robotXMLView = client.resource(robot)
                .accept(MediaType.APPLICATION_XML_TYPE)
                .get(RobotXMLView.class);
        assertThat(robotXMLView, notNullValue());
    }

    @Test
    public void theLastExecutionResultIsReturnedInTheResponse() {
        final PeriodicalExecution existent = ConfigurationStub.PERIODICAL_EXECUTION_WITH_RESULT;
        PeriodicalExecutionView periodicalExecution = retrievePeriodicalFromServer(existent);

        ExecutionResultView last = periodicalExecution
                .getLastExecutionResult();

        assertThat(last.getResultLines(), equalTo(existent
                .getLastExecutionResult().getResultLines()));
    }

    @Test
    public void theJSONMappingIsIdiomatic() throws JSONException {
        final PeriodicalExecution existent = ConfigurationStub.PERIODICAL_EXECUTION_WITH_RESULT;
        JSONObject result = periodicalExecutionResult.path(existent.getCode())
                .accept(MediaType.APPLICATION_JSON_TYPE).get(JSONObject.class);

        assertThat(result.get("creationDateMillis"), is(Number.class));
        assertThat(result.get("periodAmount"), is(Number.class));
        assertThat(result.get("inputs"), is(JSONArray.class));
        assertThat(result.get("lastExecutionResult"), is(JSONObject.class));

        JSONObject last = result.getJSONObject("lastExecutionResult");
        assertThat(last.get("resultLines"), is(JSONArray.class));
        assertThat(last.get("executionTime"), is(Number.class));
        assertThat(last.get("date"), is(Number.class));

        JSONArray inputs = result.getJSONArray("inputs");
        assertThat(inputs.get(0), is(String.class));
    }

    private PeriodicalExecutionView retrievePeriodicalFromServer(
            final PeriodicalExecution existent) {
        final String code = existent.getCode();
        return retrievePeriodicalFromServer(code);
    }

    private PeriodicalExecutionView retrievePeriodicalFromServer(
            final String code) {
        return periodicalExecutionResult.path(code)
                .accept(MediaType.APPLICATION_XML)
                .get(PeriodicalExecutionView.class);
    }

}
