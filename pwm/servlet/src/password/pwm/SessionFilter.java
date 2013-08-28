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

package password.pwm;

import password.pwm.bean.SessionStateBean;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.*;
import password.pwm.util.stats.Statistic;

import javax.servlet.*;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
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
public class SessionFilter implements Filter {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(SessionFilter.class);

// -------------------------- STATIC METHODS --------------------------


// ------------------------ INTERFACE METHODS ------------------------


// --------------------- Interface Filter ---------------------

    public void init(final FilterConfig filterConfig)
            throws ServletException {
        //servletContext = filterConfig.getServletContext();
    }

    public void doFilter(
            final ServletRequest servletRequest,
            final ServletResponse servletResponse,
            final FilterChain filterChain
    )
            throws IOException, ServletException {

        final HttpServletRequest req = (HttpServletRequest) servletRequest;
        final HttpServletResponse resp = (HttpServletResponse) servletResponse;

        try {
            resp.setHeader(
                    "X-" + PwmConstants.PWM_APP_NAME + "-Noise",
                    PwmRandom.getInstance().alphaNumericString(PwmRandom.getInstance().nextInt(100)+10)
            );
        } catch (Exception e) {
            /* noop */
        }

        try {
            processFilter(req,resp,filterChain);
        } catch (PwmUnrecoverableException e) {
            LOGGER.fatal("unexpected error processing session filter: " + e.getMessage());
            ServletHelper.forwardToErrorPage(req,resp,true);
        }
    }

    private void processFilter(final HttpServletRequest req, final HttpServletResponse resp, final FilterChain filterChain)
            throws PwmUnrecoverableException, IOException, ServletException
    {
        final PwmSession pwmSession = PwmSession.getPwmSession(req.getSession());
        final ServletContext servletContext = req.getSession().getServletContext();
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req.getSession());
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();

        ServletHelper.handleRequestInitialization(req, pwmApplication, pwmSession);

        try {
            ServletHelper.handleRequestSecurityChecks(req, pwmApplication, pwmSession);
        } catch (PwmUnrecoverableException e) {
            LOGGER.error(e.getErrorInformation().toDebugStr());
            ssBean.setSessionError(e.getErrorInformation());
            ServletHelper.forwardToErrorPage(req,resp,true);
            return;
        }

        // mark last url
        if (!PwmServletURLHelper.isCommandServletURL(req)) {
            ssBean.setLastRequestURL(req.getRequestURI());
        }

        // mark last request time.
        ssBean.setSessionLastAccessedTime(new Date());

        // debug the http session headers
        if (!pwmSession.getSessionStateBean().isDebugInitialized()) {
            LOGGER.trace(pwmSession, ServletHelper.debugHttpHeaders(req));
            pwmSession.getSessionStateBean().setDebugInitialized(true);
        }

        // increment the page counter
        ssBean.incrementRequestCounter();

        // output request information to debug log
        LOGGER.trace(pwmSession, ServletHelper.debugHttpRequest(req));

        // check the page leave notice
        if (checkPageLeaveNotice(pwmSession, pwmApplication.getConfig())) {
            LOGGER.warn("invalidating session due to dirty page leave time greater then configured timeout");
            pwmSession.invalidate();
            resp.sendRedirect(req.getRequestURI());
            return;
        }

        //override session locale due to parameter
        handleLocaleParam(req, resp, pwmSession);

        //set the session's theme
        final String themeReqParamter = Validator.readStringFromRequest(req, PwmConstants.PARAM_THEME);
        if (themeReqParamter != null && themeReqParamter.length() > 0) {
            ssBean.setTheme(themeReqParamter);
            final Cookie newCookie = new Cookie(PwmConstants.COOKIE_THEME, themeReqParamter);
            newCookie.setMaxAge(PwmConstants.USER_COOKIE_MAX_AGE_SECONDS);
            newCookie.setPath(req.getContextPath() + "/");
            final String configuredTheme = pwmApplication.getConfig().readSettingAsString(PwmSetting.INTERFACE_THEME);
            if (configuredTheme != null && configuredTheme.equalsIgnoreCase(themeReqParamter)) {
                newCookie.setMaxAge(0);
            }
            resp.addCookie(newCookie);
        }

