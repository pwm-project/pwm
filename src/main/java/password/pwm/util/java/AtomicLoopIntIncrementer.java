/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
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

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread safe rotating int incrementer with configurable floor and ceiling values.
 */
public class AtomicLoopIntIncrementer {
    private final AtomicInteger incrementer = new AtomicInteger(0);
    private final int ceiling;
    private final int floor;

    public AtomicLoopIntIncrementer(final int ceiling)
    {
        this.ceiling = ceiling;
        this.floor = 0;
    }

    public int next() {
        return incrementer.getAndUpdate(operand -> {
            operand++;
            if (operand >= ceiling) {
                operand = floor;
            }
            return operand;
        });
    }
}
