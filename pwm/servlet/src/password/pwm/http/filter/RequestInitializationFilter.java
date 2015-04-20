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

package password.pwm.http.filter;

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.*;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

public class RequestInitializationFilter implements Filter {
    
    private static final PwmLogger LOGGER = PwmLogger.forClass(RequestInitializationFilter.class);

    @Override
    public void init(FilterConfig filterConfig)
            throws ServletException
    {
    }

    @Override
    public void destroy()
    {
    }

    @Override
    public void doFilter(
            ServletRequest servletRequest,
            ServletResponse servletResponse,
            FilterChain filterChain
    )
            throws IOException, ServletException
    {
        final HttpServletRequest req = (HttpServletRequest)servletRequest;
        final HttpServletResponse resp = (HttpServletResponse)servletResponse;

        try {
            //if (!(new PwmURL(req).isResourceURL())) {
                checkAndInitSessionState(req);
                final PwmRequest pwmRequest = PwmRequest.forRequest(req,resp);
                checkIfSessionRecycleNeeded(pwmRequest);
            //}
        } catch (Throwable e) {
            LOGGER.error("can't load application: " + e.getMessage());
            if (!(new PwmURL(req).isResourceURL())) {
                ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_APP_UNAVAILABLE);
                try {
                    ContextManager contextManager = ContextManager.getContextManager(servletRequest.getServletContext());
                    if (contextManager != null) {
                        errorInformation = contextManager.getStartupErrorInformation();
                    }
                } catch (Throwable e2) {
                    e2.getMessage();
                }
                servletRequest.setAttribute(PwmConstants.REQUEST_ATTR.PwmErrorInfo.toString(),errorInformation);
                final String url = PwmConstants.JSP_URL.APP_UNAVAILABLE.getPath();
                servletRequest.getServletContext().getRequestDispatcher(url).forward(req, resp);
            }
            return;
        }

        filterChain.doFilter(servletRequest, servletResponse);
    }

    private void checkAndInitSessionState(final HttpServletRequest request) 
            throws PwmUnrecoverableException 
    {
        final ContextManager contextManager = ContextManager.getContextManager(request.getSession());

        { // destroy any outdated sessions
            final HttpSession httpSession = request.getSession(false);
            if (httpSession != null) {
                final String sessionContextInitGUID = (String) httpSession.getAttribute(PwmConstants.SESSION_ATTR_CONTEXT_GUID);
                if (sessionContextInitGUID == null || !sessionContextInitGUID.equals(contextManager.getInstanceGuid())) {
                    LOGGER.debug("invalidating http session created with non-current servlet context");
                    httpSession.invalidate();
                }
            }
        }

        { // handle pwmSession init and assignment.
            final HttpSession httpSession = request.getSession();
            if (httpSession.getAttribute(PwmConstants.SESSION_ATTR_PWM_SESSION) == null) {
                final PwmApplication pwmApplication = contextManager.getPwmApplication();
                final PwmSession pwmSession = PwmSession.createPwmSession(pwmApplication);
                PwmSessionWrapper.sessionMerge(pwmApplication, pwmSession, httpSession);
            }
        }

    }

    private void checkIfSessionRecycleNeeded(final PwmRequest pwmRequest)
            throws IOException, ServletException
    {
        if (!pwmRequest.getPwmSession().getSessionStateBean().isSessionIdRecycleNeeded()) {
            return;
        }
        
        final boolean recycleEnabled = Boolean.parseBoolean( pwmRequest.getConfig().readAppProperty(AppProperty.HTTP_SESSION_RECYCLE_AT_AUTH));
        
        if (!recycleEnabled) {
            return;
        }
        LOGGER.debug(pwmRequest,"forcing new http session due to authentication");

        final HttpServletRequest req = pwmRequest.getHttpServletRequest();

        // read the old session data
        final HttpSession oldSession = req.getSession(true);
        final int oldMaxInactiveInterval = oldSession.getMaxInactiveInterval();
        final Map<String,Object> sessionAttributes = new HashMap<>();
        final Enumeration oldSessionAttrNames = oldSession.getAttributeNames();
        while (oldSessionAttrNames.hasMoreElements()) {
            final String attrName = (String)oldSessionAttrNames.nextElement();
            sessionAttributes.put(attrName, oldSession.getAttribute(attrName));
        }

        for (final String attrName : sessionAttributes.keySet()) {
            oldSession.removeAttribute(attrName);
        }

        //invalidate the old session
        oldSession.invalidate();

        // make a new session
        final HttpSession newSession = req.getSession(true);

        // write back all the session data
        for (final String attrName : sessionAttributes.keySet()) {
            newSession.setAttribute(attrName, sessionAttributes.get(attrName));
        }
        
        newSession.setMaxInactiveInterval(oldMaxInactiveInterval);
        
        pwmRequest.getPwmSession().getSessionStateBean().setSessionIdRecycleNeeded(false);
    }


}
