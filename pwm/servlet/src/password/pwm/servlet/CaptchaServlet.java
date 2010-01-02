/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
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
import password.pwm.Constants;
import password.pwm.Helper;
import password.pwm.PwmSession;
import password.pwm.Validator;
import password.pwm.config.Message;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmException;
import password.pwm.util.PwmLogger;
import password.pwm.util.stats.Statistic;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.params.HttpClientParams;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;


public class CaptchaServlet extends TopServlet {

    private static final PwmLogger LOGGER = PwmLogger.getLogger(CaptchaServlet.class.getName());

    private static final String SKIP_COOKIE_NAME = "pwm-captcha-key";
    private static final String COOKIE_SKIP_INSTANCE_VALUE = "instanceID";

    private static final String RECAPTCHA_VALIDATE_URL = "http://api-verify.recaptcha.net/verify";

    private static final MultiThreadedHttpConnectionManager HTTP_CONNECTION_MANAGER = new MultiThreadedHttpConnectionManager();

    private static final HttpClientParams HTTP_CLIENT_PARAMS = new HttpClientParams();

    static {
        HTTP_CLIENT_PARAMS.setSoTimeout(30 * 1000); // 30 seconds
        HTTP_CLIENT_PARAMS.setConnectionManagerTimeout(30 * 1000); // 30 seconds
    }

    protected void processRequest(final HttpServletRequest req, final HttpServletResponse resp)
            throws ServletException, IOException, ChaiUnavailableException, PwmException
    {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);

        //check intruder detection, if it is tripped, send user to error page
        try {
            pwmSession.getContextManager().getIntruderManager().checkAddress(pwmSession);
        } catch (PwmException e) {
            Helper.forwardToErrorPage(req, resp, req.getSession().getServletContext(), false);
            return;
        }

        if (checkRequestForCaptchaSkipCookie(pwmSession, req)) {
            pwmSession.getSessionStateBean().setPassedCaptcha(true);
            LOGGER.debug(pwmSession, "browser has a valid " + SKIP_COOKIE_NAME + " cookie value of " + figureSkipCookieValue(pwmSession) + ", skipping captcha check");
            forwardToOriginalLocation(req,resp);
            return;
        }
        
        final String processRequestParam = Validator.readStringFromRequest(req, Constants.PARAM_ACTION_REQUEST, 255);

        if (processRequestParam != null) {
            if (processRequestParam.equalsIgnoreCase("doVerify")) {
                handleVerify(req,resp);
            }
        }

