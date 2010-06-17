/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2010 The PWM Project
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
import password.pwm.config.ConfigurationReader;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.util.PwmLogger;
import password.pwm.util.PwmRandom;
import password.pwm.util.stats.Statistic;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.URLDecoder;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Set;

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

    private ServletContext servletContext;

// -------------------------- STATIC METHODS --------------------------

    public static void debugHttpRequest(final HttpServletRequest req, final PwmSession pwmSession)
    {
        final StringBuilder sb = new StringBuilder();

        sb.append(req.getMethod());
        sb.append(" request for: ");
        sb.append(req.getRequestURI());

        if (req.getParameterMap().isEmpty()) {
            sb.append(" (no params)");
        } else {
            sb.append("\n");

            for (final Enumeration paramNameEnum = req.getParameterNames(); paramNameEnum.hasMoreElements();) {
                final String paramName = (String) paramNameEnum.nextElement();
                final Set<String> paramValues = Validator.readStringsFromRequest(req, paramName, 1024);

                for (final String paramValue : paramValues) {
                    sb.append("  ").append(paramName).append("=");
                    if (paramName.toLowerCase().contains("password") || paramName.startsWith(PwmConstants.PARAM_RESPONSE_PREFIX)) {
                        sb.append("***removed***");
                    } else {
                        sb.append('\'');
                        sb.append(paramValue);
                        sb.append('\'');
                    }

                    sb.append('\n');
                }
            }

            sb.deleteCharAt(sb.length() -1);
        }
        LOGGER.trace(pwmSession, sb.toString());
    }

    public static String readUserHostname(final HttpServletRequest req, final PwmSession pwmSession)
    {
        final String userIPAddress = readUserIPAddress(req, pwmSession);
        try {
            return InetAddress.getByName(userIPAddress).getCanonicalHostName();
        } catch (UnknownHostException e) {
            LOGGER.trace(pwmSession, "unknown host while trying to compute hostname for src request: " + e.getMessage());
        }
        return "";
    }

    /**
     * Returns the IP address of the user.  If there is an X-Forwarded-For header in the request, that address will
     * be used.  Otherwise, the source address of the request is used.
     *
     * @param req        A valid HttpServletRequest.
     * @param pwmSession pwmSession used for config lookup
     * @return String containing the textual representation of the source IP address, or null if the request is invalid.
     */
    public static String readUserIPAddress(final HttpServletRequest req, final PwmSession pwmSession)
    {
        final boolean useXForwardedFor = pwmSession.getConfig() != null && pwmSession.getConfig().readSettingAsBoolean(PwmSetting.USE_X_FORWARDED_FOR_HEADER);

        String userIP = "";

        if (useXForwardedFor) {
            try {
                userIP = req.getHeader(PwmConstants.HTTP_HEADER_X_FORWARDED_FOR);
            } catch (Exception e) {
                //ip address not in header (no X-Forwarded-For)
            }
        }

        if (userIP == null || userIP.length() < 1) {
            userIP = req.getRemoteAddr();
        }

        return userIP == null ? "" : userIP;
    }

// ------------------------ INTERFACE METHODS ------------------------


