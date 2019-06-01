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

package password.pwm.resttest;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;

@WebServlet(
        name = "RestTestPasswordCheckServlet",
        urlPatterns = { "/external-password-check" }
)
public class RestTestPasswordCheckServlet extends HttpServlet
{

    @Override
    protected void doPost( final HttpServletRequest req, final HttpServletResponse resp ) throws ServletException, IOException
    {
        System.out.println( "REST TEST: --External Password Check--" );
        final Gson gson = new Gson();
        final Map<String, String> inputJson = gson.fromJson( RestTestUtilities.readRequestBodyAsString( req ), new TypeToken<Map<String, Object>>()
                {
                }.getType() );
        final String inputPassword = inputJson.get( "password" );
        final boolean error = inputPassword.contains( "aaa" );

        final String errorMessage = error ? "TOO Many aaa's (REMOTE REST SERVICE)" : "No error. (REMOTE REST SERVICE)";

        resp.setHeader( "Content-Type", "application/json" );
        final PrintWriter writer = resp.getWriter();
        final String response = "{\"error\":\"" + error + "\",\"errorMessage\":\"" + errorMessage + "\"}";
        writer.write( response );
        writer.close();
    }
}