        if (!resp.isCommitted()) {
            this.forwardToJSP(req, resp);
        }
    }

    private void handleVerify(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException, ChaiUnavailableException, PwmException
    {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);

        final boolean verified;
        try {
            verified = verifyReCaptcha(req, pwmSession);
        } catch (PwmException e) {
            LOGGER.fatal("error " + e.getCause().getClass().getName() + "during recaptcha api validation: " + e.getMessage());
            pwmSession.getSessionStateBean().setSessionError(e.getError());
            Helper.forwardToErrorPage(req, resp, this.getServletContext());
            return;
        }

        if (verified) { // passed captcha
            pwmSession.getSessionStateBean().setPassedCaptcha(true);
            pwmSession.getContextManager().getStatisticsManager().incrementValue(Statistic.CAPTCHA_SUCCESSES);

            LOGGER.debug(pwmSession, "captcha passcode verified");
            pwmSession.getContextManager().getIntruderManager().addGoodAddressAttempt(pwmSession);
            writeCaptchaSkipCookie(pwmSession, resp);
            forwardToOriginalLocation(req,resp);
        } else { //failed captcha
            pwmSession.getSessionStateBean().setPassedCaptcha(false);
            pwmSession.getSessionStateBean().setSessionError(new ErrorInformation(Message.ERROR_BAD_CAPTCHA_RESPONSE));
            pwmSession.getContextManager().getStatisticsManager().incrementValue(Statistic.CAPTCHA_FAILURES);

            LOGGER.debug(pwmSession, "incorrect captcha passcode");
            pwmSession.getContextManager().getIntruderManager().addBadAddressAttempt(pwmSession);
            forwardToJSP(req,resp);
        }
    }

    /**
     * Verify a reCaptcha request.  The reCaptcha request API is documented at <a href="http://recaptcha.net/apidocs/captcha/">reCaptcha API.
     * @param req httpRequest
     * @param pwmSession users session
     * @return true if correct captcha response
     * @throws password.pwm.error.PwmException if a captcha api error occurs
     */
    private boolean verifyReCaptcha(
            final HttpServletRequest req,
            final PwmSession pwmSession
    ) throws PwmException {
        final HttpClient httpClient = new HttpClient(HTTP_CONNECTION_MANAGER);
        httpClient.setParams(HTTP_CLIENT_PARAMS);

        PostMethod httpPost = null;
        try {
            final URI requestURI = new URI(RECAPTCHA_VALIDATE_URL);
            httpPost = new PostMethod(requestURI.toString());
            httpPost.setParameter("privatekey",pwmSession.getConfig().readSettingAsString(PwmSetting.RECAPTCHA_KEY_PRIVATE));
            httpPost.setParameter("remoteip",PwmSession.getPwmSession(req).getSessionStateBean().getSrcAddress());
            httpPost.setParameter("challenge",Validator.readStringFromRequest(req, "recaptcha_challenge_field", 1024));
            httpPost.setParameter("response",Validator.readStringFromRequest(req, "recaptcha_response_field", 1024));

            LOGGER.debug(pwmSession, "sending reCaptcha verification request: " + httpMethodToDebugString(httpPost));
            final int statusCode = httpClient.executeMethod(httpPost);

            if (statusCode != HttpStatus.SC_OK) {
                throw PwmException.createPwmException(new ErrorInformation(
                        Message.ERROR_CAPTCHA_API_ERROR,
                        "unexpected HTTP status code (" + statusCode + ")"
                ));
            }

            final String responseBody = new String(httpPost.getResponseBody());

            final String[] splitResponse = responseBody.split("\n");
            if (splitResponse.length > 0 && Boolean.parseBoolean(splitResponse[0])) {
                return true;
            }

            if (splitResponse.length > 1) {
                final String errorCode = splitResponse[1];
                LOGGER.debug(pwmSession, "reCaptcha error response: " + errorCode);
            }
        } catch (Exception e) {
            final PwmException pwmE = PwmException.createPwmException(new ErrorInformation(Message.ERROR_CAPTCHA_API_ERROR,e.getMessage()));
            pwmE.initCause(e);
            throw pwmE;
        } finally {
            if (httpPost != null) {
                httpPost.releaseConnection();
            }
        }


        return false;
    }

    private static String httpMethodToDebugString(final PostMethod httpMethod) {
        final StringBuilder sb = new StringBuilder();

        sb.append(httpMethod.getName());
        sb.append(" ");
        sb.append(httpMethod.getPath());

        final NameValuePair[] params = httpMethod.getParameters();
        if (params != null && params.length > 0) {
            sb.append(" {");

            for (final NameValuePair nameValuePair : params) {
                sb.append(nameValuePair.getName()).append("=").append(nameValuePair.getValue());
                sb.append(", ");
            }

            sb.delete(sb.length() - 2, sb.length());
            sb.append("}");
        }

        return sb.toString();
    }

    private void forwardToJSP(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException
    {
        this.getServletContext().getRequestDispatcher('/' + Constants.URL_JSP_CAPTCHA).forward(req, resp);
    }

    private void forwardToOriginalLocation(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException
    {
        Helper.forwardToOriginalRequestURL(req,resp);
    }

    private static void writeCaptchaSkipCookie(final PwmSession pwmSession, final HttpServletResponse resp) {
        final String cookieValue = figureSkipCookieValue(pwmSession);
        if (cookieValue != null) {
            final Cookie skipCookie = new Cookie(SKIP_COOKIE_NAME,cookieValue);
            skipCookie.setMaxAge(60 * 60 * 24 * 365);
            LOGGER.debug(pwmSession, "setting " + SKIP_COOKIE_NAME + " cookie to " + cookieValue);
            resp.addCookie(skipCookie);
        }
    }

    private static String figureSkipCookieValue(final PwmSession pwmSession) {
        String cookieValue = pwmSession.getConfig().readSettingAsString(PwmSetting.CAPTCHA_SKIP_COOKIE);
        if (cookieValue == null || cookieValue.trim().length() < 1) {
            return null;
        }

        if (cookieValue.equals(COOKIE_SKIP_INSTANCE_VALUE)) {
            cookieValue = pwmSession.getContextManager().getInstanceID();

        }

        return cookieValue != null && cookieValue.trim().length() > 0 ? cookieValue : null;
    }

    private static boolean checkRequestForCaptchaSkipCookie(final PwmSession pwmSession, final HttpServletRequest req) {
        final String cookieValue = figureSkipCookieValue(pwmSession);
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