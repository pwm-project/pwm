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

package password.pwm.http;

import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.Validator;
import password.pwm.bean.SessionStateBean;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsManager;
import password.pwm.util.IPMatcher;
import password.pwm.util.LocaleHelper;
import password.pwm.util.StringUtil;
import password.pwm.util.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmRandom;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;

public class ServletHelper {

    private static final PwmLogger LOGGER = PwmLogger.forClass(ServletHelper.class);

    public static String debugHttpHeaders(final HttpServletRequest req) {
        final StringBuilder sb = new StringBuilder();

        sb.append("http").append(req.isSecure() ? "s " : " non-").append("secure request headers: ");
        sb.append("\n");

        for (Enumeration enumeration = req.getHeaderNames(); enumeration.hasMoreElements();) {
            final String headerName = (enumeration.nextElement()).toString();
            sb.append("  ");
            sb.append(headerName);
            sb.append("=");
            if (headerName.contains("Authorization")) {
                sb.append(PwmConstants.LOG_REMOVED_VALUE_REPLACEMENT);
            } else {
                sb.append(req.getHeader(headerName));
            }
            sb.append(enumeration.hasMoreElements() ? "\n" : "");
        }

        return sb.toString();
    }


    public static void addPwmResponseHeaders(
            final PwmRequest pwmRequest,
            boolean fromServlet
    ) {

        if (pwmRequest == null) {
            return;
        }
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final PwmResponse resp = pwmRequest.getPwmResponse();

        if (!resp.isCommitted()) {
            final boolean includeXAmb = Boolean.parseBoolean(pwmApplication.getConfig().readAppProperty(AppProperty.HTTP_HEADER_SEND_XAMB));
            final boolean includeXInstance = Boolean.parseBoolean(pwmApplication.getConfig().readAppProperty(AppProperty.HTTP_HEADER_SEND_XINSTANCE));
            final boolean includeXSessionID = Boolean.parseBoolean(pwmApplication.getConfig().readAppProperty(AppProperty.HTTP_HEADER_SEND_XSESSIONID));
            final boolean includeXVersion = Boolean.parseBoolean(pwmApplication.getConfig().readAppProperty(AppProperty.HTTP_HEADER_SEND_XVERSION));
            final boolean includeXContentTypeOptions = Boolean.parseBoolean(pwmApplication.getConfig().readAppProperty(AppProperty.HTTP_HEADER_SEND_XCONTENTTYPEOPTIONS));
            final boolean includeXXSSProtection = Boolean.parseBoolean(pwmApplication.getConfig().readAppProperty(AppProperty.HTTP_HEADER_SEND_XXSSPROTECTION));


            final boolean includeXFrameDeny = pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.SECURITY_PREVENT_FRAMING);
            final boolean sendNoise = Boolean.parseBoolean(pwmApplication.getConfig().readAppProperty(
                    AppProperty.HTTP_HEADER_SEND_XNOISE));

            if (sendNoise) {
                resp.setHeader(
                        PwmConstants.HttpHeader.XNoise,
                        PwmRandom.getInstance().alphaNumericString(PwmRandom.getInstance().nextInt(100)+10)
                );
            }

            if (fromServlet && includeXAmb) {
                resp.setHeader(PwmConstants.HttpHeader.XAmb, PwmConstants.X_AMB_HEADER[PwmRandom.getInstance().nextInt(PwmConstants.X_AMB_HEADER.length)]);
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

            if (includeXFrameDeny && fromServlet) {
                resp.setHeader(PwmConstants.HttpHeader.XFrameOptions, "DENY");
            }


            if (fromServlet) {
                resp.setHeader(PwmConstants.HttpHeader.Cache_Control, "no-cache, no-store, must-revalidate, proxy-revalidate");
            }

            if (fromServlet && pwmSession != null) {
                final String contentPolicy = pwmApplication.getConfig().readSettingAsString(PwmSetting.SECURITY_CSP_HEADER);
                if (contentPolicy != null && !contentPolicy.isEmpty()) {
                    final String nonce = pwmRequest.getCspNonce();
                    final String expandedPolicy = contentPolicy.replace("%NONCE%", nonce);
                    resp.setHeader(PwmConstants.HttpHeader.ContentSecurityPolicy, expandedPolicy);
                }
            }

            final String instanceCookieName = pwmApplication.getConfig().readAppProperty(AppProperty.HTTP_COOKIE_INSTANCE_GUID_NAME);
            if (instanceCookieName != null && instanceCookieName.length() > 0) {
                resp.writeCookie(
                        instanceCookieName,
                        pwmApplication.getInstanceNonce(),
                        Integer.parseInt(pwmApplication.getConfig().readAppProperty(AppProperty.HTTP_COOKIE_INSTANCE_GUID_AGE)),
                        pwmRequest.getContextPath()
                );

            }

            resp.setHeader(PwmConstants.HttpHeader.Server, null);
        }
    }

