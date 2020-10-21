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

package password.pwm.util.logging;

import org.apache.log4j.Level;

public enum PwmLogLevel
{
    TRACE( Level.TRACE ),
    DEBUG( Level.DEBUG ),
    INFO( Level.INFO ),
    WARN( Level.WARN ),
    ERROR( Level.ERROR ),
    FATAL( Level.FATAL ),;

    PwmLogLevel( final Level log4jLevel )
    {
        this.log4jLevel = log4jLevel;
    }

    private final Level log4jLevel;

    public Level getLog4jLevel( )
    {
        return log4jLevel;
    }

    public static PwmLogLevel fromLog4jLevel( final Level level )
    {
        if ( level == null )
        {
            return null;
        }

        if ( Level.TRACE.equals( level ) )
        {
            return TRACE;
        }
        else if ( Level.DEBUG.equals( level ) )
        {
            return DEBUG;
        }
        else if ( Level.INFO.equals( level ) )
        {
            return INFO;
        }
        else if ( Level.WARN.equals( level ) )
        {
            return WARN;
        }
        else if ( Level.ERROR.equals( level ) )
        {
            return ERROR;
        }
        else if ( Level.FATAL.equals( level ) )
        {
            return FATAL;
        }
        return TRACE;
    }
}
