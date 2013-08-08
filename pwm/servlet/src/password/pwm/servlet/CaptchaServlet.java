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

package password.pwm.servlet;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import password.pwm.*;
import password.pwm.bean.SessionStateBean;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.Helper;
import password.pwm.util.PwmLogger;
import password.pwm.util.ServletHelper;
import password.pwm.util.stats.Statistic;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;


public class CaptchaServlet extends TopServlet {

    private static final PwmLogger LOGGER = PwmLogger.getLogger(CaptchaServlet.class.getName());

    private static final String SKIP_COOKIE_NAME = "captcha-key";
    private static final String COOKIE_SKIP_INSTANCE_VALUE = "INSTANCEID";

    private static final String RECAPTCHA_VALIDATE_URL = PwmConstants.RECAPTCHA_VALIDATE_URL;


    protected void processRequest(final HttpServletRequest req, final HttpServletResponse resp)
            throws ServletException, IOException, ChaiUnavailableException, PwmUnrecoverableException {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);


        //check intruder detection, if it is tripped, send user to error page
        try {
            pwmApplication.getIntruderManager().check(null,null,pwmSession);
        } catch (PwmUnrecoverableException e) {
            ServletHelper.forwardToErrorPage(req, resp, false);
            return;
        }

        if (checkRequestForCaptchaSkipCookie(pwmApplication, req)) {
            pwmSession.getSessionStateBean().setPassedCaptcha(true);
            LOGGER.debug(pwmSession, "browser has a valid " + SKIP_COOKIE_NAME + " cookie value of " + figureSkipCookieValue(pwmApplication) + ", skipping captcha check");
            forwardToOriginalLocation(req, resp);
            return;
        }

        final String processRequestParam = Validator.readStringFromRequest(req, PwmConstants.PARAM_ACTION_REQUEST);

        if (processRequestParam != null) {
            if (processRequestParam.equalsIgnoreCase("doVerify")) {
                handleVerify(req, resp);
            }
        }

