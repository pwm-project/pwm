/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2010 The PWM Project
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

import password.pwm.PwmSession;

import java.io.Serializable;
import java.util.*;

/**
 * An ErrorInformation is a package of error data generated within PWM.  Error information includes an error code
 * (in the form of an {@link PwmError}), additional detailed error information for logging, and string substitutions
 * to use when presenting error messages to users. 
 */
public class ErrorInformation implements Serializable {
// ------------------------------ FIELDS ------------------------------

    private PwmError error = PwmError.ERROR_UNKNOWN;
    private String detailedError;
    private final List<String> fieldValues = new ArrayList<String>();

// --------------------------- CONSTRUCTORS ---------------------------

    public ErrorInformation(final PwmError error) {
        this(error, null);
    }

    public ErrorInformation(final PwmError error, final String detailedError, final String... fields) {
        if (detailedError != null && detailedError.length() > 0) {
            this.detailedError = detailedError;
        }

        if (error != null) {
            this.error = error;
        }

        if (fields != null && fields.length > 0) {
            fieldValues.addAll(Arrays.asList(fields));
        }
    }

// --------------------- GETTER / SETTER METHODS ---------------------

    public String getDetailedError() {
        return detailedError;
    }

    public PwmError getError() {
        return error;
    }

// -------------------------- OTHER METHODS --------------------------

    public List<String> getFieldValues() {
        return Collections.unmodifiableList(fieldValues);
    }

    public String toDebugStr() {
        final StringBuilder sb = new StringBuilder();
        sb.append(error.toString());
        if (detailedError != null && detailedError.length() > 0) {
            sb.append(" (");
            sb.append(detailedError);
            sb.append((")"));
        }

        if (!fieldValues.isEmpty()) {
            sb.append(" fields: ");
            Arrays.toString(fieldValues.toArray(new String[fieldValues.size()]));
        }

        return sb.toString();
    }

    public String toUserStr(final PwmSession pwmSession) {
        final Locale userLocale = pwmSession.getSessionStateBean().getLocale();

        final String userStr;
        if (fieldValues.isEmpty()) {
            userStr = PwmError.getLocalizedMessage(userLocale, this.getError());
        } else {
            userStr = PwmError.getLocalizedMessage(userLocale, this.getError(), fieldValues.get(0));
        }

        return userStr;
    }
}
