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

package password.pwm.util.logging;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Level;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.log4j.spi.ThrowableInformation;

import java.time.Instant;

public class LocalDBLog4jAppender extends AppenderSkeleton
{

    private LocalDBLogger localDBLogger;

    public LocalDBLog4jAppender( final LocalDBLogger localDBLogger )
    {
        this.localDBLogger = localDBLogger;
    }

    @Override
    protected void append( final LoggingEvent loggingEvent )
    {
        final Object message = loggingEvent.getMessage();
        final ThrowableInformation throwableInformation = loggingEvent.getThrowableInformation();
        final Level level = loggingEvent.getLevel();
        final Instant timeStamp = Instant.ofEpochMilli( loggingEvent.getTimeStamp() );
        final String sourceLogger = loggingEvent.getLogger().getName();

        if ( localDBLogger != null )
        {
            final PwmLogEvent logEvent = PwmLogEvent.createPwmLogEvent(
                    timeStamp,
                    sourceLogger,
                    message.toString(),
                    null,
                    throwableInformation == null ? null : throwableInformation.getThrowable(),
                    PwmLogLevel.fromLog4jLevel( level )
            );

            localDBLogger.writeEvent( logEvent );
        }
    }

    @Override
    public void close( )
    {
    }

    @Override
    public boolean requiresLayout( )
    {
        return false;
    }
}
