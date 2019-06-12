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

package password.pwm.http.servlet;

import password.pwm.util.java.AtomicLoopIntIncrementer;

public class PwmRequestID
{
    private final int value;
    private static final AtomicLoopIntIncrementer INCREMENTER = new AtomicLoopIntIncrementer( Integer.MAX_VALUE );

    private PwmRequestID( final int value )
    {
        this.value = value;
    }

    private String value()
    {
        return String.valueOf( value );
    }

    public String toString()
    {
        return value();
    }

    public static PwmRequestID next()
    {
        return new PwmRequestID( INCREMENTER.next() );
    }
}
