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

package password.pwm.util;

import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import password.pwm.*;
import password.pwm.bean.SessionStateBean;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.i18n.Message;
import password.pwm.ws.server.RestResultBean;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.*;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.*;

public class ServletHelper {

    private static final PwmLogger LOGGER = PwmLogger.getLogger(ServletHelper.class);

    /**
     * Wrapper for {@link #forwardToErrorPage(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse, boolean)} )}
     * with forceLogout=true;
     *
     * @param req        Users http request
     * @param resp       Users http response
     * @param theContext The Servlet context
     * @throws java.io.IOException            if there is an error writing to the response
     * @throws javax.servlet.ServletException if there is a problem accessing the http objects
     */
    public static void forwardToErrorPage(
            final HttpServletRequest req,
            final HttpServletResponse resp,
            final ServletContext theContext
    )
            throws IOException, ServletException {
        forwardToErrorPage(req, resp, true);
    }

    /**
     * Forwards the user to the error page.  Callers to this method should populate the session bean's
     * session error state.  If the session error state is null, then this method will populate it
     * with a generic unknown error.
     *
     *
     * @param req         Users http request
     * @param resp        Users http response
     * @param forceLogout if the user should be unauthenticated after showing the error
     * @throws java.io.IOException            if there is an error writing to the response
     * @throws javax.servlet.ServletException if there is a problem accessing the http objects
     */
    public static void forwardToErrorPage(
            final HttpServletRequest req,
            final HttpServletResponse resp,
            final boolean forceLogout
    )
            throws IOException, ServletException {
        try {
            String errorPageURL = '/' + PwmConstants.URL_JSP_ERROR;
            try {
                errorPageURL = SessionFilter.rewriteURL(errorPageURL, req, resp);
            } catch (PwmUnrecoverableException e) {
                /* system must not be up enough to handle the rewrite */
            }

            req.getSession().getServletContext().getRequestDispatcher(errorPageURL).forward(req, resp);
            if (forceLogout) {
                PwmSession.getPwmSession(req).unauthenticateUser();
            }
        } catch (PwmUnrecoverableException e) {
            LOGGER.error("unexpected error sending user to error page: " + e.toString());
        }
    }

    public static void forwardToLoginPage(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException {
        final String loginServletURL = req.getContextPath() + "/private/" + PwmConstants.URL_SERVLET_LOGIN;
        try{
            resp.sendRedirect(SessionFilter.rewriteRedirectURL(loginServletURL, req, resp));
        } catch (PwmUnrecoverableException e) {
            LOGGER.error("unexpected error sending user to error page: " + e.toString());
        }
    }

    public static void forwardToRedirectPage(
            final HttpServletRequest req,
            final HttpServletResponse resp,
            final String redirectURL
    )
            throws IOException, ServletException
    {
        req.setAttribute("nextURL",redirectURL);
        final String redirectPageJsp = '/' + PwmConstants.URL_JSP_REDIRECT;
        req.getSession().getServletContext().getRequestDispatcher(redirectPageJsp).forward(req, resp);
    }

    public static void forwardToSuccessPage(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();

        if (!pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.DISPLAY_SUCCESS_PAGES)) {
            ssBean.setSessionSuccess(null, null);
            LOGGER.trace(pwmSession, "skipping success page due to configuration setting.");
            final StringBuilder redirectURL = new StringBuilder();
            redirectURL.append(req.getContextPath());
            redirectURL.append("/public/");
            redirectURL.append(SessionFilter.rewriteURL("CommandServlet",req,resp));
            redirectURL.append("?processAction=continue");
            redirectURL.append("&pwmFormID=");
            redirectURL.append(Helper.buildPwmFormID(pwmSession.getSessionStateBean()));
            resp.sendRedirect(redirectURL.toString());
            return;
        }

        try {

            if (ssBean.getSessionSuccess() == null) {
                ssBean.setSessionSuccess(Message.SUCCESS_UNKNOWN, null);
            }

            final String url = SessionFilter.rewriteURL('/' + PwmConstants.URL_JSP_SUCCESS, req, resp);
            req.getSession().getServletContext().getRequestDispatcher(url).forward(req, resp);
        } catch (PwmUnrecoverableException e) {
            LOGGER.error("unexpected error sending user to success page: " + e.toString());
        }
    }

