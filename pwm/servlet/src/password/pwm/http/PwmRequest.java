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

package password.pwm.http;

import com.google.gson.reflect.TypeToken;
import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.Validator;
import password.pwm.bean.SessionLabel;
import password.pwm.config.Configuration;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.filter.SessionFilter;
import password.pwm.util.Helper;
import password.pwm.util.JsonUtil;
import password.pwm.util.PwmLogger;
import password.pwm.util.ServletHelper;
import password.pwm.ws.server.RestResultBean;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.*;

public class PwmRequest implements Serializable {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(PwmRequest.class);

    private final HttpServletRequest httpServletRequest;
    private final HttpServletResponse httpServletResponse;
    private final PwmApplication pwmApplication;
    private final PwmSession pwmSession;

    public static PwmRequest forRequest(
            HttpServletRequest request,
            HttpServletResponse response
    )
            throws PwmUnrecoverableException
    {
        PwmRequest pwmRequest = (PwmRequest)request.getAttribute(PwmConstants.REQUEST_ATTR_PWM_REQUEST);
        if (pwmRequest == null) {
            pwmRequest = new PwmRequest(request, response);
            request.setAttribute(PwmConstants.REQUEST_ATTR_PWM_REQUEST,pwmRequest);
        }
        return pwmRequest;
    }

    private PwmRequest(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse)
            throws PwmUnrecoverableException
    {
        this.httpServletRequest = httpServletRequest;
        this.httpServletResponse = httpServletResponse;
        pwmSession = PwmSession.getPwmSession(httpServletRequest);
        pwmApplication = ContextManager.getPwmApplication(httpServletRequest);
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
        return pwmSession.getSessionLabel();
    }

    public HttpServletRequest getHttpServletRequest()
    {
        return httpServletRequest;
    }

    public HttpServletResponse getHttpServletResponse()
    {
        return httpServletResponse;
    }

    public Locale getLocale() {
        return pwmSession.getSessionStateBean().getLocale();
    }

    public Configuration getConfig() {
        return pwmApplication.getConfig();
    }

    public String readStringParameter(final String name)
            throws PwmUnrecoverableException
    {
        return Validator.readStringFromRequest(this.getHttpServletRequest(), name);
    }

    public void forwardToJsp(final PwmConstants.JSP_URL jspURL)
            throws ServletException, IOException, PwmUnrecoverableException
    {
        ServletHelper.forwardToJsp(this.getHttpServletRequest(), this.getHttpServletResponse(), jspURL);
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
        if (isJsonRequest()) {
            outputJsonResult(RestResultBean.fromError(errorInformation, this));
            return;
        }

        pwmSession.getSessionStateBean().setSessionError(errorInformation);

        try {
            forwardToJsp(PwmConstants.JSP_URL.ERROR);
            if (forceLogout) {
                pwmSession.unauthenticateUser();
            }
        } catch (PwmUnrecoverableException e) {
            LOGGER.error("unexpected error sending user to error page: " + e.toString());
        }
    }

    public void forwardToSuccessPage()
            throws ServletException, PwmUnrecoverableException, IOException
    {
        ServletHelper.forwardToSuccessPage(this.getHttpServletRequest(), this.getHttpServletResponse());
    }


    public String readRequestBody()
            throws IOException, PwmUnrecoverableException
    {
        final int maxChars = Integer.parseInt(this.getConfig().readAppProperty(AppProperty.HTTP_BODY_MAXREAD_LENGTH));
        return readRequestBody(maxChars);
    }

    public String readRequestBody(final int maxChars) throws IOException {
        final StringBuilder inputData = new StringBuilder();
        String line;
        try {
            final BufferedReader reader = this.getHttpServletRequest().getReader();
            while (((line = reader.readLine()) != null) && inputData.length() < maxChars) {
                inputData.append(line);
            }
        } catch (Exception e) {
            LOGGER.error("error reading request body stream: " + e.getMessage());
        }
        return inputData.toString();
    }

    public Map<String,String> readCookiePreferences() {
        Map<String,String> preferences = new HashMap<>();
        try {
            final String jsonString = ServletHelper.readCookie(this.getHttpServletRequest(), "preferences");
            preferences = JsonUtil.getGson().fromJson(jsonString, new TypeToken<Map<String, String>>() {
            }.getType());
        } catch (Exception e) {
            LOGGER.warn("error parsing cookie preferences: " + e.getMessage());
        }
        return Collections.unmodifiableMap(preferences);
    }

    public void sendRedirect(final String redirectURL)
            throws PwmUnrecoverableException, IOException
    {
        httpServletResponse.sendRedirect(SessionFilter.rewriteRedirectURL(redirectURL, httpServletRequest, httpServletResponse));
    }

    public void sendRedirectToContinue()
            throws PwmUnrecoverableException, IOException
    {
        final String redirectURL = PwmConstants.URL_SERVLET_COMMAND + "?" + PwmConstants.PARAM_ACTION_REQUEST + "=continue&pwmFormID="
                + Helper.buildPwmFormID(pwmSession.getSessionStateBean());
        sendRedirect(redirectURL);
    }

    public void recycleSessions()
            throws IOException, ServletException
    {
        ServletHelper.recycleSessions(pwmApplication, pwmSession, httpServletRequest, httpServletResponse);
    }

    public void outputJsonResult(final RestResultBean restResultBean)
            throws IOException
    {
        ServletHelper.outputJsonResult(this.httpServletResponse, restResultBean);
    }

    public boolean isJsonRequest() {
        final String acceptHeader = httpServletRequest.getHeader("Accept");
        return acceptHeader.contains("application/json");
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
}
