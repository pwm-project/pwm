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

package password.pwm.http.filter;

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmApplicationMode;
import password.pwm.PwmConstants;
import password.pwm.bean.LocalSessionStateBean;
import password.pwm.bean.LoginInfoBean;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.SessionVerificationMode;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.*;
import password.pwm.svc.stats.Statistic;
import password.pwm.util.Helper;
import password.pwm.util.StringUtil;
import password.pwm.util.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.*;

/**
 * This session filter (invoked by the container through the web.xml descriptor) wraps all calls to the
 * servlets in the container.
 * <p/>
 * It is responsible for managing some aspects of the user session and also for enforcing security
 * functionality such as intruder lockout.
 *
 * @author Jason D. Rivard
 */
public class SessionFilter extends AbstractPwmFilter {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.forClass(SessionFilter.class);

    @Override
    boolean isInterested(PwmApplicationMode mode, PwmURL pwmURL) {
        return true;
    }

    public void processFilter(
            final PwmApplicationMode mode,
            final PwmRequest pwmRequest,
            final PwmFilterChain chain
    )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        final PwmURL pwmURL = pwmRequest.getURL();
        if (!pwmURL.isWebServiceURL() && !pwmURL.isResourceURL()) {
            if (handleStandardRequestOperations(pwmRequest) == ProcessStatus.Halt) {
                return;
            }
        }

