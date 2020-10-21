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

package password.pwm.receiver;

import java.util.logging.Level;
import java.util.logging.Logger;

public class PwmReceiverLogger
{
    private final Class clazz;

    private PwmReceiverLogger( final Class clazz )
    {
        this.clazz = clazz;
    }

    public static PwmReceiverLogger forClass( final Class clazz )
    {
        return new PwmReceiverLogger( clazz );
    }

    public void debug( final CharSequence logMsg )
    {
        log( Level.FINE, logMsg, null );
    }

    public void info( final CharSequence logMsg )
    {
        log( Level.INFO, logMsg, null );
    }

    public void error( final CharSequence logMsg )
    {
        log( Level.SEVERE, logMsg, null );
    }

    public void error( final CharSequence logMsg, final Throwable throwable )
    {
        log( Level.SEVERE, logMsg, throwable );
    }

    private void log( final Level level, final CharSequence logMsg, final Throwable throwable )
    {
        final Logger logger = Logger.getLogger( clazz.getName() );
        logger.log( level, logMsg.toString(), throwable );
    }
}
