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

package password.pwm.config.option;

import password.pwm.config.Configuration;
import password.pwm.i18n.Config;
import password.pwm.i18n.Display;

import java.util.Locale;

public enum RecoveryVerificationMethod implements ConfigurationOption {
    PREVIOUS_AUTH(      false,  Display.Field_VerificationMethodPreviousAuth,       Config.Field_VerificationMethodPreviousAuth),
    ATTRIBUTES(         true,   Display.Field_VerificationMethodAttributes,         Config.Field_VerificationMethodAttributes),
    CHALLENGE_RESPONSES(true,   Display.Field_VerificationMethodChallengeResponses, Config.Field_VerificationMethodChallengeResponses),
    TOKEN(              true,   Display.Field_VerificationMethodToken,              Config.Field_VerificationMethodToken),
    OTP(                true,   Display.Field_VerificationMethodOTP,                Config.Field_VerificationMethodOTP),
    REMOTE_RESPONSES(   false,  Display.Field_VerificationMethodRemoteResponses,    Config.Field_VerificationMethodRemoteResponses),

    ;
    
    private final boolean userSelectable;
    private final Display displayKey;
    private final Config configDisplayKey;

    RecoveryVerificationMethod(boolean userSelectable, Display displayKey, Config configDisplayKey) {
        this.userSelectable = userSelectable;
        this.displayKey = displayKey;
        this.configDisplayKey = configDisplayKey;
    }

    public boolean isUserSelectable() {
        return userSelectable;
    }
    
    public Display getDisplayKey() {
        return displayKey;
    }

    public Config getConfigDisplayKey() {
        return configDisplayKey;
    }

    public String getLabel(final Configuration configuration, final Locale locale) {
        return Display.getLocalizedMessage(locale, this.getDisplayKey(), configuration);
    }
}
