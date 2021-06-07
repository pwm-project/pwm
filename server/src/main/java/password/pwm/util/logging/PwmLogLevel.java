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

import org.apache.log4j.Level;
import password.pwm.util.java.JavaHelper;

public enum PwmLogLevel
{
    TRACE( Level.TRACE ),
    DEBUG( Level.DEBUG ),
    INFO( Level.INFO ),
    WARN( Level.WARN ),
    ERROR( Level.ERROR ),
    FATAL( Level.FATAL ),;

    private final int log4jLevel;

    PwmLogLevel( final Level log4jLevel )
    {
        this.log4jLevel = log4jLevel.toInt();
    }

    public Level getLog4jLevel( )
    {
        return Level.toLevel( log4jLevel );
    }

    public static PwmLogLevel fromLog4jLevel( final Level level )
    {
        final int log4jIntLevel = level == null
                ? Level.TRACE.toInt()
                : level.toInt();

        return JavaHelper.readEnumFromPredicate(
                PwmLogLevel.class,
                pwmLogLevel -> pwmLogLevel.log4jLevel == log4jIntLevel
        )
                .orElse( TRACE );
    }
}
