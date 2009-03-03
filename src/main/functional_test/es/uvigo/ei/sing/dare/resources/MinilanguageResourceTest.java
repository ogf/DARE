package es.uvigo.ei.sing.dare.resources;

import java.net.URI;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriBuilder;

import junit.framework.TestCase;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.UniformInterfaceException;
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
		doPostOnMinilanguageResource(new MultivaluedMapImpl() {
			{
				add("transformer",
						"url | xpath('//a/@href') | patternMatcher('(http://.*)') ");
				add("input", "http://www.google.es");
				add("input", "http://www.ei.uvigo.es");
			}
		});
	}

	public void testWrongTransformer() throws Exception {
		try {
			doPostOnMinilanguageResource(new MultivaluedMapImpl() {
				{
					add("transformer",
							"ur xpath('//a/@href') | patternMatcher('(http://.*)') ");
					add("input", "http://www.google.es");
				}
			});
		} catch (UniformInterfaceException e) {
			assertEquals(400, e.getResponse().getStatus());
		}
	}

	private void doPostOnMinilanguageResource(MultivaluedMapImpl postEntity) {
		doPostOnMinilanguageResource(ExecutionResult.class, postEntity);
	}

	private <T> T doPostOnMinilanguageResource(Class<T> type,
			MultivaluedMapImpl postEntity) {
		return appResource.path(MinilanguageResource.PATH).type(
				MediaType.APPLICATION_FORM_URLENCODED).accept(
				MediaType.APPLICATION_JSON_TYPE).post(type, postEntity);
	}

	public void testErrorExecuting() throws Exception {
		try {
			doPostOnMinilanguageResource(new MultivaluedMapImpl() {
				{
					add("transformer",
							"url | xpath('//a/@href') | patternMatcher('(http://.*)') ");
					add("input", "http://www.ogle.es");
				}
			});
		} catch (UniformInterfaceException e) {
			assertEquals(500, e.getResponse().getStatus());
		}
	}

	public void testReturnResults() throws Exception {
		ExecutionResult result = doPostOnMinilanguageResource(ExecutionResult.class,
				new MultivaluedMapImpl() {
					{
						add("transformer",
								"url | xpath('//a/@href') | patternMatcher('(http://.*)') ");
						add("input", "http://www.google.es");
						add("input", "http://www.ei.uvigo.es");
					}
				});
		assertNotNull(result);
		assertNotNull(result.getLines());
		assertFalse(result.getLines().isEmpty());

	}

}