        try {
            chain.doFilter();
        } catch (IOException e) {
            LOGGER.trace(pwmRequest.getPwmSession(), "IO exception during servlet processing: " + e.getMessage());
            throw new ServletException(e);
        } catch (Throwable e) {
            if (e instanceof ServletException
                    && e.getCause() != null
                    && e.getCause() instanceof NoClassDefFoundError
                    && e.getCause().getMessage() != null
                    && e.getCause().getMessage().contains("JaxbAnnotationIntrospector")
                    ) {
                LOGGER.debug("ignoring JaxbAnnotationIntrospector NoClassDefFoundError: " + e.getMessage()); // this is a jersey 1.18 bug that occurs once per execution
            } else {
                LOGGER.warn(pwmRequest.getPwmSession(), "unhandled exception " + e.getMessage(), e);
            }

            throw new ServletException(e);
        }
    }

    private ProcessStatus handleStandardRequestOperations(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException, IOException, ServletException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final Configuration config = pwmRequest.getConfig();

        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final LocalSessionStateBean ssBean = pwmSession.getSessionStateBean();
        final PwmResponse resp = pwmRequest.getPwmResponse();


        // debug the http session headers
        if (!pwmSession.getSessionStateBean().isDebugInitialized()) {
            LOGGER.trace(pwmSession, pwmRequest.debugHttpHeaders());
            pwmSession.getSessionStateBean().setDebugInitialized(true);
        }

        // output request information to debug log
        pwmRequest.debugHttpRequestToLog();

        try {
            pwmApplication.getSessionStateService().readLoginSessionState(pwmRequest);
        } catch (PwmUnrecoverableException e) {
            LOGGER.warn(pwmRequest, "error while reading login session state: " + e.getMessage());
        }

        // mark last url
        if (!new PwmURL(pwmRequest.getHttpServletRequest()).isCommandServletURL()) {
            ssBean.setLastRequestURL(pwmRequest.getHttpServletRequest().getRequestURI());
        }

        // mark last request time.
        ssBean.setSessionLastAccessedTime(new Date());


        // check the page leave notice
        if (checkPageLeaveNotice(pwmSession, config)) {
            LOGGER.warn("invalidating session due to dirty page leave time greater then configured timeout");
            pwmRequest.invalidateSession();
            resp.sendRedirect(pwmRequest.getHttpServletRequest().getRequestURI());
            return ProcessStatus.Halt;
        }

        //override session locale due to parameter
        handleLocaleParam(pwmRequest);

        //set the session's theme
        handleThemeParam(pwmRequest);

        //check the sso override flag
        handleSsoOverrideParam(pwmRequest);

        // make sure connection is secure.
        if (config.readSettingAsBoolean(PwmSetting.REQUIRE_HTTPS) && !pwmRequest.getHttpServletRequest().isSecure()) {
            pwmRequest.respondWithError(PwmError.ERROR_SECURE_REQUEST_REQUIRED.toInfo());
            return ProcessStatus.Halt;
        }

        //check for session verification failure
        if (!ssBean.isSessionVerified()) {
            // ignore resource requests
            final SessionVerificationMode mode = config.readSettingAsEnum(PwmSetting.ENABLE_SESSION_VERIFICATION,
                    SessionVerificationMode.class);
            if (mode == SessionVerificationMode.OFF) {
                ssBean.setSessionVerified(true);
            } else {
                if (verifySession(pwmRequest, mode) == ProcessStatus.Halt) {
                    return ProcessStatus.Halt;
                }
            }
        }
        {
            final String forwardURLParamName = config.readAppProperty(AppProperty.HTTP_PARAM_NAME_FORWARD_URL);
            final String forwardURL = pwmRequest.readParameterAsString(forwardURLParamName);
            if (forwardURL != null && forwardURL.length() > 0) {
                try {
                    Helper.checkUrlAgainstWhitelist(pwmApplication, pwmRequest.getSessionLabel(), forwardURL);
                } catch (PwmOperationalException e) {
                    LOGGER.error(pwmRequest, e.getErrorInformation());
                    pwmRequest.respondWithError(e.getErrorInformation());
                    return ProcessStatus.Halt;
                }
                ssBean.setForwardURL(forwardURL);
                LOGGER.debug(pwmRequest, "forwardURL parameter detected in request, setting session forward url to " + forwardURL);
            }
        }

        {
            final String logoutURLParamName = config.readAppProperty(AppProperty.HTTP_PARAM_NAME_LOGOUT_URL);
            final String logoutURL = pwmRequest.readParameterAsString(logoutURLParamName);
            if (logoutURL != null && logoutURL.length() > 0) {
                try {
                    Helper.checkUrlAgainstWhitelist(pwmApplication, pwmRequest.getSessionLabel(), logoutURL);
                } catch (PwmOperationalException e) {
                    LOGGER.error(pwmRequest, e.getErrorInformation());
                    pwmRequest.respondWithError(e.getErrorInformation());
                    return ProcessStatus.Halt;
                }
                ssBean.setLogoutURL(logoutURL);
                LOGGER.debug(pwmRequest, "logoutURL parameter detected in request, setting session logout url to " + logoutURL);
            }
        }

        final String skipCaptcha = pwmRequest.readParameterAsString(PwmConstants.PARAM_SKIP_CAPTCHA);
        if (skipCaptcha != null && skipCaptcha.length() > 0) {
            final String configValue = config.readSettingAsString(PwmSetting.CAPTCHA_SKIP_PARAM);
            if (configValue != null && configValue.equals(skipCaptcha)) {
                LOGGER.trace(pwmSession, "valid skipCaptcha value in request, skipping captcha check for this session");
                ssBean.setPassedCaptcha(true);
            } else {
                LOGGER.error(pwmSession, "skipCaptcha value is in request, however value '" + skipCaptcha + "' does not match configured value");
            }
        }

        if ("true".equalsIgnoreCase(pwmRequest.readParameterAsString(
                pwmRequest.getConfig().readAppProperty(AppProperty.HTTP_PARAM_NAME_PASSWORD_EXPIRED)))) {
            pwmSession.getUserInfoBean().getPasswordState().setExpired(true);
        }

        // update last request time.
        ssBean.setSessionLastAccessedTime(new Date());

        if (pwmApplication.getStatisticsManager() != null) {
            pwmApplication.getStatisticsManager().incrementValue(Statistic.HTTP_REQUESTS);
        }

        return ProcessStatus.Continue;
    }

    public void destroy() {
    }

    /**
     * Attempt to determine if user agent is able to track sessions (either via url rewriting or cookies).
     */
    private static ProcessStatus verifySession(
            final PwmRequest pwmRequest,
            final SessionVerificationMode mode
    )
            throws IOException, ServletException, PwmUnrecoverableException {
        final LocalSessionStateBean ssBean = pwmRequest.getPwmSession().getSessionStateBean();
        final HttpServletRequest req = pwmRequest.getHttpServletRequest();
        final PwmResponse pwmResponse = pwmRequest.getPwmResponse();

        if (!pwmRequest.getMethod().isIdempotent() && pwmRequest.hasParameter(PwmConstants.PARAM_FORM_ID)) {
            LOGGER.debug(pwmRequest,"session is unvalidated but can not be validated during a " + pwmRequest.getMethod().toString() + " request, will allow");
            return ProcessStatus.Continue;
        }

        if (pwmRequest.getURL().isCommandServletURL()) {
            return ProcessStatus.Continue;
        }

        final String verificationParamName = pwmRequest.getConfig().readAppProperty(AppProperty.HTTP_PARAM_SESSION_VERIFICATION);
        final String keyFromRequest = pwmRequest.readParameterAsString(verificationParamName, PwmHttpRequestWrapper.Flag.BypassValidation);

        // request doesn't have key, so make a new one, store it in the session, and redirect back here with the new key.
        if (keyFromRequest == null || keyFromRequest.length() < 1) {

            final String returnURL = figureValidationURL(pwmRequest, ssBean.getSessionVerificationKey());

            LOGGER.trace(pwmRequest, "session has not been validated, redirecting with verification key to " + returnURL);

            pwmResponse.setHeader(PwmConstants.HttpHeader.Connection, "close");  // better chance of detecting un-sticky sessions this way
            if (mode == SessionVerificationMode.VERIFY_AND_CACHE) {
                req.setAttribute("Location", returnURL);
                pwmResponse.forwardToJsp(PwmConstants.JSP_URL.INIT);
            } else {
                pwmResponse.sendRedirect(returnURL);
            }
            return ProcessStatus.Halt;
        }

        // else, request has a key, so investigate.
        if (keyFromRequest.equals(ssBean.getSessionVerificationKey())) {
            final String returnURL = figureValidationURL(pwmRequest, null);

            // session looks, good, mark it as such and return;
            LOGGER.trace(pwmRequest, "session validated, redirecting to original request url: " + returnURL);
            ssBean.setSessionVerified(true);
            pwmRequest.getPwmResponse().sendRedirect(returnURL);
            return ProcessStatus.Halt;
        }

        // user's session is messed up.  send to error page.
        final String errorMsg = "client unable to reply with session key";
        final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_BAD_SESSION, errorMsg);
        LOGGER.error(pwmRequest, errorInformation);
        pwmRequest.respondWithError(errorInformation, true);
        return ProcessStatus.Halt;
    }

    private static String figureValidationURL(final PwmRequest pwmRequest, final String validationKey) {
        final HttpServletRequest req = pwmRequest.getHttpServletRequest();

        final StringBuilder sb = new StringBuilder();
        sb.append(req.getRequestURL());

        final String verificationParamName = pwmRequest.getConfig().readAppProperty(AppProperty.HTTP_PARAM_SESSION_VERIFICATION);

        for (final Enumeration paramEnum = req.getParameterNames(); paramEnum.hasMoreElements(); ) {
            final String paramName = (String) paramEnum.nextElement();

            // check to make sure param is in query string
            if (req.getQueryString() != null && req.getQueryString().contains(StringUtil.urlDecode(paramName))) {
                if (!verificationParamName.equals(paramName)) {
                    final List<String> paramValues = Arrays.asList(req.getParameterValues(paramName));

                    for (final Iterator<String> valueIter = paramValues.iterator(); valueIter.hasNext(); ) {
                        final String value = valueIter.next();
                        sb.append(sb.toString().contains("?") ? "&" : "?");
                        sb.append(StringUtil.urlEncode(paramName)).append("=");
                        sb.append(StringUtil.urlEncode(value));
                    }
                }
            } else {
                LOGGER.debug("dropping non-query string (body?) parameter '" + paramName + "' during redirect validation)");
            }
        }

        if (validationKey != null) {
            sb.append(sb.toString().contains("?") ? "&" : "?");
            sb.append(verificationParamName).append("=").append(validationKey);
        }

        return sb.toString();
    }


    private static boolean checkPageLeaveNotice(final PwmSession pwmSession, final Configuration config) {
        final long configuredSeconds = config.readSettingAsLong(PwmSetting.SECURITY_PAGE_LEAVE_NOTICE_TIMEOUT);
        if (configuredSeconds <= 0) {
            return false;
        }

        final Date currentPageLeaveNotice = pwmSession.getSessionStateBean().getPageLeaveNoticeTime();
        pwmSession.getSessionStateBean().setPageLeaveNoticeTime(null);
        if (currentPageLeaveNotice == null) {
            return false;
        }

        if (TimeDuration.fromCurrent(currentPageLeaveNotice).getTotalSeconds() <= configuredSeconds) {
            return false;
        }

        return true;
    }

    private static void handleLocaleParam(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException
    {
        final Configuration config = pwmRequest.getConfig();
        final String localeParamName = config.readAppProperty(AppProperty.HTTP_PARAM_NAME_LOCALE);
        final String localeCookieName = config.readAppProperty(AppProperty.HTTP_COOKIE_LOCALE_NAME);
        final String requestedLocale = pwmRequest.readParameterAsString(localeParamName);
        final int cookieAgeSeconds = (int) pwmRequest.getConfig().readSettingAsLong(PwmSetting.LOCALE_COOKIE_MAX_AGE);
        if (requestedLocale != null && requestedLocale.length() > 0) {
            LOGGER.debug(pwmRequest, "detected locale request parameter " + localeParamName + " with value " + requestedLocale);
            if (pwmRequest.getPwmSession().setLocale(pwmRequest.getPwmApplication(), requestedLocale)) {
                if (cookieAgeSeconds > 0) {
                    pwmRequest.getPwmResponse().writeCookie(
                            localeCookieName,
                            requestedLocale,
                            cookieAgeSeconds,
                            PwmHttpResponseWrapper.CookiePath.Application
                    );
                }
            }
        }
    }

    private static void handleThemeParam(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException {
        final Configuration config = pwmRequest.getConfig();
        final String themeParameterName = config.readAppProperty(AppProperty.HTTP_PARAM_NAME_THEME);
        final String themeReqParameter = pwmRequest.readParameterAsString(themeParameterName);

        if (themeReqParameter != null && !themeReqParameter.isEmpty()) {
            if (pwmRequest.getPwmApplication().getResourceServletService().checkIfThemeExists(pwmRequest, themeReqParameter)) {
                pwmRequest.getPwmSession().getSessionStateBean().setTheme(themeReqParameter);
                final String themeCookieName = config.readAppProperty(AppProperty.HTTP_COOKIE_THEME_NAME);
                if (themeCookieName != null && themeCookieName.length() > 0) {
                    final String configuredTheme = config.readSettingAsString(PwmSetting.INTERFACE_THEME);

                    if (configuredTheme != null && configuredTheme.equalsIgnoreCase(themeReqParameter)) {
                        pwmRequest.getPwmResponse().removeCookie(themeCookieName, PwmHttpResponseWrapper.CookiePath.Application);
                    } else {
                        int maxAge = Integer.parseInt(config.readAppProperty(AppProperty.HTTP_COOKIE_THEME_AGE));
                        pwmRequest.getPwmResponse().writeCookie(themeCookieName, themeReqParameter, maxAge, PwmHttpResponseWrapper.CookiePath.Application);
                    }
                }
            }
        }
    }

    private static void handleSsoOverrideParam(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException
    {

        final String ssoOverrideParameterName = pwmRequest.getConfig().readAppProperty(AppProperty.HTTP_PARAM_NAME_SSO_OVERRIDE);
        if (pwmRequest.hasParameter(ssoOverrideParameterName)) {
            if (pwmRequest.readParameterAsBoolean(ssoOverrideParameterName)) {
                LOGGER.trace(pwmRequest, "enabling sso authentication due to parameter " + ssoOverrideParameterName + "=" + pwmRequest.readParameterAsString(ssoOverrideParameterName));
                pwmRequest.getPwmSession().getLoginInfoBean().removeFlag(LoginInfoBean.LoginFlag.noSso);
            } else {
                LOGGER.trace(pwmRequest, "disabling sso authentication due to parameter " + ssoOverrideParameterName + "=" + pwmRequest.readParameterAsString(ssoOverrideParameterName));
                pwmRequest.getPwmSession().getLoginInfoBean().setFlag(LoginInfoBean.LoginFlag.noSso);
            }
        }
    }
}
