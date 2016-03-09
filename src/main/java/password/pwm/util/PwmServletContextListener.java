package password.pwm.util;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

public class PwmServletContextListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        // Doing this here so the level will be set before any calls are made to LogManager or Logger.
        Logger.getLogger("org.glassfish.jersey").setLevel(Level.SEVERE);
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
    }

}
