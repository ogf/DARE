package es.uvigo.ei.sing.dare.resources;

import java.net.URI;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriBuilder;

import junit.framework.TestCase;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.core.util.MultivaluedMapImpl;

public class MinilanguageResourceTest extends TestCase {

	private static final URI BASE_URI = UriBuilder.fromUri("http://localhost/")
			.port(8080).path("DARE").build();
	private WebResource appResource;

	public MinilanguageResourceTest() {
		Client c = Client.create();
		appResource = c.resource(BASE_URI);
	}

	public void testExistsPostMethod() throws Exception {
		appResource.path(MinilanguageResource.PATH).type(
				MediaType.APPLICATION_FORM_URLENCODED).post(
				new MultivaluedMapImpl() {
					{
						add("transformer", "url | xpath('//a/@href') | patternMatcher('(http://.*)') ");
						add("input", "www.google.es");
					}
				});
	}
}
