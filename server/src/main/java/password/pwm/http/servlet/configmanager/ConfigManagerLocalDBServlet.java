/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2019 The PWM Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package password.pwm.http.servlet.configmanager;

import org.apache.commons.fileupload.servlet.ServletFileUpload;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmApplicationMode;
import password.pwm.PwmConstants;
import password.pwm.config.Configuration;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.ContextManager;
import password.pwm.http.HttpContentType;
import password.pwm.http.HttpHeader;
import password.pwm.http.HttpMethod;
import password.pwm.http.JspUrl;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmResponse;
import password.pwm.http.servlet.AbstractPwmServlet;
import password.pwm.i18n.Message;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBFactory;
import password.pwm.util.localdb.LocalDBUtility;
import password.pwm.util.logging.PwmLogLevel;
import password.pwm.util.logging.PwmLogger;
import password.pwm.ws.server.RestResultBean;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;

@WebServlet(
        name = "ConfigManagerLocalDBServlet",
        urlPatterns = {
                PwmConstants.URL_PREFIX_PRIVATE + "/config/manager/localdb",
        }
)
public class ConfigManagerLocalDBServlet extends AbstractPwmServlet
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( ConfigManagerLocalDBServlet.class );

    public enum ConfigManagerAction implements ProcessAction
    {
        exportLocalDB( HttpMethod.GET ),
        importLocalDB( HttpMethod.POST ),;

        private final HttpMethod method;

        ConfigManagerAction( final HttpMethod method )
        {
            this.method = method;
        }

        public Collection<HttpMethod> permittedMethods( )
        {
            return Collections.singletonList( method );
        }
    }

    protected ConfigManagerAction readProcessAction( final PwmRequest request )
            throws PwmUnrecoverableException
    {
        try
        {
            return ConfigManagerAction.valueOf( request.readParameterAsString( PwmConstants.PARAM_ACTION_REQUEST ) );
        }
        catch ( final IllegalArgumentException e )
        {
            return null;
        }
    }

    protected void processAction( final PwmRequest pwmRequest )
            throws ServletException, IOException, PwmUnrecoverableException
    {
        ConfigManagerServlet.verifyConfigAccess( pwmRequest );

        final ConfigManagerAction processAction = readProcessAction( pwmRequest );
        if ( processAction != null )
        {
            switch ( processAction )
            {
                case exportLocalDB:
                    doExportLocalDB( pwmRequest );
                    break;

                case importLocalDB:
                    restUploadLocalDB( pwmRequest );
                    return;

                default:
                    JavaHelper.unhandledSwitchStatement( processAction );


            }
            return;
        }

        pwmRequest.forwardToJsp( JspUrl.CONFIG_MANAGER_LOCALDB );
    }

    private void doExportLocalDB( final PwmRequest pwmRequest )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        final PwmResponse resp = pwmRequest.getPwmResponse();
        final Instant startTime = Instant.now();
        resp.setHeader( HttpHeader.ContentDisposition, "attachment;filename=" + PwmConstants.PWM_APP_NAME + "-LocalDB.bak" );
        resp.setContentType( HttpContentType.octetstream );
        resp.setHeader( HttpHeader.ContentTransferEncoding, "binary" );
        final LocalDBUtility localDBUtility = new LocalDBUtility( pwmRequest.getPwmApplication().getLocalDB() );
        try
        {
            final int bufferSize = Integer.parseInt( pwmRequest.getConfig().readAppProperty( AppProperty.HTTP_DOWNLOAD_BUFFER_SIZE ) );
            final OutputStream bos = new BufferedOutputStream( resp.getOutputStream(), bufferSize );
            localDBUtility.exportLocalDB( bos, LOGGER.asAppendable( PwmLogLevel.DEBUG, pwmRequest.getLabel() ) );
            LOGGER.debug( pwmRequest, () -> "completed localDBExport process in " + TimeDuration.fromCurrent( startTime ).asCompactString() );
        }
        catch ( final Exception e )
        {
            LOGGER.error( pwmRequest, () -> "error downloading export localdb: " + e.getMessage() );
        }
    }

    void restUploadLocalDB( final PwmRequest pwmRequest )
            throws IOException, ServletException, PwmUnrecoverableException

    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final HttpServletRequest req = pwmRequest.getHttpServletRequest();

        if ( pwmApplication.getApplicationMode() == PwmApplicationMode.RUNNING )
        {
            final String errorMsg = "database upload is not permitted when in running mode";
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.CONFIG_UPLOAD_FAILURE, errorMsg, new String[]
                    {
                            errorMsg,
                    }
            );
            pwmRequest.respondWithError( errorInformation, true );
            return;
        }

        if ( !ServletFileUpload.isMultipartContent( req ) )
        {
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INTERNAL, "no file found in upload" );
            pwmRequest.outputJsonResult( RestResultBean.fromError( errorInformation, pwmRequest ) );
            LOGGER.error( pwmRequest, () -> "error during database import: " + errorInformation.toDebugStr() );
            return;
        }

        final InputStream inputStream = pwmRequest.readFileUploadStream( PwmConstants.PARAM_FILE_UPLOAD );

        final ContextManager contextManager = ContextManager.getContextManager( pwmRequest );
        LocalDB localDB = null;
        try
        {
            localDB = pwmApplication.getLocalDB();
            final File localDBLocation = pwmApplication.getLocalDB().getFileLocation();
            final Configuration configuration = pwmApplication.getConfig();
            contextManager.shutdown();

            localDB.close();
            localDB = LocalDBFactory.getInstance( localDBLocation, false, null, configuration );

            final LocalDBUtility localDBUtility = new LocalDBUtility( localDB );
            LOGGER.info( pwmRequest, () -> "beginning LocalDB import" );
            localDBUtility.importLocalDB( inputStream,
                    LOGGER.asAppendable( PwmLogLevel.DEBUG, pwmRequest.getLabel() ) );
            LOGGER.info( pwmRequest, () -> "completed LocalDB import" );
            localDB.close();
        }
        catch ( final Exception e )
        {
            final ErrorInformation errorInformation = e instanceof PwmException
                    ? ( ( PwmException ) e ).getErrorInformation()
                    : new ErrorInformation( PwmError.ERROR_INTERNAL, e.getMessage() );
            pwmRequest.outputJsonResult( RestResultBean.fromError( errorInformation, pwmRequest ) );
            LOGGER.error( pwmRequest, () -> "error during LocalDB import: " + errorInformation.toDebugStr() );
            return;
        }
        finally
        {
            if ( localDB != null )
            {
                try
                {
                    localDB.close();
                }
                catch ( final Exception e )
                {
                    LOGGER.error( pwmRequest, () -> "error closing LocalDB after import process: " + e.getMessage() );
                }
            }
            contextManager.initialize();
        }

        pwmRequest.outputJsonResult( RestResultBean.forSuccessMessage( pwmRequest, Message.Success_Unknown ) );
    }
}

