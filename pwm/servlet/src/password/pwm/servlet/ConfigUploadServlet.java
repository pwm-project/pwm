/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2011 The PWM Project
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

package password.pwm.servlet;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import password.pwm.ContextManager;
import password.pwm.PwmApplication;
import password.pwm.PwmSession;
import password.pwm.bean.ConfigManagerBean;
import password.pwm.config.Message;
import password.pwm.config.StoredConfiguration;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.PwmLogger;
import password.pwm.util.ServletHelper;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class ConfigUploadServlet extends TopServlet {
    private static final PwmLogger LOGGER = PwmLogger.getLogger(ConfigUploadServlet.class);
    private static final int MAX_UPLOAD_CHARS = 1024 * 50;

    protected void processRequest(final HttpServletRequest req, final HttpServletResponse resp)
            throws ServletException, IOException, ChaiUnavailableException, PwmUnrecoverableException
    {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
        final ConfigManagerBean configManagerBean = pwmSession.getConfigManagerBean();

        boolean success = false;
        if (ServletFileUpload.isMultipartContent(req)) {
            final String uploadedFile = getUploadedFile(req);
            if (uploadedFile != null && uploadedFile.length() > 0) {
                try {
                    final StoredConfiguration storedConfig = StoredConfiguration.fromXml(uploadedFile);
                    if (storedConfig != null) {
                        configManagerBean.setConfiguration(storedConfig);
                        LOGGER.trace(pwmSession, "read config from file: " + storedConfig.toString());
                        success = true;
                    }
                } catch (PwmUnrecoverableException e) {
                    pwmSession.getSessionStateBean().setSessionError(new ErrorInformation(PwmError.CONFIG_UPLOAD_FAILURE, "error reading config file", e.getMessage()));
                    LOGGER.error(pwmSession, "error reading config input file: " + e.getMessage());
                }
            } else {
                pwmSession.getSessionStateBean().setSessionError(new ErrorInformation(PwmError.CONFIG_UPLOAD_FAILURE, "error reading config file", "unable to read config file"));
            }
        }

        if (!success) {
            if (pwmSession.getSessionStateBean().getSessionError() == null) {
                pwmSession.getSessionStateBean().setSessionError(new ErrorInformation(PwmError.CONFIG_UPLOAD_FAILURE, "error reading config file", "unknown error"));
            }
            LOGGER.info(pwmSession, "unable to read uploaded file");
            ConfigManagerServlet.forwardToJSP(req,resp);
            return;
        }

        pwmSession.getSessionStateBean().setSessionError(null);
        pwmSession.getSessionStateBean().setForwardURL(req.getContextPath() + "/config/ConfigManager");
        if (pwmApplication.getConfigMode() == PwmApplication.MODE.RUNNING) {
            configManagerBean.setEditMode(ConfigManagerServlet.EDIT_MODE.SETTINGS);
            pwmSession.getSessionStateBean().setSessionSuccess(Message.SUCCESS_CONFIG_UPLOAD,"");
        } else {
            pwmSession.getSessionStateBean().setSessionSuccess(Message.SUCCESS_CONFIG_UPLOAD,"Please wait a moment for PWM to restart.");
            ConfigManagerServlet.saveConfiguration(pwmSession,pwmApplication,req.getSession().getServletContext());
        }

        ServletHelper.forwardToSuccessPage(req,resp);
    }

    private String getUploadedFile(
            final HttpServletRequest req
    )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        try {
            if (ServletFileUpload.isMultipartContent(req)) {

                // Create a new file upload handler
                final ServletFileUpload upload = new ServletFileUpload();

                boolean pwmFormIDvalidated = false;
                String uploadFile = null;

                // Parse the request
                for (final FileItemIterator iter = upload.getItemIterator(req); iter.hasNext();) {
                    final FileItemStream item = iter.next();

                    if ("uploadFile".equals(item.getFieldName())) {
                        uploadFile = streamToString(item.openStream());
                    } else if ("pwmFormID".equals(item.getFieldName())) {
                        final String formNonce = PwmSession.getPwmSession(req).getSessionStateBean().getSessionVerificationKey();
                        final String inputpwmFormID = streamToString(item.openStream());
                        if (formNonce.equals(inputpwmFormID)) {
                            pwmFormIDvalidated = true;
                        }
                    }
                }

                if (!pwmFormIDvalidated) {
                    LOGGER.warn(PwmSession.getPwmSession(req), "form submitted with incorrect or missing pwmFormID value");
                    throw new PwmUnrecoverableException(PwmError.ERROR_INVALID_FORMID);
                }

                return uploadFile;
            }
        } catch (Exception e) {
            LOGGER.error("error reading file upload: " + e.getMessage());
        }
        return null;
    }

    public String streamToString(final InputStream stream)
            throws IOException
    {
        final BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(stream,"UTF-8"));
        final StringBuilder sb = new StringBuilder();
        int charCounter = 0;
        int nextChar = bufferedReader.read();
        while (charCounter < MAX_UPLOAD_CHARS && nextChar != -1) {
            charCounter++;
            sb.append((char)nextChar);
            nextChar = bufferedReader.read();
        }
        return sb.toString();
    }
}