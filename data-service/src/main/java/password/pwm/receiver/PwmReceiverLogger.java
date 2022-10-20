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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

public class PwmReceiverLogger
{
    private final Logger logger;

    private PwmReceiverLogger( final Class<?> clazz )
    {
        this.logger = LoggerFactory.getLogger( clazz );
    }

    public static PwmReceiverLogger forClass( final Class<?> clazz )
    {
        return new PwmReceiverLogger( clazz );
    }

    public void debug( final CharSequence logMsg )
    {
        log( Level.DEBUG, logMsg, null );
    }

    public void info( final CharSequence logMsg )
    {
        log( Level.INFO, logMsg, null );
    }

    public void error( final CharSequence logMsg )
    {
        log( Level.ERROR, logMsg, null );
    }

    public void error( final CharSequence logMsg, final Throwable throwable )
    {
        log( Level.ERROR, logMsg, throwable );
    }

    private void log( final org.slf4j.event.Level level, final CharSequence logMsg, final Throwable throwable )
    {
        logger.atLevel( level )
                .setCause( throwable )
                .log( logMsg == null ? null : logMsg.toString() );
    }
}
