/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2021 The PWM Project
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

package password.pwm.receiver;

import password.pwm.bean.TelemetryPublishBean;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.ServletUtility;
import password.pwm.i18n.Message;
import password.pwm.util.java.AtomicLoopIntIncrementer;
import password.pwm.util.json.JsonFactory;
import password.pwm.ws.server.RestResultBean;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebServlet(
        urlPatterns = {
                "/telemetry",
        }
)

public class TelemetryRestReceiver extends HttpServlet
{
    private static final Logger LOGGER = Logger.createLogger( TelemetryViewerServlet.class );
    private static final AtomicLoopIntIncrementer REQ_COUNTER = new AtomicLoopIntIncrementer();


    @Override
    protected void doPost( final HttpServletRequest req, final HttpServletResponse resp )
            throws ServletException, IOException
    {
        try
        {
            final int requestId = REQ_COUNTER.next();
            LOGGER.debug( "http rest request #" + requestId + " for telemetry update" );

            final String input = ServletUtility.readRequestBodyAsString( req, 1024 * 1024 );
            final TelemetryPublishBean telemetryPublishBean = JsonFactory.get().deserialize( input, TelemetryPublishBean.class );
            final Storage storage = ContextManager.getContextManager( this.getServletContext() ).getApp().getStorage();
            storage.store( telemetryPublishBean );

            final RestResultBean restResultBean = RestResultBean.forSuccessMessage( null, null, null, Message.Success_Unknown );
            ReceiverUtil.outputJsonResponse( req, resp, restResultBean );
            LOGGER.debug( "http rest request #" + requestId + " received from " + telemetryPublishBean.getSiteDescription() );
        }
        catch ( final PwmUnrecoverableException e )
        {
            final RestResultBean restResultBean = RestResultBean.fromError( e.getErrorInformation() );
            ReceiverUtil.outputJsonResponse( req, resp, restResultBean );
        }
        catch ( final Exception e )
        {
            final RestResultBean restResultBean = RestResultBean.fromError( new ErrorInformation( PwmError.ERROR_INTERNAL, e.getMessage() ) );
            ReceiverUtil.outputJsonResponse( req, resp, restResultBean );
        }
    }
}
