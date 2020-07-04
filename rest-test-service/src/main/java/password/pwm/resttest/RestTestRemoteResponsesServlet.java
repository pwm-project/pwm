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
import lombok.Builder;
import lombok.Value;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@WebServlet(
    name = "RestTestRemoteResponsesServlet",
    urlPatterns = { "/responses" }
)
public class RestTestRemoteResponsesServlet extends HttpServlet
{
    private static final String USERNAME_PARAMETER = "username";
    private static final String SUCCESSFUL = "true";
    private static final String UNSUCCESSFUL = "false";

    @Override
    protected void doPost( final HttpServletRequest req, final HttpServletResponse resp ) throws ServletException, IOException
    {
        final String reqBody = RestTestUtilities.readRequestBodyAsString( req );

        System.out.println( "REST TEST: --Remote Responses=-" );
        System.out.println( "  Received: " + reqBody );
        final Gson gson = new Gson();
        final RequestData requestData = gson.fromJson( reqBody, RequestData.class );

        boolean correct = false;
        if ( requestData.getUserResponses() != null
            && requestData.getUserResponses().size() > 0
            && requestData.getUserResponses().containsKey( "id1" )
            && requestData.getUserResponses().get( "id1" ).equalsIgnoreCase( "answer1" ) )
        {
            correct = true;
        }

        final ResponseData responseData = ResponseData.builder()
            .displayInstructions( "remote responses test server instructions" )
            .verificationState( correct ? "COMPLETE" : "INPROGRESS" )
            .userPrompts( Collections.singletonList( new Prompt( "prompt1", "id1" ) ) )
            .errorMessage(  correct ? "" : "incorrect response for 'id1'.  ( correct response is 'answer1' ) " )
            .build();

        resp.setHeader( "Content-Type", "application/json" );
        final PrintWriter writer = resp.getWriter();
        System.out.println( "  Response: " + gson.toJson( responseData ) );
        writer.print( gson.toJson( responseData ) );
    }

    @Value
    public static class RequestData implements Serializable
    {
        private final String responseSessionID;
        private final Map<String, String> userResponses;
    }

    @Value
    @Builder
    public static class ResponseData implements Serializable
    {
        private final String displayInstructions;
        private final String verificationState;
        private final List<Prompt> userPrompts;
        private final String errorMessage;

    }

    @Value
    public static class Prompt implements Serializable
    {
        private final String displayPrompt;
        private final String identifier;
    }
}
