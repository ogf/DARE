package es.uvigo.ei.sing.dare.configuration;

import javax.servlet.ServletContext;

import es.uvigo.ei.sing.dare.domain.IRobotExecutor;
import es.uvigo.ei.sing.dare.domain.IStore;

public abstract class Configuration {

    public static void associate(ServletContext context,
            Configuration configuration) {
        if (getFromAttribute(context) != null) {
            throw new IllegalStateException(
                    "the servlet context already has an associated "
                            + Configuration.class.getSimpleName()
                            + " with the provided context: " + context);
        }
        context.setAttribute(Configuration.class.getName(), configuration);
    }

    public static Configuration from(ServletContext context) {
        Configuration configuration = getFromAttribute(context);
        if (configuration == null) {
            throw new IllegalStateException("the "
                    + Configuration.class.getSimpleName()
                    + " has not been associated with the provided context: "
                    + context);
        }
        return configuration;
    }

    private static Configuration getFromAttribute(ServletContext context) {
        return (Configuration) context.getAttribute(Configuration.class
                .getName());
    }

    public abstract IStore getStore();

    public abstract IRobotExecutor getRobotExecutor();

}
