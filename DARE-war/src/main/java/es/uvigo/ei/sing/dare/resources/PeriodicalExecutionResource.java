package es.uvigo.ei.sing.dare.resources;

import java.net.URI;
import java.util.Date;

import javax.servlet.ServletContext;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import org.joda.time.DateTime;

import es.uvigo.ei.sing.dare.configuration.Configuration;
import es.uvigo.ei.sing.dare.entities.ExecutionPeriod;
import es.uvigo.ei.sing.dare.entities.ExecutionResult;
import es.uvigo.ei.sing.dare.entities.PeriodicalExecution;
import es.uvigo.ei.sing.dare.resources.views.ExecutionResultView;
import es.uvigo.ei.sing.dare.resources.views.PeriodicalExecutionView;

@Path(PeriodicalExecutionResource.BASE_PATH)
public class PeriodicalExecutionResource {

    public static ExecutionPeriod parsePeriod(String periodString) {
        try {
            return ExecutionPeriod.parse(periodString);
        } catch (IllegalArgumentException e) {
            throw new WebApplicationException(e, Status.BAD_REQUEST);
        }
    }

    public static URI buildURIFor(UriInfo uriInfo,
            PeriodicalExecution periodicalExecution) {
        String code = periodicalExecution.getCode();
        return buildURIFor(uriInfo, code);
    }

    public static URI buildURIFor(UriInfo uriInfo, String code) {
        return UriBuilder.fromUri(uriInfo.getBaseUri())
                .path("periodical/result/{code}")
                .build(code);
    }

    public static final String BASE_PATH = "/periodical";

    @Context
    private ServletContext servletContext;

    @Context
    private UriInfo uriInfo;

    private Configuration getConfiguration() {
        return Configuration.from(servletContext);
    }

    @GET
    @Path("/result/{periodical-execution-code}")
    @Produces({ MediaType.APPLICATION_XML, MediaType.TEXT_XML })
    public Response retrievePeriodicalExecution(
            @Context Request request,
            @PathParam("periodical-execution-code") String periodicalExecutionCode) {
        PeriodicalExecutionView entity = retrieve(periodicalExecutionCode);
        return responseWithExpiresAndEtag(request, entity);
    }

    private Response responseWithExpiresAndEtag(Request request,
            PeriodicalExecutionView entity) {
        return responseWithExpiresAndEtag(request, entity, entity);
    }

    private Response responseWithExpiresAndEtag(Request request,
            PeriodicalExecutionView entity, Object variantReturned) {
        EntityTag etag = calculateEtag(entity);
        ResponseBuilder response = request.evaluatePreconditions(etag);
        if (response != null) {
            return response.build();
        }
        return Response.ok(variantReturned).tag(etag)
                .expires(expirationDate(entity))
                .build();
    }

    private EntityTag calculateEtag(PeriodicalExecutionView periodical) {
        DateTime lastModification = getLastModificationTime(periodical);
        return new EntityTag(lastModification.getMillis() + "", true);
    }

    private Date expirationDate(PeriodicalExecutionView entity) {
        DateTime lastModification = getLastModificationTime(entity);
        DateTime nextExecution = entity.getExecutionPeriod()
                .calculateNextExecution(lastModification);
        return nextExecution.toDate();
    }

    private DateTime getLastModificationTime(PeriodicalExecutionView periodical) {
        ExecutionResultView lastExecution = periodical.getLastExecutionResult();
        DateTime lastUpdate = lastExecution != null ? lastExecution.getDate()
                : new DateTime(periodical.getCreationTimeMillis());
        return lastUpdate;
    }

    private PeriodicalExecutionView retrieve(String periodicalExecutionCode) {
        PeriodicalExecution periodicalExecution = getConfiguration()
                .getBackend().findPeriodicalExecution(periodicalExecutionCode);
        if (periodicalExecution == null) {
            throw new WebApplicationException(Status.NOT_FOUND);
        }
        URI robotURI = RobotResource.buildURIFor(uriInfo,
                periodicalExecution.getRobotCode());
        ExecutionResultView lastResult = getLastResult(periodicalExecution);
        return PeriodicalExecutionView.create(periodicalExecution.getCode(),
                periodicalExecution.getCreationTime(), robotURI,
                periodicalExecution.getExecutionPeriod(),
                periodicalExecution.getInputs(), lastResult);
    }

    private ExecutionResultView getLastResult(
            PeriodicalExecution periodicalExecution) {
        ExecutionResult lastExecutionResult = periodicalExecution
                .getLastExecutionResult();
        if (lastExecutionResult == null) {
            return null;
        }
        return new ExecutionResultView(
                lastExecutionResult.getCreationTime(),
                lastExecutionResult.getExecutionTimeMilliseconds(),
                lastExecutionResult.getResultLines());
    }

    @GET
    @Path("/result/{periodical-execution-code}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response retrievePeriodicalExecutionAsJSON(
            @Context Request request,
            @PathParam("periodical-execution-code") String periodicalExecutionCode) {
        PeriodicalExecutionView periodicalExecutionView = retrieve(periodicalExecutionCode);
        return responseWithExpiresAndEtag(request, periodicalExecutionView,
                retrieve(periodicalExecutionCode).asJSON());
    }

    @DELETE
    @Path("/result/{periodical-execution-code}")
    public Response delete(@PathParam("periodical-execution-code") String code) {
        getConfiguration().getBackend().deletePeriodical(code);
        return Response.ok().build();
    }


}
