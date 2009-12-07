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

public class PasswordValidationException extends PwmException {
// ------------------------------ FIELDS ------------------------------

    private Message errorMessage;

// -------------------------- STATIC METHODS --------------------------

    public static PasswordValidationException createPasswordValidationException(final Message message) {
        if (message == null) {
            throw new IllegalArgumentException();
        }

        final PasswordValidationException pve = new PasswordValidationException(new ErrorInformation(Message.ERROR_WRONGPASSWORD, message.toString()));
        pve.errorMessage = message;
        return pve;
    }

// --------------------------- CONSTRUCTORS ---------------------------

    private PasswordValidationException(ErrorInformation error) {
        super(error, error.toDebugStr());
    }
}
