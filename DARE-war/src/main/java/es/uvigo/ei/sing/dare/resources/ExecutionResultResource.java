package es.uvigo.ei.sing.dare.resources;

import static es.uvigo.ei.sing.dare.configuration.Configuration.EXECUTION_RESULT_BASE_URL;

import java.net.URI;

import javax.servlet.ServletContext;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import es.uvigo.ei.sing.dare.configuration.Configuration;
import es.uvigo.ei.sing.dare.domain.ExecutionFailedException;
import es.uvigo.ei.sing.dare.domain.ExecutionTimeExceededException;
import es.uvigo.ei.sing.dare.domain.IBackend;
import es.uvigo.ei.sing.dare.domain.Maybe;
import es.uvigo.ei.sing.dare.entities.ExecutionResult;
import es.uvigo.ei.sing.dare.resources.views.RobotExecutionResultView;

@Path(EXECUTION_RESULT_BASE_URL)
public class ExecutionResultResource {

    public static URI buildURIFor(UriInfo uriInfo,
            ExecutionResult executionResult) {
        return buildURIFor(uriInfo, executionResult.getCode());
    }

    public static URI buildURIFor(UriInfo uriInfo, String resultCode) {
        return UriBuilder.fromUri(uriInfo.getBaseUri())
                .path(EXECUTION_RESULT_BASE_URL + "/{executionResultCode}")
                .build(resultCode);
    }

    @Context
    private ServletContext context;

    @Context
    private UriInfo uriInfo;

    private Configuration getConfiguration() {
        return Configuration.from(context);
    }

    private IBackend getStore() {
        return getConfiguration().getBackend();
    }

    @DELETE
    @Path("{executionResultCode}")
    public Response delete(
            @PathParam("executionResultCode") String executionResultCode) {
        getStore().deleteExecution(executionResultCode);
        return Response.ok().build();
    }

    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.TEXT_XML })
    @Path("{executionResultCode}")
    public Response retrieve(
            @PathParam("executionResultCode") String executionResultCode) {
        return CacheUtil.cacheImmutable(retrieveExecution(executionResultCode));
    }

    private RobotExecutionResultView retrieveExecution(
            String executionResultCode) {
        try {
            Maybe<ExecutionResult> possibleResult = getStore()
                    .retrieveExecution(executionResultCode);
            if (possibleResult == null) {
                throw new WebApplicationException(Status.NOT_FOUND);
            }
            if (possibleResult.isNone()) {// not completed
                throw new WebApplicationException(Status.NO_CONTENT);
            }
            ExecutionResult result = possibleResult.getValue();
            URI createdFrom = getCreatedFrom(result);
            RobotExecutionResultView entity = new RobotExecutionResultView(createdFrom,
                    result.getCreationTime(),
                    result.getExecutionTimeMilliseconds(),
                    result.getInputs(),
                    result.getResultLines());
            return entity;
        } catch (ExecutionTimeExceededException e) {
            throw errorResponse(e.getMessage());
        } catch (ExecutionFailedException e) {
            throw errorResponse(e.getMessage());
        }
    }

    WebApplicationException errorResponse(String errorMessage) {
        return new WebApplicationException(Response
                .status(Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.TEXT_PLAIN).entity(errorMessage).build());
    }

    private URI getCreatedFrom(ExecutionResult result) {
        return RobotResource
                .buildURIFor(uriInfo, result.getOptionalRobotCode());
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{executionResultCode}")
    public Response retrieveAsJSON(
            @PathParam("executionResultCode") String executionResultCode) {
        return CacheUtil.cacheImmutable(retrieveExecution(executionResultCode)
                .asJSON());
    }
}