    public static String debugHttpHeaders(final HttpServletRequest req) {
        final StringBuilder sb = new StringBuilder();

        sb.append("http session headers: ");
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

    public static String debugHttpRequest(final HttpServletRequest req) {
        return debugHttpRequest(req, "");
    }

    public static String debugHttpRequest(final HttpServletRequest req, final String extraText) {
        final StringBuilder sb = new StringBuilder();

        sb.append(req.getMethod());
        sb.append(" request for: ");
        sb.append(req.getRequestURI());

        if (req.getParameterMap().isEmpty()) {
            sb.append(" (no params)");
            if (extraText != null) {
                sb.append(" ");
                sb.append(extraText);
            }
        } else {
            if (extraText != null) {
                sb.append(" ");
                sb.append(extraText);
            }
            sb.append("\n");

            for (final Enumeration paramNameEnum = req.getParameterNames(); paramNameEnum.hasMoreElements();) {
                final String paramName = (String) paramNameEnum.nextElement();
                final Set<String> paramValues = new HashSet<String>();
                try {
                    paramValues.addAll(Validator.readStringsFromRequest(req, paramName, 1024));
                } catch (PwmUnrecoverableException e) {
                    LOGGER.error("unexpected error debugging http request: " + e.toString());
                }

                for (final String paramValue : paramValues) {
                    sb.append("  ").append(paramName).append("=");
                    if (
                            paramName.toLowerCase().contains("password") ||
                                    paramName.startsWith(PwmConstants.PARAM_RESPONSE_PREFIX) ||
                                    paramName.contains(PwmConstants.PARAM_TOKEN)
                            ) {
                        sb.append(PwmConstants.LOG_REMOVED_VALUE_REPLACEMENT);
                    } else {
                        sb.append('\'');
                        sb.append(paramValue);
                        sb.append('\'');
                    }

                    sb.append('\n');
                }
            }

            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.toString();
    }

    /**
     * Try to find the real path to a file.  Used for configuration, database, and temporary files.
     * <p/>
     * Multiple strategies are used to determine the real path of files because different servlet containers
     * have different symantics.  In principal, servlets are not supposed
     *
     * @param filename       A filename that will be appended to the end of the verified directory
     * @param relativePath  The desired path of the file, either relative to the servlet directory or an absolute path
     *                       on the file system
     * @param servletContext The HttpServletContext to be used to retrieve a path.
     * @return a File referencing the desired suggestedPath and filename.
     * @throws Exception if unabble to discover a path.
     */
    public static File figureFilepath(final String filename, final String relativePath, final ServletContext servletContext)
            throws Exception {
        final File servletPath = new File(servletContext.getRealPath(relativePath));

        if (!servletPath.isAbsolute()) {
            // for containers which do not retrieve the real path, try to use the classloader to find the path.
            final String cManagerName = PwmApplication.class.getCanonicalName();
            final String resourcePathname = "/" + cManagerName.replace(".", "/") + ".class";
            final URL fileURL = PwmApplication.class.getResource(resourcePathname);
            if (fileURL != null) {
                final String newString = fileURL.toString().replace("WEB-INF/classes" + resourcePathname, "");
                final File finalDirectory = new File(new URL(newString + relativePath).toURI());
                if (finalDirectory.exists()) {
                    return Helper.figureFilepath(filename, finalDirectory);
                }
            }
        } else {
            return Helper.figureFilepath(filename, servletPath);
        }

        throw new Exception("unable to locate resource file path=" + relativePath + ", name=" + filename);
    }

    public static String readRequestBody(final HttpServletRequest request) throws IOException {
        return readRequestBody(request, PwmConstants.HTTP_BODY_READ_LENGTH);
    }

    public static String readRequestBody(final HttpServletRequest request, final int maxChars) throws IOException {
        final StringBuilder inputData = new StringBuilder();
        String line;
        try {
            final BufferedReader reader = request.getReader();
            while (((line = reader.readLine()) != null) && inputData.length() < maxChars) {
                inputData.append(line);
            }
        } catch (Exception e) {
            LOGGER.error("error reading request body stream: " + e.getMessage());
        }
        return inputData.toString();
    }

    public static void addPwmResponseHeaders(final PwmApplication pwmApplication, final HttpServletResponse resp, boolean includeXAmb) {
        if (!resp.isCommitted()) {
            if (includeXAmb && PwmConstants.INCLUDE_X_VERSION_HEADER) {
                resp.setHeader("X-" + PwmConstants.PWM_APP_NAME + "-Version", PwmConstants.SERVLET_VERSION);
            }

            resp.setHeader("X-" + PwmConstants.PWM_APP_NAME + "-Instance", String.valueOf(pwmApplication.getInstanceID()));

            if (includeXAmb && PwmConstants.INCLUDE_X_AMB_HEADER) {
                resp.setHeader("X-" + PwmConstants.PWM_APP_NAME + "-Amb", PwmConstants.X_AMB_HEADER[PwmRandom.getInstance().nextInt(PwmConstants.X_AMB_HEADER.length)]);
            }
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
                        return cookie.getValue();
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

    public static void outputJsonResult(
            final HttpServletResponse resp,
            final RestResultBean restResultBean
    )
            throws IOException
    {
        final String outputString = restResultBean.toJson();
        resp.setContentType("application/json;charset=utf-8");
        resp.getWriter().print(outputString);
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

    private static String streamToString(final InputStream stream, final int maxFileChars)
            throws IOException, PwmUnrecoverableException {
        final BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(stream,"UTF-8"));
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

    public static void recycleSessions(final PwmSession pwmSession, HttpServletRequest req)
            throws IOException, ServletException
    {
        pwmSession.getSessionStateBean().regenerateSessionVerificationKey();

        if (!PwmConstants.HTTP_RECYCLE_SESSIONS_ON_AUTH) {
            return;
        }

        LOGGER.debug(pwmSession,"forcing new http session due to authentication");

        // read the old session data
        final HttpSession oldSession = req.getSession(true);
        final Map<String,Object> sessionAttributes = new HashMap<String,Object>();
        final Enumeration oldSessionAttrNames = oldSession.getAttributeNames();
        while (oldSessionAttrNames.hasMoreElements()) {
            final String attrName = (String)oldSessionAttrNames.nextElement();
            sessionAttributes.put(attrName,oldSession.getAttribute(attrName));
        }

        //invalidate the old session
        oldSession.invalidate();

        // make a new session
        final HttpSession newSession = req.getSession(true);

        // write back all the session data
        for (final String attrName : sessionAttributes.keySet()) {
            newSession.setAttribute(attrName, sessionAttributes.get(attrName));
        }

        pwmSession.setHttpSession(newSession);
    }

    public static void handleRequestInitialization(
            final HttpServletRequest req,
            final PwmApplication pwmApplication,
            final PwmSession pwmSession
    )
            throws PwmUnrecoverableException
    {
        // set the auto-config url value.
        pwmApplication.setAutoSiteURL(req);

        final SessionStateBean ssBean = pwmSession.getSessionStateBean();

        // mark if first request
        if (ssBean.getSessionCreationTime() == null) {
            ssBean.setSessionCreationTime(new Date());
        }

        // mark session ip address
        if (ssBean.getSrcAddress() == null) {
            ssBean.setSrcAddress(readUserIPAddress(req, pwmSession));
        }

        // mark the user's hostname in the session bean
        if (ssBean.getSrcHostname() == null) {
            ssBean.setSrcHostname(readUserHostname(req, pwmSession));
        }

        // update the privateUrlAccessed flag
        if (PwmServletURLHelper.isPrivateUrl(req)) {
            ssBean.setPrivateUrlAccessed(true);
        }

        // initialize the session's locale
        if (ssBean.getLocale() == null) {
            initializeLocaleAndTheme(req, pwmApplication, pwmSession);
        }
    }

    private static void initializeLocaleAndTheme(
            final HttpServletRequest req,
            final PwmApplication pwmApplication,
            final PwmSession pwmSession
    )
            throws PwmUnrecoverableException
    {
        final String localeCookie = ServletHelper.readCookie(req,PwmConstants.COOKIE_LOCALE);
        if (PwmConstants.COOKIE_LOCALE.length() > 0 && localeCookie != null) {
            LOGGER.debug(pwmSession, "detected locale cookie in request, setting locale to " + localeCookie);
            pwmSession.setLocale(localeCookie);
        } else {
            final List<Locale> knownLocales = pwmApplication.getConfig().getKnownLocales();
            final Locale userLocale = Helper.localeResolver(req.getLocale(), knownLocales);
            pwmSession.getSessionStateBean().setLocale(userLocale == null ? PwmConstants.DEFAULT_LOCALE : userLocale);
            LOGGER.trace(pwmSession, "user locale set to '" + pwmSession.getSessionStateBean().getLocale() + "'");
        }

        final String themeCookie = ServletHelper.readCookie(req,PwmConstants.COOKIE_THEME);
        if (PwmConstants.COOKIE_THEME.length() > 0 && themeCookie != null && themeCookie.length() > 0) {
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

        // check idle time
        {
            if (ssBean.getSessionLastAccessedTime() != null) {
                final Long maxIdleSeconds = pwmApplication.getConfig().readSettingAsLong(PwmSetting.IDLE_TIMEOUT_SECONDS);
                final TimeDuration maxIdleCheckTime = new TimeDuration((maxIdleSeconds * 1000) + (60 * 1000));
                final TimeDuration idleTime = TimeDuration.fromCurrent(ssBean.getSessionLastAccessedTime());
                if (idleTime.isLongerThan(maxIdleCheckTime)) {
                    final String errorMsg = "session idle time (" + idleTime.asCompactString() + ") is longer than maximum idle time age";
                    final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_SECURITY_VIOLATION,errorMsg);
                    throw new PwmUnrecoverableException(errorInformation);
                }
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
                final Map<String, String> configuredValues  = Configuration.convertStringListToNameValuePair(requiredHeaders, "=");
                for (final String key : configuredValues.keySet()) {
                    if (key != null && key.length() > 0) {
                        final String requiredValue = configuredValues.get(key);
                        if (requiredValue != null && requiredValue.length() > 0) {
                            final String value = Validator.sanatizeInputValue(pwmApplication.getConfig(),req.getHeader(key),1024);
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
    }
}
