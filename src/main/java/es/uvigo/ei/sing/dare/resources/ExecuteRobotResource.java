package es.uvigo.ei.sing.dare.resources;

import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import es.uvigo.ei.sing.stringeditor.Minilanguage;
import es.uvigo.ei.sing.stringeditor.Transformer;
import es.uvigo.ei.sing.stringeditor.Util;

@Path(ExecuteRobotResource.PATH)
public class ExecuteRobotResource {

    public static final String PATH = "/execute";

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public ExecutionResult execute(
            @FormParam("transformer") String transformerParam,
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
