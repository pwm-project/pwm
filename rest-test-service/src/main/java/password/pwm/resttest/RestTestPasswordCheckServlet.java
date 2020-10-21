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
        final String errorMessage = ( error ? "TOO Many aaa's (REMOTE REST SERVICE)" : "No error. (REMOTE REST SERVICE)" )
                + ", pw=" + inputPassword;

        resp.setHeader( "Content-Type", "application/json" );
        final PrintWriter writer = resp.getWriter();
        final String response = "{\"error\":\"" + error + "\",\"errorMessage\":\"" + errorMessage + "\"}";
        writer.write( response );
        writer.close();
    }
}
