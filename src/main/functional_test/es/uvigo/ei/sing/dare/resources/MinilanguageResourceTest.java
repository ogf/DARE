package es.uvigo.ei.sing.dare.resources;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

import java.net.URI;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriBuilder;

import org.junit.Test;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.core.util.MultivaluedMapImpl;

public class MinilanguageResourceTest {


    private static final URI BASE_URI = UriBuilder.fromUri("http://localhost/")
            .port(8080).path("DARE").build();

    private WebResource appResource;

    public MinilanguageResourceTest() {
        Client c = Client.create();
        appResource = c.resource(BASE_URI);
    }

    @Test
    public void existsPostMethod() throws Exception {
        doPostOnMinilanguageResource(new MultivaluedMapImpl() {
            {
                add("transformer",
                        "url | xpath('//a/@href') | patternMatcher('(http://.*)') ");
                add("input", "http://www.google.es");
                add("input", "http://www.esei.uvigo.es");
            }
        });
    }

    @Test
    public void onWrongTransformerThrowsException() throws Exception {
        try {
            doPostOnMinilanguageResource(new MultivaluedMapImpl() {
                {
                    add("transformer",
                            "ur xpath('//a/@href') | patternMatcher('(http://.*)') ");
                    add("input", "http://www.google.es");
                }
            });
        } catch (UniformInterfaceException e) {
            assertThat(e.getResponse().getStatus(), equalTo(400));
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

    @Test
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
            assertThat(e.getResponse().getStatus(), equalTo(500));
        }
    }

    @Test
    public void testReturnResults() throws Exception {
        ExecutionResult result = doPostOnMinilanguageResource(
                ExecutionResult.class, new MultivaluedMapImpl() {
                    {
                        add("transformer",
                                "url | xpath('//a/@href') | patternMatcher('(http://.*)') ");
                        add("input", "http://www.google.es");
                        add("input", "http://www.esei.uvigo.es");
                    }
                });
        assertNotNull(result);
        assertNotNull(result.getLines());
        assertFalse(result.getLines().isEmpty());
    }

}
