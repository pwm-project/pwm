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

package password.pwm.resttest;

import org.apache.commons.io.IOUtils;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.Charset;

public class RestTestUtilities
{
    public static String readRequestBodyAsString( final HttpServletRequest req )
            throws IOException
    {
        final StringWriter stringWriter = new StringWriter();
        final Reader readerStream = new InputStreamReader(
                req.getInputStream(),
                Charset.forName( "UTF8" )
        );

        try
        {
            IOUtils.copy( readerStream, stringWriter );
        }
        catch ( final Exception e )
        {
            final String errorMsg = "error reading request body stream: " + e.getMessage();
            throw new IOException( errorMsg );
        }
        finally
        {
            IOUtils.closeQuietly( readerStream );
        }

        return stringWriter.toString();
    }
}
