/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2016 The PWM Project
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
import password.pwm.config.StoredValue;
import password.pwm.config.option.IdentityVerificationMethod;
import password.pwm.config.stored.StoredConfiguration;
import password.pwm.config.value.VerificationMethodValue;

import java.util.*;

public class HelpdeskProfile extends AbstractProfile implements Profile {

    private static final ProfileType PROFILE_TYPE = ProfileType.Helpdesk;

    protected HelpdeskProfile(String identifier, Map<PwmSetting, StoredValue> storedValueMap) {
        super(identifier, storedValueMap);
    }

    public static HelpdeskProfile makeFromStoredConfiguration(final StoredConfiguration storedConfiguration, final String identifier) {
        final Map<PwmSetting,StoredValue> valueMap = makeValueMap(storedConfiguration, identifier, PROFILE_TYPE.getCategory());
        return new HelpdeskProfile(identifier, valueMap);
    }

    @Override
    public String getDisplayName(Locale locale)
    {
        return this.getIdentifier();
    }

    @Override
    public ProfileType profileType() {
        return PROFILE_TYPE;
    }

    public Collection<IdentityVerificationMethod> readOptionalVerificationMethods() {
        final Set<IdentityVerificationMethod> result = new LinkedHashSet<>();
        result.addAll(readVerificationMethods(PwmSetting.HELPDESK_VERIFICATION_METHODS, VerificationMethodValue.EnabledState.optional));
        result.addAll(readVerificationMethods(PwmSetting.HELPDESK_VERIFICATION_METHODS, VerificationMethodValue.EnabledState.required));
        return Collections.unmodifiableSet(result);
    }

    public Collection<IdentityVerificationMethod> readRequiredVerificationMethods() {
        return readVerificationMethods(PwmSetting.HELPDESK_VERIFICATION_METHODS, VerificationMethodValue.EnabledState.required);
    }
}
