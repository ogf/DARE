package es.uvigo.ei.sing.dare.configuration;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import backend.core.BackendBuilder;
import clojure.lang.RT;
import es.uvigo.ei.sing.dare.domain.IBackend;
import es.uvigo.ei.sing.dare.domain.IBackendBuilder;

@WebListener
public class ConfigurationBootstrapper implements ServletContextListener {

    public enum ConfigurationType {
        STUB {
            @Override
            protected Configuration build(Context context) {
                return new ConfigurationStub();
            }
        },
        PRODUCTION {

            private IBackendBuilder builder = new BackendBuilder();

            {
                try {
                    RT.load("backend/core");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            protected Configuration build(final Context context) {
                final Map<String, Object> parameters = from(context,
                        builder.getParametersNeeded());
                final IBackend backend = builder.build(parameters);
                return new Configuration() {
                    @Override
                    public IBackend getBackend() {
                        return backend;
                    }
                };
            }

            private Map<String, Object> from(Context context,
                    Collection<String> parametersNeeded) {
                Map<String, Object> result = new HashMap<String, Object>();
                for (String each : parametersNeeded) {
                    result.put(each, lookup(context, each));
                }
                return result;
            }
        };

        public static Configuration from(String chosenConfiguration) {
            Context context = getContext();
            return ConfigurationType.valueOf(chosenConfiguration.toUpperCase())
                    .build(context);
        }

        protected abstract Configuration build(Context context);
    }

    private static Context getContext() {
        try {
            return (Context) new InitialContext().lookup("java:comp/env");
        } catch (NamingException e) {
            throw new RuntimeException(e);
        }
    }

    private static Object lookup(Context context, String name) {
        try {
            return context.lookup(name);
        } catch (NamingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        Configuration configuration = ConfigurationType
                .from((String) lookup(getContext(), "backend-type"));
        Configuration.associate(sce.getServletContext(), configuration);
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
    }
}
