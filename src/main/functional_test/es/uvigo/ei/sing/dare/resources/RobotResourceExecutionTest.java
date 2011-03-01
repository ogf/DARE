package es.uvigo.ei.sing.dare.resources;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.UUID;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriBuilder;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.filter.LoggingFilter;
import com.sun.jersey.core.util.MultivaluedMapImpl;

import es.uvigo.ei.sing.dare.entities.ExecutionResult;

@SuppressWarnings("serial")
@RunWith(Parameterized.class)
public class RobotResourceExecutionTest {

    public static final URI APPLICATION_URI = UriBuilder
            .fromUri("http://localhost/").port(8080).path("DARE").build();

    @Parameters
    public static Collection<Object[]> acceptedTypes() {
        Object[] json = { MediaType.APPLICATION_JSON_TYPE };
        Object[] xml = { MediaType.APPLICATION_XML_TYPE };
        return Arrays.asList(json, xml);
    }

    private WebResource appResource;

    private MediaType acceptedType;

    public RobotResourceExecutionTest(MediaType acceptedType) {
        Client c = Client.create();
        this.appResource = c.resource(APPLICATION_URI);
        this.acceptedType = acceptedType;
        c.addFilter(new LoggingFilter());
    }

    @Test
    public void existsPostMethod() throws Exception {
        postRobotExecution(new MultivaluedMapImpl() {
            {
                add("robot",
                        "url | xpath('//a/@href') | patternMatcher('(http://.*)') ");
                add("input", "http://www.google.es");
                add("input", "http://www.esei.uvigo.es");
            }
        });
    }

    @Test
    public void onWrongTransformerThrowsException() throws Exception {
        try {
            postRobotExecution(new MultivaluedMapImpl() {
                {
                    add("robot",
                            "ur xpath('//a/@href') | patternMatcher('(http://.*)') ");
                    add("input", "http://www.google.es");
                }
            });
        } catch (UniformInterfaceException e) {
            assertThat(e.getResponse().getStatus(), equalTo(400));
        }
    }

    private void postRobotExecution(MultivaluedMapImpl postEntity) {
        postRobotExecution(ExecutionResult.class, postEntity);
    }

    private <T> T postRobotExecution(Class<T> type,
            MultivaluedMapImpl postEntity) {
        return appResource.path("robot/execute")
                .type(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(acceptedType).post(type, postEntity);
    }

    @Test
    @Ignore("review string editor execution handling")
    public void testErrorExecuting() throws Exception {
        try {
            postRobotExecution(new MultivaluedMapImpl() {
                {
                    add("robot",
                            "url | xpath('//a/@href') | patternMatcher('(http://.*)') ");
                    add("input", "http://www." + UUID.randomUUID() + ".es");
                }
            });
            fail("it must fail since it can't be executed because one of the inputs is wrong");
        } catch (UniformInterfaceException e) {
            assertThat(e.getResponse().getStatus(), equalTo(500));
        }
    }

    @Test
    public void testReturnResults() throws Exception {
        ExecutionResult result = postRobotExecution(
                ExecutionResult.class, new MultivaluedMapImpl() {
                    {
                        add("robot",
                                "url | xpath('//a/@href') | patternMatcher('(http://.*)') ");
                        add("input", "http://www.google.es");
                        add("input", "http://www.esei.uvigo.es");
                    }
                });
        assertNotNull(result);
        assertNotNull(result.getLines());
        assertFalse(result.getLines().isEmpty());
    }

    @Test
    public void itReturnsTheTimeElapsedAndTheDate() throws Exception {
        ExecutionResult result = postRobotExecution(
                ExecutionResult.class, new MultivaluedMapImpl() {
                    {
                        add("robot",
                                "url | xpath('//a/@href') | patternMatcher('(http://.*)') ");
                        add("input", "http://www.google.es");
                        add("input", "http://www.esei.uvigo.es");
                    }
                });
        assertThat(result.getExecutionTime(), greaterThan(0l));
        assertThat(result.getDate(), notNullValue());
    }

    private static final String linesPropertyName = "lines";

    private static final String executionTimePropertyName = "executionTime";

    private static final String datePropertyName = "date";

    @Test
    public void testStructureDocumentReturnedDirectly() throws Exception {
        MultivaluedMapImpl request = new MultivaluedMapImpl() {
            {
                add("robot",
                        "url | xpath('//a/@href') | patternMatcher('(http://.*)') ");
                add("input", "http://www.google.es");
                add("input", "http://www.esei.uvigo.es");
            }
        };
        if (acceptedType == MediaType.APPLICATION_JSON_TYPE) {
            JSONObject result = postRobotExecution(JSONObject.class,
                    request);
            checkStructureIsCorrect(result);
        } else {
            Document document = postRobotExecution(Document.class,
                    request);
            checkStructureIsCorrect(document);
        }
    }

    private void checkStructureIsCorrect(JSONObject result)
            throws JSONException {
        assertTrue(result.has(linesPropertyName));
        assertThat(result.get(linesPropertyName), is(JSONArray.class));

        JSONArray jsonArray = result.getJSONArray(linesPropertyName);
        assertThat(jsonArray.length(), greaterThan(0));

        assertTrue(result.has(executionTimePropertyName));
        assertThat(result.get(executionTimePropertyName), is(Number.class));

        assertTrue(result.has(datePropertyName));
        assertThat(result.get(datePropertyName), is(Number.class));
    }

    private void checkStructureIsCorrect(Document document) {
        Element root = document.getDocumentElement();
        assertThat("we don't want to use namespaces", root.getNamespaceURI(),
                nullValue());
        assertThat(root.getNodeName(), equalTo("result"));

        NodeList executionTimeElements = root
                .getElementsByTagName(executionTimePropertyName);
        assertThat("there is one executionTime element in the response",
                executionTimeElements.getLength(), equalTo(1));

        NodeList linesElements = root.getElementsByTagName(linesPropertyName);
        assertThat("there is one lines property name in the response",
                linesElements.getLength(), equalTo(1));

        NodeList dateElements = root.getElementsByTagName(datePropertyName);
        assertThat("there is one date element", dateElements.getLength(),
                equalTo(1));
    }

}
