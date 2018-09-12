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
