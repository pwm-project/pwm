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

package password.pwm.util.logging;

import password.pwm.bean.SessionLabel;

import java.io.IOException;
import java.time.Instant;

class PwmLoggerAppendable implements Appendable
{
    private final PwmLogger pwmLogger;
    private final PwmLogLevel logLevel;
    private final SessionLabel sessionLabel;

    private final StringBuilder buffer = new StringBuilder();

    PwmLoggerAppendable( final PwmLogger pwmLogger,
                         final PwmLogLevel logLevel,
                         final SessionLabel sessionLabel
    )
    {
        this.pwmLogger = pwmLogger;
        this.logLevel = logLevel;
        this.sessionLabel = sessionLabel;
    }

    @Override
    public Appendable append( final CharSequence csq )
            throws IOException
    {

        doAppend( csq );
        return this;
    }

    @Override
    public Appendable append(
            final CharSequence csq,
            final int start,
            final int end
    )
            throws IOException
    {
        doAppend( csq.subSequence( start, end ) );
        return this;
    }

    @Override
    public Appendable append( final char c )
            throws IOException
    {
        doAppend( String.valueOf( c ) );
        return this;
    }

    private synchronized void doAppend( final CharSequence charSequence )
    {
        buffer.append( charSequence );

        final PwmLogMessage pwmLogMessage = PwmLogMessage.create(
                Instant.now(),
                pwmLogger.getName(),
                logLevel,
                sessionLabel,
                () -> charSequence,
                null,
                null );

        int length = buffer.indexOf( "\n" );
        while ( length > 0 )
        {
            final String line = buffer.substring( 0, length );
            buffer.delete( 0, +length + 1 );
            pwmLogger.doLogEvent( pwmLogMessage );
            length = buffer.indexOf( "\n" );
        }
    }
}
