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
