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

package password.pwm.util.java;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread safe rotating int incrementer with configurable floor and ceiling values.
 */
public class AtomicLoopLongIncrementer
{
    private final AtomicLong incrementer;
    private final long ceiling;
    private final long floor;

    public AtomicLoopLongIncrementer( final long initialValue, final long ceiling )
    {
        this.ceiling = ceiling;
        this.floor = 0;
        incrementer = new AtomicLong( JavaHelper.rangeCheck( floor, ceiling, initialValue ) );
    }

    public long get()
    {
        return incrementer.get();
    }

    public long incrementAndGet( )
    {
        return incrementer.getAndUpdate( operand ->
        {
            operand++;
            if ( operand >= ceiling )
            {
                operand = floor;
            }
            return operand;
        } );
    }
}
