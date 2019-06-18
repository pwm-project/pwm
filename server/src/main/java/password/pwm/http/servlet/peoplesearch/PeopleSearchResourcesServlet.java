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

import org.apache.commons.lang3.StringUtils;
import password.pwm.PwmConstants;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebServlet(
        name = "PeopleSearchResourcesServlet",
        urlPatterns = {
                PwmConstants.URL_PREFIX_PRIVATE + "/peoplesearch/fonts/*",
                PwmConstants.URL_PREFIX_PUBLIC + "/peoplesearch/fonts/*"
        }
)
public class PeopleSearchResourcesServlet extends HttpServlet
{

    @Override
    protected void service( final HttpServletRequest request, final HttpServletResponse response ) throws ServletException, IOException
    {
        response.sendRedirect( String.format(
                // Build a relative URL with place holders:
                "%s%s/resources/app/fonts/%s",

                // Place holder values:
                request.getContextPath(),
                PwmConstants.URL_PREFIX_PUBLIC,
                StringUtils.substringAfterLast( request.getRequestURI(), "/" )
        ) );
    }
}
