package es.uvigo.ei.sing.dare.resources;

import java.net.URI;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.servlet.ServletContext;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
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

import org.w3c.dom.Document;

import es.uvigo.ei.sing.dare.configuration.Configuration;
import es.uvigo.ei.sing.dare.domain.IBackend;
import es.uvigo.ei.sing.dare.domain.MinilanguageProducer;
import es.uvigo.ei.sing.dare.entities.ExecutionPeriod;
import es.uvigo.ei.sing.dare.entities.PeriodicalExecution;
import es.uvigo.ei.sing.dare.entities.Robot;
import es.uvigo.ei.sing.dare.resources.views.RobotJSONView;
import es.uvigo.ei.sing.dare.resources.views.RobotXMLView;
import es.uvigo.ei.sing.dare.util.XMLUtil;

@Path("robot")
public class RobotResource {

    public static URI buildURIFor(UriInfo uriInfo, Robot robot) {
        return buildURIFor(uriInfo, robot.getCode());
    }

    public static URI buildURIFor(UriInfo uriInfo, String robotCode) {
        URI baseUri = uriInfo.getBaseUri();
        return UriBuilder.fromUri(baseUri).path("robot/{code}")
                .build(robotCode);
    }

    @Context
    private UriInfo uriInfo;

    @Context
    private ServletContext context;

    private Configuration getConfiguration() {
        return Configuration.from(context);
    }

    private IBackend getBackend() {
        return getConfiguration().getBackend();
    }

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Path("create")
    public Response create(
            @FormParam("minilanguage") String miniLanguage) {
        Robot robot = parseRobot(miniLanguage);
        return create(robot);
    }

    private Robot parseRobot(String miniLanguage) {
        try {
            Configuration conf = getConfiguration();
            MinilanguageProducer producer = conf.getMinilanguageProducer();
            return Robot.createFromMinilanguage(producer.newMinilanguage(),
                    miniLanguage, conf.getRobotParserExecutor(),
                    1, TimeUnit.SECONDS);
        } catch (IllegalArgumentException e) {
            throw new WebApplicationException(e, Status.BAD_REQUEST);
        } catch (TimeoutException e) {
            throw new WebApplicationException(Response.serverError()
                            .entity("Max time(1 second) to parse the minilanguage exceeded. ")
                            .build());
        }
    }

    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.TEXT_XML })
    @Path("create")
    public Response createFromXML(Document robotXML) {
        Robot robot = parseRobot(robotXML);
        return create(robot);
    }

    private Robot parseRobot(Document robotXML) {
        try {
            return Robot.createFromXML(robotXML);
        } catch (IllegalArgumentException e) {
            throw new WebApplicationException(e, Status.BAD_REQUEST);
        }
    }

    private Response create(Robot robot) {
        getBackend().save(robot);
        URI robotURI = buildURIFor(robot);
        return Response.created(robotURI).build();
    }

    private URI buildURIFor(Robot robot) {
        return buildURIFor(uriInfo, robot);
    }

    @GET
    @Path("{code}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response viewAsJSON(@PathParam("code") String robotCode) {
        Robot robot = find(robotCode);
        RobotJSONView result = new RobotJSONView(robot.getCode(),
                robot.getCreationTime(), robot.getTransformerInXML(),
                robot.getTransformerInMinilanguage());
        return CacheUtil.cacheImmutable(result);
    }

    @GET
    @Path("{code}")
    @Produces({ MediaType.APPLICATION_XML, MediaType.TEXT_XML })
    public Response viewAsXML(@PathParam("code") String robotCode) {
        Robot robot = find(robotCode);
        return CacheUtil.cacheImmutable(new RobotXMLView(robot.getCode(), robot
                .getCreationTime(), robot.getTransformerInMinilanguage(),
                XMLUtil.toDocument(robot
                        .getTransformerInXML())));
    }

    @DELETE
    @Path("{code}")
    public Response delete(@PathParam("code") String code) {
        getBackend().deleteRobot(code);
        return Response.ok().build();
    }

    private Robot find(String robotCode) {
        Robot robot = getBackend().find(robotCode);
        if (robot == null) {
            throw new WebApplicationException(Status.NOT_FOUND);
        }
        return robot;
    }

    @POST
    @Path("{code}/execute")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response executeOnAlreadyExistentRobot(
            @PathParam("code") String robotCode,
            @FormParam("input") final List<String> inputs) {
        String resultCode = getBackend().submitExecutionForExistentRobot(robotCode, inputs);
        if (resultCode == null) {
            throw new WebApplicationException(Status.NOT_FOUND);
        }
        return redirectToResult(resultCode);
    }

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Path("execute")
    public Response execute(@FormParam("robot") final String robotParam,
            @FormParam("input") final List<String> inputs) {

        final Robot robot = parseRobot(robotParam);
        String resultCode = getBackend().submitExecution(robot, inputs);
        return redirectToResult(resultCode);
    }

    private Response redirectToResult(String resultCode) {
        URI uriFor = ExecutionResultResource.buildURIFor(uriInfo, resultCode);
        return Response.created(uriFor).build();
    }

    @POST
    @Path("{code}/periodical")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response createPeriodical(
            @PathParam("code") String robotCode,
            @FormParam("period") String periodString,
            @FormParam("input") final List<String> inputs) {

        Robot robot = find(robotCode);
        ExecutionPeriod period = PeriodicalExecutionResource.parsePeriod(periodString);
        PeriodicalExecution periodicalExecution = new PeriodicalExecution(
                robot, period, inputs);
        getBackend().save(periodicalExecution);
        return Response.created(
                PeriodicalExecutionResource.buildURIFor(uriInfo,
                        periodicalExecution)).build();
    }

}
