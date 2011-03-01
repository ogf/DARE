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
import es.uvigo.ei.sing.dare.backend.IStore;
import es.uvigo.ei.sing.dare.entities.ExecutionResult;
import es.uvigo.ei.sing.dare.entities.Robot;
import es.uvigo.ei.sing.stringeditor.Minilanguage;
import es.uvigo.ei.sing.stringeditor.Transformer;
import es.uvigo.ei.sing.stringeditor.Util;

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

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Path("create")
    public Response create(
            @FormParam("minilanguage") String miniLanguage) {
        Robot robot;
        try {
            robot = Robot.createFromMinilanguage(miniLanguage);
        } catch (IllegalArgumentException e) {
            throw new WebApplicationException(e, Status.BAD_REQUEST);
        }
        return create(robot);
    }

    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.TEXT_XML })
    @Path("create")
    public Response createFromXML(Document robotXML) {
        Robot robot;
        try {
            robot = Robot.createFromXML(robotXML);
        } catch (IllegalArgumentException e) {
            throw new WebApplicationException(e, Status.BAD_REQUEST);
        }
        return create(robot);
    }

    private Response create(Robot robot) {
        getStore().save(robot);
        URI robotURI = UriBuilder.fromUri(uriInfo.getBaseUri()).path("robot")
                .path("view/{code}")
                .build(robot.getCode());
        return Response.created(robotURI).build();
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
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML,
            MediaType.TEXT_XML })
    @Path("execute")
    public ExecutionResult execute(@FormParam("robot") String transformerParam,
            @FormParam("input") List<String> inputs) {
        long startTime = System.currentTimeMillis();

        Transformer transformer = parseTransformer(transformerParam);
        String[] result = execute(transformer, inputs);

        long elapsedTime = System.currentTimeMillis() - startTime;
        return new ExecutionResult(elapsedTime, result);
    }

    private Transformer parseTransformer(String transformerParam) {
        try {
            return Minilanguage.eval(transformerParam);
        } catch (Exception e) {
            throw new WebApplicationException(errorParsingTranformerResponse());
        }
    }

    private Response errorParsingTranformerResponse() {
        return Response.status(400).entity("transformer not valid").build();
    }

    private String[] execute(Transformer transformer, List<String> inputs) {
        String[] asArray = inputs.toArray(new String[0]);
        return Util.runRobot(transformer, asArray);
    }

}