        // make sure connection is secure.
        if (pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.REQUIRE_HTTPS) && !req.isSecure()) {
            ssBean.setSessionError(new ErrorInformation(PwmError.ERROR_SECURE_REQUEST_REQUIRED));
            ServletHelper.forwardToErrorPage(req,resp, true);
            return;
        }

        //check for session verification failure
        if (!ssBean.isSessionVerified()) {
            // ignore resource requests
            if (pwmApplication.getConfig() != null && !pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.ENABLE_SESSION_VERIFICATION)) {
                ssBean.setSessionVerified(true);
            } else {
                if (verifySession(req, resp, servletContext)) {
                    return;
                }
            }
        }

        //check intruder detection, if it is tripped, send user to error page
        if (pwmApplication.getIntruderManager() != null && pwmApplication.getConfig() != null) {
            try {
                pwmApplication.getIntruderManager().check(null,null,pwmSession);
            } catch (PwmUnrecoverableException e) {
                pwmSession.getSessionStateBean().setSessionError(e.getErrorInformation());
                ServletHelper.forwardToErrorPage(req, resp, false);
                return;
            }
        }

        final String forwardURLParam = Validator.readStringFromRequest(req, PwmConstants.PARAM_FORWARD_URL);
        if (forwardURLParam != null && forwardURLParam.length() > 0) {
            ssBean.setForwardURL(forwardURLParam);
            LOGGER.debug(pwmSession, "forwardURL parameter detected in request, setting session forward url to " + forwardURLParam);
        }

        final String logoutURL = Validator.readStringFromRequest(req, PwmConstants.PARAM_LOGOUT_URL);
        if (logoutURL != null && logoutURL.length() > 0) {
            ssBean.setLogoutURL(logoutURL);
            LOGGER.debug(pwmSession, "logoutURL parameter detected in request, setting session logout url to " + logoutURL);
        }

        final String skipCaptcha = Validator.readStringFromRequest(req, "skipCaptcha");
        if (skipCaptcha != null && skipCaptcha.length() > 0) {
            final String configValue = pwmApplication.getConfig().readSettingAsString(PwmSetting.CAPTCHA_SKIP_PARAM);
            if (configValue != null && configValue.equals(skipCaptcha)) {
                LOGGER.trace(pwmSession, "valid skipCaptcha value in request, skipping captcha check for this session");
                ssBean.setPassedCaptcha(true);
            } else {
                LOGGER.error(pwmSession, "skipCaptcha value is in request, however value '" + skipCaptcha + "' does not match configured value");
            }
        }

        if (Validator.readBooleanFromRequest(req, PwmConstants.PARAM_PASSWORD_EXPIRED)) {
            pwmSession.getUserInfoBean().getPasswordState().setExpired(true);
        }

        if (!resp.isCommitted()) {
            resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate, proxy-revalidate");
            ServletHelper.addPwmResponseHeaders(pwmApplication, resp, true);
        }

        try {
            filterChain.doFilter(req, resp);
        } catch (Exception e) {
            LOGGER.warn(pwmSession, "unhandled exception", e);
            throw new ServletException(e);
        } catch (Throwable e) {
            LOGGER.warn(pwmSession, "unhandled exception " + e.getMessage(), e);
            throw new ServletException(e);
        }

        // update last request time.
        ssBean.setSessionLastAccessedTime(new Date());

        if (pwmApplication.getStatisticsManager() != null) {
            pwmApplication.getStatisticsManager().incrementValue(Statistic.HTTP_REQUESTS);
        }
    }

    public void destroy() {
        //servletContext = null;
    }

    public static String rewriteURL(final String url, final ServletRequest request, final ServletResponse response) throws PwmUnrecoverableException {
        if (urlSessionsAllowed(request) && url != null) {
            final HttpServletResponse resp = (HttpServletResponse) response;
            final String newURL = resp.encodeURL(resp.encodeURL(url));
            if (!url.equals(newURL)) {
                final PwmSession pwmSession = PwmSession.getPwmSession((HttpServletRequest) request);
                LOGGER.trace(pwmSession, "rewriting URL from '" + url + "' to '" + newURL + "'");
            }
            return newURL;
        } else {
            return url;
        }
    }

    public static String rewriteRedirectURL(final String url, final ServletRequest request, final ServletResponse response) throws PwmUnrecoverableException {
        if (urlSessionsAllowed(request) && url != null) {
            final HttpServletResponse resp = (HttpServletResponse) response;
            final String newURL = resp.encodeRedirectURL(resp.encodeURL(url));
            if (!url.equals(newURL)) {
                final PwmSession pwmSession = PwmSession.getPwmSession((HttpServletRequest) request);
                LOGGER.trace(pwmSession, "rewriting redirect URL from '" + url + "' to '" + newURL + "'");
            }
            return newURL;
        } else {
            return url;
        }
    }

    private static boolean urlSessionsAllowed(final ServletRequest request) throws PwmUnrecoverableException {
        final HttpServletRequest req = (HttpServletRequest) request;
        final PwmApplication theManager = ContextManager.getPwmApplication(req);
        return theManager != null && theManager.getConfig() != null && theManager.getConfig().readSettingAsBoolean(PwmSetting.ALLOW_URL_SESSIONS);
    }

    /**
     * Attempt to determine if user agent is able to track sessions (either via url rewriting or cookies).
     *
     * @param req            http request
     * @param resp           http response
     * @param servletContext pwm servlet context
     * @throws IOException                    if error touching the io streams
     * @throws javax.servlet.ServletException error using the servlet context
     */
    private static boolean verifySession(
            final HttpServletRequest req,
            final HttpServletResponse resp,
            final ServletContext servletContext
    )
            throws IOException, ServletException, PwmUnrecoverableException {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();

        final String keyFromRequest = Validator.readStringFromRequest(req, PwmConstants.PARAM_VERIFICATION_KEY);

        // request doesn't have key, so make a new one, store it in the session, and redirect back here with the new key.
        if (keyFromRequest == null || keyFromRequest.length() < 1) {

            final String returnURL = figureValidationURL(req, ssBean.getSessionVerificationKey());

            LOGGER.trace(pwmSession, "session has not been validated, redirecting with verification key to " + returnURL);

            resp.setHeader("Connection","close");  // better chance of detecting un-sticky sessions this way
            resp.sendRedirect(SessionFilter.rewriteRedirectURL(returnURL, req, resp));
            return true;
        }

        // else, request has a key, so investigate.
        if (keyFromRequest.equals(ssBean.getSessionVerificationKey())) {
            final String returnURL = figureValidationURL(req, null);

            // session looks, good, mark it as such and return;
            LOGGER.trace(pwmSession, "session validated, redirecting to original request url: " + returnURL);
            ssBean.setSessionVerified(true);
            return false;
        }

        // user's session is messed up.  send to error page.
        final String errorMsg = "client unable to reply with session key";
        final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_BAD_SESSION,errorMsg);
        LOGGER.error(pwmSession, errorInformation.toDebugStr());
        ssBean.setSessionError(errorInformation);
        ServletHelper.forwardToErrorPage(req, resp, servletContext);
        return false;
    }

    private static String figureValidationURL(final HttpServletRequest req, final String validationKey) {
        final StringBuilder sb = new StringBuilder();
        sb.append(req.getRequestURL());
        if (!req.getParameterMap().isEmpty()) {
            sb.append("?");
        }
        for (final Enumeration paramEnum = req.getParameterNames(); paramEnum.hasMoreElements(); ) {
            final String paramName = (String)paramEnum.nextElement();
            if (!PwmConstants.PARAM_VERIFICATION_KEY.equals(paramName)) {
                final List<String> paramValues = Arrays.asList(req.getParameterValues(paramName));

                for (final Iterator<String> valueIter = paramValues.iterator(); valueIter.hasNext();) {
                    final String value = valueIter.next();
                    sb.append(paramName).append("=");
                    try {
                        sb.append(URLEncoder.encode(value, "UTF-8"));
                    } catch (UnsupportedEncodingException e) {
                        sb.append(value);
                        LOGGER.warn("unexpected error",e);
                    }
                    if (valueIter.hasNext()) {
                        sb.append("&");
                    }
                }
                if (paramEnum.hasMoreElements()) {
                    sb.append("&");
                }
            }
        }

        if (validationKey != null) {
            sb.append(sb.toString().contains("?") ? "&" : "?");
            sb.append(PwmConstants.PARAM_VERIFICATION_KEY).append("=").append(validationKey);
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

    private static void handleLocaleParam(final HttpServletRequest request, final HttpServletResponse response, final PwmSession pwmSession) throws PwmUnrecoverableException {
        final String requestedLocale = Validator.readStringFromRequest(request, PwmConstants.PARAM_LOCALE);
        if (requestedLocale != null && requestedLocale.length() > 0) {
            LOGGER.debug(pwmSession, "detected locale request parameter " + PwmConstants.PARAM_LOCALE + " with value " + requestedLocale);
            if (pwmSession.setLocale(requestedLocale)) {
                final Cookie newCookie = new Cookie(PwmConstants.COOKIE_LOCALE, requestedLocale);
                newCookie.setMaxAge(PwmConstants.USER_COOKIE_MAX_AGE_SECONDS);
                newCookie.setPath(request.getContextPath() + "/");
                response.addCookie(newCookie);
            }
        }
    }

}
