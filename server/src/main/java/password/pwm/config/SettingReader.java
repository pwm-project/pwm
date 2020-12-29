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

import password.pwm.bean.DomainID;
import password.pwm.bean.EmailItemBean;
import password.pwm.bean.PrivateKeyCertificate;
import password.pwm.config.profile.Profile;
import password.pwm.config.profile.ProfileDefinition;
import password.pwm.config.stored.StoredConfigKey;
import password.pwm.config.stored.StoredConfiguration;
import password.pwm.config.stored.StoredConfigurationUtil;
import password.pwm.config.value.FileValue;
import password.pwm.config.value.StoredValue;
import password.pwm.config.value.ValueTypeConverter;
import password.pwm.config.value.VerificationMethodValue;
import password.pwm.config.value.data.ActionConfiguration;
import password.pwm.config.value.data.ChallengeItemConfiguration;
import password.pwm.config.value.data.FormConfiguration;
import password.pwm.config.value.data.NamedSecretData;
import password.pwm.config.value.data.RemoteWebServiceConfiguration;
import password.pwm.config.value.data.UserPermission;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.PasswordData;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;

import java.lang.reflect.InvocationTargetException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class SettingReader
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( SettingReader.class );

    private final ProfileReader profileReader = new ProfileReader();

    private final StoredConfiguration storedConfiguration;
    private final String profileID;
    private final DomainID domainID;

    public SettingReader( final StoredConfiguration storedConfiguration, final String profileID, final DomainID domainID )
    {
        this.storedConfiguration = Objects.requireNonNull( storedConfiguration );
        this.profileID = profileID;
        this.domainID = Objects.requireNonNull( domainID );
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

    public Map<FileValue.FileInformation, FileValue.FileContent> readSettingAsFile( final PwmSetting pwmSetting )
    {
        return ValueTypeConverter.valueToFile( pwmSetting, readSetting( pwmSetting ) );
    }

    public List<ChallengeItemConfiguration> readSettingAsChallengeItems( final PwmSetting setting, final Locale locale )
    {
        final Map<String, List<ChallengeItemConfiguration>> storedValues = ValueTypeConverter.valueToChallengeItems ( readSetting( setting ) );
        final Map<Locale, List<ChallengeItemConfiguration>> availableLocaleMap = storedValues.entrySet().stream()
                .collect( Collectors.toUnmodifiableMap(
                        entry -> LocaleHelper.parseLocaleString( entry.getKey() ),
                        Map.Entry::getValue
                ) );

        final Locale matchedLocale = LocaleHelper.localeResolver( locale, availableLocaleMap.keySet() );

        return availableLocaleMap.get( matchedLocale );
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

    public PrivateKeyCertificate readSettingAsPrivateKey( final PwmSetting setting )
    {
        Objects.requireNonNull( setting );

        if ( PwmSettingSyntax.PRIVATE_KEY != setting.getSyntax() )
        {
            throw new IllegalArgumentException( "may not read PRIVATE_KEY value for setting: " + setting.toString() );
        }

        return ( PrivateKeyCertificate ) readSetting( setting ).toNativeObject();
    }

    public EmailItemBean readSettingAsEmail( final PwmSetting setting, final Locale locale )
    {
        final Map<Locale, EmailItemBean> availableLocaleMap = ValueTypeConverter.valueToLocalizedEmail( setting, readSetting( setting ) );
        final Locale matchedLocale = LocaleHelper.localeResolver( locale, availableLocaleMap.keySet() );
        return availableLocaleMap.get( matchedLocale );
    }

    public VerificationMethodValue.VerificationMethodSettings readVerificationMethods( final PwmSetting setting )
    {
        final StoredValue configValue = readSetting( setting );
        return ( VerificationMethodValue.VerificationMethodSettings ) configValue.toNativeObject();
    }


    public <T extends Profile> Map<String, T> getProfileMap( final ProfileDefinition profileDefinition )
    {
        return profileReader.getProfileMap( profileDefinition );
    }


    private class ProfileReader
    {
        private final Map<ProfileDefinition, Map> profileCache = new LinkedHashMap<>();

        public <T extends Profile> Map<String, T> getProfileMap( final ProfileDefinition profileDefinition )
        {
            return profileCache.computeIfAbsent( profileDefinition, ( p ) ->
            {
                final Map<String, T> returnMap = new LinkedHashMap<>();
                final Map<String, Profile> profileMap = profileMap( profileDefinition );
                for ( final Map.Entry<String, Profile> entry : profileMap.entrySet() )
                {
                    returnMap.put( entry.getKey(), ( T ) entry.getValue() );
                }
                return Collections.unmodifiableMap( returnMap );
            } );
        }

        private Map<String, Profile> profileMap( final ProfileDefinition profileDefinition )
        {
            if ( profileDefinition.getProfileFactoryClass().isEmpty() )
            {
                return Collections.emptyMap();
            }

            return profileIDsForCategory( profileDefinition.getCategory() ).stream()
                    .collect( Collectors.toUnmodifiableMap(
                        profileID -> profileID,
                        profileID -> newProfileForID( profileDefinition, profileID )
                    ) );
        }

        private Profile newProfileForID( final ProfileDefinition profileDefinition, final String profileID )
        {
            Objects.requireNonNull( profileDefinition );
            Objects.requireNonNull( profileID );

            final Optional<Class<? extends Profile.ProfileFactory>> optionalProfileFactoryClass = profileDefinition.getProfileFactoryClass();

            if ( optionalProfileFactoryClass.isPresent() )
            {
                final Profile.ProfileFactory profileFactory;
                try
                {
                    profileFactory = optionalProfileFactoryClass.get().getDeclaredConstructor().newInstance();
                    return profileFactory.makeFromStoredConfiguration( storedConfiguration, profileID );
                }
                catch ( final InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e )
                {
                    throw new IllegalStateException( "unable to create profile instance for " + profileDefinition );
                }
            }

            throw new IllegalStateException( "unable to create profile instance for " + profileDefinition + " ( profile factory class not defined )" );
        }

        public List<String> profileIDsForCategory( final PwmSettingCategory pwmSettingCategory )
        {
            final PwmSetting profileSetting = pwmSettingCategory.getProfileSetting().orElseThrow( IllegalStateException::new );
            return SettingReader.this.readSettingAsStringArray( profileSetting );
        }
    }

    private StoredValue readSetting( final PwmSetting setting )
    {
        if ( DomainID.systemId().equals( domainID ) )
        {
            if ( setting.getCategory().getScope() == PwmSettingScope.DOMAIN )
            {
                final String msg = "attempt to read DOMAIN scope setting '" + setting.toMenuLocationDebug( profileID, null ) + "' via system scope";
                final PwmUnrecoverableException pwmUnrecoverableException = PwmUnrecoverableException.newException( PwmError.ERROR_INTERNAL, msg );
                LOGGER.error( () -> pwmUnrecoverableException.getErrorInformation().toDebugStr(), pwmUnrecoverableException );
                //throw new IllegalStateException( msg );
            }
        }
        else
        {
            if ( setting.getCategory().getScope() == PwmSettingScope.SYSTEM )
            {
                final String msg = "attempt to read SYSTEM scope setting '" + setting.toMenuLocationDebug( profileID, null ) + "' via domain scope";
                final PwmUnrecoverableException pwmUnrecoverableException = PwmUnrecoverableException.newException( PwmError.ERROR_INTERNAL, msg );
                LOGGER.error( () -> pwmUnrecoverableException.getErrorInformation().toDebugStr(), pwmUnrecoverableException );
                //throw new IllegalStateException( msg );
            }
        }

        if ( setting.getFlags().contains( PwmSettingFlag.Deprecated ) )
        {
            LOGGER.warn( () -> "attempt to read deprecated config setting: " + setting.toMenuLocationDebug( profileID, null ) );
        }

        if ( StringUtil.isEmpty( profileID ) )
        {
            if ( setting.getCategory().hasProfiles() )
            {
                throw new IllegalStateException( "attempt to read profiled setting '" + setting.toMenuLocationDebug( profileID, null ) + "' via non-profile" );
            }
        }
        else
        {
            if ( !setting.getCategory().hasProfiles() )
            {
                throw new IllegalStateException( "attempt to read non-profiled setting '" + setting.toMenuLocationDebug( profileID, null ) + "' via profile" );
            }
        }

        final StoredConfigKey key = StoredConfigKey.forSetting( setting, profileID, domainID );
        return StoredConfigurationUtil.getValueOrDefault( storedConfiguration, key );
    }
}