    public static String readCookie(final HttpServletRequest req, final String cookieName) {
        if (req == null || cookieName == null) {
            return null;
        }
        final Cookie[] cookies = req.getCookies();
        if (cookies != null) {
            for (final Cookie cookie : cookies) {
                if (cookie != null) {
                    final String loopName = cookie.getName();
                    if (cookieName.equals(loopName)) {
                        return StringUtil.urlDecode(cookie.getValue());
                    }
                }
            }
        }
        return null;
    }


    public static boolean cookieEquals(final HttpServletRequest req, final String cookieName, final String cookieValue) {
        final String value = readCookie(req, cookieName);
        if (value == null) {
            return cookieValue == null;
        }
        return value.equals(cookieValue);
    }

    public static String readUserHostname(final HttpServletRequest req, final PwmSession pwmSession) throws PwmUnrecoverableException {
        final Configuration config = ContextManager.getPwmApplication(req).getConfig();
        if (config != null && !config.readSettingAsBoolean(PwmSetting.REVERSE_DNS_ENABLE)) {
            return "";
        }

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
    public static String readUserIPAddress(final HttpServletRequest req, final PwmSession pwmSession) throws PwmUnrecoverableException {
        final Configuration config = ContextManager.getPwmApplication(req).getConfig();
        final boolean useXForwardedFor = config != null && config.readSettingAsBoolean(PwmSetting.USE_X_FORWARDED_FOR_HEADER);

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


    public static String readFileUpload(
            final HttpServletRequest req,
            final String filePartName,
            int maxFileChars
    )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        try {
            if (ServletFileUpload.isMultipartContent(req)) {

                // Create a new file upload handler
                final ServletFileUpload upload = new ServletFileUpload();

                String uploadFile = null;

                // Parse the request
                for (final FileItemIterator iter = upload.getItemIterator(req); iter.hasNext();) {
                    final FileItemStream item = iter.next();

                    if (filePartName.equals(item.getFieldName())) {
                        uploadFile = streamToString(item.openStream(),maxFileChars);
                    }
                }

                return uploadFile;
            }
        } catch (Exception e) {
            LOGGER.error("error reading file upload: " + e.getMessage());
        }
        return null;
    }

    public static InputStream readFileUpload(
            final HttpServletRequest req,
            final String filePartName
    )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        try {
            if (ServletFileUpload.isMultipartContent(req)) {

                // Create a new file upload handler
                final ServletFileUpload upload = new ServletFileUpload();

                // Parse the request
                for (final FileItemIterator iter = upload.getItemIterator(req); iter.hasNext();) {
                    final FileItemStream item = iter.next();

                    if (filePartName.equals(item.getFieldName())) {
                        return item.openStream();
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("error reading file upload: " + e.getMessage());
        }
        return null;
    }

    private static String streamToString(final InputStream stream, final int maxFileChars)
            throws IOException, PwmUnrecoverableException {
        final BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(stream,PwmConstants.DEFAULT_CHARSET));
        final StringBuilder sb = new StringBuilder();
        int charCounter = 0;
        int nextChar = bufferedReader.read();
        while (nextChar != -1) {
            charCounter++;
            sb.append((char)nextChar);
            nextChar = bufferedReader.read();
            if (charCounter > maxFileChars) {
                stream.close();
                throw new PwmUnrecoverableException(new ErrorInformation(PwmError.CONFIG_UPLOAD_FAILURE,"file too large"));
            }
        }
        return sb.toString();
    }

    public static void handleRequestInitialization(
            final PwmRequest pwmRequest,
            final PwmApplication pwmApplication,
            final PwmSession pwmSession
    )
            throws PwmUnrecoverableException
    {
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();
        final PwmURL pwmURL = pwmRequest.getURL();

        // mark if first request
        if (ssBean.getSessionCreationTime() == null) {
            ssBean.setSessionCreationTime(new Date());
            ssBean.setSessionLastAccessedTime(new Date());
        }

        // mark session ip address
        if (ssBean.getSrcAddress() == null) {
            ssBean.setSrcAddress(readUserIPAddress(pwmRequest.getHttpServletRequest(), pwmSession));
        }

        // mark the user's hostname in the session bean
        if (ssBean.getSrcHostname() == null) {
            ssBean.setSrcHostname(readUserHostname(pwmRequest.getHttpServletRequest(), pwmSession));
        }

        // update the privateUrlAccessed flag
        if (pwmURL.isPrivateUrl()) {
            ssBean.setPrivateUrlAccessed(true);
        }

        // initialize the session's locale
        if (ssBean.getLocale() == null) {
            initializeLocaleAndTheme(pwmRequest.getHttpServletRequest(), pwmApplication, pwmSession);
        }

        // set idle timeout (may get overridden by module-specific values elsewhere
        if (!pwmURL.isResourceURL() && !pwmURL.isCommandServletURL() && !pwmURL.isWebServiceURL()){
            final int sessionIdleSeconds = (int) pwmApplication.getConfig().readSettingAsLong(PwmSetting.IDLE_TIMEOUT_SECONDS);
            pwmSession.setSessionTimeout(pwmRequest.getHttpServletRequest().getSession(), sessionIdleSeconds);
        }
    }

    private static void initializeLocaleAndTheme(
            final HttpServletRequest req,
            final PwmApplication pwmApplication,
            final PwmSession pwmSession
    )
            throws PwmUnrecoverableException
    {
        final String localeCookieName = pwmApplication.getConfig().readAppProperty(AppProperty.HTTP_COOKIE_LOCALE_NAME);
        final String localeCookie = ServletHelper.readCookie(req,localeCookieName);
        if (localeCookieName.length() > 0 && localeCookie != null) {
            LOGGER.debug(pwmSession, "detected locale cookie in request, setting locale to " + localeCookie);
            pwmSession.setLocale(pwmApplication, localeCookie);
        } else {
            final List<Locale> knownLocales = pwmApplication.getConfig().getKnownLocales();
            final Locale userLocale = LocaleHelper.localeResolver(req.getLocale(), knownLocales);
            pwmSession.getSessionStateBean().setLocale(userLocale == null ? PwmConstants.DEFAULT_LOCALE : userLocale);
            LOGGER.trace(pwmSession, "user locale set to '" + pwmSession.getSessionStateBean().getLocale() + "'");
        }

        final String themeCookieName = pwmApplication.getConfig().readAppProperty(AppProperty.HTTP_COOKIE_THEME_NAME);
        final String themeCookie = ServletHelper.readCookie(req,themeCookieName);
        if (localeCookieName.length() > 0 && themeCookie != null && themeCookie.length() > 0) {
            LOGGER.debug(pwmSession, "detected theme cookie in request, setting theme to " + themeCookie);
            pwmSession.getSessionStateBean().setTheme(themeCookie);
        }
    }

    public static void handleRequestSecurityChecks(
            final HttpServletRequest req,
            final PwmApplication pwmApplication,
            final PwmSession pwmSession
    )
            throws PwmUnrecoverableException
    {
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();

        // check the user's IP address
        if (!pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.MULTI_IP_SESSION_ALLOWED)) {
            final String remoteAddress = readUserIPAddress(req, pwmSession);
            if (!ssBean.getSrcAddress().equals(remoteAddress)) {
                final String errorMsg = "current network address '" + remoteAddress + "' has changed from original network address '" + ssBean.getSrcAddress() + "'";
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_SECURITY_VIOLATION,errorMsg);
                throw new PwmUnrecoverableException(errorInformation);
            }
        }

        // check total time.
        {
            if (ssBean.getSessionCreationTime() != null) {
                final Long maxSessionSeconds = pwmApplication.getConfig().readSettingAsLong(PwmSetting.SESSION_MAX_SECONDS);
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
            final List<String> requiredHeaders = pwmApplication.getConfig().readSettingAsStringArray(PwmSetting.REQUIRED_HEADERS);
            if (requiredHeaders != null && !requiredHeaders.isEmpty()) {
                final Map<String, String> configuredValues  = StringUtil.convertStringListToNameValuePair(requiredHeaders, "=");
                for (final String key : configuredValues.keySet()) {
                    if (key != null && key.length() > 0) {
                        final String requiredValue = configuredValues.get(key);
                        if (requiredValue != null && requiredValue.length() > 0) {
                            final String value = Validator.sanitizeInputValue(pwmApplication.getConfig(),
                                    req.getHeader(key), 1024);
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
            final List<String> requiredHeaders = pwmApplication.getConfig().readSettingAsStringArray(PwmSetting.IP_PERMITTED_RANGE);
            if (requiredHeaders != null && !requiredHeaders.isEmpty()) {
                boolean match = false;
                final String requestAddress = req.getRemoteAddr();
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
                    final String errorMsg = "request network address '" + req.getRemoteAddr() + "' does not match any configured permitted source address";
                    final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_SECURITY_VIOLATION,errorMsg);
                    throw new PwmUnrecoverableException(errorInformation);
                }
            }
        }

        // check trial
        if (PwmConstants.TRIAL_MODE) {
            final String currentAuthString = pwmApplication.getStatisticsManager().getStatBundleForKey(StatisticsManager.KEY_CURRENT).getStatistic(Statistic.AUTHENTICATIONS);
            if (new BigInteger(currentAuthString).compareTo(BigInteger.valueOf(PwmConstants.TRIAL_MAX_AUTHENTICATIONS)) > 0) {
                throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_TRIAL_VIOLATION,"maximum usage per server startup exceeded"));
            }

            final String totalAuthString = pwmApplication.getStatisticsManager().getStatBundleForKey(StatisticsManager.KEY_CUMULATIVE).getStatistic(Statistic.AUTHENTICATIONS);
            if (new BigInteger(totalAuthString).compareTo(BigInteger.valueOf(PwmConstants.TRIAL_MAX_TOTAL_AUTH)) > 0) {
                throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_TRIAL_VIOLATION,"maximum usage for this server has been exceeded"));
            }
        }

        // check intruder
        pwmApplication.getIntruderManager().convenience().checkAddressAndSession(pwmSession);
    }

    public static String appendAndEncodeUrlParameters(
            final String inputUrl,
            final Map<String, String> parameters
    )
    {
        final StringBuilder output = new StringBuilder();
        output.append(inputUrl == null ? "" : inputUrl);

        if (parameters != null) {
            for (final String key : parameters.keySet()) {
                final String value = parameters.get(key);
                final String encodedValue = StringUtil.urlEncode(value);

                output.append(output.toString().contains("?") ? "&" : "?");
                output.append(key);
                output.append("=");
                output.append(encodedValue);
            }
        }

        if (output.charAt(0) == '?' || output.charAt(0) == '&') {
            output.deleteCharAt(0);
        }

        return output.toString();
    }
}
