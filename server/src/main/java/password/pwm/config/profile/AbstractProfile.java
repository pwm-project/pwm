/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2020 The PWM Project
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

import password.pwm.PwmConstants;
import password.pwm.config.PwmSetting;
import password.pwm.config.SettingReader;
import password.pwm.config.option.IdentityVerificationMethod;
import password.pwm.config.stored.StoredConfiguration;
import password.pwm.config.value.StoredValue;
import password.pwm.config.value.VerificationMethodValue;
import password.pwm.config.value.data.ActionConfiguration;
import password.pwm.config.value.data.FormConfiguration;
import password.pwm.config.value.data.UserPermission;
import password.pwm.util.PasswordData;

import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public abstract class AbstractProfile implements Profile
{
    private final String identifier;
    private final StoredConfiguration storedConfiguration;
    private final SettingReader settingReader;

    AbstractProfile( final String identifier, final StoredConfiguration storedConfiguration )
    {
        this.identifier = identifier;
        this.storedConfiguration = storedConfiguration;
        this.settingReader = new SettingReader( storedConfiguration, identifier, PwmConstants.DOMAIN_ID_PLACEHOLDER );
    }

    @Override
    public String getIdentifier( )
    {
        return identifier;
    }

    @Override
    public String getDisplayName( final Locale locale )
    {
        return getIdentifier();
    }

    public List<UserPermission> readSettingAsUserPermission( final PwmSetting setting )
    {
        return settingReader.readSettingAsUserPermission( setting );
    }

    public String readSettingAsString( final PwmSetting setting )
    {
        return settingReader.readSettingAsString( setting );
    }

    public List<String> readSettingAsStringArray( final PwmSetting setting )
    {
        return settingReader.readSettingAsStringArray( setting );
    }

    public List<FormConfiguration> readSettingAsForm( final PwmSetting setting )
    {
        return settingReader.readSettingAsForm( setting );
    }

    public <E extends Enum<E>> Set<E> readSettingAsOptionList( final PwmSetting setting, final Class<E> enumClass )
    {
        return settingReader.readSettingAsOptionList( setting, enumClass );
    }

    public <E extends Enum<E>> E readSettingAsEnum( final PwmSetting setting, final Class<E> enumClass )
    {
        return settingReader.readSettingAsEnum( setting, enumClass );
    }

    public List<ActionConfiguration> readSettingAsAction( final PwmSetting setting )
    {
        return settingReader.readSettingAsAction( setting );
    }

    public List<X509Certificate> readSettingAsCertificate( final PwmSetting setting )
    {
        return settingReader.readSettingAsCertificate( setting );
    }

    public boolean readSettingAsBoolean( final PwmSetting setting )
    {
        return settingReader.readSettingAsBoolean( setting );
    }

    public long readSettingAsLong( final PwmSetting setting )
    {
        return settingReader.readSettingAsLong( setting );
    }

    public String readSettingAsLocalizedString( final PwmSetting setting, final Locale locale )
    {
        return settingReader.readSettingAsLocalizedString( setting, locale );
    }

    public PasswordData readSettingAsPassword( final PwmSetting setting )
    {
        return settingReader.readSettingAsPassword( setting );
    }

    @Override
    public List<UserPermission> profilePermissions( )
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

    Set<IdentityVerificationMethod> readVerificationMethods( final PwmSetting setting, final VerificationMethodValue.EnabledState enabledState )
    {
        final Set<IdentityVerificationMethod> result = EnumSet.noneOf( IdentityVerificationMethod.class );
        final StoredValue configValue = readSetting( setting );
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

    protected StoredValue readSetting( final PwmSetting setting )
    {
        if ( !setting.getCategory().hasProfiles() )
        {
            throw new IllegalStateException( "attempt to read non-profiled setting '" + setting.getKey() + "' via profile" );
        }
        return storedConfiguration.readSetting( setting, getIdentifier() );
    }

}
