/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
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

package password.pwm.http.servlet.peoplesearch;


import password.pwm.Permission;
import password.pwm.PwmConstants;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.ProcessStatus;
import password.pwm.http.PwmRequest;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import java.io.IOException;

@WebServlet(
        name = "PrivatePeopleSearchServlet",
        urlPatterns = {
                PwmConstants.URL_PREFIX_PRIVATE + "/peoplesearch/",
                PwmConstants.URL_PREFIX_PRIVATE + "/peoplesearch",
                PwmConstants.URL_PREFIX_PRIVATE + "/peoplesearch/*",
                PwmConstants.URL_PREFIX_PRIVATE + "/PeopleSearch",
                PwmConstants.URL_PREFIX_PRIVATE + "/PeopleSearch/*",
        }
)
public class PrivatePeopleSearchServlet extends PeopleSearchServlet
{

    @Override
    public ProcessStatus preProcessCheck( final PwmRequest pwmRequest ) throws PwmUnrecoverableException, IOException, ServletException
    {
        if ( !pwmRequest.getPwmSession().getSessionManager().checkPermission( pwmRequest.getPwmApplication(), Permission.PEOPLE_SEARCH ) )
        {
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_UNAUTHORIZED ) );
        }

        return super.preProcessCheck( pwmRequest );
    }
}
