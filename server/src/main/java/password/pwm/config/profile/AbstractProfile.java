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

import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.SettingReader;
import password.pwm.config.StoredValue;
import password.pwm.config.option.IdentityVerificationMethod;
import password.pwm.config.option.MessageSendMethod;
import password.pwm.config.stored.StoredConfiguration;
import password.pwm.config.value.VerificationMethodValue;
import password.pwm.config.value.data.ActionConfiguration;
import password.pwm.config.value.data.FormConfiguration;
import password.pwm.config.value.data.UserPermission;
import password.pwm.util.PasswordData;

import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public abstract class AbstractProfile implements Profile, SettingReader
{
    private final String identifier;
    private final StoredConfiguration storedConfiguration;

    AbstractProfile( final String identifier, final StoredConfiguration storedConfiguration )
    {
        this.identifier = identifier;
        this.storedConfiguration = storedConfiguration;
    }

    @Override
    public String getIdentifier( )
    {
        return identifier;
    }

    public String getDisplayName( final Locale locale )
    {
        return getIdentifier();
    }

    public List<UserPermission> readSettingAsUserPermission( final PwmSetting setting )
    {
        return Configuration.JavaTypeConverter.valueToUserPermissions( storedConfiguration.readSetting( setting, identifier ) );
    }

    public String readSettingAsString( final PwmSetting setting )
    {
        return Configuration.JavaTypeConverter.valueToString( storedConfiguration.readSetting( setting, identifier ) );
    }

    @Override
    public List<String> readSettingAsStringArray( final PwmSetting setting )
    {
        return Configuration.JavaTypeConverter.valueToStringArray( storedConfiguration.readSetting( setting, identifier ) );
    }

    @Override
    public List<FormConfiguration> readSettingAsForm( final PwmSetting setting )
    {
        return Configuration.JavaTypeConverter.valueToForm( storedConfiguration.readSetting( setting, identifier ) );
    }

    @Override
    public <E extends Enum<E>> Set<E> readSettingAsOptionList( final PwmSetting setting, final Class<E> enumClass )
    {
        return Configuration.JavaTypeConverter.valueToOptionList( setting, storedConfiguration.readSetting( setting, identifier ), enumClass );
    }

    @Override
    public <E extends Enum<E>> E readSettingAsEnum( final PwmSetting setting, final Class<E> enumClass )
    {
        final StoredValue value = storedConfiguration.readSetting( setting, identifier );
        final E returnValue = Configuration.JavaTypeConverter.valueToEnum( setting, value, enumClass );
        if ( MessageSendMethod.class.equals( enumClass ) )
        {
            Configuration.deprecatedSettingException( setting, this.getIdentifier(), ( MessageSendMethod ) returnValue );
        }

        return returnValue;
    }

    public List<ActionConfiguration> readSettingAsAction( final PwmSetting setting )
    {
        return Configuration.JavaTypeConverter.valueToAction( setting, storedConfiguration.readSetting( setting, identifier ) );
    }

    @Override
    public List<X509Certificate> readSettingAsCertificate( final PwmSetting setting )
    {
        return Configuration.JavaTypeConverter.valueToX509Certificates( setting, storedConfiguration.readSetting( setting, identifier ) );
    }

    @Override
    public boolean readSettingAsBoolean( final PwmSetting setting )
    {
        return Configuration.JavaTypeConverter.valueToBoolean( storedConfiguration.readSetting( setting, identifier ) );
    }

    @Override
    public long readSettingAsLong( final PwmSetting setting )
    {
        return Configuration.JavaTypeConverter.valueToLong( storedConfiguration.readSetting( setting, identifier ) );
    }

    @Override
    public String readSettingAsLocalizedString( final PwmSetting setting, final Locale locale )
    {
        return Configuration.JavaTypeConverter.valueToLocalizedString( storedConfiguration.readSetting( setting, identifier ), locale );
    }

    @Override
    public PasswordData readSettingAsPassword( final PwmSetting setting )
    {
        return Configuration.JavaTypeConverter.valueToPassword( storedConfiguration.readSetting( setting, identifier ) );
    }

    @Override
    public List<UserPermission> getPermissionMatches( )
    {
        final Optional<PwmSetting> optionalQueryMatchSetting = profileType().getQueryMatch();
        if ( optionalQueryMatchSetting.isPresent() )
        {
            return readSettingAsUserPermission( optionalQueryMatchSetting.get() );
        }
        return Collections.emptyList();
    }

    protected StoredConfiguration getStoredConfiguration()
    {
        return storedConfiguration;
    }

    Set<IdentityVerificationMethod> readVerificationMethods( final PwmSetting pwmSetting, final VerificationMethodValue.EnabledState enabledState )
    {
        final Set<IdentityVerificationMethod> result = new LinkedHashSet<>();
        final StoredValue configValue = storedConfiguration.readSetting( pwmSetting, identifier );
        final VerificationMethodValue.VerificationMethodSettings verificationMethodSettings = ( VerificationMethodValue.VerificationMethodSettings ) configValue.toNativeObject();

        for ( final IdentityVerificationMethod recoveryVerificationMethods : IdentityVerificationMethod.availableValues() )
        {
            if ( verificationMethodSettings.getMethodSettings().containsKey( recoveryVerificationMethods ) )
            {
                if ( verificationMethodSettings.getMethodSettings().get( recoveryVerificationMethods ).getEnabledState() == enabledState )
                {
                    result.add( recoveryVerificationMethods );
                }
            }
        }
        return result;
    }

}
