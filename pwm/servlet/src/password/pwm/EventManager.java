/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2011 The PWM Project
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package password.pwm;

import password.pwm.util.PwmLogger;
import password.pwm.util.stats.Statistic;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.http.*;

/**
 * Servlet event listener, defined in web.xml
 *
 * @author Jason D. Rivard
 */
public class EventManager implements ServletContextListener, HttpSessionListener, HttpSessionActivationListener {
// ------------------------------ FIELDS ------------------------------

    // ----------------------------- CONSTANTS ----------------------------
    private static final PwmLogger LOGGER = PwmLogger.getLogger(EventManager.class);

// --------------------------- CONSTRUCTORS ---------------------------

    public EventManager()
    {
    }

// ------------------------ INTERFACE METHODS ------------------------


// --------------------- Interface HttpSessionListener ---------------------

    public void sessionCreated(final HttpSessionEvent httpSessionEvent)
    {
        final HttpSession httpSession = httpSessionEvent.getSession();
        final PwmSession pwmSession = PwmSession.getPwmSession(httpSession);
        final ContextManager contextManager = pwmSession.getContextManager();

        if (contextManager != null) {
            if (contextManager.getStatisticsManager() != null) {
                contextManager.getStatisticsManager().incrementValue(Statistic.HTTP_SESSIONS);
            }
            contextManager.addPwmSession(pwmSession);
        }

        // add a few grace seconds to the idle interval
        if (httpSession.getMaxInactiveInterval() % 60 == 0) {
            httpSession.setMaxInactiveInterval(httpSession.getMaxInactiveInterval() + 2);
        }

        LOGGER.trace(pwmSession, "http session created");
    }

    public void sessionDestroyed(final HttpSessionEvent httpSessionEvent)
    {
        final PwmSession pwmSession = PwmSession.getPwmSession(httpSessionEvent.getSession());
        LOGGER.trace(pwmSession, "http session destroyed");
        pwmSession.getSessionManager().closeConnections();
    }

// --------------------- Interface ServletContextListener ---------------------

    public void contextInitialized(final ServletContextEvent servletContextEvent)
    {
        if (null != servletContextEvent.getServletContext().getAttribute(PwmConstants.CONTEXT_ATTR_CONTEXT_MANAGER)) {
            LOGGER.warn("notice, previous servlet ContextManager exists");
        }


        try {
            final ContextManager newContextManager = new ContextManager();
            newContextManager.initialize(servletContextEvent.getServletContext());
            servletContextEvent.getServletContext().setAttribute(PwmConstants.CONTEXT_ATTR_CONTEXT_MANAGER, newContextManager);
        } catch (OutOfMemoryError e) {
            LOGGER.fatal("JAVA OUT OF MEMORY ERROR!, please allocate more memory for java: " + e.getMessage(),e);
            throw e;
        } catch (Exception e) {
            LOGGER.fatal("error initializing pwm context: " + e, e);
            System.err.println("error initializing pwm context: " + e);
        }
    }

    public void contextDestroyed(final ServletContextEvent servletContextEvent)
    {
        final ContextManager contextManager = ContextManager.getContextManager(servletContextEvent.getServletContext());
        contextManager.shutdown();
    }


// --------------------- Interface HttpSessionActivationListener ---------------------

    public void sessionWillPassivate(final HttpSessionEvent event)
    {
        final PwmSession pwmSession = PwmSession.getPwmSession(event.getSession());
        LOGGER.trace(pwmSession,"passivating session");
        pwmSession.getSessionManager().closeConnections();
    }

    public void sessionDidActivate(final HttpSessionEvent event)
    {
        final PwmSession pwmSession = PwmSession.getPwmSession(event.getSession());
        LOGGER.trace(pwmSession,"activating (de-passivating) session");
    }
}

