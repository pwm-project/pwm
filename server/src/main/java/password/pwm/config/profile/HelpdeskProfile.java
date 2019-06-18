/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2019 The PWM Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package password.pwm.config.profile;

import password.pwm.config.PwmSetting;
import password.pwm.config.StoredValue;
import password.pwm.config.option.IdentityVerificationMethod;
import password.pwm.config.stored.StoredConfiguration;
import password.pwm.config.value.VerificationMethodValue;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class HelpdeskProfile extends AbstractProfile implements Profile
{

    private static final ProfileType PROFILE_TYPE = ProfileType.Helpdesk;

    protected HelpdeskProfile( final String identifier, final Map<PwmSetting, StoredValue> storedValueMap )
    {
        super( identifier, storedValueMap );
    }

    public static HelpdeskProfile makeFromStoredConfiguration( final StoredConfiguration storedConfiguration, final String identifier )
    {
        final Map<PwmSetting, StoredValue> valueMap = makeValueMap( storedConfiguration, identifier, PROFILE_TYPE.getCategory() );
        return new HelpdeskProfile( identifier, valueMap );
    }

    @Override
    public String getDisplayName( final Locale locale )
    {
        return this.getIdentifier();
    }

    @Override
    public ProfileType profileType( )
    {
        return PROFILE_TYPE;
    }

    public Collection<IdentityVerificationMethod> readOptionalVerificationMethods( )
    {
        final Set<IdentityVerificationMethod> result = new LinkedHashSet<>();
        result.addAll( readVerificationMethods( PwmSetting.HELPDESK_VERIFICATION_METHODS, VerificationMethodValue.EnabledState.optional ) );
        result.addAll( readVerificationMethods( PwmSetting.HELPDESK_VERIFICATION_METHODS, VerificationMethodValue.EnabledState.required ) );
        return Collections.unmodifiableSet( result );
    }

    public Collection<IdentityVerificationMethod> readRequiredVerificationMethods( )
    {
        return readVerificationMethods( PwmSetting.HELPDESK_VERIFICATION_METHODS, VerificationMethodValue.EnabledState.required );
    }
}
