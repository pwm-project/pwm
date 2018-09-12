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
