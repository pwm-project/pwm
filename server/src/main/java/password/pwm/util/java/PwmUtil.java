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

package password.pwm.util.java;

import org.apache.commons.csv.CSVPrinter;
import password.pwm.PwmConstants;
import password.pwm.util.logging.PwmLogger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

public final class PwmUtil
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( PwmUtil.class );

    private PwmUtil()
    {
    }

    public static void unhandledSwitchStatement( final Object switchParameter )
    {
        final String className = switchParameter == null
                ? "unknown - see stack trace"
                : switchParameter.getClass().getName();

        final String paramValue = switchParameter == null
                ? "unknown"
                : switchParameter.toString();

        final String errorMsg = "unhandled switch statement on parameter class=" + className + ", value=" + paramValue;
        final UnsupportedOperationException exception = new UnsupportedOperationException( errorMsg );
        LOGGER.warn( () -> errorMsg, exception );
        throw exception;
    }

    /**
     * Very naive implementation to get a rough order estimate of object memory size, used for debug
     * purposes only.
     *
     * @param object object to be analyzed
     * @return size of object (very rough estimate)
     */
    public static long sizeof( final Object object )
    {
        try ( ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream() )
        {
            final ObjectOutputStream out = new ObjectOutputStream( byteArrayOutputStream );
            out.writeObject( object );
            out.flush();
            return byteArrayOutputStream.toByteArray().length;
        }
        catch ( final IOException e )
        {
            LOGGER.debug( () -> "exception while estimating session size: " + e.getMessage() );
            return 0;
        }
    }

    public static CSVPrinter makeCsvPrinter( final OutputStream outputStream )
            throws IOException
    {
        return new CSVPrinter( new OutputStreamWriter( outputStream, PwmConstants.DEFAULT_CHARSET ), PwmConstants.DEFAULT_CSV_FORMAT );
    }

    public static PwmNumberFormat forDefaultLocale( )
    {
        return PwmNumberFormat.forLocale( PwmConstants.DEFAULT_LOCALE );
    }

    public static PwmDateFormat newPwmDateFormat( final String formatString )
    {
        return PwmDateFormat.newPwmDateFormat( formatString, PwmConstants.DEFAULT_LOCALE, PwmConstants.DEFAULT_TIMEZONE );
    }
}
