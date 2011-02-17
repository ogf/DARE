package es.uvigo.ei.sing.dare.entities;

import java.io.ByteArrayOutputStream;
import java.util.UUID;

import org.joda.time.DateTime;
import org.w3c.dom.Document;

import es.uvigo.ei.sing.dare.util.XMLUtil;
import es.uvigo.ei.sing.stringeditor.Minilanguage;
import es.uvigo.ei.sing.stringeditor.Transformer;
import es.uvigo.ei.sing.stringeditor.XMLInputOutput;

public class Robot {

    public static Robot createFromMinilanguage(String transformerInminilanguage) {
        Transformer transformer = Minilanguage.eval(transformerInminilanguage);
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
        String minilanguage = Minilanguage.xmlToLanguage(robotXML);
        return new Robot(minilanguage, robotXMLAsString, new DateTime(),
                minilanguage);

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

    private Robot(String code, String transformerInMinilanguage,
            String transformerInXML, DateTime creationTime, String description) {
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

}
