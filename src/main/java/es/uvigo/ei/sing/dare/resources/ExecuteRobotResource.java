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
            @FormParam("input") List<String> input) {
        Transformer transformer = parseTransformer(transformerParam);
        String[] result = Util.runRobot(transformer,
                input.toArray(new String[0]));
        return new ExecutionResult(result);
    }

    private Transformer parseTransformer(String transformerParam) {
        try {
            return Minilanguage.eval(transformerParam);
        } catch (Exception e) {
            throw new WebApplicationException(
                    createResporseForErrorParsingTransformer(e));
        }
    }

    private Response createResporseForErrorParsingTransformer(Exception e) {
        return Response.status(400).entity("transformer not valid").build();
    }

}
