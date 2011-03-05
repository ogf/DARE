package es.uvigo.ei.sing.dare.resources;

import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.Provider;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;

import com.sun.jersey.api.json.JSONConfiguration;
import com.sun.jersey.api.json.JSONJAXBContext;

import es.uvigo.ei.sing.dare.resources.views.ExecutionResultView;
import es.uvigo.ei.sing.dare.resources.views.RobotJSONView;

@Provider
public class JAXBJSONConfigurator implements ContextResolver<JAXBContext> {

    @SuppressWarnings("serial")
    private Map<Class<?>, JAXBContext> contextsByClass = new HashMap<Class<?>, JAXBContext>() {
        {
            add(ExecutionResultView.class,
                    JSONConfiguration.mapped().nonStrings("executionTime")
                            .nonStrings("date").arrays("resultLines").build());
            add(RobotJSONView.class,
                    JSONConfiguration.mapped().nonStrings("creationDateMillis")
                            .build());
        }

        private void add(Class<?> klass, JSONConfiguration configuration) {
            try {
                put(klass, new JSONJAXBContext(configuration,
                        new Class[] { klass }));
            } catch (JAXBException e) {
                throw new RuntimeException(e);
            }
        }
    };

    @Override
    public JAXBContext getContext(Class<?> type) {
        return contextsByClass.get(type);
    }

}
