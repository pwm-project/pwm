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
                    null,
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
