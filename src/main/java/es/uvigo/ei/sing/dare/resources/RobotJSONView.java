package es.uvigo.ei.sing.dare.resources;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import org.joda.time.DateTime;

@XmlRootElement(name = RobotXMLView.ROOT_ELEMENT_NAME)
@XmlAccessorType(XmlAccessType.FIELD)
public class RobotJSONView {

    private final String code;

    /**
     * The number of milliseconds since 1970-01-01T00:00:00Z
     */
    private final long creationDateMillis;

    private final String robotInMinilanguage;

    /**
     * Default constructor for JAXB. DO NOT USE!
     */
    public RobotJSONView() {
        this.code = null;
        this.creationDateMillis = -1;
        this.robotInMinilanguage = null;
    }

    public RobotJSONView(String code, DateTime creationDate,
            String robotInMinilanguage) {
        this.code = code;
        this.creationDateMillis = creationDate.getMillis();
        this.robotInMinilanguage = robotInMinilanguage;
    }

    public String getCode() {
        return code;
    }

    public DateTime getCreationDate() {
        return new DateTime(creationDateMillis);
    }

    public String getRobotInMinilanguage() {
        return robotInMinilanguage;
    }

}
