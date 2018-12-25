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

import org.apache.commons.io.IOUtils;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;

@WebServlet(
        name = "NewUserServlet",
        urlPatterns = { "/external/macro1" }
)

public class ExternalMacroServlet extends HttpServlet
{
    @Override
    protected void doPost( final HttpServletRequest req, final HttpServletResponse resp ) throws ServletException, IOException
    {

        final InputStream inputStream = req.getInputStream();
        final String body = IOUtils.toString( inputStream );

        System.out.println( "input POST body:  " + body );


        resp.setHeader( "Content-Type", "application/json" );

        final PrintWriter writer = resp.getWriter();
        writer.write(  "{\"output\":\"macro api output SPLAT!\"}" );
        writer.close();
    }
}
