/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
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

    private Set<IdentityVerificationMethod> readRecoveryAuthMethods( final VerificationMethodValue.EnabledState enabledState )
    {
        return this.readVerificationMethods( PwmSetting.RECOVERY_VERIFICATION_METHODS, enabledState );
    }

    public int getMinOptionalRequired( )
    {
        final StoredValue configValue = storedValueMap.get( PwmSetting.RECOVERY_VERIFICATION_METHODS );
        final VerificationMethodValue.VerificationMethodSettings verificationMethodSettings = ( VerificationMethodValue.VerificationMethodSettings ) configValue.toNativeObject();
        return verificationMethodSettings.getMinOptionalRequired();
    }
}
