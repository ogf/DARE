package es.uvigo.ei.sing.dare.resources;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.ClientResponse.Status;
import com.sun.jersey.api.client.WebResource;

@RunWith(JUnit4.class)
public class PeriodicalExecutionTest {

    private WebResource periodicalExecution;

    public PeriodicalExecutionTest() {
        Client client = new Client();
        periodicalExecution = client.resource(
                ExecuteRobotResourceTest.APPLICATION_URI).path(
                PeriodicalExecutionResource.BASE_PATH);
    }

    @Test
    public void ifNotAssociatedPeriodicalResultReturn404() {
        ClientResponse clientResponse = periodicalExecution.path("" + 2000)
                .get(ClientResponse.class);
        assertThat(clientResponse.getStatus(),
                equalTo(Status.NOT_FOUND.getStatusCode()));
    }

}
