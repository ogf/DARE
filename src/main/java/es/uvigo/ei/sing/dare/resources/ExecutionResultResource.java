package es.uvigo.ei.sing.dare.resources;

import java.net.URI;

import javax.servlet.ServletContext;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import es.uvigo.ei.sing.dare.backend.Configuration;
import es.uvigo.ei.sing.dare.backend.IStore;
import es.uvigo.ei.sing.dare.backend.Maybe;
import es.uvigo.ei.sing.dare.entities.ExecutionResult;
import es.uvigo.ei.sing.dare.entities.ExecutionResult.Type;
import es.uvigo.ei.sing.dare.resources.views.ExecutionResultView;

@Path("result")
public class ExecutionResultResource {

    public static URI buildURIFor(UriInfo uriInfo,
            ExecutionResult executionResult) {
        return buildURIFor(uriInfo, executionResult.getCode());
    }

    public static URI buildURIFor(UriInfo uriInfo, String resultCode) {
        return UriBuilder.fromUri(uriInfo.getBaseUri())
                .path("result/{executionResultCode}").build(resultCode);
    }

    @Context
    private ServletContext context;

    @Context
    private UriInfo uriInfo;

    private Configuration getConfiguration() {
        return Configuration.from(context);
    }

    private IStore getStore() {
        return getConfiguration().getStore();
    }

    @GET
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML,
            MediaType.TEXT_XML })
    @Path("{executionResultCode}")
    public ExecutionResultView retrieve(
            @PathParam("executionResultCode") String executionResultCode) {

        Maybe<ExecutionResult> possibleResult = getStore().retrieveExecution(
                executionResultCode);
        if (possibleResult == null) {
            throw new WebApplicationException(Status.NOT_FOUND);
        }
        if (possibleResult.isNone()) {// not completed
            throw new WebApplicationException(Status.NO_CONTENT);
        }
        ExecutionResult result = possibleResult.getValue();
        URI createdFrom = getCreatedFrom(result);
        return new ExecutionResultView(createdFrom, result.getCreationTime(),
                result.getExecutionTimeMilliseconds(), result.getResultLines());
    }

    private URI getCreatedFrom(ExecutionResult result) {
        if (result.getType() == Type.ROBOT) {
            return RobotResource.buildURIFor(uriInfo,
                    result.getCreatedFromCode());
        } else {
            return PeriodicalExecutionResource.buildURIFor(uriInfo,
                    result.getCreatedFromCode());
        }

    }

}
