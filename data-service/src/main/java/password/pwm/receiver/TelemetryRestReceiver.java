/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
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
        catch ( PwmUnrecoverableException e )
        {
            resp.getWriter().print( RestResultBean.fromError( e.getErrorInformation() ).toJson() );
        }
        catch ( Exception e )
        {
            final RestResultBean restResultBean = RestResultBean.fromError( new ErrorInformation( PwmError.ERROR_INTERNAL, e.getMessage() ) );
            resp.getWriter().print( restResultBean.toJson() );
        }
    }
}
