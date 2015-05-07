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
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.Validator;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.SessionStateBean;
import password.pwm.bean.UserIdentity;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.Configuration;
import password.pwm.config.FormConfiguration;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.i18n.Message;
import password.pwm.util.Helper;
import password.pwm.util.JsonUtil;
import password.pwm.util.PwmRandom;
import password.pwm.util.SecureHelper;
import password.pwm.util.logging.PwmLogger;
import password.pwm.ws.server.RestResultBean;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.URI;
import java.util.*;

public class PwmRequest extends PwmHttpRequestWrapper implements Serializable {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.forClass(PwmRequest.class);

    private static final Set<String> HTTP_DEBUG_STRIP_VALUES = new HashSet<>(
            Arrays.asList(new String[] {
                    "password",
                    PwmConstants.PARAM_TOKEN,
                    PwmConstants.PARAM_RESPONSE_PREFIX,
            }));

    private final PwmResponse pwmResponse;
    private transient PwmApplication pwmApplication;
    private transient PwmSession pwmSession;
    private final String cspNonce;

    private final Set<Flag> flags = new HashSet<>();

    public enum Flag {
        HIDE_LOCALE,
        HIDE_IDLE,
        HIDE_THEME,
        HIDE_FOOTER_TEXT,
        HIDE_HEADER_BUTTONS,
        HIDE_HEADER_WARNINGS,
        NO_REQ_COUNTER,
        NO_IDLE_TIMEOUT,
        ALWAYS_EXPAND_MESSAGE_TEXT,
    }

    public static PwmRequest forRequest(
            HttpServletRequest request,
            HttpServletResponse response
    )
            throws PwmUnrecoverableException
    {
        PwmRequest pwmRequest = (PwmRequest) request.getAttribute(PwmConstants.REQUEST_ATTR.PwmRequest.toString());
        if (pwmRequest == null) {
            final PwmSession pwmSession = PwmSessionWrapper.readPwmSession(request);
            final PwmApplication pwmApplication = ContextManager.getPwmApplication(request);
            pwmRequest = new PwmRequest(request, response, pwmApplication, pwmSession);
            request.setAttribute(PwmConstants.REQUEST_ATTR.PwmRequest.toString(), pwmRequest);
        }
        return pwmRequest;
    }

    private PwmRequest(
            final HttpServletRequest httpServletRequest,
            final HttpServletResponse httpServletResponse,
            final PwmApplication pwmApplication,
            final PwmSession pwmSession
    )
            throws PwmUnrecoverableException
    {
        super(httpServletRequest, pwmApplication.getConfig());
        this.pwmResponse = new PwmResponse(httpServletResponse, this, pwmApplication.getConfig());
        this.pwmSession = pwmSession;
        this.pwmApplication = pwmApplication;
        this.cspNonce = PwmRandom.getInstance().alphaNumericString(10);
    }

    public PwmApplication getPwmApplication()
    {
        return pwmApplication;
    }

    public PwmSession getPwmSession()
    {
        return pwmSession;
    }

    public SessionLabel getSessionLabel() {
        return pwmSession.getLabel();
    }

    public PwmResponse getPwmResponse()
    {
        return pwmResponse;
    }

    public Locale getLocale() {
        return pwmSession.getSessionStateBean().getLocale();
    }

    public Configuration getConfig() {
        return pwmApplication.getConfig();
    }


    public void forwardToJsp(final PwmConstants.JSP_URL jspURL)
            throws ServletException, IOException, PwmUnrecoverableException
    {
        this.getPwmResponse().forwardToJsp(jspURL);
    }

    public void respondWithError(final ErrorInformation errorInformation)
            throws IOException, ServletException
    {
        respondWithError(errorInformation, true);
    }

    public void respondWithError(
            final ErrorInformation errorInformation,
            final boolean forceLogout
    )
            throws IOException, ServletException
    {
        LOGGER.error(this.getSessionLabel() ,errorInformation);

        if (isJsonRequest()) {
            outputJsonResult(RestResultBean.fromError(errorInformation, this));
            return;
        }

        this.setResponseError(errorInformation);

        try {
            forwardToJsp(PwmConstants.JSP_URL.ERROR);
            if (forceLogout) {
                pwmSession.unauthenticateUser();
            }
        } catch (PwmUnrecoverableException e) {
            LOGGER.error("unexpected error sending user to error page: " + e.toString());
        }
    }

