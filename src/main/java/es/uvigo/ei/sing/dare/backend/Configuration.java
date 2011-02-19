package es.uvigo.ei.sing.dare.backend;

import javax.servlet.ServletContext;

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

    private final IStore executionsStore;

    protected Configuration(IStore executionsStore) {
        if (executionsStore == null) {
            throw new IllegalArgumentException("executionsStore can't be null");
        }
        this.executionsStore = executionsStore;
    }

    public IStore getExecutionsStore() {
        return executionsStore;
    }

}
