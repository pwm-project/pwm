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

package password.pwm.config.option;

import password.pwm.config.Configuration;
import password.pwm.i18n.Display;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public enum IdentityVerificationMethod implements ConfigurationOption {
    PREVIOUS_AUTH(      false,  Display.Field_VerificationMethodPreviousAuth,       Display.Description_VerificationMethodPreviousAuth),
    ATTRIBUTES(         true,   Display.Field_VerificationMethodAttributes,         Display.Description_VerificationMethodAttributes),
    CHALLENGE_RESPONSES(true,   Display.Field_VerificationMethodChallengeResponses, Display.Description_VerificationMethodChallengeResponses),
    TOKEN(              true,   Display.Field_VerificationMethodToken,              Display.Description_VerificationMethodToken),
    OTP(                true,   Display.Field_VerificationMethodOTP,                Display.Description_VerificationMethodOTP),
    REMOTE_RESPONSES(   false,  Display.Field_VerificationMethodRemoteResponses,    Display.Description_VerificationMethodRemoteResponses),
    OAUTH(              true,   Display.Field_VerificationMethodOAuth,              Display.Description_VerificationMethodOAuth),

    ;
    
    private final boolean userSelectable;
    private final Display labelKey;
    private final Display descriptionKey;

    IdentityVerificationMethod(final boolean userSelectable, final Display labelKey, final Display descriptionKey) {
        this.userSelectable = userSelectable;
        this.labelKey = labelKey;
        this.descriptionKey = descriptionKey;
    }

    public boolean isUserSelectable() {
        return userSelectable;
    }

    public String getLabel(final Configuration configuration, final Locale locale) {
        return Display.getLocalizedMessage(locale, this.labelKey, configuration);
    }

    public String getDescription(final Configuration configuration, final Locale locale) {
        return Display.getLocalizedMessage(locale, this.descriptionKey, configuration);
    }

    public static IdentityVerificationMethod[] availableValues() {
        final List<IdentityVerificationMethod> values = new ArrayList<>();
        values.addAll(Arrays.asList(IdentityVerificationMethod.values()));
        return values.toArray(new IdentityVerificationMethod[values.size()]);
    }

}