// --------------------- Interface Filter ---------------------

    public void init(final FilterConfig filterConfig)
            throws ServletException
    {
        servletContext = filterConfig.getServletContext();
    }

    public void doFilter(
            final ServletRequest servletRequest,
            final ServletResponse servletResponse,
            final FilterChain filterChain
    )
            throws IOException, ServletException
    {

        final HttpServletRequest req = (HttpServletRequest) servletRequest;
        final HttpServletResponse resp = (HttpServletResponse) servletResponse;

        final ContextManager theManager = ContextManager.getContextManager(req.getSession());
        final PwmSession pwmSession = PwmSession.getPwmSession(req.getSession());
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();

        // check for valid config
        if (theManager == null || theManager.getConfig() == null || theManager.getConfigReader().getConfigMode() == ConfigurationReader.MODE.NEW) {
            final String configServletPathPrefix = req.getContextPath() + "/config/";
            final String requestedURL = req.getRequestURI();

            // check if current request is actually for the login servlet url, if it is, just do nothing.
            if (requestedURL == null || !requestedURL.startsWith(configServletPathPrefix)) {
                LOGGER.warn(pwmSession, "unable to find a valid configuration, redirecting to ConfigManager");
                ssBean.setSessionError(new ErrorInformation(PwmError.ERROR_INVALID_CONFIG));
                Helper.forwardToErrorPage(req, resp, servletContext, false);
                return;
            }
        }

        // mark the user's IP address in the session bean
        ssBean.setSrcAddress(readUserIPAddress(req, pwmSession));

        // mark the user's hostname in the session bean
        ssBean.setSrcHostname(readUserHostname(req, pwmSession));

        // output request information to debug log
        debugHttpRequest(req, pwmSession);

        //set the session's locale
        final String langReqParamter = Validator.readStringFromRequest(req, "pwmLocale", 255);
        if (langReqParamter != null && langReqParamter.length() > 0) {
            LOGGER.debug(pwmSession, "setting session locale to '" + langReqParamter + "' due to 'locale' request parameter");
            ssBean.setLocale(new Locale(langReqParamter));
        } else if (ssBean.getLocale() == null) {
            ssBean.setLocale(req.getLocale());
        }

        //clear any errors in the session's state bean
        ssBean.setSessionError(null);

        //check for session verification failure
        if (!ssBean.isSessionVerified()) {
            // ignore resource requests
            verifySession(req,resp,servletContext);
            return;
        }

        //check intruder detection, if it is tripped, send user to error page
        if (theManager.getIntruderManager() != null && theManager.getConfig() != null) {
            try {
                theManager.getIntruderManager().checkAddress(pwmSession);
            } catch (PwmException e) {
                Helper.forwardToErrorPage(req, resp, servletContext, false);
                return;
            }
        }

        final String forwardURLParam = readUrlParameterFromRequest(req, "forwardURL", pwmSession);
        if (forwardURLParam != null && forwardURLParam.length() > 0) {
            ssBean.setForwardURL(forwardURLParam);
            LOGGER.debug(pwmSession, "forwardURL parameter detected in request, setting session forward url to " + forwardURLParam);
        }

        final String logoutURL = readUrlParameterFromRequest(req, "logoutURL", pwmSession);
        if (logoutURL != null && logoutURL.length() > 0) {
            ssBean.setLogoutURL(logoutURL);
            LOGGER.debug(pwmSession, "logoutURL parameter detected in request, setting session logout url to " + logoutURL);
        }

        final String skipCaptcha = Validator.readStringFromRequest(req, "skipCaptcha", 4096);
        if (skipCaptcha != null && skipCaptcha.length() > 0) {
            final String configValue = theManager.getConfig().readSettingAsString(PwmSetting.CAPTCHA_SKIP_PARAM);
            if (configValue != null && configValue.equals(skipCaptcha)) {
                LOGGER.trace(pwmSession, "valid skipCaptcha value in request, skipping captcha check for this session");
                ssBean.setPassedCaptcha(true);
            } else {
                LOGGER.error(pwmSession, "skipCaptcha value is in request, however value '" + skipCaptcha + "' does not match configured value");
            }
        }

        if (Validator.readBooleanFromRequest(req, "passwordExpired")) {
            PwmSession.getUserInfoBean(req.getSession()).getPasswordState().setExpired(true);
        }

        if (!resp.isCommitted()) {
            resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            resp.setHeader("X-Pwm-Version", PwmConstants.SERVLET_VERSION);
            resp.setHeader("X-Pwm-Instance", String.valueOf(theManager.getInstanceID()));

            if (PwmRandom.getInstance().nextInt(5) == 0) {
                resp.setHeader("X-Pwm-Amb", PwmConstants.X_AMB_HEADER[PwmRandom.getInstance().nextInt(PwmConstants.X_AMB_HEADER.length)]);
            }
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

        if (theManager.getStatisticsManager() != null) {
            theManager.getStatisticsManager().incrementValue(Statistic.HTTP_REQUESTS);
        }

        ssBean.setLastAccessTime(System.currentTimeMillis());
    }

    public void destroy()
    {
        servletContext = null;
    }

    public static String rewriteURL(final String url, final ServletRequest request, final ServletResponse response) {
        if (urlSessionsAllowed(request) && url != null) {
            final HttpServletResponse resp = (HttpServletResponse) response;
            final String newURL = resp.encodeURL(resp.encodeURL(url));
            if (!url.equals(newURL)) {
                final PwmSession pwmSession = PwmSession.getPwmSession((HttpServletRequest)request);
                LOGGER.trace(pwmSession, "rewriting URL from '" + url + "' to '" + newURL + "'");
            }
            return newURL;
        } else {
            return url;
        }
    }

    public static String rewriteRedirectURL(final String url, final ServletRequest request, final ServletResponse response) {
        if (urlSessionsAllowed(request) && url != null) {
            final HttpServletResponse resp = (HttpServletResponse) response;
            final String newURL = resp.encodeRedirectURL(resp.encodeURL(url));
            if (!url.equals(newURL)) {
                final PwmSession pwmSession = PwmSession.getPwmSession((HttpServletRequest)request);
                LOGGER.trace(pwmSession,"rewriting redirect URL from '" + url + "' to '" + newURL + "'");
            }
            return newURL;
        } else {
            return url;
        }
    }

    private static boolean urlSessionsAllowed(final ServletRequest request) {
        final HttpServletRequest req = (HttpServletRequest) request;
        final ContextManager theManager = ContextManager.getContextManager(req);
        if (theManager != null && theManager.getConfig() != null) {
            return theManager.getConfig().readSettingAsBoolean(PwmSetting.ALLOW_URL_SESSIONS);
        }
        return false;
    }

    /**
     * Attempt to determine if user agent is able to track sessions (either via url rewriting or cookies).
     *
     * @param req http request
     * @param resp http response
     * @param servletContext pwm servlet context
     * @throws IOException if error touching the io streams
     * @throws javax.servlet.ServletException error using the servlet context
     */
    private static void verifySession(
            final HttpServletRequest req,
            final HttpServletResponse resp,
            final ServletContext servletContext
    )
            throws IOException, ServletException {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();

        final String keyFromRequest = Validator.readStringFromRequest(req, PwmConstants.PARAM_VERIFICATIN_KEY,255);

        // request doesn't have key, so make a new one, store it in the session, and redirect back here with the new key.
        if (keyFromRequest == null || keyFromRequest.length() < 1) {

            final String returnURL = figureValidationURL(req, ssBean.getSessionVerificationKey());

            LOGGER.trace(pwmSession,"session has not been validated, redirecting with verification key to " + returnURL);

            resp.sendRedirect(SessionFilter.rewriteRedirectURL(returnURL,req,resp));
            return;
        }

        // else, request has a key, so investigate.
        if (keyFromRequest.equals(ssBean.getSessionVerificationKey())) {
            // session looks, good, mark it as such and return;
            LOGGER.trace(pwmSession,"session validated, redirecting to original request url");
            ssBean.setSessionVerified(true);

            final String returnURL = figureValidationURL(req, null);
            resp.sendRedirect(SessionFilter.rewriteRedirectURL(returnURL,req,resp));
            return;
        }

        // user's session is messed up.  send to error page.
        LOGGER.error(pwmSession, "incorrect verification key sent during session verification check");
        ssBean.setSessionError(new ErrorInformation(PwmError.ERROR_BAD_SESSION));
        Helper.forwardToErrorPage(req,resp,servletContext);
    }

    private static String figureValidationURL(final HttpServletRequest req, final String validationKey)
    {
        final StringBuilder sb = new StringBuilder();

        sb.append(req.getRequestURI());

        if (!req.getParameterMap().isEmpty()) {
            sb.append("?");
            for (final Enumeration paramNameEnum = req.getParameterNames(); paramNameEnum.hasMoreElements();) {
                final String paramName = (String) paramNameEnum.nextElement();
                final Set<String> paramValues = Validator.readStringsFromRequest(req, paramName, 1024);

                if (validationKey != null || !PwmConstants.PARAM_VERIFICATIN_KEY.equals(paramName)) {
                    for (final String paramValue : paramValues) {
                        sb.append(paramName);
                        sb.append("=");
                        sb.append(paramValue);
                        sb.append("&");
                    }
                }
                sb.deleteCharAt(sb.length() -1);
                sb.append("&");
            }
            sb.deleteCharAt(sb.length() -1);
        }

        if (validationKey != null) {
            if (!sb.toString().contains("?")) {
                sb.append("?");
            } else {
                sb.append("&");
            }
            sb.append(PwmConstants.PARAM_VERIFICATIN_KEY).append("=").append(validationKey);
        }

        return sb.toString();
    }

    private static String readUrlParameterFromRequest(final HttpServletRequest req, final String paramName, final PwmSession pwmSession) {
        final String paramValue = Validator.readStringFromRequest(req, paramName, 4096);
        if (paramValue == null || paramValue.length() < 1) {
            return null;
        }

        try {
            final String decodedValue = URLDecoder.decode(paramValue, "UTF-8");
            LOGGER.trace(pwmSession, "decoded value for " + paramName + " to " + decodedValue);
            return decodedValue;
        } catch (UnsupportedEncodingException e) {
            LOGGER.error(pwmSession, "unexpected error decoding " + paramName + " parameter: " + e.getMessage());
        }

        return paramValue;
    }
}