        if (!resp.isCommitted()) {
            this.forwardToJSP(req, resp);
        }
    }

    private void handleVerify(final HttpServletRequest req, final HttpServletResponse resp)
            throws ServletException, IOException, ChaiUnavailableException, PwmUnrecoverableException
    {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);

        Validator.validatePwmFormID(req);

        final boolean verified;
        try {
            verified = verifyReCaptcha(req, pwmSession);
        } catch (PwmUnrecoverableException e) {
            LOGGER.fatal("error " + e.getCause().getClass().getName() + " during recaptcha api validation: " + e.getMessage());
            pwmSession.getSessionStateBean().setSessionError(e.getErrorInformation());
            ServletHelper.forwardToErrorPage(req, resp, this.getServletContext());
            return;
        }

        if (verified) { // passed captcha
            pwmSession.getSessionStateBean().setPassedCaptcha(true);
            pwmApplication.getStatisticsManager().incrementValue(Statistic.CAPTCHA_SUCCESSES);

            LOGGER.debug(pwmSession, "captcha passcode verified");
            pwmApplication.getIntruderManager().clear(null,null,pwmSession);
            writeCaptchaSkipCookie(pwmSession, pwmApplication, resp);
            forwardToOriginalLocation(req, resp);
        } else { //failed captcha
            pwmSession.getSessionStateBean().setPassedCaptcha(false);
            pwmSession.getSessionStateBean().setSessionError(new ErrorInformation(PwmError.ERROR_BAD_CAPTCHA_RESPONSE));
            pwmApplication.getStatisticsManager().incrementValue(Statistic.CAPTCHA_FAILURES);

            LOGGER.debug(pwmSession, "incorrect captcha passcode");
            pwmApplication.getIntruderManager().mark(null,null,pwmSession);
            forwardToJSP(req, resp);
        }
    }

    /**
     * Verify a reCaptcha request.  The reCaptcha request API is documented at <a href="http://recaptcha.net/apidocs/captcha/">reCaptcha API.
     *
     * @param req        httpRequest
     * @param pwmSession users session
     * @return true if correct captcha response
     * @throws password.pwm.error.PwmUnrecoverableException
     *          if a captcha api error occurs
     */
    private boolean verifyReCaptcha(
            final HttpServletRequest req,
            final PwmSession pwmSession
    )
            throws PwmUnrecoverableException
    {
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);

        final StringBuilder bodyText = new StringBuilder();
        bodyText.append("privatekey=").append(pwmApplication.getConfig().readSettingAsString(PwmSetting.RECAPTCHA_KEY_PRIVATE));
        bodyText.append("&");
        bodyText.append("remoteip=").append(PwmSession.getPwmSession(req).getSessionStateBean().getSrcAddress());
        bodyText.append("&");
        bodyText.append("challenge=").append(Validator.readStringFromRequest(req, "recaptcha_challenge_field"));
        bodyText.append("&");
        bodyText.append("response=").append(Validator.readStringFromRequest(req, "recaptcha_response_field"));

        try {
            final URI requestURI = new URI(RECAPTCHA_VALIDATE_URL);
            final HttpPost httpPost = new HttpPost(requestURI.toString());
            httpPost.setHeader("Content-Type", "application/x-www-form-urlencoded");
            httpPost.setEntity(new StringEntity(bodyText.toString()));
            LOGGER.debug(pwmSession, "sending reCaptcha verification request: " + httpRequestToDebugString(httpPost));

            final HttpResponse httpResponse = Helper.getHttpClient(pwmApplication.getConfig()).execute(httpPost);
            if (httpResponse.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                throw new PwmUnrecoverableException(new ErrorInformation(
                        PwmError.ERROR_CAPTCHA_API_ERROR,
                        "unexpected HTTP status code (" + httpResponse.getStatusLine().getStatusCode() + ")"
                ));
            }

            final String responseBody = EntityUtils.toString(httpResponse.getEntity());

            final String[] splitResponse = responseBody.split("\n");
            if (splitResponse.length > 0 && Boolean.parseBoolean(splitResponse[0])) {
                return true;
            }

            if (splitResponse.length > 1) {
                final String errorCode = splitResponse[1];
                LOGGER.debug(pwmSession, "reCaptcha error response: " + errorCode);
            }
        } catch (Exception e) {
            final String errorMsg = "unexpected error during recpatcha API execution: " + e.getMessage();
            LOGGER.error(errorMsg,e);
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_CAPTCHA_API_ERROR, errorMsg);
            final PwmUnrecoverableException pwmE = new PwmUnrecoverableException(errorInfo);
            pwmE.initCause(e);
            throw pwmE;
        }

        return false;
    }

    private static String httpRequestToDebugString(final HttpRequest httpRequest) {
        final StringBuilder sb = new StringBuilder();

        sb.append(httpRequest.getRequestLine());

        return sb.toString();
    }

    private void forwardToJSP(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException {
        this.getServletContext().getRequestDispatcher('/' + PwmConstants.URL_JSP_CAPTCHA).forward(req, resp);
    }

    private void forwardToOriginalLocation(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException {
        try {
            final PwmSession pwmSession = PwmSession.getPwmSession(req);
            final SessionStateBean ssBean = pwmSession.getSessionStateBean();

            String destURL = ssBean.getPreCaptchaRequestURL();
            ssBean.setPreCaptchaRequestURL(null);

            if (destURL == null || destURL.indexOf(PwmConstants.URL_SERVLET_LOGIN) != -1) { // fallback, shouldnt need to be used.
                destURL = req.getContextPath();
            }

            resp.sendRedirect(SessionFilter.rewriteRedirectURL(destURL, req, resp));
        } catch (PwmUnrecoverableException e) {
            LOGGER.error("unexpected error forwarding user to original request url: " + e.toString());
        }
    }

    private static void writeCaptchaSkipCookie(final PwmSession pwmSession, final PwmApplication pwmApplication, final HttpServletResponse resp)
            throws PwmUnrecoverableException
    {
        final String cookieValue = figureSkipCookieValue(pwmApplication);
        if (cookieValue != null) {
            final Cookie skipCookie = new Cookie(SKIP_COOKIE_NAME, cookieValue);
            skipCookie.setMaxAge(60 * 60 * 24 * 365);
            LOGGER.debug(pwmSession, "setting " + SKIP_COOKIE_NAME + " cookie to " + cookieValue);
            resp.addCookie(skipCookie);
        }
    }

    private static String figureSkipCookieValue(final PwmApplication pwmApplication)
            throws PwmUnrecoverableException
    {
        String cookieValue = pwmApplication.getConfig().readSettingAsString(PwmSetting.CAPTCHA_SKIP_COOKIE);
        if (cookieValue == null || cookieValue.trim().length() < 1) {
            return null;
        }

        if (cookieValue.equals(COOKIE_SKIP_INSTANCE_VALUE)) {
            cookieValue = pwmApplication.getInstanceID();

        }

        return cookieValue != null && cookieValue.trim().length() > 0 ? cookieValue : null;
    }

    private static boolean checkRequestForCaptchaSkipCookie(final PwmApplication pwmApplication, final HttpServletRequest req) throws PwmUnrecoverableException {
        final String cookieValue = figureSkipCookieValue(pwmApplication);
        if (cookieValue != null) {
            final Cookie[] cookies = req.getCookies();
            if (cookies != null) {
                for (final Cookie cookie : cookies) {
                    if (SKIP_COOKIE_NAME.equals(cookie.getName())) {
                        if (cookieValue.equals(cookie.getValue())) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}