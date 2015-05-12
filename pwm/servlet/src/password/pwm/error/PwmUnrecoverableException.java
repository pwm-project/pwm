/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2014 The PWM Project
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


import com.novell.ldapchai.exception.ChaiException;
import com.novell.ldapchai.exception.ChaiUnavailableException;

/**
 * A general exception thrown by PWM.
 */
public class PwmUnrecoverableException extends PwmException {

    public PwmUnrecoverableException(final ErrorInformation error) {
        super(error);
    }

    public PwmUnrecoverableException(final ErrorInformation error, final Throwable initialCause) {
        super(error, initialCause);
    }

    public PwmUnrecoverableException(final PwmError error) {
        super(error);
    }

    public static PwmUnrecoverableException fromChaiException(final ChaiException e) {
        final ErrorInformation errorInformation;
        if (e instanceof ChaiUnavailableException) {
            errorInformation = new ErrorInformation(PwmError.ERROR_DIRECTORY_UNAVAILABLE, e.getMessage());
        } else {
            errorInformation = new ErrorInformation(PwmError.forChaiError(e.getErrorCode()), e.getMessage());
        }
        return new PwmUnrecoverableException(errorInformation);
    }
}

