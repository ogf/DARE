package es.uvigo.ei.sing.dare.resources;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAnyElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.joda.time.DateTime;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

@XmlRootElement(name = "stored-robot")
@XmlAccessorType(XmlAccessType.FIELD)
public class RobotXMLView {

    public static final String ROOT_ELEMENT_NAME = "stored-robot";

    private final String code;

    /**
     * The number of milliseconds since 1970-01-01T00:00:00Z
     */
    private final long creationDateMillis;

    @XmlAnyElement
    private final Element robot;

    /**
     * Default constructor for JAXB. DO NOT USE!
     */
    public RobotXMLView() {
        this.code = "";
        this.creationDateMillis = -1;
        this.robot = null;
    }

    public RobotXMLView(String code, DateTime creationDate, Document robot) {
        this.code = code;
        this.creationDateMillis = creationDate.getMillis();
        this.robot = robot.getDocumentElement();
    }

    public String getCode() {
        return code;
    }

    public DateTime getCreationDate() {
        return new DateTime(creationDateMillis);
    }

    public Element getRobot() {
        return robot;
    }

}
