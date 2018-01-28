/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.commons.lang3.exception.ExceptionUtils;
import password.pwm.PwmApplication;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.servlet.PwmServletDefinition;
import password.pwm.i18n.Message;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.logging.PwmLogger;
import password.pwm.ws.server.RestResultBean;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class PwmResponse extends PwmHttpResponseWrapper {
    private static final PwmLogger LOGGER = PwmLogger.forClass(PwmResponse.class);

    private final PwmRequest pwmRequest;

    public enum Flag {
        AlwaysShowMessage,
        ForceLogout,
    }

    public enum RedirectType {
        Permanent_301(HttpServletResponse.SC_MOVED_PERMANENTLY),
        Found_302(HttpServletResponse.SC_FOUND),
        Other_303(303),

        ;

        private final int code;

        RedirectType(final int code) {
            this.code = code;
        }

        public int getCode() {
            return code;
        }
    }

    public PwmResponse(
            final HttpServletResponse response,
            final PwmRequest pwmRequest,
            final Configuration configuration
    ) {
        super(pwmRequest.getHttpServletRequest(), response, configuration);
        this.pwmRequest = pwmRequest;
    }

    @SuppressFBWarnings("DE_MIGHT_IGNORE") // its okay to disappear the exception during logging
    public void forwardToJsp(
            final JspUrl jspURL
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

    public void forwardToSuccessPage(final Message message, final String... field)
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
        this.pwmRequest.setAttribute(PwmRequestAttribute.SuccessMessage, message);

        final boolean showMessage = !pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.DISPLAY_SUCCESS_PAGES)
                && !Arrays.asList(flags).contains(Flag.AlwaysShowMessage);

        if (showMessage) {
            LOGGER.trace(pwmSession, "skipping success page due to configuration setting.");
            final String redirectUrl = pwmRequest.getContextPath()
                    +  PwmServletDefinition.PublicCommand.servletUrl()
                    + "?processAction=next";
            sendRedirect(redirectUrl);
            return;
        }

        try {
            forwardToJsp(JspUrl.SUCCESS);
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

        pwmRequest.setAttribute(PwmRequestAttribute.PwmErrorInfo, errorInformation);

        if (JavaHelper.enumArrayContainsValue(flags, Flag.ForceLogout)) {
            LOGGER.debug(pwmRequest, "forcing logout due to error " + errorInformation.toDebugStr());
            pwmRequest.getPwmSession().unauthenticateUser(pwmRequest);
        }

        if (getResponseFlags().contains(PwmResponseFlag.ERROR_RESPONSE_SENT)) {
            LOGGER.debug(pwmRequest, "response error has been previously set, disregarding new error: " + errorInformation.toDebugStr());
            return;
        }

        if (isCommitted()) {
            final String msg = "cannot respond with error '" + errorInformation.toDebugStr() + "', response is already committed";
            LOGGER.warn(pwmRequest.getSessionLabel(), ExceptionUtils.getStackTrace(new Throwable(msg)));
            return;
        }

        if (pwmRequest.isJsonRequest()) {
            outputJsonResult(RestResultBean.fromError(errorInformation, pwmRequest));
        } else if (pwmRequest.isHtmlRequest()) {
            try {
                forwardToJsp(JspUrl.ERROR);
            } catch (PwmUnrecoverableException e) {
                LOGGER.error("unexpected error sending user to error page: " + e.toString());
            }
        } else {
            final boolean showDetail = pwmRequest.getPwmApplication().determineIfDetailErrorMsgShown();
            final String errorStatusText = showDetail
                    ? errorInformation.toDebugStr()
                    : errorInformation.toUserStr(pwmRequest.getPwmSession(),pwmRequest.getPwmApplication());
            getHttpServletResponse().sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, errorStatusText);
        }

        setResponseFlag(PwmResponseFlag.ERROR_RESPONSE_SENT);
    }


    public void outputJsonResult(
            final RestResultBean restResultBean
    )
            throws IOException {
        preCommitActions();
        final HttpServletResponse resp = this.getHttpServletResponse();
        final String outputString = restResultBean.toJson();
        resp.setContentType(HttpContentType.json.getHeaderValue());
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

    public void markAsDownload(final HttpContentType contentType, final String filename) {
        this.setHeader(HttpHeader.ContentDisposition,"attachment; fileName=" + filename);
        this.setContentType(contentType);
    }

    public void sendRedirect(final String url)
            throws IOException
    {
        sendRedirect(url, RedirectType.Found_302);
    }

    public void sendRedirect(final String url, final RedirectType redirectType)
            throws IOException
    {
        preCommitActions();

        final HttpServletResponse resp = pwmRequest.getPwmResponse().getHttpServletResponse();
        resp.setStatus(redirectType.getCode()); // http "other" redirect
        resp.setHeader(HttpHeader.Location.getHttpName(), url);
        LOGGER.trace(pwmRequest, "sending " + redirectType.getCode() + " redirect to " + url);
    }

    private void preCommitActions() {
        if (pwmRequest.getPwmResponse().isCommitted()) {
            return;
        }

        pwmRequest.getPwmApplication().getSessionStateService().saveLoginSessionState(pwmRequest);
        pwmRequest.getPwmApplication().getSessionStateService().saveSessionBeans(pwmRequest);
    }

    private final Set<PwmResponseFlag> pwmResponseFlags = new HashSet<>();

    private Collection<PwmResponseFlag> getResponseFlags() {
        return Collections.unmodifiableSet(pwmResponseFlags);
    }

    private void setResponseFlag(final PwmResponseFlag flag) {
        pwmResponseFlags.add(flag);
    }
}
