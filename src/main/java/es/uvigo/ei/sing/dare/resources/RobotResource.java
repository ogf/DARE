package es.uvigo.ei.sing.dare.resources;

import java.net.URI;
import java.util.List;

import javax.servlet.ServletContext;
import javax.ws.rs.Consumes;
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

import es.uvigo.ei.sing.dare.backend.Configuration;
import es.uvigo.ei.sing.dare.backend.IRobotExecutor;
import es.uvigo.ei.sing.dare.backend.IStore;
import es.uvigo.ei.sing.dare.entities.Robot;

@Path("robot")
public class RobotResource {

    @Context
    private UriInfo uriInfo;

    @Context
    private ServletContext context;

    private Configuration getConfiguration() {
        return Configuration.from(context);
    }

    private IStore getStore() {
        return getConfiguration().getStore();
    }

    private IRobotExecutor getRobotExecutor() {
        return getConfiguration().getRobotExecutor();
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
            return Robot.createFromMinilanguage(miniLanguage);
        } catch (IllegalArgumentException e) {
            throw new WebApplicationException(e, Status.BAD_REQUEST);
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
        getStore().save(robot);
        URI robotURI = buildURIFor(robot);
        return Response.created(robotURI).build();
    }

    private URI buildURIFor(Robot robot) {
        URI baseUri = uriInfo.getBaseUri();
        return UriBuilder.fromUri(baseUri).path("robot")
                .path("view/{code}")
                .build(robot.getCode());
    }

    @GET
    @Path("view/{code}")
    @Produces(MediaType.APPLICATION_JSON)
    public RobotJSONView viewAsJSON(@PathParam("code") String robotCode) {
        Robot robot = find(robotCode);
        return robot.asJSONView();
    }

    @GET
    @Path("view/{code}")
    @Produces({ MediaType.APPLICATION_XML, MediaType.TEXT_XML })
    public RobotXMLView viewAsXML(@PathParam("code") String robotCode) {
        Robot robot = find(robotCode);
        return robot.asXMLView();
    }

    private Robot find(String robotCode) {
        Robot robot = getStore().find(robotCode);
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

        final Robot robot = find(robotCode);
        return submitExecutionAndRedirectToResult(robot, inputs);
    }

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Path("execute")
    public Response execute(@FormParam("robot") final String robotParam,
            @FormParam("input") final List<String> inputs) {

        final Robot robot = parseRobot(robotParam);
        getStore().save(robot);
        return submitExecutionAndRedirectToResult(robot, inputs);
    }

    private Response submitExecutionAndRedirectToResult(final Robot robot,
            final List<String> inputs) {
        String resultCode = getRobotExecutor().submitExecution(
                buildURIFor(robot), robot, inputs);
        return Response.created(
                ExecutionResultResource.buildURIFor(uriInfo, resultCode))
                .build();
    }

}
