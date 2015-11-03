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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.SessionStateBean;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpMethod;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmSession;
import password.pwm.http.ServletHelper;
import password.pwm.http.client.PwmHttpClient;
import password.pwm.http.client.PwmHttpClientRequest;
import password.pwm.http.client.PwmHttpClientResponse;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsManager;
import password.pwm.util.JsonUtil;
import password.pwm.util.PasswordData;
import password.pwm.util.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;


@WebServlet(
        name="CaptchaServlet",
        urlPatterns = {
                PwmConstants.URL_PREFIX_PUBLIC + "/captcha",
                PwmConstants.URL_PREFIX_PUBLIC + "/Captcha"
        }
)
public class CaptchaServlet extends AbstractPwmServlet {

    private static final PwmLogger LOGGER = PwmLogger.getLogger(CaptchaServlet.class.getName());

    private static final String COOKIE_SKIP_INSTANCE_VALUE = "INSTANCEID";

    public enum CaptchaAction implements AbstractPwmServlet.ProcessAction {
        doVerify,
        ;

        public Collection<HttpMethod> permittedMethods()
        {
            return Collections.singletonList(HttpMethod.POST);
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

        if (checkRequestForCaptchaSkipCookie(pwmRequest)) {
            pwmSession.getSessionStateBean().setPassedCaptcha(true);
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

        if (!pwmRequest.getPwmResponse().isCommitted()) {
            forwardToCaptchaPage(pwmRequest);
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
            forwardToCaptchaPage(pwmRequest);
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
        bodyText.append("secret=").append(privateKey.getStringValue());
        bodyText.append("&");
        bodyText.append("remoteip=").append(pwmRequest.getSessionLabel().getSrcAddress());
        bodyText.append("&");
        bodyText.append("response=").append(pwmRequest.readParameterAsString("g-recaptcha-response"));

        try {
            final PwmHttpClientRequest clientRequest = new PwmHttpClientRequest(
                    HttpMethod.POST,
                    pwmApplication.getConfig().readAppProperty(AppProperty.RECAPTCHA_VALIDATE_URL),
                    bodyText.toString(),
                    Collections.singletonMap("Content-Type",PwmConstants.ContentTypeValue.form.getHeaderValue())
            );
            LOGGER.debug(pwmRequest, "sending reCaptcha verification request" );
            final PwmHttpClient client = new PwmHttpClient(pwmRequest.getPwmApplication(), pwmRequest.getSessionLabel());
            final PwmHttpClientResponse clientResponse = client.makeRequest(clientRequest);

            if (clientResponse.getStatusCode() != HttpServletResponse.SC_OK) {
                throw new PwmUnrecoverableException(new ErrorInformation(
                        PwmError.ERROR_CAPTCHA_API_ERROR,
                        "unexpected HTTP status code (" + clientResponse.getStatusCode() + ")"
                ));
            }

            final JsonElement responseJson = new JsonParser().parse(clientResponse.getBody());
            final JsonObject topObject = responseJson.getAsJsonObject();
            if (topObject != null && topObject.has("success")) {
                boolean success = topObject.get("success").getAsBoolean();
                if (success) {
                    return true;
                }

                if (topObject.has("error-codes")) {
                    final List<String> errorCodes = new ArrayList<>();
                    for (final JsonElement element : topObject.get("error-codes").getAsJsonArray()) {
                        final String errorCode = element.getAsString();
                        errorCodes.add(errorCode);
                    }
                    LOGGER.debug(pwmRequest, "recaptcha error codes: " + JsonUtil.serializeCollection(errorCodes));
                }
            }

        } catch (Exception e) {
            final String errorMsg = "unexpected error during reCaptcha API execution: " + e.getMessage();
            LOGGER.error(errorMsg,e);
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_CAPTCHA_API_ERROR, errorMsg);
            final PwmUnrecoverableException pwmE = new PwmUnrecoverableException(errorInfo);
            pwmE.initCause(e);
            throw pwmE;
        }

        return false;
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

            if (pwmRequest.getURL().isLoginServlet()) { // fallback, shouldn't need to be used.
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
        final String cookieValue = figureSkipCookieValue(pwmRequest);
        final int captchaSkipCookieLifetimeSeconds = Integer.parseInt(pwmRequest.getConfig().readAppProperty(AppProperty.HTTP_COOKIE_CAPTCHA_SKIP_AGE));
        final String captchaSkipCookieName = pwmRequest.getConfig().readAppProperty(AppProperty.HTTP_COOKIE_CAPTCHA_SKIP_NAME);
        if (cookieValue != null) {
            pwmRequest.getPwmResponse().writeCookie(
                    captchaSkipCookieName,
                    cookieValue,
                    captchaSkipCookieLifetimeSeconds
            );
        }
    }

    private static String figureSkipCookieValue(final PwmRequest pwmRequest)
            throws PwmUnrecoverableException
    {
        String cookieValue = pwmRequest.getConfig().readSettingAsString(PwmSetting.CAPTCHA_SKIP_COOKIE);
        if (cookieValue == null || cookieValue.trim().length() < 1) {
            return null;
        }

        if (cookieValue.equals(COOKIE_SKIP_INSTANCE_VALUE)) {
            cookieValue = pwmRequest.getPwmApplication().getInstanceID();
        }

        return cookieValue != null && cookieValue.trim().length() > 0 ? cookieValue : null;
    }

    private static boolean checkRequestForCaptchaSkipCookie(final PwmRequest pwmRequest)
            throws PwmUnrecoverableException
    {
        final String allowedSkipValue = figureSkipCookieValue(pwmRequest);
        final String captchaSkipCookieName = pwmRequest.getConfig().readAppProperty(AppProperty.HTTP_COOKIE_CAPTCHA_SKIP_NAME);
        if (allowedSkipValue != null) {
            final String cookieValue = ServletHelper.readCookie(pwmRequest.getHttpServletRequest(), captchaSkipCookieName);
            if (allowedSkipValue.equals(cookieValue)) {
                LOGGER.debug(pwmRequest, "browser has a valid " + captchaSkipCookieName+ " cookie value of " + figureSkipCookieValue(pwmRequest) + ", skipping captcha check");
                return true;
            }
        }
        return false;
    }

    private void forwardToCaptchaPage(final PwmRequest pwmRequest) throws ServletException, PwmUnrecoverableException, IOException {
        StatisticsManager.incrementStat(pwmRequest, Statistic.CAPTCHA_PRESENTATIONS);

        final String reCaptchaPublicKey = pwmRequest.getConfig().readSettingAsString(PwmSetting.RECAPTCHA_KEY_PUBLIC);
        pwmRequest.setAttribute(PwmConstants.REQUEST_ATTR.CaptchaPublicKey, reCaptchaPublicKey);
        {
            final String urlValue = pwmRequest.getConfig().readAppProperty(AppProperty.RECAPTCHA_CLIENT_JS_URL);
            pwmRequest.setAttribute(PwmConstants.REQUEST_ATTR.CaptchaClientUrl, urlValue);
        }
        {
            final String configuredUrl =pwmRequest.getConfig().readAppProperty(AppProperty.RECAPTCHA_CLIENT_IFRAME_URL);
            final String url = configuredUrl + "?k=" + reCaptchaPublicKey + "&hl=" + pwmRequest.getLocale().toString();
            pwmRequest.setAttribute(PwmConstants.REQUEST_ATTR.CaptchaIframeUrl,url);
        }
        pwmRequest.forwardToJsp(PwmConstants.JSP_URL.CAPTCHA);
    }
}