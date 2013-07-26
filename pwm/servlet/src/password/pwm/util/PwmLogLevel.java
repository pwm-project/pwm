/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2012 The PWM Project
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

package password.pwm.util;

import org.apache.log4j.Level;

public enum PwmLogLevel {
    TRACE(Level.TRACE),
    DEBUG(Level.DEBUG),
    INFO(Level.INFO),
    WARN(Level.WARN),
    ERROR(Level.ERROR),
    FATAL(Level.FATAL),
    ;

    private PwmLogLevel(Level log4jLevel) {
        this.log4jLevel = log4jLevel;
    }

    private Level log4jLevel;

    public Level getLog4jLevel() {
        return log4jLevel;
    }

    public static PwmLogLevel fromLog4jLevel(final Level level) {
        if (level == null) {
            return null;
        }

        if (level == Level.TRACE) {
            return TRACE;
        } else if (level == Level.DEBUG) {
            return DEBUG;
        } else if (level == Level.INFO) {
            return INFO;
        } else if (level == Level.WARN) {
            return WARN;
        } else if (level == Level.ERROR) {
            return ERROR;
        } else if (level == Level.FATAL) {
            return FATAL;
        }
        return TRACE;
    }
}
