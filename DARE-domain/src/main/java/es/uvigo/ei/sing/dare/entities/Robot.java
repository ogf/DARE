package es.uvigo.ei.sing.dare.entities;


import static es.uvigo.ei.sing.dare.util.StringUtil.quote;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.lang.Validate;
import org.joda.time.DateTime;
import org.w3c.dom.Document;

import es.uvigo.ei.sing.dare.util.XMLUtil;
import es.uvigo.ei.sing.stringeditor.Minilanguage;
import es.uvigo.ei.sing.stringeditor.Transformer;
import es.uvigo.ei.sing.stringeditor.Util;
import es.uvigo.ei.sing.stringeditor.XMLInputOutput;

public class Robot {

    public static Robot createFromMinilanguage(
            final String transformerInMinilanguage, ExecutorService executor,
            long timeout, TimeUnit timeUnit) throws TimeoutException {
        final AtomicReference<Thread> threadRef = new AtomicReference<Thread>();
        Future<Robot> f = executor.submit(new Callable<Robot>() {

            @Override
            public Robot call() throws Exception {
                threadRef.set(Thread.currentThread());
                return createFromMinilanguage(transformerInMinilanguage);
            }
        });
        try {
            return f.get(timeout, timeUnit);
        } catch (TimeoutException e) {
            f.cancel(true);
            Thread thread = threadRef.get();
            // interrupt is too weak. we must stop the execution no matter what
            // in order to prevent DoS attack
            thread.stop();
            throw e;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                RuntimeException ex = (RuntimeException) cause;
                throw ex;
            }
            throw new RuntimeException(cause);
        }
    }

    public static Robot createFromMinilanguage(String transformerInminilanguage) {
        Transformer transformer;
        try {
            transformer = Minilanguage.eval(transformerInminilanguage);
        } catch (Exception e) {
            throw new IllegalArgumentException(quote(transformerInminilanguage)
                    + "is wrong");
        }
        return new Robot(transformerInminilanguage,
                XMLUtil.toString(fromMinilanguageToXML(transformer)),
                new DateTime(), null);
    }

    private static Document fromMinilanguageToXML(Transformer transformer) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        XMLInputOutput.writeTransformer(transformer, stream);

        // using default charset for interpreting the bytes
        return XMLUtil.toDocument(new String(stream.toByteArray()));
    }

    public static Robot createFromXML(String robotXML) {
        return createFrom(XMLUtil.toDocument(robotXML), robotXML);
    }

    public static Robot createFromXML(Document robotXML) {
        return createFrom(robotXML, XMLUtil.toString(robotXML));
    }

    private static Robot createFrom(Document robotXML, String robotXMLAsString) {
        checkValidRobot(robotXML);
        String minilanguage = Minilanguage.xmlToLanguage(robotXML);
        return new Robot(minilanguage, robotXMLAsString, new DateTime(),
                minilanguage);

    }

    private static void checkValidRobot(Document robotXML) {
        try {
            XMLInputOutput.loadTransformer(robotXML);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "the xml specified doesn't contain a valid robot");
        }
    }

    private final String code;

    private final String transformerInMinilanguage;

    private final DateTime creationTime;

    private final String transformerInXML;

    private final String description;

    private Robot(String transformerInMinilanguage,
            String transformerInXML, DateTime creationTime, String description) {
        this(UUID.randomUUID().toString(), transformerInMinilanguage,
                transformerInXML, creationTime, description);
    }

    public Robot(String code, String transformerInMinilanguage,
            String transformerInXML, DateTime creationTime, String description) {
        Validate.notNull(code);
        Validate.notNull(transformerInMinilanguage);
        Validate.notNull(transformerInXML);
        Validate.notNull(creationTime);
        this.code = code;
        this.transformerInMinilanguage = transformerInMinilanguage;
        this.transformerInXML = transformerInXML;
        this.creationTime = creationTime;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getTransformerInMinilanguage() {
        return transformerInMinilanguage;
    }

    public DateTime getCreationTime() {
        return creationTime;
    }

    public String getTransformerInXML() {
        return transformerInXML;
    }

    public String getDescription() {
        return description;
    }

    public Robot description(String description) {
        return new Robot(this.code, this.transformerInMinilanguage,
                this.transformerInXML, this.creationTime, description);
    }

    public PeriodicalExecution createPeriodical(ExecutionPeriod period,
            String[] inputs) {
        Validate.notNull(inputs);
        return createPeriodical(period, Arrays.asList(inputs));
    }

    public PeriodicalExecution createPeriodical(ExecutionPeriod period,
            List<String> inputs) {
        Validate.notNull(period);
        Validate.noNullElements(inputs);
        return new PeriodicalExecution(this, period, inputs);
    }

    public String[] execute(List<String> inputs) {
        String[] asArray = inputs.toArray(new String[0]);
        return Util.runRobot(XMLInputOutput.loadTransformer(XMLUtil
                .toDocument(transformerInXML)), asArray);
    }

}
