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

package password.pwm.http;

import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.IOUtils;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.util.Validator;
import password.pwm.bean.LocalSessionStateBean;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.Configuration;
import password.pwm.config.FormConfiguration;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.servlet.PwmServletDefinition;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmRandom;
import password.pwm.ws.server.RestResultBean;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
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

    private final Set<PwmRequestFlag> flags = new HashSet<>();

    public static PwmRequest forRequest(
            HttpServletRequest request,
            HttpServletResponse response
    )
            throws PwmUnrecoverableException
    {
        PwmRequest pwmRequest = (PwmRequest) request.getAttribute(Attribute.PwmRequest.toString());
        if (pwmRequest == null) {
            final PwmSession pwmSession = PwmSessionWrapper.readPwmSession(request);
            final PwmApplication pwmApplication = ContextManager.getPwmApplication(request);
            pwmRequest = new PwmRequest(request, response, pwmApplication, pwmSession);
            request.setAttribute(Attribute.PwmRequest.toString(), pwmRequest);
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
        if (forceLogout) {
            getPwmResponse().respondWithError(errorInformation, PwmResponse.Flag.ForceLogout);
        } else {
            getPwmResponse().respondWithError(errorInformation);
        }
    }

    public void sendRedirect(final String redirectURL)
            throws PwmUnrecoverableException, IOException
    {
        getPwmResponse().sendRedirect(redirectURL);
    }

    public void sendRedirect(final PwmServletDefinition pwmServletDefinition)
            throws PwmUnrecoverableException, IOException
    {
        getPwmResponse().sendRedirect(this.getContextPath() + pwmServletDefinition.servletUrl());
    }

    public void sendRedirectToContinue()
            throws PwmUnrecoverableException, IOException
    {
        String redirectURL = this.getContextPath() + PwmServletDefinition.Command.servletUrl();
        redirectURL = PwmURL.appendAndEncodeUrlParameters(redirectURL, Collections.singletonMap(PwmConstants.PARAM_ACTION_REQUEST, "continue"));
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

    public InputStream readFileUploadStream(final String filePartName)
            throws IOException, ServletException, PwmUnrecoverableException
    {
        try {
            if (ServletFileUpload.isMultipartContent(this.getHttpServletRequest())) {

                // Create a new file upload handler
                final ServletFileUpload upload = new ServletFileUpload();

                // Parse the request
                for (final FileItemIterator iter = upload.getItemIterator(this.getHttpServletRequest()); iter.hasNext();) {
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

    public Map<String,FileUploadItem> readFileUploads(
            final int maxFileSize,
            final int maxItems
    )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        final Map<String,FileUploadItem> returnObj = new LinkedHashMap<>();
        try {
            if (ServletFileUpload.isMultipartContent(this.getHttpServletRequest())) {
                final ServletFileUpload upload = new ServletFileUpload();
                final FileItemIterator iter = upload.getItemIterator(this.getHttpServletRequest());
                while (iter.hasNext() && returnObj.size() < maxItems) {
                    final FileItemStream item = iter.next();
                    final InputStream inputStream = item.openStream();
                    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    final long length = IOUtils.copyLarge(inputStream, baos, 0, maxFileSize + 1);
                    if (length > maxFileSize) {
                        final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN,"upload file size limit exceeded");
                        LOGGER.error(this, errorInformation);
                        respondWithError(errorInformation);
                        return Collections.emptyMap();
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

    public enum Attribute {
        PwmErrorInfo,
        SuccessMessage,
        PwmRequest,
        OriginalUri,
        AgreementText,
        CompleteText,
        AvailableAuthMethods,
        ConfigurationSummaryOutput,
        LdapPermissionItems,
        PageTitle,
        ModuleBean,
        ModuleBean_String,

        FormConfiguration,
        FormReadOnly,
        FormShowPasswordFields,
        FormData,

        SetupResponses_ResponseInfo,

        HelpdeskDetail,
        HelpdeskObfuscatedDN,
        HelpdeskUsername,
        HelpdeskVerificationEnabled,

        ConfigFilename,
        ConfigLastModified,
        ConfigHasPassword,
        ConfigPasswordRememberTime,
        ConfigLoginHistory,
        ApplicationPath,

        CaptchaClientUrl,
        CaptchaIframeUrl,
        CaptchaPublicKey,

        ForgottenPasswordChallengeSet,
        ForgottenPasswordOptionalPageView,
        ForgottenPasswordPrompts,
        ForgottenPasswordInstructions,

        GuestCurrentExpirationDate,
        GuestMaximumExpirationDate,
        GuestMaximumValidDays,

        NewUser_FormShowBackButton,

        CookieBeanStorage,
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
        return this.getPwmSession().isAuthenticated()
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
        final String uri = getURLwithoutQueryString();
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
        setAttribute(Attribute.PwmErrorInfo, errorInformation);
    }

    public void setAttribute(final Attribute name, final Serializable value) {
        this.getHttpServletRequest().setAttribute(name.toString(),value);
    }

    public Serializable getAttribute(final Attribute name) {
        return (Serializable)this.getHttpServletRequest().getAttribute(name.toString());
    }

    public PwmURL getURL() {
        return new PwmURL(this.getHttpServletRequest());
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
        sb.append(getURLwithoutQueryString());

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
                        sb.append("'");
                        sb.append(paramValue);
                        sb.append("'");
                    }

                    sb.append("\n");
                }
            }

            sb.deleteCharAt(sb.length() - 1);
        }
        LOGGER.trace(this.getSessionLabel(), sb.toString());
    }

    public boolean isAuthenticated() {
        return pwmSession.isAuthenticated();
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

    public void setFlag(final PwmRequestFlag flag, final boolean status) {
        if (status) {
            flags.add(flag);
        } else {
            flags.remove(flag);
        }
    }

    public boolean isFlag(final PwmRequestFlag flag) {
        return flags.contains(flag);
    }

    public boolean hasForwardUrl() {
        final LocalSessionStateBean ssBean = this.getPwmSession().getSessionStateBean();
        final String redirectURL = ssBean.getForwardURL();
        return !((redirectURL == null || redirectURL.isEmpty()) && this.getConfig().isDefaultValue(PwmSetting.URL_FORWARD));
    }

    public String getForwardUrl() {
        final LocalSessionStateBean ssBean = this.getPwmSession().getSessionStateBean();
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
        final LocalSessionStateBean ssBean = this.getPwmSession().getSessionStateBean();
        return ssBean.getLogoutURL() == null ? pwmApplication.getConfig().readSettingAsString(PwmSetting.URL_LOGOUT) : ssBean.getLogoutURL();
    }

    public String getCspNonce()
    {
        return cspNonce;
    }

    public <T extends Serializable> T readEncryptedCookie(final String cookieName, Class<T> returnClass)
            throws PwmUnrecoverableException
    {
        final String strValue = this.readCookie(cookieName);

        if (strValue != null && !strValue.isEmpty()) {
            return pwmApplication.getSecureService().decryptObject(strValue, returnClass);
        }

        return null;
    }

    public String toString() {
        return this.getClass().getSimpleName() + " "
                + (this.getSessionLabel() == null ? "" : getSessionLabel().toString())
                + " " + getURLwithoutQueryString();

    }

    public void addFormInfoToRequestAttr(
            final PwmSetting formSetting,
            final boolean readOnly,
            final boolean showPasswordFields
    ) {
        final ArrayList<FormConfiguration> formConfiguration = new ArrayList<>(this.getConfig().readSettingAsForm(formSetting));
        addFormInfoToRequestAttr(formConfiguration, null, readOnly, showPasswordFields);

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

        this.setAttribute(Attribute.FormConfiguration, new ArrayList<>(formConfiguration));
        this.setAttribute(Attribute.FormData, formDataMapValue);
        this.setAttribute(Attribute.FormReadOnly, readOnly);
        this.setAttribute(Attribute.FormShowPasswordFields, showPasswordFields);
    }

    public void invalidateSession() {
        this.getPwmSession().unauthenticateUser(this);
        this.getHttpServletRequest().getSession().invalidate();
    }

    public String getURLwithQueryString() throws PwmUnrecoverableException {
        final HttpServletRequest req = this.getHttpServletRequest();
        return PwmURL.appendAndEncodeUrlParameters(getURLwithoutQueryString(), readParametersAsMap());
    }

    public String getURLwithoutQueryString() {
        final HttpServletRequest req = this.getHttpServletRequest();
        String requestUri = (String) req.getAttribute("javax.servlet.forward.request_uri");
        return (requestUri == null) ? req.getRequestURI() : requestUri;
    }

    public String debugHttpHeaders() {
        final String LINE_SEPERATOR = "\n";
        final StringBuilder sb = new StringBuilder();


        sb.append("http").append(getHttpServletRequest().isSecure() ? "s " : " non-").append("secure request headers: ");
        sb.append(LINE_SEPERATOR);

        final Map<String,List<String>> headerMap = readHeaderValuesMap();
        for (final String headerName : headerMap.keySet()) {
            for (final String value : headerMap.get(headerName)) {
                sb.append("  ");
                sb.append(headerName);
                sb.append("=");
                if (headerName.contains("Authorization")) {
                    sb.append(PwmConstants.LOG_REMOVED_VALUE_REPLACEMENT);
                } else {
                    sb.append(value);
                }
                sb.append(LINE_SEPERATOR);
            }
        }

        if (LINE_SEPERATOR.equals(sb.substring(sb.length() - LINE_SEPERATOR.length(), sb.length()))) {
            sb.delete(sb.length() - LINE_SEPERATOR.length(), sb.length());
        }

        return sb.toString();
    }
}
