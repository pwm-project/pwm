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

package password.pwm.http.servlet.configmanager;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.config.Configuration;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.*;
import password.pwm.http.servlet.AbstractPwmServlet;
import password.pwm.i18n.Message;
import password.pwm.util.TimeDuration;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBFactory;
import password.pwm.util.localdb.LocalDBUtility;
import password.pwm.util.logging.PwmLogLevel;
import password.pwm.util.logging.PwmLogger;
import password.pwm.ws.server.RestResultBean;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import java.io.*;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;

@WebServlet(
        name = "ConfigManagerLocalDBServlet",
        urlPatterns = {
                PwmConstants.URL_PREFIX_PRIVATE + "/config/manager/localdb",
        }
)
public class ConfigManagerLocalDBServlet extends AbstractPwmServlet {
    final static private PwmLogger LOGGER = PwmLogger.forClass(ConfigManagerLocalDBServlet.class);

    public enum ConfigManagerAction implements ProcessAction {
        exportLocalDB(HttpMethod.GET),
        importLocalDB(HttpMethod.POST),

        ;

        private final HttpMethod method;

        ConfigManagerAction(HttpMethod method)
        {
            this.method = method;
        }

        public Collection<HttpMethod> permittedMethods()
        {
            return Collections.singletonList(method);
        }
    }

    protected ConfigManagerAction readProcessAction(final PwmRequest request)
            throws PwmUnrecoverableException
    {
        try {
            return ConfigManagerAction.valueOf(request.readParameterAsString(PwmConstants.PARAM_ACTION_REQUEST));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    protected void processAction(final PwmRequest pwmRequest)
            throws ServletException, IOException, ChaiUnavailableException, PwmUnrecoverableException
    {

        final ConfigManagerAction processAction = readProcessAction(pwmRequest);
        if (processAction != null) {
            switch (processAction) {
                case exportLocalDB:
                    doExportLocalDB(pwmRequest);
                    break;

                case importLocalDB:
                    restUploadLocalDB(pwmRequest);
                    return;


            }
            return;
        }

        pwmRequest.forwardToJsp(PwmConstants.JSP_URL.CONFIG_MANAGER_LOCALDB);
    }

    private void doExportLocalDB(final PwmRequest pwmRequest)
            throws IOException, ServletException, PwmUnrecoverableException
    {
        final PwmResponse resp = pwmRequest.getPwmResponse();
        final Date startTime = new Date();
        resp.setHeader(PwmConstants.HttpHeader.ContentDisposition, "attachment;filename=" + PwmConstants.PWM_APP_NAME + "-LocalDB.bak");
        resp.setContentType(PwmConstants.ContentTypeValue.octetstream);
        resp.setHeader(PwmConstants.HttpHeader.ContentTransferEncoding, "binary");
        final LocalDBUtility localDBUtility = new LocalDBUtility(pwmRequest.getPwmApplication().getLocalDB());
        try {
            final int bufferSize = Integer.parseInt(pwmRequest.getConfig().readAppProperty(AppProperty.HTTP_DOWNLOAD_BUFFER_SIZE));
            final OutputStream bos = new BufferedOutputStream(resp.getOutputStream(),bufferSize);
            localDBUtility.exportLocalDB(bos, LOGGER.asAppendable(PwmLogLevel.DEBUG, pwmRequest.getSessionLabel()), false);
            LOGGER.debug(pwmRequest, "completed localDBExport process in " + TimeDuration.fromCurrent(startTime).asCompactString());
        } catch (Exception e) {
            LOGGER.error(pwmRequest, "error downloading export localdb: " + e.getMessage());
        }
    }

    void restUploadLocalDB(final PwmRequest pwmRequest)
            throws IOException, ServletException, PwmUnrecoverableException

    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final HttpServletRequest req = pwmRequest.getHttpServletRequest();

        if (pwmApplication.getApplicationMode() == PwmApplication.MODE.RUNNING) {
            final String errorMsg = "database upload is not permitted when in running mode";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.CONFIG_UPLOAD_FAILURE,errorMsg,new String[]{errorMsg});
            pwmRequest.respondWithError(errorInformation, true);
            return;
        }

        if (!ServletFileUpload.isMultipartContent(req)) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN,"no file found in upload");
            pwmRequest.outputJsonResult(RestResultBean.fromError(errorInformation, pwmRequest));
            LOGGER.error(pwmRequest, "error during database import: " + errorInformation.toDebugStr());
            return;
        }

        final InputStream inputStream = ServletHelper.readFileUpload(pwmRequest.getHttpServletRequest(), "uploadFile");

        final ContextManager contextManager = ContextManager.getContextManager(pwmRequest);
        LocalDB localDB = null;
        try {
            final File localDBLocation = pwmApplication.getLocalDB().getFileLocation();
            final Configuration configuration = pwmApplication.getConfig();
            contextManager.shutdown();

            localDB = LocalDBFactory.getInstance(localDBLocation, false, null, configuration);
            final LocalDBUtility localDBUtility = new LocalDBUtility(localDB);
            LOGGER.info(pwmRequest, "beginning LocalDB import");
            localDBUtility.importLocalDB(inputStream,
                    LOGGER.asAppendable(PwmLogLevel.DEBUG, pwmRequest.getSessionLabel()));
            LOGGER.info(pwmRequest, "completed LocalDB import");
        } catch (Exception e) {
            final ErrorInformation errorInformation = e instanceof PwmException
                    ? ((PwmException) e).getErrorInformation()
                    : new ErrorInformation(PwmError.ERROR_UNKNOWN,e.getMessage());
            pwmRequest.outputJsonResult(RestResultBean.fromError(errorInformation, pwmRequest));
            LOGGER.error(pwmRequest, "error during LocalDB import: " + errorInformation.toDebugStr());
            return;
        } finally {
            if (localDB != null) {
                try {
                    localDB.close();
                } catch (Exception e) {
                    LOGGER.error(pwmRequest, "error closing LocalDB after import process: " + e.getMessage());
                }
            }
            contextManager.initialize();
        }

        pwmRequest.outputJsonResult(RestResultBean.forSuccessMessage(pwmRequest, Message.Success_Unknown));
    }


}

