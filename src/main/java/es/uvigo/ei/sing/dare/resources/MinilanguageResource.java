package es.uvigo.ei.sing.dare.resources;

import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import es.uvigo.ei.sing.stringeditor.Minilanguage;
import es.uvigo.ei.sing.stringeditor.Transformer;
import es.uvigo.ei.sing.stringeditor.Util;

@Path(MinilanguageResource.PATH)
public class MinilanguageResource {
	private static final Logger LOGGER = Logger
			.getLogger(MinilanguageResource.class.getName());
	public static final String PATH = "/minilanguage";

	@POST
	@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
	@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
	public ExecutionResult execute(MultivaluedMap<String, String> parameters) {
		LOGGER.info("receiving transformer: "
				+ parameters.getFirst("transformer") + " with inputs: "
				+ parameters.get("input"));
		String transformerParam = parameters.getFirst("transformer");
		Transformer transformer = parseTransformer(transformerParam);
		String[] result = Util.runRobot(transformer, parameters.get("input")
				.toArray(new String[0]));
		LOGGER.info("result is: " + Arrays.toString(result));
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
	
	@Context UriInfo uriInfo;

	private Response createResporseForErrorParsingTransformer(Exception e) {
		return Response.status(400).entity("transformer not valid").build();
	}

}
