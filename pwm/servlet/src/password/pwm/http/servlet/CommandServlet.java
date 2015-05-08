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

package password.pwm.http.servlet;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.Permission;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.SessionStateBean;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmSession;
import password.pwm.http.filter.AuthenticationFilter;
import password.pwm.util.logging.PwmLogger;
import password.pwm.ws.server.RestResultBean;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.Date;

/**
 * Processes a variety of different commands sent in an HTTP Request, including logoff.
 *
 * @author Jason D. Rivard
 */
public class CommandServlet extends PwmServlet {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.forClass(CommandServlet.class);

    @Override
    protected ProcessAction readProcessAction(PwmRequest request)
            throws PwmUnrecoverableException
    {
        return null;
    }

    public void processAction(
            final PwmRequest pwmRequest
    )
            throws ServletException, IOException, ChaiUnavailableException, PwmUnrecoverableException
    {
        final PwmSession pwmSession = pwmRequest.getPwmSession();

        String action = pwmRequest.readParameterAsString(PwmConstants.PARAM_ACTION_REQUEST);
        if (action.isEmpty()) {
            String uri = pwmRequest.getHttpServletRequest().getRequestURI();
            if (uri != null && !uri.toLowerCase().endsWith("command") && !uri.toLowerCase().endsWith("CommandServlet")) {
                final int lastSlash = uri.lastIndexOf("/");
                action = uri.substring(lastSlash + 1, uri.length());
            }
        }

        LOGGER.trace(pwmSession, "received request for action " + action);

        if (action.equalsIgnoreCase("idleUpdate")) {
            processIdleUpdate(pwmRequest);
        } else if (action.equalsIgnoreCase("checkResponses") || action.equalsIgnoreCase("checkIfResponseConfigNeeded")) {
            CheckCommands.processCheckResponses(pwmRequest);
        } else if (action.equalsIgnoreCase("checkExpire")) {
            CheckCommands.processCheckExpire(pwmRequest);
        } else if (action.equalsIgnoreCase("checkProfile") || action.equalsIgnoreCase("checkAttributes")) {
            CheckCommands.processCheckProfile(pwmRequest);
        } else if (action.equalsIgnoreCase("checkAll")) {
            CheckCommands.processCheckAll(pwmRequest);
        } else if (action.equalsIgnoreCase("continue")) {
            processContinue(pwmRequest);
        } else if (action.equalsIgnoreCase("pageLeaveNotice")) {
            processPageLeaveNotice(pwmRequest);
        } else if (action.equalsIgnoreCase("clearIntruderTable")) {
            processClearIntruderTable(pwmRequest);
        } else {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN,"unknown command sent to CommandServlet: " + action);
            LOGGER.debug(pwmSession, errorInformation);
            pwmRequest.respondWithError(errorInformation);
        }
    }

    private static void processIdleUpdate(
            final PwmRequest pwmRequest
    )
            throws ChaiUnavailableException, IOException, ServletException, PwmUnrecoverableException
    {
        pwmRequest.validatePwmFormID();
        if (!pwmRequest.getPwmResponse().isCommitted()) {
            pwmRequest.getPwmResponse().setHeader(PwmConstants.HttpHeader.Cache_Control, "no-cache, no-store, must-revalidate");
            pwmRequest.getPwmResponse().setContentType(PwmConstants.ContentTypeValue.plain);
        }
    }



    private static void processContinue(
            final PwmRequest pwmRequest
    )
            throws IOException, PwmUnrecoverableException, ServletException
    {
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final Configuration config = pwmApplication.getConfig();

        if (ssBean.isAuthenticated()) {
            if (AuthenticationFilter.forceRequiredRedirects(pwmRequest)) {
                return;
            }

            // log the user out if our finish action is currently set to log out.
            final boolean forceLogoutOnChange = config.readSettingAsBoolean(PwmSetting.LOGOUT_AFTER_PASSWORD_CHANGE);
            if (forceLogoutOnChange && pwmSession.getSessionStateBean().isPasswordModified()) {
                LOGGER.trace(pwmSession, "logging out user; password has been modified");
                pwmRequest.sendRedirect(PwmConstants.URL_SERVLET_LOGOUT);
                return;
            }
        }

        redirectToForwardURL(pwmRequest);
    }


    private void processPageLeaveNotice(final PwmRequest pwmRequest)
            throws PwmUnrecoverableException, IOException, ChaiUnavailableException, ServletException
    {
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final String referrer = pwmRequest.getHttpServletRequest().getHeader("Referer");
        final Date pageLeaveNoticeTime = new Date();
        pwmSession.getSessionStateBean().setPageLeaveNoticeTime(pageLeaveNoticeTime);
        LOGGER.debug("pageLeaveNotice indicated at " + PwmConstants.DEFAULT_DATETIME_FORMAT.format(pageLeaveNoticeTime) + ", referer=" + referrer);
        if (!pwmRequest.getPwmResponse().isCommitted()) {
            pwmRequest.getPwmResponse().setHeader(PwmConstants.HttpHeader.Cache_Control, "no-cache, no-store, must-revalidate");
            pwmRequest.getPwmResponse().setContentType(PwmConstants.ContentTypeValue.plain);
        }
    }


    private void processClearIntruderTable(
            final PwmRequest pwmRequest
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, IOException, ServletException
    {
        pwmRequest.validatePwmFormID();
        if (!checkIfUserAuthenticated(pwmRequest)) {
            return;
        }

        if (!pwmRequest.getPwmSession().getSessionManager().checkPermission(pwmRequest.getPwmApplication(), Permission.PWMADMIN)) {
            LOGGER.info(pwmRequest, "unable to execute clear intruder records");
            return;
        }

        //pwmApplication.getIntruderManager().clear();

        RestResultBean restResultBean = new RestResultBean();
        pwmRequest.outputJsonResult(restResultBean);
    }


    private static void redirectToForwardURL(final PwmRequest pwmRequest)
            throws IOException, PwmUnrecoverableException
    {
        final SessionStateBean sessionStateBean = pwmRequest.getPwmSession().getSessionStateBean();

        final String redirectURL = pwmRequest.getForwardUrl();
        LOGGER.trace(pwmRequest, "redirecting user to forward url: " + redirectURL);

        // after redirecting we need to clear the session forward url
        if (sessionStateBean.getForwardURL() != null) {
            LOGGER.trace(pwmRequest, "clearing session forward url: " +  sessionStateBean.getForwardURL());
            sessionStateBean.setForwardURL(null);
        }

        pwmRequest.sendRedirect(redirectURL);
    }

    private static boolean checkIfUserAuthenticated(
            final PwmRequest pwmRequest
    )
            throws ChaiUnavailableException, IOException, ServletException, PwmUnrecoverableException {
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();

        if (!ssBean.isAuthenticated()) {
            final String action = pwmRequest.readParameterAsString(PwmConstants.PARAM_ACTION_REQUEST);
            LOGGER.info(pwmSession, "authentication required for " + action);
            pwmRequest.respondWithError(PwmError.ERROR_AUTHENTICATION_REQUIRED.toInfo());
            return false;
        }
        return true;
    }

    private static class CheckCommands {
        private static void processCheckProfile(
                final PwmRequest pwmRequest
        )
                throws ChaiUnavailableException, IOException, ServletException, PwmUnrecoverableException
        {
            if (!checkIfUserAuthenticated(pwmRequest)) {
                return;
            }

            if (pwmRequest.getPwmSession().getUserInfoBean().isRequiresUpdateProfile()) {
                pwmRequest.sendRedirect(PwmConstants.URL_SERVLET_UPDATE_PROFILE);
            } else {
                redirectToForwardURL(pwmRequest);
            }
        }

        private static void processCheckAll(
                final PwmRequest pwmRequest
        )
                throws ChaiUnavailableException, IOException, ServletException, PwmUnrecoverableException
        {
            if (!checkIfUserAuthenticated(pwmRequest)) {
                return;
            }

            if (!AuthenticationFilter.forceRequiredRedirects(pwmRequest)) {
                redirectToForwardURL(pwmRequest);
            }
        }

        private static void processCheckResponses(
                final PwmRequest pwmRequest
        )
                throws ChaiUnavailableException, IOException, ServletException, PwmUnrecoverableException
        {
            if (!checkIfUserAuthenticated(pwmRequest)) {
                return;
            }

            if (pwmRequest.getPwmSession().getUserInfoBean().isRequiresResponseConfig()) {
                pwmRequest.sendRedirect(PwmConstants.URL_SERVLET_SETUP_RESPONSES);
            } else {
                redirectToForwardURL(pwmRequest);
            }
        }

        private static void processCheckExpire(
                final PwmRequest pwmRequest
        )
                throws ChaiUnavailableException, IOException, ServletException, PwmUnrecoverableException
        {
            if (!checkIfUserAuthenticated(pwmRequest)) {
                return;
            }

            final PwmSession pwmSession = pwmRequest.getPwmSession();
            if (pwmSession.getUserInfoBean().isRequiresNewPassword() && !pwmSession.getSessionStateBean().isSkippedRequireNewPassword()) {
                pwmRequest.sendRedirect(PwmConstants.URL_SERVLET_CHANGE_PASSWORD);
            } else {
                redirectToForwardURL(pwmRequest);
            }
        }
    }
}

