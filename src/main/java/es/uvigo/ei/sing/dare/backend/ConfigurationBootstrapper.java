package es.uvigo.ei.sing.dare.backend;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

@WebListener
public class ConfigurationBootstrapper implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        ServletContext context = sce.getServletContext();

        IStore executionsStore = findExecutionsStore(context);
        Configuration.associate(context, new Configuration(executionsStore));
    }

    private IStore findExecutionsStore(ServletContext context) {
        String className = (String) context
                .getInitParameter("executions-store-class-name");
        return (IStore) instantiate(className);
    }

    private Object instantiate(String className) {
        try {
            return Class.forName(className).newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
    }
}
