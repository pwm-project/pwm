/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2016 The PWM Project
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
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.servlet.PwmServletDefinition;
import password.pwm.i18n.Message;
import password.pwm.util.Helper;
import password.pwm.util.JsonUtil;
import password.pwm.util.logging.PwmLogger;
import password.pwm.ws.server.RestResultBean;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;

public class PwmResponse extends PwmHttpResponseWrapper {
    private static final PwmLogger LOGGER = PwmLogger.forClass(PwmResponse.class);

    final private PwmRequest pwmRequest;

    public enum Flag {
        AlwaysShowMessage,
        ForceLogout,
    }

    public PwmResponse(
            HttpServletResponse response,
            PwmRequest pwmRequest,
            Configuration configuration
    ) {
        super(pwmRequest.getHttpServletRequest(), response, configuration);
        this.pwmRequest = pwmRequest;
    }

    public void forwardToJsp(
            final PwmConstants.JSP_URL jspURL
    )
            throws ServletException, IOException, PwmUnrecoverableException
    {
        if (!pwmRequest.isFlag(PwmRequestFlag.NO_REQ_COUNTER)) {
            pwmRequest.getPwmSession().getSessionManager().incrementRequestCounterKey();
        }

        preCommitActions();

        final HttpServletRequest httpServletRequest = pwmRequest.getHttpServletRequest();
        final ServletContext servletContext = httpServletRequest.getSession().getServletContext();
        final String url = jspURL.getPath();
        try {
            LOGGER.trace(pwmRequest.getSessionLabel(), "forwarding to " + url);
        } catch (Exception e) {
            /* noop, server may not be up enough to do the log output */
        }
        servletContext.getRequestDispatcher(url).forward(httpServletRequest, this.getHttpServletResponse());
    }

    public void forwardToSuccessPage(Message message, final String... field)
            throws ServletException, PwmUnrecoverableException, IOException

    {
        final String messageStr = Message.getLocalizedMessage(pwmRequest.getLocale(), message, pwmRequest.getConfig(), field);
        forwardToSuccessPage(messageStr);
    }

    public void forwardToSuccessPage(final String message, final Flag... flags)
            throws ServletException, PwmUnrecoverableException, IOException

    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        this.pwmRequest.setAttribute(PwmRequest.Attribute.SuccessMessage, message);

        final boolean showMessage = !pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.DISPLAY_SUCCESS_PAGES)
                && !Arrays.asList(flags).contains(Flag.AlwaysShowMessage);

        if (showMessage) {
            LOGGER.trace(pwmSession, "skipping success page due to configuration setting.");
            final String redirectUrl = pwmRequest.getContextPath()
                    +  PwmServletDefinition.Command.servletUrl()
                    + "?processAction=continue";
            sendRedirect(redirectUrl);
            return;
        }

        try {
            forwardToJsp(PwmConstants.JSP_URL.SUCCESS);
        } catch (PwmUnrecoverableException e) {
            LOGGER.error("unexpected error sending user to success page: " + e.toString());
        }
    }

    public void respondWithError(
            final ErrorInformation errorInformation,
            final Flag... flags
    )
            throws IOException, ServletException
    {
        LOGGER.error(pwmRequest.getSessionLabel(), errorInformation);

        pwmRequest.setResponseError(errorInformation);

        if (Helper.enumArrayContainsValue(flags, Flag.ForceLogout)) {
            LOGGER.debug(pwmRequest, "forcing logout due to error " + errorInformation.toDebugStr());
            pwmRequest.getPwmSession().unauthenticateUser(pwmRequest);
        }

        if (pwmRequest.isJsonRequest()) {
            outputJsonResult(RestResultBean.fromError(errorInformation, pwmRequest));
        } else if (pwmRequest.isHtmlRequest()) {
            try {
                forwardToJsp(PwmConstants.JSP_URL.ERROR);
            } catch (PwmUnrecoverableException e) {
                LOGGER.error("unexpected error sending user to error page: " + e.toString());
            }
        } else {
            boolean showDetail = Helper.determineIfDetailErrorMsgShown(pwmRequest.getPwmApplication());
            final String errorStatusText = showDetail
                    ? errorInformation.toDebugStr()
                    : errorInformation.toUserStr(pwmRequest.getPwmSession(),pwmRequest.getPwmApplication());
            getHttpServletResponse().sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, errorStatusText);
        }
    }


    public void outputJsonResult(
            final RestResultBean restResultBean
    )
            throws IOException {
        preCommitActions();
        final HttpServletResponse resp = this.getHttpServletResponse();
        final String outputString = restResultBean.toJson();
        resp.setContentType(PwmConstants.ContentTypeValue.json.getHeaderValue());
        resp.getWriter().print(outputString);
        resp.getWriter().close();
    }


    public void writeEncryptedCookie(final String cookieName, final Serializable cookieValue, final CookiePath path)
            throws PwmUnrecoverableException
    {
        writeEncryptedCookie(cookieName, cookieValue, -1, path);
    }

    public void writeEncryptedCookie(final String cookieName, final Serializable cookieValue, final int seconds, final CookiePath path)
            throws PwmUnrecoverableException
    {
        final String jsonValue = JsonUtil.serialize(cookieValue);
        final String encryptedValue = pwmRequest.getPwmApplication().getSecureService().encryptToString(jsonValue);
        writeCookie(cookieName, encryptedValue, seconds, path, PwmHttpResponseWrapper.Flag.BypassSanitation);
    }

    public void markAsDownload(final PwmConstants.ContentTypeValue contentType, final String filename) {
        this.setHeader(PwmConstants.HttpHeader.ContentDisposition,"attachment; fileName=" + filename);
        this.setContentType(contentType);
    }

    public void sendRedirect(final String url)
            throws IOException
    {
        preCommitActions();
        super.sendRedirect(url);
    }

    private void preCommitActions() {
        if (pwmRequest.getPwmResponse().isCommitted()) {
            return;
        }

        pwmRequest.getPwmApplication().getSessionStateService().saveLoginSessionState(pwmRequest);
        pwmRequest.getPwmApplication().getSessionStateService().saveSessionBeans(pwmRequest);
    }
}
