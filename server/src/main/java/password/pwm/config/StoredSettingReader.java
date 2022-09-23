/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2021 The PWM Project
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

import password.pwm.PwmConstants;
import password.pwm.bean.DomainID;
import password.pwm.bean.EmailItemBean;
import password.pwm.bean.PrivateKeyCertificate;
import password.pwm.bean.ProfileID;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.config.profile.Profile;
import password.pwm.config.profile.ProfileDefinition;
import password.pwm.config.profile.ProfileUtility;
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
import password.pwm.i18n.PwmLocaleBundle;
import password.pwm.util.PasswordData;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.java.CollectorUtil;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmHashAlgorithm;

import java.lang.reflect.InvocationTargetException;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class StoredSettingReader implements SettingReader
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( StoredSettingReader.class );

    private final StoredConfiguration storedConfiguration;
    private final ProfileID profileID;
    private final DomainID domainID;

    private final Map<ProfileDefinition, Map> profileCache;
    private final String valueHash;

    public StoredSettingReader( final StoredConfiguration storedConfiguration, final ProfileID profileID, final DomainID domainID )
    {
        this.storedConfiguration = Objects.requireNonNull( storedConfiguration );
        this.profileID = profileID;
        this.domainID = Objects.requireNonNull( domainID );
        this.valueHash = valueHash( storedConfiguration, domainID );
        this.profileCache = profileID == null
                ? ProfileReader.makeCacheMap( storedConfiguration, domainID )
                : Collections.emptyMap();
    }

    @Override
    public List<UserPermission> readSettingAsUserPermission( final PwmSetting setting )
    {
        return ValueTypeConverter.valueToUserPermissions( readSetting( setting ) );
    }

    @Override
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

    @Override
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

    public List<DataStorageMethod> readGenericStorageLocations( final PwmSetting setting )
    {
        final String input = readSettingAsString( setting );

        return Arrays.stream( input.split( "-" ) )
                .map( s ->  JavaHelper.readEnumFromString( DataStorageMethod.class, s ) )
                .flatMap( Optional::stream )
                .collect( Collectors.toUnmodifiableList() );
    }

    public PrivateKeyCertificate readSettingAsPrivateKey( final PwmSetting setting )
    {
        Objects.requireNonNull( setting );

        if ( PwmSettingSyntax.PRIVATE_KEY != setting.getSyntax() )
        {
            throw new IllegalArgumentException( "may not read PRIVATE_KEY value for setting: " + setting );
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


    public <T extends Profile> Map<ProfileID, T> getProfileMap( final ProfileDefinition profileDefinition )
    {
        if ( profileID != null )
        {
            throw new IllegalStateException( "can not read profile map from profiled setting reader" );
        }
        return profileCache.get( profileDefinition );
    }


    private static class ProfileReader
    {
        public static Map<ProfileDefinition, Map> makeCacheMap(
                final StoredConfiguration storedConfiguration,
                final DomainID domainID
        )
        {
            return CollectionUtil.enumStream( ProfileDefinition.class )
                    .filter( profileDefinition -> domainID.inScope( profileDefinition.getCategory().getScope() ) )
                    .collect( CollectorUtil.toUnmodifiableLinkedMap(
                            profileDefinition -> profileDefinition,
                            profileDefinition -> profileMap( profileDefinition, storedConfiguration, domainID )
                    ) );
        }

        private static <T extends Profile> Map<ProfileID, T> profileMap(
                final ProfileDefinition profileDefinition,
                final StoredConfiguration storedConfiguration,
                final DomainID domainID
        )
        {
            if ( profileDefinition.getProfileFactoryClass().isEmpty() )
            {
                return Collections.emptyMap();
            }

            return ProfileUtility.profileIDsForCategory( storedConfiguration, domainID, profileDefinition.getCategory() ).stream()
                    .collect( CollectorUtil.toUnmodifiableLinkedMap(
                            Function.identity(),
                            profileID -> newProfileForID( profileDefinition, storedConfiguration, domainID, profileID )
                    ) );
        }

        private static <T extends Profile> T newProfileForID(
                final ProfileDefinition profileDefinition,
                final StoredConfiguration storedConfiguration,
                final DomainID domainID,
                final ProfileID profileID
        )
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
                    return ( T ) profileFactory.makeFromStoredConfiguration( storedConfiguration, domainID, profileID );
                }
                catch ( final InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e )
                {
                    throw new IllegalStateException( "unable to create profile instance for " + profileDefinition );
                }
            }

            throw new IllegalStateException( "unable to create profile instance for " + profileDefinition + " ( profile factory class not defined )" );
        }
    }

    private StoredValue readSetting( final PwmSetting setting )
    {
        if ( DomainID.systemId().equals( domainID ) )
        {
            if ( setting.getCategory().getScope() == PwmSettingScope.DOMAIN )
            {
                final String msg = "attempt to read domain scope setting '" + setting.toMenuLocationDebug( profileID, null ) + "' as system scope";
                final PwmUnrecoverableException pwmUnrecoverableException = PwmUnrecoverableException.newException( PwmError.ERROR_INTERNAL, msg );
                throw new IllegalStateException( msg, pwmUnrecoverableException );
            }
        }
        else
        {
            if ( setting.getCategory().getScope() == PwmSettingScope.SYSTEM )
            {
                final String msg = "attempt to read system scope setting '" + setting.toMenuLocationDebug( profileID, null ) + "' as domain scope";
                final PwmUnrecoverableException pwmUnrecoverableException = PwmUnrecoverableException.newException( PwmError.ERROR_INTERNAL, msg );
                throw new IllegalStateException( msg, pwmUnrecoverableException );
            }
        }

        if ( setting.getFlags().contains( PwmSettingFlag.Deprecated ) )
        {
            LOGGER.warn( () -> "attempt to read deprecated config setting: " + setting.toMenuLocationDebug( profileID, null ) );
        }

        if ( profileID == null )
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

    public Optional<Map<Locale, String>> readLocalizedBundle( final PwmLocaleBundle className, final String keyName )
    {
        final Map<String, String> storedValue = storedConfiguration.readLocaleBundleMap( className, keyName, domainID );
        if ( storedValue == null || storedValue.isEmpty() )
        {
            return Optional.empty();
        }

        return Optional.of( storedValue.entrySet().stream().collect( Collectors.toUnmodifiableMap(
                entry -> LocaleHelper.parseLocaleString( entry.getKey() ),
                Map.Entry::getValue
        ) ) );
    }

    @Override
    public String getValueHash()
    {
        return valueHash;
    }

    private static String valueHash( final StoredConfiguration storedConfiguration, final DomainID domainID )
    {
        final MessageDigest messageDigest = PwmHashAlgorithm.SHA512.newMessageDigest();
        messageDigest.update( domainID.stringValue().getBytes( PwmConstants.DEFAULT_CHARSET ) );

        CollectionUtil.iteratorToStream( storedConfiguration.keys() )
                .filter( key -> Objects.equals( key.getDomainID(), domainID ) )
                .map( storedConfiguration::readStoredValue )
                .flatMap( Optional::stream )
                .map( StoredValue::valueHash )
                .forEach( s -> messageDigest.update( s.getBytes( PwmConstants.DEFAULT_CHARSET ) ) );

        return JavaHelper.binaryArrayToHex( messageDigest.digest() );
    }
}
