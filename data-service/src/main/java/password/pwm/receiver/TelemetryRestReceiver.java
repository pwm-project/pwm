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

package password.pwm.receiver;

import password.pwm.bean.TelemetryPublishBean;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.i18n.Message;
import password.pwm.util.ServletUtility;
import password.pwm.util.java.JsonUtil;
import password.pwm.ws.server.RestResultBean;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebServlet(
        name = "TelemetryRestReceiver",
        urlPatterns = {
                "/telemetry",
        }
)

public class TelemetryRestReceiver extends HttpServlet
{
    @Override
    protected void doPost( final HttpServletRequest req, final HttpServletResponse resp )
            throws ServletException, IOException
    {
        try
        {
            resp.setHeader( "Content", "application/json" );
            final String input = ServletUtility.readRequestBodyAsString( req, 1024 * 1024 );
            final TelemetryPublishBean telemetryPublishBean = JsonUtil.deserialize( input, TelemetryPublishBean.class );
            final Storage stoage = ContextManager.getContextManager( this.getServletContext() ).getApp().getStorage();
            stoage.store( telemetryPublishBean );
            resp.getWriter().print( RestResultBean.forSuccessMessage( null, null, null, Message.Success_Unknown ).toJson() );
        }
        catch ( final PwmUnrecoverableException e )
        {
            resp.getWriter().print( RestResultBean.fromError( e.getErrorInformation() ).toJson() );
        }
        catch ( final Exception e )
        {
            final RestResultBean restResultBean = RestResultBean.fromError( new ErrorInformation( PwmError.ERROR_INTERNAL, e.getMessage() ) );
            resp.getWriter().print( restResultBean.toJson() );
        }
    }
}
