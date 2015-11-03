/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2015 The PWM Project
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

package password.pwm.http;

import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.logging.PwmLogger;

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
public class HttpEventManager implements
        ServletContextListener,
        HttpSessionListener,
        HttpSessionActivationListener
{
    private static final PwmLogger LOGGER = PwmLogger.forClass(HttpEventManager.class);

    public HttpEventManager()
    {
    }

    public void sessionCreated(final HttpSessionEvent httpSessionEvent)
    {
        final HttpSession httpSession = httpSessionEvent.getSession();
        try {
            final ContextManager contextManager = ContextManager.getContextManager(httpSession);
            httpSession.setAttribute(PwmConstants.SESSION_ATTR_CONTEXT_GUID,contextManager.getInstanceGuid());
            LOGGER.trace("new http session created");
        } catch (PwmUnrecoverableException e) {
            LOGGER.warn("error during sessionCreated event: " + e.getMessage());
        }
    }

    public void sessionDestroyed(final HttpSessionEvent httpSessionEvent)
    {
        final HttpSession httpSession = httpSessionEvent.getSession();
        try {
            if (httpSession.getAttribute(PwmConstants.SESSION_ATTR_PWM_SESSION) != null) {
                final PwmSession pwmSession = PwmSessionWrapper.readPwmSession(httpSession);
                if (pwmSession != null) {
                    pwmSession.unauthenticateUser(null);
                }
                final PwmApplication pwmApplication = ContextManager.getPwmApplication(httpSession);
                if (pwmApplication != null) {
                    pwmApplication.getSessionTrackService().removeSessionData(pwmSession);
                }
                LOGGER.trace(pwmSession, "destroyed session");
            } else {
                LOGGER.trace("invalidated uninitialized session");
            }
        } catch (PwmUnrecoverableException e) {
            LOGGER.warn("error during httpSessionDestroyed: " + e.getMessage());
        }
    }


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
            LOGGER.fatal("error initializing context: " + e, e);
            System.err.println("error initializing context: " + e);
            System.out.println("error initializing context: " + e);
            e.printStackTrace();
        }
    }

    public void contextDestroyed(final ServletContextEvent servletContextEvent)
    {
        try {
            final ContextManager contextManager = ContextManager.getContextManager(servletContextEvent.getServletContext());
            contextManager.shutdown();
        } catch (PwmUnrecoverableException e) {
            LOGGER.error("unable to destroy context: " + e.getMessage());
        }
    }


    public void sessionWillPassivate(final HttpSessionEvent event)
    {
        try {
            final PwmSession pwmSession = PwmSessionWrapper.readPwmSession(event.getSession());
            LOGGER.trace(pwmSession.getLabel(), "passivating session");
        } catch (PwmUnrecoverableException e) {
            LOGGER.error("unable to passivate session: " + e.getMessage());
        }
    }

    public void sessionDidActivate(final HttpSessionEvent event)
    {
        try {
            final HttpSession httpSession = event.getSession();
            final PwmSession pwmSession = PwmSessionWrapper.readPwmSession(httpSession);
            LOGGER.trace(pwmSession.getLabel(),"activating (de-passivating) session");
            final PwmApplication pwmApplication = ContextManager.getPwmApplication(httpSession);
            if (pwmApplication != null) {
                pwmApplication.getSessionTrackService().addSessionData(pwmSession);
            }
        } catch (PwmUnrecoverableException e) {
            LOGGER.error("unable to activate (de-passivate) session: " + e.getMessage());
        }
    }
}

