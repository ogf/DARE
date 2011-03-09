package es.uvigo.ei.sing.dare.configuration;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

@WebListener
public class ConfigurationBootstrapper implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        ServletContext context = sce.getServletContext();

        Configuration.associate(context, instantiateConfiguration(context));
    }

    private Configuration instantiateConfiguration(ServletContext context) {
        String className = (String) context
                .getInitParameter("configuration-class-name");
        return (Configuration) instantiate(className);
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
