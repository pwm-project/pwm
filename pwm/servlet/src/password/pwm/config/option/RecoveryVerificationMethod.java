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
import password.pwm.config.PwmSetting;
import password.pwm.i18n.Display;

import java.util.Locale;

public enum RecoveryVerificationMethod implements ConfigurationOption {
    PREVIOUS_AUTH(      false,  PwmSetting.RECOVERY_VERIFICATION_PREVIOUS_AUTH,          Display.Field_VerificationMethodPreviousAuth),
    ATTRIBUTES(         true,   PwmSetting.RECOVERY_VERIFICATION_ATTRIBUTES,             Display.Field_VerificationMethodAttributes),
    CHALLENGE_RESPONSES(true,   PwmSetting.RECOVERY_VERIFICATION_CHALLENGE_RESPONSE,     Display.Field_VerificationMethodChallengeResponses),
    TOKEN(              true,   PwmSetting.RECOVERY_VERIFICATION_TOKEN,                  Display.Field_VerificationMethodToken),
    OTP(                true,   PwmSetting.RECOVERY_VERIFICATION_OTP,                    Display.Field_VerificationMethodOTP),
    REMOTE_RESPONSES(   false,  PwmSetting.RECOVERY_VERIFICATION_REMOTE_RESPONSES,       Display.Field_VerificationMethodRemoteResponses),

    ;
    
    private final boolean userSelectable;
    private final PwmSetting associatedSetting;
    private final Display displayKey;

    RecoveryVerificationMethod(boolean userSelectable, PwmSetting associatedSetting, Display displayKey) {
        this.userSelectable = userSelectable;
        this.associatedSetting = associatedSetting;
        this.displayKey = displayKey;
    }

    public PwmSetting getAssociatedSetting() {
        return associatedSetting;
    }

    public boolean isUserSelectable() {
        return userSelectable;
    }
    
    public Display getDisplayKey() {
        return displayKey;
    }
    
    public String getLabel(final Configuration configuration, final Locale locale) {
        return Display.getLocalizedMessage(locale, this.getDisplayKey(), configuration);
    }
}
