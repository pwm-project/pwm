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

import com.google.gson.Gson;
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
        name = "RestTestSmsGatewayServlet",
        urlPatterns = { "/sms" }
)
public class RestTestSmsGatewayServlet extends HttpServlet
{
    private static final String USERNAME_PARAMETER = "username";
    private static final String SUCCESSFUL = "true";
    private static final String UNSUCCESSFUL = "false";

    @Override
    protected void doPost( final HttpServletRequest req, final HttpServletResponse resp ) throws ServletException, IOException
    {
            final SmsResponse instance = new SmsResponse();
            final InputStream inputStream = req.getInputStream();
            final String body = IOUtils.toString( inputStream );

            final String[] messageContent = body.split( "=" );
            final String message = messageContent[messageContent.length - 1];
            final String username = message.split( "\\+" )[0];
            final SmsPostResponseBody messageBody = new SmsPostResponseBody( message );

            instance.addToMap( username, messageBody );

            System.out.println( "input POST body:  " + body );

            resp.setHeader( "Content-Type", "application/json" );

            final PrintWriter writer = resp.getWriter();
            writer.write(  "{\"output\":\"Message Received\"}" );
            writer.close();
    }

    @Override
    protected void doGet( final HttpServletRequest req, final HttpServletResponse resp ) throws IOException
    {
            //Check request
            final SmsResponse instance = new SmsResponse();
            final String requestUsername = req.getParameter( USERNAME_PARAMETER );
            final SmsGetResponseBody responseBody;

            //Get body
            if ( instance.getRecentSmsMessages().containsKey( requestUsername ) )
            {
                final SmsPostResponseBody body = instance.getRecentFromMap( requestUsername );
                responseBody = new SmsGetResponseBody( SUCCESSFUL, body.getMessageContent() );
            }
            else
            {
                responseBody = new SmsGetResponseBody( UNSUCCESSFUL, "" );
            }

            //Send response
            final Gson gson = new Gson();
            resp.setHeader( "Content-Type", "application/json"  );
            final PrintWriter writer = resp.getWriter();
            writer.write( gson.toJson( responseBody ) );
            writer.close();

        }

}
