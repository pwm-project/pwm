/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2014 The PWM Project
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
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.SessionStateBean;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmSession;
import password.pwm.util.Helper;
import password.pwm.util.PasswordData;
import password.pwm.util.ServletHelper;
import password.pwm.util.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.stats.Statistic;

import javax.servlet.ServletException;
import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;


public class CaptchaServlet extends PwmServlet {

    private static final PwmLogger LOGGER = PwmLogger.getLogger(CaptchaServlet.class.getName());

    private static final String SKIP_COOKIE_NAME = "captcha-key";
    private static final String COOKIE_SKIP_INSTANCE_VALUE = "INSTANCEID";

    private static final String RECAPTCHA_VALIDATE_URL = PwmConstants.RECAPTCHA_VALIDATE_URL;

    public enum CaptchaAction implements PwmServlet.ProcessAction {
        doVerify,
        ;

        public Collection<PwmServlet.HttpMethod> permittedMethods()
        {
            return Collections.singletonList(PwmServlet.HttpMethod.POST);
        }
    }

    protected CaptchaAction readProcessAction(final PwmRequest request)
            throws PwmUnrecoverableException
    {
        try {
            return CaptchaAction.valueOf(request.readParameterAsString(PwmConstants.PARAM_ACTION_REQUEST));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    protected void processAction(final PwmRequest pwmRequest)
            throws ServletException, IOException, ChaiUnavailableException, PwmUnrecoverableException
    {
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();

        if (checkRequestForCaptchaSkipCookie(pwmRequest)) {
            pwmSession.getSessionStateBean().setPassedCaptcha(true);
            LOGGER.debug(pwmSession, "browser has a valid " + SKIP_COOKIE_NAME + " cookie value of " + figureSkipCookieValue(pwmApplication) + ", skipping captcha check");
            forwardToOriginalLocation(pwmRequest);
            return;
        }

        final CaptchaAction action = readProcessAction(pwmRequest);

        if (action != null) {
            pwmRequest.validatePwmFormID();
            switch (action) {
                case doVerify:
                    handleVerify(pwmRequest);
                    break;

            }
        }

        if (!pwmRequest.getHttpServletResponse().isCommitted()) {
            pwmRequest.forwardToJsp(PwmConstants.JSP_URL.CAPTCHA);
        }
    }

    private void handleVerify(final PwmRequest pwmRequest)
            throws ServletException, IOException, ChaiUnavailableException, PwmUnrecoverableException
    {
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();

        final long startTime = System.currentTimeMillis();
        final boolean verified;
        try {
            verified = verifyReCaptcha(pwmRequest);
        } catch (PwmUnrecoverableException e) {
            LOGGER.fatal(
                    "error " + e.getCause().getClass().getName() + " during recaptcha api validation: " + e.getMessage());
            pwmRequest.respondWithError(e.getErrorInformation());
            return;
        }

        if (verified) { // passed captcha
            pwmSession.getSessionStateBean().setPassedCaptcha(true);
            pwmApplication.getStatisticsManager().incrementValue(Statistic.CAPTCHA_SUCCESSES);

            LOGGER.debug(pwmSession, "captcha passcode verified (" + TimeDuration.fromCurrent(startTime).asCompactString() + ")");
            pwmApplication.getIntruderManager().convenience().clearAddressAndSession(pwmSession);
            writeCaptchaSkipCookie(pwmRequest);
            forwardToOriginalLocation(pwmRequest);
        } else { //failed captcha
            pwmSession.getSessionStateBean().setPassedCaptcha(false);
            pwmApplication.getStatisticsManager().incrementValue(Statistic.CAPTCHA_FAILURES);
            LOGGER.debug(pwmSession, "incorrect captcha passcode");
            pwmApplication.getIntruderManager().convenience().markAddressAndSession(pwmSession);
            pwmRequest.setResponseError(PwmError.ERROR_BAD_CAPTCHA_RESPONSE.toInfo());
            pwmRequest.forwardToJsp(PwmConstants.JSP_URL.CAPTCHA);
        }
    }

    /**
     * Verify a reCaptcha request.  The reCaptcha request API is documented at <a href="http://recaptcha.net/apidocs/captcha/">reCaptcha API.
     */
    private boolean verifyReCaptcha(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PasswordData privateKey = pwmApplication.getConfig().readSettingAsPassword(PwmSetting.RECAPTCHA_KEY_PRIVATE);

        final StringBuilder bodyText = new StringBuilder();
        bodyText.append("privatekey=").append(privateKey.getStringValue());
        bodyText.append("&");
        bodyText.append("remoteip=").append(pwmRequest.getSessionLabel().getSrcAddress());
        bodyText.append("&");
        bodyText.append("challenge=").append(pwmRequest.readParameterAsString("recaptcha_challenge_field"));
        bodyText.append("&");
        bodyText.append("response=").append(pwmRequest.readParameterAsString("recaptcha_response_field"));

        try {
            final URI requestURI = new URI(RECAPTCHA_VALIDATE_URL);
            final HttpPost httpPost = new HttpPost(requestURI.toString());
            httpPost.setHeader("Content-Type", PwmConstants.ContentTypeValue.form.getHeaderValue());
            httpPost.setEntity(new StringEntity(bodyText.toString(),PwmConstants.DEFAULT_CHARSET));
            LOGGER.debug(pwmRequest, "sending reCaptcha verification request: " + httpRequestToDebugString(httpPost));

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
                LOGGER.debug(pwmRequest, "reCaptcha error response: " + errorCode);
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
        return httpRequest.getRequestLine().toString();
    }


    private void forwardToOriginalLocation(
            final PwmRequest pwmRequest
    )
            throws IOException, ServletException {
        try {
            final PwmSession pwmSession = pwmRequest.getPwmSession();
            final SessionStateBean ssBean = pwmSession.getSessionStateBean();

            String destURL = ssBean.getPreCaptchaRequestURL();
            ssBean.setPreCaptchaRequestURL(null);

            if (destURL == null || destURL.contains(PwmConstants.URL_SERVLET_LOGIN)) { // fallback, shouldnt need to be used.
                destURL = pwmRequest.getHttpServletRequest().getContextPath();
            }

            pwmRequest.sendRedirect(destURL);
        } catch (PwmUnrecoverableException e) {
            LOGGER.error("unexpected error forwarding user to original request url: " + e.toString());
        }
    }

    private static void writeCaptchaSkipCookie(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException
    {
        final String cookieValue = figureSkipCookieValue(pwmRequest.getPwmApplication());
        if (cookieValue != null) {
            ServletHelper.writeCookie(
                    pwmRequest.getHttpServletResponse(),
                    SKIP_COOKIE_NAME,
                    cookieValue,
                    60 * 60 * 24 * 365
            );
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

    private static boolean checkRequestForCaptchaSkipCookie(final PwmRequest pwmRequest)
            throws PwmUnrecoverableException
    {
        final String allowedSkipValue = figureSkipCookieValue(pwmRequest.getPwmApplication());
        if (allowedSkipValue != null) {
            final String cookieValue = ServletHelper.readCookie(pwmRequest.getHttpServletRequest(), SKIP_COOKIE_NAME);
            if (allowedSkipValue.equals(cookieValue)) {
                return true;
            }
        }
        return false;
    }
}