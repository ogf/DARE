package es.uvigo.ei.sing.dare.configuration;

import java.util.concurrent.ExecutorService;

import javax.servlet.ServletContext;

import es.uvigo.ei.sing.dare.domain.IBackend;
import es.uvigo.ei.sing.dare.domain.MinilanguageProducer;

public abstract class Configuration {

    public static final String PERIODICAL_EXECUTION_BASE_PATH = "periodical";

    public static final String ROBOT_BASE_PATH = "robot";

    public static final String EXECUTION_RESULT_BASE_URL = "result";

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

    public abstract IBackend getBackend();

    public abstract ExecutorService getRobotParserExecutor();

    public abstract MinilanguageProducer getMinilanguageProducer();

}
