package es.uvigo.ei.sing.dare.resources;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collection;

import javax.ws.rs.core.MediaType;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.UniformInterfaceException;

import es.uvigo.ei.sing.dare.client.DARE;
import es.uvigo.ei.sing.dare.configuration.ConfigurationStub;
import es.uvigo.ei.sing.dare.resources.views.RobotExecutionResultView;
import es.uvigo.ei.sing.dare.resources.views.RobotJSONView;

@RunWith(Parameterized.class)
public class RobotResourceExecutionTest {

    public static DARE buildClientWithLoggingAndCaching(MediaType acceptedType) {
        return DARE.build("http://localhost/", 8080, acceptedType, true);
    }

    @Parameters
    public static Collection<Object[]> acceptedTypes() {
        Object[] json = { MediaType.APPLICATION_JSON_TYPE };
        Object[] xml = { MediaType.APPLICATION_XML_TYPE };
        return Arrays.asList(json, xml);
    }

    private MediaType acceptedType;

    private DARE dare;

    public RobotResourceExecutionTest(MediaType acceptedType) {
        dare = buildClientWithLoggingAndCaching(acceptedType);
        this.acceptedType = acceptedType;
    }

    @Test
    public void existsPostMethod() throws Exception {
        String robot = "url | xpath('//a/@href') | patternMatcher('(http://.*)') ";
        dare.doExecution(robot, "http://www.google.es",
                "http://www.esei.uvigo.es");
    }

    @Test
    public void onWrongTransformerThrowsException() throws Exception {
        try {
            String robot = "ur  xpath('//a/@href') | patternMatcher('(http://.*)') ";
            dare.doExecution(robot, "http://www.google.es");
        } catch (UniformInterfaceException e) {
            assertThat(e.getResponse().getStatus(), equalTo(400));
        }
    }

    @Test
    public void testErrorExecuting() throws Exception {
        ClientResponse response = dare.doExecution("url", ClientResponse.class,
                ConfigurationStub.INPUT_THAT_ALWAYS_CAUSES_ERROR);
        assertThat(response.getStatus(), equalTo(500));
        assertThat(
                response.getEntity(String.class),
                containsString(ConfigurationStub.INPUT_THAT_ALWAYS_CAUSES_ERROR));
    }

    @Test
    public void testTimeoutExecuting() throws Exception {
        ClientResponse response = dare.doExecution("url", ClientResponse.class,
                ConfigurationStub.INPUT_THAT_ALWAYS_TIMEOUTS);
        assertThat(response.getStatus(), equalTo(500));
        assertThat(response.getEntity(String.class),
                containsString("took more than"));
    }

    @Test
    public void testReturnResults() throws Exception {
        String robot = "url | xpath('//a/@href') | patternMatcher('(http://.*)') ";
        RobotExecutionResultView result = dare.doExecution(robot,
                RobotExecutionResultView.class,
                "http://www.google.es", "http://www.esei.uvigo.es");
        assertNotNull(result);
        assertNotNull(result.getInputs());
        assertFalse(result.getInputs().isEmpty());
        assertNotNull(result.getResultLines());
        assertFalse(result.getResultLines().isEmpty());
    }

    @Test
    public void itReturnsTheTimeElapsedAndTheDate() throws Exception {
        String robot = "url | xpath('//a/@href') | patternMatcher('(http://.*)') ";
        RobotExecutionResultView result = dare.doExecution(robot,
                "http://www.google.es", "http://www.esei.uvigo.es");
        assertThat(result.getExecutionTime(), greaterThan(0l));
        assertThat(result.getDate(), notNullValue());
    }

    @Test
    public void theRobotAssociatedIsStoredAndAURLToItIsStored() {
        final String robotInMinilanguage = "url | xpath('//a/@href') | patternMatcher('(http://.*)') ";
        RobotExecutionResultView result = dare.doExecution(robotInMinilanguage,
                RobotExecutionResultView.class, "http://www.google.es",
                "http://www.esei.uvigo.es");
        RobotJSONView robotJSONView = dare.doGet(
                result.getCreatedFrom(), RobotJSONView.class,
                MediaType.APPLICATION_JSON_TYPE);
        assertThat(robotJSONView.getRobotInMinilanguage(),
                equalTo(robotInMinilanguage));
    }

    private static final String linesPropertyName = "resultLines";

    private static final String executionTimePropertyName = "executionTime";

    private static final String datePropertyName = "creationDateMillis";

    private static final String inputsPropertyName = "inputs";

    @Test
    public void testStructureDocumentReturnedDirectly() throws Exception {
        String robot = "url | xpath('//a/@href') | patternMatcher('(http://.*)') ";
        if (acceptedType == MediaType.APPLICATION_JSON_TYPE) {
            JSONObject result = dare.doExecution(robot, JSONObject.class,
                    "http://www.google.es",
                    "http://www.esei.uvigo.es");
            checkStructureIsCorrect(result);
        } else {
            Document document = dare.doExecution(robot, Document.class,
                    "http://www.google.es", "http://www.esei.uvigo.es");
            checkStructureIsCorrect(document);
        }
    }

    private void checkStructureIsCorrect(JSONObject result)
            throws JSONException {
        assertTrue(result.has(inputsPropertyName));
        assertThat(result.get(inputsPropertyName), is(JSONArray.class));
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

        NodeList inputsElement = root.getElementsByTagName(inputsPropertyName);
        assertThat("there is one inputs element in the response",
                inputsElement.getLength(), equalTo(1));

        NodeList dateElements = root.getElementsByTagName(datePropertyName);
        assertThat("there is one date element", dateElements.getLength(),
                equalTo(1));
    }

}
