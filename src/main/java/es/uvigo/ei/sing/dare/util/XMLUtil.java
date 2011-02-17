package es.uvigo.ei.sing.dare.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;

public class XMLUtil {

    private static final Logger LOGGER = Logger.getLogger(XMLUtil.class
            .getName());

    private static final DocumentBuilderFactory builderFactory = DocumentBuilderFactory
            .newInstance();

    private static final TransformerFactory xmlPrinterFactory;

    static {
        builderFactory.setIgnoringElementContentWhitespace(true);
        builderFactory.setIgnoringComments(true);
        xmlPrinterFactory = TransformerFactory.newInstance();
    }

    public static Document toDocument(String xmlAsString) {
        try {
            DocumentBuilder builder = builderFactory.newDocumentBuilder();
            return builder
                    .parse(new InputSource(new StringReader(xmlAsString)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Document toDocument(URL resource) {
        InputStream input = null;
        try {
            DocumentBuilder builder = builderFactory.newDocumentBuilder();
            input = resource.openStream();
            return builder.parse(input);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING,
                            "error closing InputStream for url " + resource, e);
                }
            }
        }
    }

    public static String toString(Document xml) {
        try {
            Transformer newTransformer = xmlPrinterFactory.newTransformer();
            Source source = new DOMSource(xml);
            StringWriter result = new StringWriter();
            Result target = new StreamResult(result);
            newTransformer.transform(source, target);
            return result.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private XMLUtil() {
        // utility class, not instantiable
    }

}
