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

package password.pwm.config.profile;

import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingCategory;
import password.pwm.config.StoredConfiguration;
import password.pwm.config.StoredValue;
import password.pwm.config.option.RecoveryVerificationMethod;

import java.util.*;

public class ForgottenPasswordProfile extends AbstractProfile {

    private Set<RecoveryVerificationMethod> requiredRecoveryVerificationMethods;
    private Set<RecoveryVerificationMethod> optionalRecoveryVerificationMethods;

    public ForgottenPasswordProfile(String identifier, Map<PwmSetting, StoredValue> storedValueMap) {
        super(identifier, storedValueMap);
    }


    @Override
    public String getDisplayName(Locale locale) {
        return null;
    }

    public static ForgottenPasswordProfile makeFromStoredConfiguration(final StoredConfiguration storedConfiguration, final String identifier) {
        final Map<PwmSetting,StoredValue> valueMap = new LinkedHashMap<>();
        for (final PwmSetting setting : PwmSettingCategory.RECOVERY_PROFILE.getSettings()) {
            final StoredValue value = storedConfiguration.readSetting(setting, identifier);
            valueMap.put(setting, value);
        }
        return new ForgottenPasswordProfile(identifier, valueMap);

    }

    @Override
    public ProfileType profileType() {
        return ProfileType.ForgottenPassword;
    }
    
    public Set<RecoveryVerificationMethod> requiredRecoveryAuthenticationMethods() {
        if (requiredRecoveryVerificationMethods == null) {
            requiredRecoveryVerificationMethods = readRecoveryAuthMethods(VerificationOptionValue.REQUIRED);
        }
        return requiredRecoveryVerificationMethods;
    }

    public Set<RecoveryVerificationMethod> optionalRecoveryAuthenticationMethods() {
        if (optionalRecoveryVerificationMethods == null) {
            optionalRecoveryVerificationMethods = readRecoveryAuthMethods(VerificationOptionValue.OPTIONAL);
        }
        return optionalRecoveryVerificationMethods;
    }
    
    private Set<RecoveryVerificationMethod> readRecoveryAuthMethods(final VerificationOptionValue matchValue) {
        final Set<RecoveryVerificationMethod> result = new LinkedHashSet<>();
        for (final RecoveryVerificationMethod recoveryVerificationMethod : RecoveryVerificationMethod.values()) {
            final PwmSetting setting = recoveryVerificationMethod.getAssociatedSetting();
            final VerificationOptionValue value = this.readSettingAsEnum(setting,VerificationOptionValue.class);
            if (value != null && value == matchValue) {
                result.add(recoveryVerificationMethod);
            }
        }
        return result;
    }
    
    public static enum VerificationOptionValue {
        NONE,
        OPTIONAL,
        REQUIRED,
    }

}
