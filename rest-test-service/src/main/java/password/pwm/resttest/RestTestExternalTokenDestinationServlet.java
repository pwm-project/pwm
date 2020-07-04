/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2020 The PWM Project
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

package password.pwm.resttest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
        name = "RestTestExternalTokenDestinationServlet",
        urlPatterns = { "/external-token-destination", }
)
public class RestTestExternalTokenDestinationServlet extends HttpServlet
{

    @Override
    protected void doPost( final HttpServletRequest req, final HttpServletResponse resp )
            throws ServletException, IOException
    {
        System.out.println( "--External Token Destination--" );
        final InputStream inputStream = req.getInputStream();
        final String body = IOUtils.toString( inputStream );
        final JsonObject jsonObject = JsonParser.parseString( body ).getAsJsonObject();
        final String email = jsonObject.getAsJsonObject( "tokenDestination" ).get( "email" ).getAsString();
        final String sms = jsonObject.getAsJsonObject( "tokenDestination" ).get( "sms" ).getAsString();
        final String displayValue = "YourTokenDestination";

        resp.setHeader( "Content-Type", "application/json" );

        final PrintWriter writer = resp.getWriter();
        final String response = "{\"email\":\"" + email + "\",\"sms\":\"" + sms + "\",\"displayValue\":\"" + displayValue + "\"}";
        writer.write( response );
        writer.close();
    }
}
