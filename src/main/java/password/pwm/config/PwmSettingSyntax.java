/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2015 The PWM Project
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

package password.pwm.config;

import password.pwm.config.value.*;

public enum PwmSettingSyntax {
    STRING(StringValue.factory()),
    USER_PERMISSION(UserPermissionValue.factory()),
    STRING_ARRAY(StringArrayValue.factory()),
    TEXT_AREA(StringValue.factory()),
    LOCALIZED_STRING(LocalizedStringValue.factory()),
    LOCALIZED_TEXT_AREA(LocalizedStringValue.factory()),
    LOCALIZED_STRING_ARRAY(LocalizedStringArrayValue.factory()),
    PASSWORD(PasswordValue.factory()),
    NUMERIC(NumericValue.factory()),
    DURATION(NumericValue.factory()),
    BOOLEAN(BooleanValue.factory()),
    SELECT(StringValue.factory()),
    FORM(FormValue.factory()),
    ACTION(ActionValue.factory()),
    EMAIL(EmailValue.factory()),
    X509CERT(X509CertificateValue.factory()),
    CHALLENGE(ChallengeValue.factory()),
    OPTIONLIST(OptionListValue.factory()),
    FILE(FileValue.factory()),
    PROFILE(StringArrayValue.factory()),
    VERIFICATION_METHOD(VerificationMethodValue.factory()),

    ;

    private StoredValue.StoredValueFactory storedValueImpl;

    PwmSettingSyntax(StoredValue.StoredValueFactory storedValueImpl) {
        this.storedValueImpl = storedValueImpl;
    }

    public StoredValue.StoredValueFactory getStoredValueImpl() {
        return storedValueImpl;
    }
}