    public void forwardToSuccessPage(Message message)
            throws ServletException, PwmUnrecoverableException, IOException
    {
        forwardToSuccessPage(message, null);
    }

    public void forwardToSuccessPage(Message message, final String field)
            throws ServletException, PwmUnrecoverableException, IOException
    {
        getPwmResponse().forwardToSuccessPage(message, field);
    }


    public void sendRedirect(final String redirectURL)
            throws PwmUnrecoverableException, IOException
    {
        getPwmResponse().sendRedirect(redirectURL);
    }

    public void sendRedirectToContinue()
            throws PwmUnrecoverableException, IOException
    {
        final String redirectURL = PwmConstants.URL_SERVLET_COMMAND + "?" + PwmConstants.PARAM_ACTION_REQUEST + "=continue&pwmFormID="
                
                + Helper.buildPwmFormID(pwmSession.getSessionStateBean());

        sendRedirect(redirectURL);
    }


    public void outputJsonResult(final RestResultBean restResultBean)
            throws IOException
    {
        this.getPwmResponse().outputJsonResult(restResultBean);
    }

    public ContextManager getContextManager()
            throws PwmUnrecoverableException
    {
        return ContextManager.getContextManager(this);
    }

    public Map<String,FileUploadItem> readFileUploads(
            final int maxFileSize,
            final int maxItems
    )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        final Map<String,FileUploadItem> returnObj = new LinkedHashMap<>();
        try {
            if (ServletFileUpload.isMultipartContent(this.getHttpServletRequest())) {
                final byte[] buffer = new byte[1024];

                final ServletFileUpload upload = new ServletFileUpload();
                final FileItemIterator iter = upload.getItemIterator(this.getHttpServletRequest());
                while (iter.hasNext() && returnObj.size() < maxItems) {
                    final FileItemStream item = iter.next();
                    final InputStream inputStream = item.openStream();
                    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    int length;
                    while ((length = inputStream.read(buffer)) > 0) {
                        baos.write(buffer, 0, length);
                        if (baos.size() > maxFileSize) {
                            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_UNKNOWN,"upload file size limit exceeded"));
                        }
                    }
                    final byte[] outputFile = baos.toByteArray();
                    final FileUploadItem fileUploadItem = new FileUploadItem(
                            item.getName(),
                            item.getContentType(),
                            outputFile
                    );
                    returnObj.put(item.getFieldName(),fileUploadItem);
                }
            }
        } catch (Exception e) {
            LOGGER.error("error reading file upload: " + e.getMessage());
        }
        return Collections.unmodifiableMap(returnObj);
    }

    public static class FileUploadItem {
        private final String name;
        private final String type;
        private final byte[] content;

        public FileUploadItem(
                String name,
                String type,
                byte[] content
        )
        {
            this.name = name;
            this.type = type;
            this.content = content;
        }

        public String getName()
        {
            return name;
        }

        public String getType()
        {
            return type;
        }

        public byte[] getContent()
        {
            return content;
        }
    }

    public UserIdentity getUserInfoIfLoggedIn() {
        return this.getPwmSession().getSessionStateBean().isAuthenticated()
                ? this.getPwmSession().getUserInfoBean().getUserIdentity()
                : null;
    }



    public void validatePwmFormID()
            throws PwmUnrecoverableException
    {
        Validator.validatePwmFormID(this);
    }

    public boolean convertURLtokenCommand()
            throws IOException, PwmUnrecoverableException
    {
        final String uri = this.getHttpServletRequest().getRequestURI();
        if (uri == null || uri.length() < 1) {
            return false;
        }
        final String servletPath = this.getHttpServletRequest().getServletPath();
        if (!uri.contains(servletPath)) {
            LOGGER.error("unexpected uri handler, uri '" + uri + "' does not contain servlet path '" + servletPath + "'");
            return false;
        }

        String aftPath = uri.substring(uri.indexOf(servletPath) + servletPath.length(),uri.length());
        if (aftPath.startsWith("/")) {
            aftPath = aftPath.substring(1,aftPath.length());
        }

        if (aftPath.contains("?")) {
            aftPath = aftPath.substring(0,aftPath.indexOf("?"));
        }

        if (aftPath.contains("&")) {
            aftPath = aftPath.substring(0,aftPath.indexOf("?"));
        }

        if (aftPath.length() <= 1) {
            return false;
        }

        final String tokenValue = aftPath; // note this value is still urlencoded - the servlet container does not decode path values.

        final StringBuilder redirectURL = new StringBuilder();
        redirectURL.append(this.getHttpServletRequest().getContextPath());
        redirectURL.append(this.getHttpServletRequest().getServletPath());
        redirectURL.append("?");
        redirectURL.append(PwmConstants.PARAM_ACTION_REQUEST).append("=enterCode");
        redirectURL.append("&");
        redirectURL.append(PwmConstants.PARAM_TOKEN).append("=").append(tokenValue);

        LOGGER.debug(pwmSession, "detected long servlet url, redirecting user to " + redirectURL);
        sendRedirect(redirectURL.toString());
        return true;
    }



    public void setResponseError(final ErrorInformation errorInformation) {
        setAttribute(PwmConstants.REQUEST_ATTR.PwmErrorInfo, errorInformation);
    }

    public void setAttribute(final PwmConstants.REQUEST_ATTR name, final Serializable value) {
        this.getHttpServletRequest().setAttribute(name.toString(),value);
    }

    public Serializable getAttribute(final PwmConstants.REQUEST_ATTR name) {
        return (Serializable)this.getHttpServletRequest().getAttribute(name.toString());
    }

    public PwmURL getURL() {
        return new PwmURL(this.getHttpServletRequest());
    }

    public void markPreLoginUrl()
    {
        final String originalRequestedUrl = this.getURLwithQueryString();
        if (pwmSession.getSessionStateBean().getOriginalRequestURL() == null) {
            LOGGER.trace(this, "noted originally requested url as: " + originalRequestedUrl);
            pwmSession.getSessionStateBean().setOriginalRequestURL(originalRequestedUrl);
        }
    }

    public void sendRedirectToPreLoginUrl()
            throws IOException, PwmUnrecoverableException
    {
        LOGGER.trace(this, "redirecting user to originally requested (pre-authentication) url: ");
        sendRedirect(determinePostLoginUrl());
    }

    public String determinePostLoginUrl() {
        final String originalURL = this.getPwmSession().getSessionStateBean().getOriginalRequestURL();
        this.getPwmSession().getSessionStateBean().setOriginalRequestURL(null);

        if (originalURL != null && !originalURL.isEmpty()) {
            final PwmURL originalPwmURL = new PwmURL(URI.create(originalURL),this.getContextPath());
            if (!originalPwmURL.isLoginServlet()) {
                return originalURL;
            }
        }
        return this.getContextPath();
    }


    public void debugHttpRequestToLog()
            throws PwmUnrecoverableException
    {
        debugHttpRequestToLog(null);
    }

    public void debugHttpRequestToLog(final String extraText)
            throws PwmUnrecoverableException
    {

        final StringBuilder sb = new StringBuilder();
        final HttpServletRequest req = this.getHttpServletRequest();

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

            final Map<String,List<String>> valueMap = this.readMultiParametersAsMap();
            for (final String paramName : valueMap.keySet()) {
                for (final String paramValue : valueMap.get(paramName)) {
                    sb.append("  ").append(paramName).append("=");
                    boolean strip = false;
                    for (final String stripValue : HTTP_DEBUG_STRIP_VALUES) {
                        if (paramName.toLowerCase().contains(stripValue.toLowerCase())) {
                            strip = true;
                        }
                    }
                    if (strip) {
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
        LOGGER.trace(this.getSessionLabel(), sb.toString());
    }

    public boolean isAuthenticated() {
        return pwmSession.getSessionStateBean().isAuthenticated();
    }

    public boolean isForcedPageView() {
        if (!isAuthenticated()) {
            return false;
        }

        final PwmURL pwmURL = getURL();
        final UserInfoBean userInfoBean = pwmSession.getUserInfoBean();

        if (userInfoBean.isRequiresNewPassword() && pwmURL.isChangePasswordURL()) {
            return true;
        }

        if (userInfoBean.isRequiresResponseConfig() && pwmURL.isSetupResponsesURL()) {
            return true;
        }

        if (userInfoBean.isRequiresOtpConfig() && pwmURL.isSetupOtpSecretURL()) {
            return true;
        }

        if (userInfoBean.isRequiresUpdateProfile() && pwmURL.isProfileUpdateURL()) {
            return true;
        }

        return false;
    }

    public void setFlag(final Flag flag, final boolean status) {
        if (status) {
            flags.add(flag);
        } else {
            flags.remove(flag);
        }
    }

    public boolean isFlag(final Flag flag) {
        return flags.contains(flag);
    }

    public boolean hasForwardUrl() {
        final SessionStateBean ssBean = this.getPwmSession().getSessionStateBean();
        final String redirectURL = ssBean.getForwardURL();
        return !((redirectURL == null || redirectURL.isEmpty()) && this.getConfig().isDefaultValue(PwmSetting.URL_FORWARD));
    }

    public String getForwardUrl() {
        final SessionStateBean ssBean = this.getPwmSession().getSessionStateBean();
        String redirectURL = ssBean.getForwardURL();
        if (redirectURL == null || redirectURL.length() < 1) {
            redirectURL = this.getConfig().readSettingAsString(PwmSetting.URL_FORWARD);
        }

        if (redirectURL == null || redirectURL.length() < 1) {
            redirectURL = this.getContextPath();
        }

        return redirectURL;
    }

    public String getLogoutURL(
    ) {
        final SessionStateBean ssBean = this.getPwmSession().getSessionStateBean();
        return ssBean.getLogoutURL() == null ? pwmApplication.getConfig().readSettingAsString(PwmSetting.URL_LOGOUT) : ssBean.getLogoutURL();
    }

    public String getCspNonce()
    {
        return cspNonce;
    }
    //        public static <E extends Enum<E>> E valueToEnum(final PwmSetting setting, StoredValue value, Class<E> enumClass) {

    public <T extends Serializable> T readCookie(final String cookieName, Class<T> returnClass) 
            throws PwmUnrecoverableException 
    {
        final String strValue = this.readCookie(cookieName);

        if (strValue != null && !strValue.isEmpty()) {
            final String decryptedCookie = SecureHelper.decryptStringValue(strValue, getConfig().getSecurityKey(), true);
            return JsonUtil.deserialize(decryptedCookie, returnClass);
        }
        
        return null;
    }
    
    public String toString() {
        return this.getClass().getSimpleName() + " "
                + (this.getSessionLabel() == null ? "" : getSessionLabel().toString())
                + " " + this.getHttpServletRequest().getRequestURI();
        
    }

    public void addFormInfoToRequestAttr(
            final PwmSetting formSetting,
            final boolean readOnly,
            final boolean showPasswordFields
    ) {
        final ArrayList<FormConfiguration> formConfiguration = new ArrayList<>(this.getConfig().readSettingAsForm(formSetting));
        addFormInfoToRequestAttr(formConfiguration,null,readOnly,showPasswordFields);
        
    }
    public void addFormInfoToRequestAttr(
            final List<FormConfiguration> formConfiguration,
            final Map<FormConfiguration, String> formDataMap,
            final boolean readOnly,
            final boolean showPasswordFields
    ) {
        final LinkedHashMap<FormConfiguration,String> formDataMapValue = formDataMap == null 
                ? new LinkedHashMap<FormConfiguration,String>() 
                : new LinkedHashMap<>(formDataMap);
        
        this.setAttribute(PwmConstants.REQUEST_ATTR.FormConfiguration, new ArrayList<>(formConfiguration));
        this.setAttribute(PwmConstants.REQUEST_ATTR.FormData, formDataMapValue);
        this.setAttribute(PwmConstants.REQUEST_ATTR.FormReadOnly, readOnly);
        this.setAttribute(PwmConstants.REQUEST_ATTR.FormShowPasswordFields, showPasswordFields);
    }

    public void invalidateSession() {
        this.getHttpServletRequest().getSession().invalidate();
    }

    public String getURLwithQueryString() {
        final HttpServletRequest req = this.getHttpServletRequest();
        final String queryString = req.getQueryString();
        if (queryString != null && !queryString.isEmpty()) {
            return req.getRequestURI() + '?' + queryString;
        } else {
            return req.getRequestURI();
        }
    }
}
