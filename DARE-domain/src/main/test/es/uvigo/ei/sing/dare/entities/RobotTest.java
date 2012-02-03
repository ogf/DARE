package es.uvigo.ei.sing.dare.entities;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.junit.Test;
import org.w3c.dom.Document;

import es.uvigo.ei.sing.dare.util.XMLUtil;
import es.uvigo.ei.sing.stringeditor.Minilanguage;

public class RobotTest {

    public static Document buildValidRobot() {
        return XMLUtil.toDocument(RobotTest.class
                .getResource("robot-example.xml"));
    }

    public static Document buildInvalidRobot() {
        return XMLUtil.toDocument(RobotTest.class
                .getResource("robot-invalid-example.xml"));
    }

    @Test
    public void aRobotCanBeCreatedFromACodeAndAMinilanguageString() {
        String minilanguage = "url";
        Robot robot = Robot.createFromMinilanguage(minilanguage);
        assertThat(robot.getCode(), not(nullValue()));
        assertThat(robot.getTransformerInMinilanguage(), equalTo(minilanguage));
    }

    @Test(expected = IllegalArgumentException.class)
    public void ifTheMinilanguageIsIncorretThrowIllegalArgumentException() {
        String minilanguage = "notValidMinilanguage";
        Robot.createFromMinilanguage(minilanguage);
    }

    @Test
    public void aRobotCanBeCreatedFromAXmlStringWithARobot() {
        String robotXML = readAsString(RobotTest.class
                .getResource("robot-example.xml"));
        Robot robot = Robot.createFromXML(robotXML);
        assertThat(robot.getCode(), not(nullValue()));
        assertThat(robot.getTransformerInXML(), equalTo(robotXML));
    }

    @Test
    public void aRobotCanBeCreatedFromAXmlDocumentWithARobot() {
        Document robotXML = buildValidRobot();
        Robot robot = Robot.createFromXML(robotXML);
        assertThat(robot.getTransformerInXML(),
                equalTo(XMLUtil.toString(robotXML)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void ifTheXMLDocumentIsWrongAIllegalArgumentExceptionIsThrown() {
        Document notValidRobot = buildInvalidRobot();
        Robot.createFromXML(notValidRobot);
    }

    @Test
    public void aRobotHasACreationTime() {
        Minilanguage minilanguage = new Minilanguage();
        DateTime beforeCreating = new DateTime();
        Robot robot = Robot.createFromMinilanguage(minilanguage, "url");
        Interval interval = new Interval(beforeCreating, robot.getCreationTime());

        // 60 milliseconds should be more than enough
        assertThat(interval.toDurationMillis(), lessThan(60l));
    }

    @Test
    public void aRobotCreatedFromAMinilanguageHasTheTransformerInXMLToo() {
        Robot robot = Robot.createFromMinilanguage("url");

        Document document = XMLUtil.toDocument(robot.getTransformerInXML());
        assertThat(document, not(nullValue()));
    }

    @Test
    public void aRobotInitiallyHasNoDescription() {
        Robot robot = Robot.createFromMinilanguage("url");
        assertThat(robot.getDescription(), nullValue());
    }

    @Test
    public void changingTheDescriptionImpliesCreatingANewRobotInstanceSinceItsImmutable() {
        Robot robot = Robot.createFromMinilanguage("url");

        String description = "with description";
        Robot robotWithDescription = robot.description(description);

        assertThat(robot.getDescription(), nullValue());
        assertThat(robotWithDescription.getDescription(), equalTo(description));
        assertThat(robotWithDescription.getCode(), equalTo(robot.getCode()));
        assertThat(robotWithDescription.getTransformerInMinilanguage(),
                equalTo(robot.getTransformerInMinilanguage()));
        assertThat(robotWithDescription.getTransformerInXML(),
                equalTo(robot.getTransformerInXML()));
        assertThat(robotWithDescription.getCreationTime(),
                equalTo(robot.getCreationTime()));
    }

    private ExecutorService executor = Executors.newSingleThreadExecutor();

    @Test
    public void aRobotCanBeCreatedWithATimeout() throws TimeoutException {
        try {
            Robot.createFromMinilanguage("sleep(10);url", executor, 100,
                    TimeUnit.MILLISECONDS);
            fail("it should have timeout");
        } catch (TimeoutException e) {
        }
        long t = System.currentTimeMillis();
        Robot robot = Robot.createFromMinilanguage("url", executor, 20,
                TimeUnit.SECONDS);
        long elapsedTime = System.currentTimeMillis() - t;
        assertThat(elapsedTime, lessThan(1000l));
        assertThat(robot, not(nullValue()));
    }

    private static String readAsString(URL resource) {
        Reader inputStreamReader = null;
        try {
            inputStreamReader = new InputStreamReader(resource.openStream());
            StringBuilder stringBuilder = new StringBuilder();
            char[] buffer = new char[1024];
            int read = -1;
            while ((read = inputStreamReader.read(buffer)) != -1) {
                stringBuilder.append(buffer, 0, read);
            }
            return stringBuilder.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (inputStreamReader != null) {
                try {
                    inputStreamReader.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }


}
