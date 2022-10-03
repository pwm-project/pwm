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

import org.slf4j.event.Level;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.java.EnumUtil;
import password.pwm.util.java.StringUtil;

import java.util.Collection;

public enum PwmLogLevel
{
    TRACE( ch.qos.logback.classic.Level.TRACE, org.slf4j.event.Level.TRACE ),
    DEBUG( ch.qos.logback.classic.Level.DEBUG, org.slf4j.event.Level.DEBUG ),
    INFO( ch.qos.logback.classic.Level.INFO, org.slf4j.event.Level.INFO ),
    WARN( ch.qos.logback.classic.Level.WARN, org.slf4j.event.Level.WARN ),
    ERROR( ch.qos.logback.classic.Level.ERROR, org.slf4j.event.Level.ERROR ),
    FATAL( ch.qos.logback.classic.Level.ERROR, org.slf4j.event.Level.ERROR ),
    NONE( ch.qos.logback.classic.Level.OFF, org.slf4j.event.Level.ERROR ),;

    private final ch.qos.logback.classic.Level logbackLevel;
    private final org.slf4j.event.Level slf4jLevel;

    PwmLogLevel( final ch.qos.logback.classic.Level logbackLevel, final org.slf4j.event.Level slf4jLevel )
    {
        this.logbackLevel = logbackLevel;
        this.slf4jLevel = slf4jLevel;
    }

    public ch.qos.logback.classic.Level getLogbackLevel( )
    {
        return logbackLevel;
    }

    public Level getSlf4jLevel()
    {
        return slf4jLevel;
    }

    public boolean isGreaterOrSameAs( final PwmLogLevel logLevel )
    {
        return logLevel != null && this.compareTo( logLevel ) >= 0;
    }

    public static PwmLogLevel fromLogbackLevel( final ch.qos.logback.classic.Level level )
    {
        if ( level == null )
        {
            return TRACE;
        }

        return EnumUtil.readEnumFromPredicate(
                PwmLogLevel.class,
                pwmLogLevel -> pwmLogLevel.logbackLevel == level
        ).orElse( TRACE );
    }

    public static PwmLogLevel fromString( final String stringLogLevel )
    {
        return EnumUtil.readEnumFromPredicate(
                        PwmLogLevel.class,
                        pwmLogLevel -> StringUtil.nullSafeEqualsIgnoreCase( stringLogLevel, pwmLogLevel.name() ) )
                .orElse( TRACE );
    }

    public static PwmLogLevel lowestLevel( final Collection<PwmLogLevel> logLevels )
    {
        if ( CollectionUtil.isEmpty( logLevels ) )
        {
            return TRACE;
        }

        return CollectionUtil.copyToEnumSet( logLevels, PwmLogLevel.class ).iterator().next();
    }
}
