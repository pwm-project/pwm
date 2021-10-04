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

package password.pwm.http.servlet.admin;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.PwmConstants;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.ProcessStatus;
import password.pwm.http.PwmRequest;
import password.pwm.http.servlet.ControlledPwmServlet;
import password.pwm.http.servlet.PwmServletDefinition;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import java.io.IOException;
import java.util.Optional;


@WebServlet(
        name = "AdminServlet",
        urlPatterns = {
                PwmConstants.URL_PREFIX_PRIVATE + "/admin",
                PwmConstants.URL_PREFIX_PRIVATE + "/admin/",
                PwmConstants.URL_PREFIX_PRIVATE + "/administration",
                PwmConstants.URL_PREFIX_PRIVATE + "/administration/",
        }
)
/**
 * Simple servlet to front requests to the otherwise standard index page at '/private/admin/index.jsp'.
 */
public class AdminServlet extends ControlledPwmServlet
{
    @Override
    protected PwmServletDefinition getServletDefinition()
    {
        return PwmServletDefinition.Admin;
    }

    @Override
    public Optional<Class<? extends ProcessAction>> getProcessActionsClass()
    {
        return Optional.empty();
    }

    @Override
    protected void nextStep( final PwmRequest pwmRequest ) throws PwmUnrecoverableException, IOException, ChaiUnavailableException, ServletException
    {
        pwmRequest.getHttpServletRequest().getServletContext()
                .getRequestDispatcher( PwmConstants.URL_PREFIX_PRIVATE + "/admin/index.jsp" )
                .forward( pwmRequest.getHttpServletRequest(), pwmRequest.getPwmResponse().getHttpServletResponse() );
    }

    @Override
    public ProcessStatus preProcessCheck( final PwmRequest pwmRequest ) throws PwmUnrecoverableException, IOException, ServletException
    {
        return SystemAdminServlet.preProcessAdminCheck( pwmRequest );
    }
}
