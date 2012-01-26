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

package password.pwm;

import password.pwm.config.Configuration;

public interface ExternalJudgeMethod {
    /**
     * Return a "judge" value for a supplied password.  Implementations of this method may be invoked
     * as frequently as individual keystrokes of users 
     * @param configuration The user's current pwmSession.  In some cases this may be null.
     * @param password password string to evaluate
     * @return an integer between 0 and 100, inclusive.  Values outside this range will be ignored.
     */
    int judgePassword(Configuration configuration, String password);
}
