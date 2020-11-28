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

package password.pwm.config;

import password.pwm.config.stored.StoredConfiguration;
import password.pwm.config.value.StoredValue;
import password.pwm.config.value.ValueTypeConverter;
import password.pwm.config.value.data.ActionConfiguration;
import password.pwm.config.value.data.FormConfiguration;
import password.pwm.config.value.data.NamedSecretData;
import password.pwm.config.value.data.RemoteWebServiceConfiguration;
import password.pwm.config.value.data.UserPermission;
import password.pwm.util.PasswordData;
import password.pwm.util.java.StringUtil;

import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class SettingReader
{
    private final StoredConfiguration storedConfiguration;
    private final String profileID;
    private final String domainID;

    public SettingReader( final StoredConfiguration storedConfiguration, final String profileID, final String domainID )
    {
        this.storedConfiguration = Objects.requireNonNull( storedConfiguration );
        this.profileID = profileID;
        this.domainID = domainID;
    }

    public List<UserPermission> readSettingAsUserPermission( final PwmSetting setting )
    {
        return ValueTypeConverter.valueToUserPermissions( readSetting( setting ) );
    }

    public String readSettingAsString( final PwmSetting setting )
    {
        return ValueTypeConverter.valueToString( readSetting( setting ) );
    }

    public List<String> readSettingAsStringArray( final PwmSetting setting )
    {
        return ValueTypeConverter.valueToStringArray( readSetting( setting ) );
    }

    public List<String> readSettingAsLocalizedStringArray( final PwmSetting setting, final Locale locale )
    {
        return ValueTypeConverter.valueToLocalizedStringArray( readSetting( setting ), locale );
    }

    public List<FormConfiguration> readSettingAsForm( final PwmSetting setting )
    {
        return ValueTypeConverter.valueToForm( readSetting( setting ) );
    }

    public <E extends Enum<E>> Set<E> readSettingAsOptionList( final PwmSetting setting, final Class<E> enumClass )
    {
        return ValueTypeConverter.valueToOptionList( setting, readSetting( setting ), enumClass );
    }

    public <E extends Enum<E>> E readSettingAsEnum( final PwmSetting setting, final Class<E> enumClass )
    {
        return ValueTypeConverter.valueToEnum( setting, readSetting( setting ), enumClass );
    }

    public List<ActionConfiguration> readSettingAsAction( final PwmSetting setting )
    {
        return ValueTypeConverter.valueToAction( setting, readSetting( setting ) );
    }

    public List<X509Certificate> readSettingAsCertificate( final PwmSetting setting )
    {
        return ValueTypeConverter.valueToX509Certificates( setting, readSetting( setting ) );
    }

    public boolean readSettingAsBoolean( final PwmSetting setting )
    {
        return ValueTypeConverter.valueToBoolean( readSetting( setting ) );
    }

    public long readSettingAsLong( final PwmSetting setting )
    {
        return ValueTypeConverter.valueToLong( readSetting( setting ) );
    }

    public String readSettingAsLocalizedString( final PwmSetting setting, final Locale locale )
    {
        return ValueTypeConverter.valueToLocalizedString( readSetting( setting ), locale );
    }

    public PasswordData readSettingAsPassword( final PwmSetting setting )
    {
        return ValueTypeConverter.valueToPassword( readSetting( setting ) );
    }

    public List<RemoteWebServiceConfiguration> readSettingAsRemoteWebService( final PwmSetting pwmSetting )
    {
        return ValueTypeConverter.valueToRemoteWebServiceConfiguration( readSetting( pwmSetting ) );
    }

    public Map<String, NamedSecretData> readSettingAsNamedPasswords( final PwmSetting setting )
    {
        return ValueTypeConverter.valueToNamedPassword( readSetting( setting ) );
    }

    private StoredValue readSetting( final PwmSetting setting )
    {
        if ( StringUtil.isEmpty( profileID ) )
        {
            if ( setting.getCategory().hasProfiles() )
            {
                throw new IllegalStateException( "attempt to read profiled setting '" + setting.getKey() + "' via non-profile" );
            }
        }
        else
        {
            if ( !setting.getCategory().hasProfiles() )
            {
                throw new IllegalStateException( "attempt to read non-profiled setting '" + setting.getKey() + "' via profile" );
            }
        }

        return storedConfiguration.readSetting( setting, profileID );
    }
}
