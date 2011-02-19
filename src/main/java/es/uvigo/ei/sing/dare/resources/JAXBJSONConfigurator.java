package es.uvigo.ei.sing.dare.resources;

import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.Provider;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;

import com.sun.jersey.api.json.JSONConfiguration;
import com.sun.jersey.api.json.JSONJAXBContext;

import es.uvigo.ei.sing.dare.entities.ExecutionResult;

@Provider
public class JAXBJSONConfigurator implements ContextResolver<JAXBContext> {

    private JSONJAXBContext context;

    public JAXBJSONConfigurator() throws JAXBException {
        JSONConfiguration configuration = JSONConfiguration.mapped()
                .nonStrings("executionTime").nonStrings("date").arrays("lines")
                .build();
        context = new JSONJAXBContext(configuration,
                new Class[] { ExecutionResult.class });
    }

    @Override
    public JAXBContext getContext(Class<?> type) {
        if (type.equals(ExecutionResult.class)) {
            return context;
        }
        return null;
    }

}
