/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
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

package password.pwm.error;

import password.pwm.config.Message;


/**
 * A general exception thrown by PWM.
 */
public class PwmException extends Exception {
// ------------------------------ FIELDS ------------------------------

    protected ErrorInformation error;

// -------------------------- STATIC METHODS --------------------------

    public static PwmException createPwmException(final Message Messages) {
        return createPwmException(new ErrorInformation(Messages));
    }

    public static PwmException createPwmException(final ErrorInformation error) {
        return new PwmException(error, error.toDebugStr());
    }

// --------------------------- CONSTRUCTORS ---------------------------

    PwmException(final ErrorInformation error, final String exceptionMsg) {
        super(exceptionMsg);
        this.error = error;
    }

// --------------------- GETTER / SETTER METHODS ---------------------

    public ErrorInformation getError() {
        return error;
    }
}

