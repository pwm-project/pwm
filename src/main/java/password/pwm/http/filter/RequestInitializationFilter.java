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
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.*;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsManager;
import password.pwm.util.IPMatcher;
import password.pwm.util.LocaleHelper;
import password.pwm.util.StringUtil;
import password.pwm.util.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;
import password.pwm.util.secure.PwmRandom;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;

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

    public void doFilter(
            final ServletRequest servletRequest,
            final ServletResponse servletResponse,
            final FilterChain filterChain) throws IOException, ServletException {

        final HttpServletRequest req = (HttpServletRequest)servletRequest;
        final HttpServletResponse resp = (HttpServletResponse)servletResponse;
        final PwmApplicationMode mode = PwmApplicationMode.determineMode(req);
        final PwmURL pwmURL = new PwmURL(req);

        PwmApplication testPwmApplicationLoad = null;
        try { testPwmApplicationLoad = ContextManager.getPwmApplication(req); } catch (PwmException e) {}

        if (testPwmApplicationLoad == null && pwmURL.isResourceURL()) {
            filterChain.doFilter(req, resp);
        } else {
            if (mode == PwmApplicationMode.ERROR) {
                try {
                    final ContextManager contextManager = ContextManager.getContextManager(req.getServletContext());
                    if (contextManager != null) {
                        final ErrorInformation startupError = contextManager.getStartupErrorInformation();
                        servletRequest.setAttribute(PwmRequest.Attribute.PwmErrorInfo.toString(), startupError);
                    }
                } catch (Exception e) {
                    if (pwmURL.isResourceURL()) {
                        filterChain.doFilter(servletRequest, servletResponse);
                        return;
                    }

                    LOGGER.error("error while trying to detect application status: " + e.getMessage());
                }

                LOGGER.error("unable to satisfy incoming request, application is not available");
                resp.setStatus(500);
                final String url = PwmConstants.JSP_URL.APP_UNAVAILABLE.getPath();
                servletRequest.getServletContext().getRequestDispatcher(url).forward(servletRequest, servletResponse);
            } else {
                initializeServletRequest(req, resp, filterChain);
            }
        }
    }


    private void initializeServletRequest(
            final HttpServletRequest req,
            final HttpServletResponse resp,
            final FilterChain filterChain
    )
            throws IOException, ServletException
    {
        try {
            checkAndInitSessionState(req);
            PwmRequest.forRequest(req,resp);
        } catch (Throwable e) {
            LOGGER.error("can't load application: " + e.getMessage(),e);
            if (!(new PwmURL(req).isResourceURL())) {
                ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_APP_UNAVAILABLE);
                try {
                    ContextManager contextManager = ContextManager.getContextManager(req.getServletContext());
                    if (contextManager != null) {
                        errorInformation = contextManager.getStartupErrorInformation();
                    }
                } catch (Throwable e2) {
                    e2.getMessage();
                }
                req.setAttribute(PwmRequest.Attribute.PwmErrorInfo.toString(),errorInformation);
                final String url = PwmConstants.JSP_URL.APP_UNAVAILABLE.getPath();
                req.getServletContext().getRequestDispatcher(url).forward(req, resp);
            }
            return;
        }

        try {
            final PwmRequest pwmRequest = PwmRequest.forRequest(req,resp);

            checkIfSessionRecycleNeeded(pwmRequest);

            handleRequestInitialization(pwmRequest);

            addPwmResponseHeaders(pwmRequest);

            checkIdleTimeout(pwmRequest);

            try {
                handleRequestSecurityChecks(pwmRequest);
            } catch (PwmUnrecoverableException e) {
                LOGGER.error(pwmRequest, e.getErrorInformation());
                pwmRequest.respondWithError(e.getErrorInformation());
                if (PwmError.ERROR_INTRUDER_SESSION != e.getError()) {
                    pwmRequest.invalidateSession();
                }
                return;
            }

        } catch (Throwable e) {
            LOGGER.error("can't init request: " + e.getMessage(),e);
            if (!(new PwmURL(req).isResourceURL())) {
                ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_APP_UNAVAILABLE);
                try {
                    ContextManager contextManager = ContextManager.getContextManager(req.getServletContext());
                    if (contextManager != null) {
                        errorInformation = contextManager.getStartupErrorInformation();
                    }
                } catch (Throwable e2) {
                    e2.getMessage();
                }
                req.setAttribute(PwmRequest.Attribute.PwmErrorInfo.toString(),errorInformation);
                final String url = PwmConstants.JSP_URL.APP_UNAVAILABLE.getPath();
                req.getServletContext().getRequestDispatcher(url).forward(req, resp);
            }
            return;
        }

        filterChain.doFilter(req, resp);
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

    public static void addPwmResponseHeaders(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException
    {

        if (pwmRequest == null) {
            return;
        }
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final Configuration config = pwmApplication.getConfig();
        final PwmResponse resp = pwmRequest.getPwmResponse();

        if (resp.isCommitted()) {
            return;
        }

        final String serverHeader = config.readAppProperty(AppProperty.HTTP_HEADER_SERVER);
        final boolean includeXInstance = Boolean.parseBoolean(config.readAppProperty(AppProperty.HTTP_HEADER_SEND_XINSTANCE));
        final boolean includeXSessionID = Boolean.parseBoolean(config.readAppProperty(AppProperty.HTTP_HEADER_SEND_XSESSIONID));
        final boolean includeXVersion = Boolean.parseBoolean(config.readAppProperty(AppProperty.HTTP_HEADER_SEND_XVERSION));
        final boolean includeXContentTypeOptions = Boolean.parseBoolean(config.readAppProperty(AppProperty.HTTP_HEADER_SEND_XCONTENTTYPEOPTIONS));
        final boolean includeXXSSProtection = Boolean.parseBoolean(config.readAppProperty(AppProperty.HTTP_HEADER_SEND_XXSSPROTECTION));

        final boolean sendNoise = Boolean.parseBoolean(config.readAppProperty(AppProperty.HTTP_HEADER_SEND_XNOISE));

        if (sendNoise) {
            final int noiseLength = Integer.parseInt(config.readAppProperty(AppProperty.HTTP_HEADER_NOISE_LENGTH));
            resp.setHeader(
                    PwmConstants.HttpHeader.XNoise,
                    PwmRandom.getInstance().alphaNumericString(PwmRandom.getInstance().nextInt(noiseLength)+11)
            );
        }

        if (includeXVersion) {
            resp.setHeader(PwmConstants.HttpHeader.XVersion, PwmConstants.SERVLET_VERSION);
        }

        if (includeXContentTypeOptions) {
            resp.setHeader(PwmConstants.HttpHeader.XContentTypeOptions, "nosniff");
        }

        if (includeXXSSProtection) {
            resp.setHeader(PwmConstants.HttpHeader.XXSSProtection, "1");
        }

        if (includeXInstance) {
            resp.setHeader(PwmConstants.HttpHeader.XInstance, String.valueOf(pwmApplication.getInstanceID()));
        }

        if (includeXSessionID && pwmSession != null) {
            resp.setHeader(PwmConstants.HttpHeader.XSessionID, pwmSession.getSessionStateBean().getSessionID());
        }

        if (serverHeader != null && !serverHeader.isEmpty()) {
            final String value = MacroMachine.forNonUserSpecific(pwmApplication, null).expandMacros(serverHeader);
            resp.setHeader(PwmConstants.HttpHeader.Server, value);
        }


        if (pwmRequest.getURL().isResourceURL()) {
            return;
        }

        // ----- non-resource urls only for the following operations -----

        final boolean includeXFrameDeny = config.readSettingAsBoolean(PwmSetting.SECURITY_PREVENT_FRAMING);
        final boolean includeXAmb = Boolean.parseBoolean(config.readAppProperty(AppProperty.HTTP_HEADER_SEND_XAMB));
        final boolean includeContentLanguage = Boolean.parseBoolean(config.readAppProperty(AppProperty.HTTP_HEADER_SEND_CONTENT_LANGUAGE));

        if (includeXFrameDeny) {
            resp.setHeader(PwmConstants.HttpHeader.XFrameOptions, "DENY");
        }

        if (includeXAmb) {
            resp.setHeader(PwmConstants.HttpHeader.XAmb, PwmConstants.X_AMB_HEADER[PwmRandom.getInstance().nextInt(PwmConstants.X_AMB_HEADER.length)]);
        }

        if (includeContentLanguage) {
            resp.setHeader(PwmConstants.HttpHeader.Content_Language, pwmRequest.getLocale().toLanguageTag());
        }

        resp.setHeader(PwmConstants.HttpHeader.Cache_Control, "no-cache, no-store, must-revalidate, proxy-revalidate");

        if (pwmSession != null) {
            final String contentPolicy = config.readSettingAsString(PwmSetting.SECURITY_CSP_HEADER);
            if (contentPolicy != null && !contentPolicy.isEmpty()) {
                final String nonce = pwmRequest.getCspNonce();
                final String expandedPolicy = contentPolicy.replace("%NONCE%", nonce);
                resp.setHeader(PwmConstants.HttpHeader.ContentSecurityPolicy, expandedPolicy);
            }
        }
    }


    public static String readUserHostname(final PwmRequest pwmRequest) throws PwmUnrecoverableException {
        final Configuration config = pwmRequest.getConfig();
        if (config != null && !config.readSettingAsBoolean(PwmSetting.REVERSE_DNS_ENABLE)) {
            return "";
        }

        final String userIPAddress = readUserIPAddress(pwmRequest);
        try {
            return InetAddress.getByName(userIPAddress).getCanonicalHostName();
        } catch (UnknownHostException e) {
            LOGGER.trace(pwmRequest, "unknown host while trying to compute hostname for src request: " + e.getMessage());
        }
        return "";
    }

    /**
     * Returns the IP address of the user.  If there is an X-Forwarded-For header in the request, that address will
     * be used.  Otherwise, the source address of the request is used.
     *
     * @return String containing the textual representation of the source IP address, or null if the request is invalid.
     */
    public static String readUserIPAddress(final PwmRequest pwmRequest) throws PwmUnrecoverableException {
        final Configuration config = pwmRequest.getConfig();
        final boolean useXForwardedFor = config != null && config.readSettingAsBoolean(PwmSetting.USE_X_FORWARDED_FOR_HEADER);

        String userIP = "";

        if (useXForwardedFor) {
            try {
                userIP = pwmRequest.readHeaderValueAsString(PwmConstants.HTTP_HEADER_X_FORWARDED_FOR);
            } catch (Exception e) {
                //ip address not in header (no X-Forwarded-For)
            }
        }

        if (userIP == null || userIP.length() < 1) {
            userIP = pwmRequest.getHttpServletRequest().getRemoteAddr();
        }

        return userIP == null ? "" : userIP;
    }


    public static void handleRequestInitialization(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException
    {
        final LocalSessionStateBean ssBean = pwmRequest.getPwmSession().getSessionStateBean();
        final PwmURL pwmURL = pwmRequest.getURL();

        // mark if first request
        if (ssBean.getSessionCreationTime() == null) {
            ssBean.setSessionCreationTime(new Date());
            ssBean.setSessionLastAccessedTime(new Date());
        }

        // mark session ip address
        if (ssBean.getSrcAddress() == null) {
            ssBean.setSrcAddress(readUserIPAddress(pwmRequest));
        }

        // mark the user's hostname in the session bean
        if (ssBean.getSrcHostname() == null) {
            ssBean.setSrcHostname(readUserHostname(pwmRequest));
        }

        // update the privateUrlAccessed flag
        if (pwmURL.isPrivateUrl()) {
            ssBean.setPrivateUrlAccessed(true);
        }

        // initialize the session's locale
        if (ssBean.getLocale() == null) {
            initializeLocaleAndTheme(pwmRequest);
        }

        // set idle timeout (may get overridden by module-specific values elsewhere
        if (!pwmURL.isResourceURL() && !pwmURL.isCommandServletURL() && !pwmURL.isWebServiceURL()){
            final int sessionIdleSeconds = (int) pwmRequest.getConfig().readSettingAsLong(PwmSetting.IDLE_TIMEOUT_SECONDS);
            final TimeDuration maxIdleTimeout = IdleTimeoutCalculator.figureMaxIdleTimeout(pwmRequest.getPwmApplication(), pwmRequest.getPwmSession());
            pwmRequest.getHttpServletRequest().getSession().setMaxInactiveInterval((int) maxIdleTimeout.getTotalSeconds());
        }
    }

    private static void initializeLocaleAndTheme(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException
    {
        final String localeCookieName = pwmRequest.getConfig().readAppProperty(AppProperty.HTTP_COOKIE_LOCALE_NAME);
        final String localeCookie = pwmRequest.readCookie(localeCookieName);
        if (localeCookieName.length() > 0 && localeCookie != null) {
            LOGGER.debug(pwmRequest, "detected locale cookie in request, setting locale to " + localeCookie);
            pwmRequest.getPwmSession().setLocale(pwmRequest.getPwmApplication(), localeCookie);
        } else {
            final List<Locale> knownLocales = pwmRequest.getConfig().getKnownLocales();
            final Locale userLocale = LocaleHelper.localeResolver(pwmRequest.getHttpServletRequest().getLocale(), knownLocales);
            pwmRequest.getPwmSession().getSessionStateBean().setLocale(userLocale == null ? PwmConstants.DEFAULT_LOCALE : userLocale);
            LOGGER.trace(pwmRequest, "user locale set to '" + pwmRequest.getLocale() + "'");
        }

        final String themeCookieName = pwmRequest.getConfig().readAppProperty(AppProperty.HTTP_COOKIE_THEME_NAME);
        final String themeCookie = pwmRequest.readCookie(themeCookieName);
        if (localeCookieName.length() > 0 && themeCookie != null && themeCookie.length() > 0) {
            if (pwmRequest.getPwmApplication().getResourceServletService().checkIfThemeExists(pwmRequest, themeCookie)) {
                LOGGER.debug(pwmRequest, "detected theme cookie in request, setting theme to " + themeCookie);
                pwmRequest.getPwmSession().getSessionStateBean().setTheme(themeCookie);
            }
        }
    }

    public static void handleRequestSecurityChecks(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException
    {
        final LocalSessionStateBean ssBean = pwmRequest.getPwmSession().getSessionStateBean();

        // check the user's IP address
        if (!pwmRequest.getConfig().readSettingAsBoolean(PwmSetting.MULTI_IP_SESSION_ALLOWED)) {
            final String remoteAddress = readUserIPAddress(pwmRequest);
            if (!ssBean.getSrcAddress().equals(remoteAddress)) {
                final String errorMsg = "current network address '" + remoteAddress + "' has changed from original network address '" + ssBean.getSrcAddress() + "'";
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_SECURITY_VIOLATION,errorMsg);
                throw new PwmUnrecoverableException(errorInformation);
            }
        }

        // check total time.
        {
            if (ssBean.getSessionCreationTime() != null) {
                final Long maxSessionSeconds = pwmRequest.getConfig().readSettingAsLong(PwmSetting.SESSION_MAX_SECONDS);
                final TimeDuration sessionAge = TimeDuration.fromCurrent(ssBean.getSessionCreationTime());
                if (sessionAge.getTotalSeconds() > maxSessionSeconds) {
                    final String errorMsg = "session age (" + sessionAge.asCompactString() + ") is longer than maximum permitted age";
                    final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_SECURITY_VIOLATION,errorMsg);
                    throw new PwmUnrecoverableException(errorInformation);
                }
            }
        }

        // check headers
        {
            final List<String> requiredHeaders = pwmRequest.getConfig().readSettingAsStringArray(PwmSetting.REQUIRED_HEADERS);
            if (requiredHeaders != null && !requiredHeaders.isEmpty()) {
                final Map<String, String> configuredValues  = StringUtil.convertStringListToNameValuePair(requiredHeaders, "=");
                for (final String key : configuredValues.keySet()) {
                    if (key != null && key.length() > 0) {
                        final String requiredValue = configuredValues.get(key);
                        if (requiredValue != null && requiredValue.length() > 0) {
                            final String value = pwmRequest.readHeaderValueAsString(key);
                            if (value == null || value.length() < 1) {
                                final String errorMsg = "request is missing required value for header '" + key + "'";
                                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_SECURITY_VIOLATION,errorMsg);
                                throw new PwmUnrecoverableException(errorInformation);
                            } else {
                                if (!requiredValue.equals(value)) {
                                    final String errorMsg = "request has incorrect required value for header '" + key + "'";
                                    final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_SECURITY_VIOLATION,errorMsg);
                                    throw new PwmUnrecoverableException(errorInformation);
                                }
                            }
                        }
                    }
                }
            }
        }

        // check permitted source IP address
        {
            final List<String> requiredHeaders = pwmRequest.getConfig().readSettingAsStringArray(PwmSetting.IP_PERMITTED_RANGE);
            if (requiredHeaders != null && !requiredHeaders.isEmpty()) {
                boolean match = false;
                final String requestAddress = pwmRequest.getHttpServletRequest().getRemoteAddr();
                for (int i = 0; i < requiredHeaders.size() && !match; i++) {
                    String ipMatchString = requiredHeaders.get(i);
                    try {
                        final IPMatcher ipMatcher = new IPMatcher(ipMatchString);
                        try {
                            if (ipMatcher.match(requestAddress)) {
                                match = true;
                            }
                        } catch (IPMatcher.IPMatcherException e) {
                            LOGGER.error("error while attempting to match permitted address range '" + ipMatchString + "', error: " + e);
                        }
                    } catch (IPMatcher.IPMatcherException e) {
                        LOGGER.error("error parsing permitted address range '" + ipMatchString + "', error: " + e);
                    }
                }
                if (!match) {
                    final String errorMsg = "request network address '" + requestAddress + "' does not match any configured permitted source address";
                    final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_SECURITY_VIOLATION,errorMsg);
                    throw new PwmUnrecoverableException(errorInformation);
                }
            }
        }

        // check trial
        if (PwmConstants.TRIAL_MODE) {
            final String currentAuthString = pwmRequest.getPwmApplication().getStatisticsManager().getStatBundleForKey(StatisticsManager.KEY_CURRENT).getStatistic(Statistic.AUTHENTICATIONS);
            if (new BigInteger(currentAuthString).compareTo(BigInteger.valueOf(PwmConstants.TRIAL_MAX_AUTHENTICATIONS)) > 0) {
                throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_TRIAL_VIOLATION,"maximum usage per server startup exceeded"));
            }

            final String totalAuthString = pwmRequest.getPwmApplication().getStatisticsManager().getStatBundleForKey(StatisticsManager.KEY_CUMULATIVE).getStatistic(Statistic.AUTHENTICATIONS);
            if (new BigInteger(totalAuthString).compareTo(BigInteger.valueOf(PwmConstants.TRIAL_MAX_TOTAL_AUTH)) > 0) {
                throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_TRIAL_VIOLATION,"maximum usage for this server has been exceeded"));
            }
        }

        // check intruder
        pwmRequest.getPwmApplication().getIntruderManager().convenience().checkAddressAndSession(pwmRequest.getPwmSession());
    }

    private void checkIdleTimeout(final PwmRequest pwmRequest) throws PwmUnrecoverableException {
        final TimeDuration maxDurationForRequest = IdleTimeoutCalculator.idleTimeoutForRequest(pwmRequest);
        final TimeDuration currentDuration = TimeDuration.fromCurrent(pwmRequest.getHttpServletRequest().getSession().getLastAccessedTime());
        if (currentDuration.isLongerThan(maxDurationForRequest)) {
            LOGGER.debug("closing session due to idle time, max for request is " + maxDurationForRequest.asCompactString() + ", session idle time is " + currentDuration.asCompactString());
            pwmRequest.getPwmSession().unauthenticateUser(pwmRequest);
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_USERAUTHENTICATED,"idle timeout exceeded"));
        }
    }
}
