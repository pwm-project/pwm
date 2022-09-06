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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.LayoutBase;
import password.pwm.util.java.StringUtil;

import java.time.Instant;

/**
 * Logback output pattern to produce output similar to original log4j1 PWM logging
 * output.
 */
public class PwmLogbackPattern extends LayoutBase<ILoggingEvent>
{
    public String doLayout( final ILoggingEvent event )
    {
        if ( event == null )
        {
            return "[null logback event]";
        }

        return StringUtil.toIsoDate( Instant.ofEpochMilli( event.getTimeStamp() ) )
                + ", "
                + printLevel( event.getLevel() )
                + ", "
                + printLoggerName( event.getLoggerName() )
                + ", "
                + event.getFormattedMessage()
                + "\n";
    }

    private static String printLevel( final Level pwmLogLevel )
    {
        return StringUtil.padRight( pwmLogLevel.toString(), 5, ' ' );
    }

    /**
     * Creates output similar to "c{2}" pattern from legacy Log4j1 which was
     * original logging API used in PWM.
     * @param loggerName name of logger (typically fully-qualified class and package name)
     * @return formatted class name with top level package name.
     */
    private static String printLoggerName( final String loggerName )
    {
        if ( StringUtil.isEmpty( loggerName ) )
        {
            return "";
        }

        final int firstDot = loggerName.lastIndexOf( '.' );
        if ( firstDot > 0 )
        {
            final int secondDot = loggerName.lastIndexOf( '.', firstDot - 1 );
            if ( secondDot > -1 )
            {
                return loggerName.substring( secondDot + 1 );
            }
        }

        return loggerName;
    }
}
