/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2012 The PWM Project
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

import password.pwm.config.PwmSetting;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.PwmLogger;
import password.pwm.util.stats.Statistic;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionActivationListener;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;

/**
 * Servlet event listener, defined in web.xml
 *
 * @author Jason D. Rivard
 */
public class HttpEventManager implements ServletContextListener, HttpSessionListener, HttpSessionActivationListener {
// ------------------------------ FIELDS ------------------------------

    // ----------------------------- CONSTANTS ----------------------------
    private static final PwmLogger LOGGER = PwmLogger.getLogger(HttpEventManager.class);

// --------------------------- CONSTRUCTORS ---------------------------

    public HttpEventManager()
    {
    }

// ------------------------ INTERFACE METHODS ------------------------


// --------------------- Interface HttpSessionListener ---------------------

    public void sessionCreated(final HttpSessionEvent httpSessionEvent)
    {
        final HttpSession httpSession = httpSessionEvent.getSession();

        try {
            final PwmSession pwmSession = PwmSession.getPwmSession(httpSession);
            final PwmApplication pwmApplication = ContextManager.getPwmApplication(httpSession);

            if (pwmApplication != null) {
                if (pwmApplication.getStatisticsManager() != null) {
                    pwmApplication.getStatisticsManager().incrementValue(Statistic.HTTP_SESSIONS);
                }
                ContextManager.getContextManager(httpSessionEvent.getSession().getServletContext()).addPwmSession(pwmSession);

                final int sessionIdle = (int)pwmApplication.getConfig().readSettingAsLong(PwmSetting.IDLE_TIMEOUT_SECONDS);
                httpSession.setMaxInactiveInterval(sessionIdle);
            }

            LOGGER.trace(pwmSession, "http session created");
        } catch (PwmUnrecoverableException e) {
            LOGGER.error("unable to establish pwm session: " + e.getMessage());
        }

    }

    public void sessionDestroyed(final HttpSessionEvent httpSessionEvent)
    {
        try {
            final PwmSession pwmSession = PwmSession.getPwmSession(httpSessionEvent.getSession());
            LOGGER.trace(pwmSession, "http session destroyed");
            pwmSession.getSessionManager().closeConnections();
        } catch (PwmUnrecoverableException e) {
            LOGGER.error("unable to destroy pwm session: " + e.getMessage());
        }
    }

// --------------------- Interface ServletContextListener ---------------------

    public void contextInitialized(final ServletContextEvent servletContextEvent)
    {
        if (null != servletContextEvent.getServletContext().getAttribute(PwmConstants.CONTEXT_ATTR_CONTEXT_MANAGER)) {
            LOGGER.warn("notice, previous servlet ContextManager exists");
        }


        try {
            final ContextManager newContextManager = new ContextManager(servletContextEvent.getServletContext());
            newContextManager.initialize();
            servletContextEvent.getServletContext().setAttribute(PwmConstants.CONTEXT_ATTR_CONTEXT_MANAGER, newContextManager);
        } catch (OutOfMemoryError e) {
            LOGGER.fatal("JAVA OUT OF MEMORY ERROR!, please allocate more memory for java: " + e.getMessage(),e);
            throw e;
        } catch (Exception e) {
            LOGGER.fatal("error initializing pwm context: " + e, e);
            System.err.println("error initializing pwm context: " + e);
            System.out.println("error initializing pwm context: " + e);
            e.printStackTrace();
        }
    }

    public void contextDestroyed(final ServletContextEvent servletContextEvent)
    {
        try {
            final ContextManager contextManager = ContextManager.getContextManager(servletContextEvent.getServletContext());
            contextManager.shutdown();
        } catch (PwmUnrecoverableException e) {
            LOGGER.error("unable to destroy pwm context: " + e.getMessage());
        }
    }


// --------------------- Interface HttpSessionActivationListener ---------------------

    public void sessionWillPassivate(final HttpSessionEvent event)
    {
        try {
            final PwmSession pwmSession = PwmSession.getPwmSession(event.getSession());
            LOGGER.trace(pwmSession,"passivating session");
            pwmSession.getSessionManager().closeConnections();
        } catch (PwmUnrecoverableException e) {
            LOGGER.error("unable to passivate pwm context: " + e.getMessage());
        }
    }

    public void sessionDidActivate(final HttpSessionEvent event)
    {
        try {
            final PwmSession pwmSession = PwmSession.getPwmSession(event.getSession());
            LOGGER.trace(pwmSession,"activating (de-passivating) session");
        } catch (PwmUnrecoverableException e) {
            LOGGER.error("unable to activate  pwm context: " + e.getMessage());
        }
    }
}

