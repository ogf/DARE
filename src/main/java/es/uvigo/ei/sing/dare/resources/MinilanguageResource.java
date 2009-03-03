package es.uvigo.ei.sing.dare.resources;

import java.util.logging.Logger;

import javax.ws.rs.FormParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;

@Path(MinilanguageResource.PATH)
public class MinilanguageResource {
	private static final Logger LOGGER = Logger.getLogger(MinilanguageResource.class.getName());
	public static final String PATH = "/minilanguage";
	
	@POST
	public void execute(@FormParam("transformer") String transformer, @FormParam("input") String input){
		LOGGER.info("receiving transformer: "+transformer+" with inputs: "+input);
	}

}
