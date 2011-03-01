package es.uvigo.ei.sing.dare.resources;

import javax.servlet.ServletContext;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import es.uvigo.ei.sing.dare.backend.Configuration;
import es.uvigo.ei.sing.dare.entities.PeriodicalExecution;

@Path(PeriodicalExecutionResource.BASE_PATH)
public class PeriodicalExecutionResource {

    public static final String BASE_PATH = "/periodical";

    @Context
    private ServletContext servletContext;

    private Configuration getConfiguration() {
        return Configuration.from(servletContext);
    }

    @GET
    @Path("/result/{periodical-execution-code}")
    public Response retrievePeriodicalExecution(
            @PathParam("periodical-execution-code") String periodicalExecutionCode) {
        PeriodicalExecution periodicalExecution = getConfiguration()
                .getStore().findPeriodicalExecution(
                        periodicalExecutionCode);
        if (periodicalExecution == null) {
            return Response.status(Status.NOT_FOUND).build();
        } else {
            return Response.status(Status.OK).entity("")
                    .type(MediaType.TEXT_PLAIN).build();
        }
    }

}
