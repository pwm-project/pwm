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

package password.pwm.error;

import password.pwm.PwmApplication;
import password.pwm.PwmSession;
import password.pwm.config.Configuration;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

/**
 * An ErrorInformation is a package of error data generated within PWM.  Error information includes an error code
 * (in the form of an {@link PwmError}), additional detailed error information for logging, and string substitutions
 * to use when presenting error messages to users.
 */
public class ErrorInformation implements Serializable {
// ------------------------------ FIELDS ------------------------------

    private final PwmError error;
    private final String detailedErrorMsg;
    private final String userStrOverride;
    private final String[] fieldValues;
    private final Date date = new Date();

// --------------------------- CONSTRUCTORS ---------------------------

    // private constructor used for gson de-serialization
    private ErrorInformation() {
        error = PwmError.ERROR_UNKNOWN;
        detailedErrorMsg = null;
        fieldValues = null;
        userStrOverride = null;
    }

    public ErrorInformation(final PwmError error) {
        this.error = error == null ? PwmError.ERROR_UNKNOWN : error;
        this.detailedErrorMsg = null;
        this.userStrOverride = null;
        this.fieldValues = new String[0];
    }

    public ErrorInformation(final PwmError error, final String detailedErrorMsg) {
        this.error = error == null ? PwmError.ERROR_UNKNOWN : error;
        this.detailedErrorMsg = detailedErrorMsg;
        this.userStrOverride = null;
        this.fieldValues = new String[0];
    }

    public ErrorInformation(final PwmError error, final String detailedErrorMsg, final String[] fields) {
        this.error = error == null ? PwmError.ERROR_UNKNOWN : error;
        this.detailedErrorMsg = detailedErrorMsg;
        this.userStrOverride = null;
        this.fieldValues = fields == null ? new String[0] : fields;
    }

    public ErrorInformation(final PwmError error, final String detailedErrorMsg, final String userStrOverride, final String[] fields) {
        this.error = error == null ? PwmError.ERROR_UNKNOWN : error;
        this.detailedErrorMsg = detailedErrorMsg;
        this.userStrOverride = userStrOverride;
        this.fieldValues = fields == null ? new String[0] : fields;
    }

// --------------------- GETTER / SETTER METHODS ---------------------

    public String getDetailedErrorMsg() {
        return detailedErrorMsg;
    }

    public PwmError getError() {
        return error;
    }

// -------------------------- OTHER METHODS --------------------------

    public String[] getFieldValues() {
        return fieldValues;
    }

    public String toDebugStr() {
        final StringBuilder sb = new StringBuilder();
        sb.append(error.getErrorCode());
        sb.append(" ");
        sb.append(error.toString());
        if (detailedErrorMsg != null && detailedErrorMsg.length() > 0) {
            sb.append(" (");
            sb.append(detailedErrorMsg);
            sb.append((")"));
        }

        if (fieldValues != null && fieldValues.length > 0) {
            sb.append(" fields: ");
            sb.append(Arrays.toString(fieldValues));
        }

        return sb.toString();
    }

    public String toUserStr(final PwmSession pwmSession, final PwmApplication pwmApplication) {

        if (userStrOverride != null) {
            return userStrOverride;
        }

        Configuration config = null;
        Locale userLocale = null;

        if (pwmSession != null && pwmApplication.getConfig() != null) {
            config = pwmApplication.getConfig();
        }

        if (pwmSession != null) {
            userLocale = pwmSession.getSessionStateBean().getLocale();
        }

        return toUserStr(userLocale, config);
    }

    public String toUserStr(final Locale userLocale, final Configuration config) {
        if (userStrOverride != null) {
            return userStrOverride;
        }

        if (fieldValues != null && fieldValues.length > 0) {
            return PwmError.getLocalizedMessage(userLocale, this.getError(), config, fieldValues);
        } else {
            return PwmError.getLocalizedMessage(userLocale, this.getError(), config);
        }
    }

    public Date getDate() {
        return date;
    }
}
