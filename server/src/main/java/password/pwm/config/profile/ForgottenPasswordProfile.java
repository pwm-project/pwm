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

import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ForgottenPasswordProfile extends AbstractProfile
{

    private static final ProfileType PROFILE_TYPE = ProfileType.ForgottenPassword;

    private Set<IdentityVerificationMethod> requiredRecoveryVerificationMethods;
    private Set<IdentityVerificationMethod> optionalRecoveryVerificationMethods;

    public ForgottenPasswordProfile( final String identifier, final Map<PwmSetting, StoredValue> storedValueMap )
    {
        super( identifier, storedValueMap );
    }


    @Override
    public String getDisplayName( final Locale locale )
    {
        return null;
    }

    public static ForgottenPasswordProfile makeFromStoredConfiguration( final StoredConfiguration storedConfiguration, final String identifier )
    {
        final Map<PwmSetting, StoredValue> valueMap = makeValueMap( storedConfiguration, identifier, PROFILE_TYPE.getCategory() );
        return new
                ForgottenPasswordProfile( identifier, valueMap );

    }

    @Override
    public ProfileType profileType( )
    {
        return PROFILE_TYPE;
    }

    public Set<IdentityVerificationMethod> requiredRecoveryAuthenticationMethods( )
    {
        if ( requiredRecoveryVerificationMethods == null )
        {
            requiredRecoveryVerificationMethods = readRecoveryAuthMethods( VerificationMethodValue.EnabledState.required );
        }
        return requiredRecoveryVerificationMethods;
    }

    public Set<IdentityVerificationMethod> optionalRecoveryAuthenticationMethods( )
    {
        if ( optionalRecoveryVerificationMethods == null )
        {
            optionalRecoveryVerificationMethods = readRecoveryAuthMethods( VerificationMethodValue.EnabledState.optional );
        }
        return optionalRecoveryVerificationMethods;
    }

    private Set<IdentityVerificationMethod> readRecoveryAuthMethods( final VerificationMethodValue.EnabledState enforcement )
    {
        return this.readVerificationMethods( PwmSetting.RECOVERY_VERIFICATION_METHODS, enforcement );
    }

    public int getMinOptionalRequired( )
    {
        final StoredValue configValue = storedValueMap.get( PwmSetting.RECOVERY_VERIFICATION_METHODS );
        final VerificationMethodValue.VerificationMethodSettings verificationMethodSettings = ( VerificationMethodValue.VerificationMethodSettings ) configValue.toNativeObject();
        return verificationMethodSettings.getMinOptionalRequired();
    }
}
