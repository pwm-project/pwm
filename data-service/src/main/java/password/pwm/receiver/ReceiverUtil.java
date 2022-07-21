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

package password.pwm.receiver;

import password.pwm.http.PwmHttpRequestWrapper;
import password.pwm.ws.server.RestResultBean;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class ReceiverUtil
{
    static <T> void outputJsonResponse(

            final HttpServletRequest request,
            final HttpServletResponse response,
            final RestResultBean<T> restResultBean
    )
            throws IOException
    {
        final boolean jsonPrettyPrint = PwmHttpRequestWrapper.isPrettyPrintJsonParameterTrue( request );
        response.setHeader( "Content", "application/json" );
        response.getWriter().print( restResultBean.toJson( jsonPrettyPrint ) );
    }

    static int silentIntParser( final String input )
    {
        try
        {
            return Integer.parseInt( input );
        }
        catch ( final NumberFormatException e )
        {
            return 0;
        }
    }
}
