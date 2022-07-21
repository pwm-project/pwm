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

import password.pwm.AppProperty;
import password.pwm.PwmConstants;
import password.pwm.bean.DomainID;
import password.pwm.bean.PrivateKeyCertificate;
import password.pwm.bean.SessionLabel;
import password.pwm.config.option.CertificateMatchingMode;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.config.profile.EmailServerProfile;
import password.pwm.config.profile.ProfileDefinition;
import password.pwm.config.stored.StoredConfiguration;
import password.pwm.config.stored.StoredConfigurationFactory;
import password.pwm.config.value.FileValue;
import password.pwm.config.value.data.UserPermission;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmInternalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.i18n.PwmLocaleBundle;
import password.pwm.util.PasswordData;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.java.LazySupplier;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogLevel;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmRandom;
import password.pwm.util.secure.PwmSecurityKey;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class AppConfig implements SettingReader
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( AppConfig.class );

    private final StoredConfiguration storedConfiguration;
    private final StoredSettingReader settingReader;
    private final Map<DomainID, DomainConfig> domainConfigMap;
    private final Set<String> domainIDs;

    private final PwmSecurityKey applicationSecurityKey;
    private final Map<AppProperty, String> appPropertyOverrides;
    private final Map<Locale, String> localeFlagMap;

    private static final Supplier<AppConfig> DEFAULT_CONFIG = new LazySupplier<>( AppConfig::makeDefaultConfig );

    private static AppConfig makeDefaultConfig()
    {
        try
        {
            return new AppConfig( StoredConfigurationFactory.newConfig() );
        }
        catch ( final PwmUnrecoverableException e )
        {
            throw new IllegalStateException( e );
        }
    }

    public static AppConfig defaultConfig()
    {
        return DEFAULT_CONFIG.get();
    }

    public AppConfig( final StoredConfiguration storedConfiguration )
    {
        this.storedConfiguration = storedConfiguration;
        this.settingReader = new StoredSettingReader( storedConfiguration, null, DomainID.systemId() );
        this.appPropertyOverrides = makeAppPropertyOverrides( settingReader );

        this.applicationSecurityKey = makeAppSecurityKey( this );

        this.localeFlagMap = makeLocaleFlagMap( this );

        this.domainIDs = Collections.unmodifiableSet(  new TreeSet<>(
                settingReader.readSettingAsStringArray( PwmSetting.DOMAIN_LIST ).stream()
                .collect( Collectors.toUnmodifiableSet() ) ) );

        this.domainConfigMap = Collections.unmodifiableMap(  domainIDs.stream()
                .collect( CollectionUtil.collectorToLinkedMap(
                        DomainID::create,
                        ( domainID ) -> new DomainConfig( this, DomainID.create( domainID ) ) ) ) );
    }

    public Set<String> getDomainIDs()
    {
        return domainIDs;
    }

    public Map<DomainID, DomainConfig> getDomainConfigs()
    {
        return domainConfigMap;
    }

    public DomainID getAdminDomainID()
            throws PwmUnrecoverableException
    {
        return getDomainConfigs().values().stream()
                .filter( DomainConfig::isAdministrativeDomain )
                .findFirst()
                .map( DomainConfig::getDomainID )
                .orElseThrow( () -> PwmUnrecoverableException.newException( PwmError.ERROR_INTERNAL, "administrative domain is not defined" ) );
    }

    public DomainConfig getAdminDomain()
            throws PwmUnrecoverableException
    {
        return getDomainConfigs().get( getAdminDomainID() );
    }

    public String readSettingAsString( final PwmSetting pwmSetting )
    {
        return settingReader.readSettingAsString( pwmSetting );
    }

    public String readAppProperty( final AppProperty property )
    {
        return appPropertyOverrides.getOrDefault( property, property.getDefaultValue() );
    }

    public boolean readBooleanAppProperty( final AppProperty appProperty )
    {
        return Boolean.parseBoolean( readAppProperty( appProperty ) );
    }

    public TimeDuration readDurationAppProperty( final AppProperty appProperty )
    {
        final TimeDuration.Unit unit;
        final String lcasePropName = appProperty.getKey().toLowerCase( PwmConstants.DEFAULT_LOCALE );
        if ( lcasePropName.endsWith( "ms" ) )
        {
            unit = TimeDuration.Unit.MILLISECONDS;
        }
        else if ( lcasePropName.endsWith( "seconds" ) )
        {
            unit = TimeDuration.Unit.SECONDS;
        }
        else
        {
            throw new IllegalStateException( "can't read appProperty '" + appProperty.getKey() + "' as duration, unknown time unit" );
        }

        return TimeDuration.of( Long.parseLong( readAppProperty( appProperty ) ), unit );
    }

    public Map<AppProperty, String> readAllNonDefaultAppProperties( )
    {
        return appPropertyOverrides;
    }

    public Map<AppProperty, String> readAllAppProperties()
    {
          return Collections.unmodifiableMap( CollectionUtil.enumStream( AppProperty.class )
                  .collect( CollectionUtil.collectorToLinkedMap(
                          Function.identity(),
                          this::readAppProperty
                  ) ) );

    }

    public StoredConfiguration getStoredConfiguration()
    {
        return storedConfiguration;
    }

    public PwmSecurityKey getSecurityKey() throws PwmUnrecoverableException
    {
        return applicationSecurityKey;
    }

    @Override
    public <E extends Enum<E>> Set<E> readSettingAsOptionList( final PwmSetting pwmSetting, final Class<E> enumClass )
    {
        return settingReader.readSettingAsOptionList( pwmSetting, enumClass );
    }

    @Override
    public boolean readSettingAsBoolean( final PwmSetting pwmSetting )
    {
        return settingReader.readSettingAsBoolean( pwmSetting );
    }

    @Override
    public List<String> readSettingAsStringArray( final PwmSetting pwmSetting )
    {
        return settingReader.readSettingAsStringArray( pwmSetting );
    }

    public PwmLogLevel getEventLogLocalDBLevel()
    {
        return readSettingAsEnum( PwmSetting.EVENTS_LOCALDB_LOG_LEVEL, PwmLogLevel.class );
    }

    public boolean isDevDebugMode()
    {
        return Boolean.parseBoolean( readAppProperty( AppProperty.LOGGING_DEV_OUTPUT ) );
    }

    @Override
    public long readSettingAsLong( final PwmSetting pwmSetting )
    {
        return settingReader.readSettingAsLong( pwmSetting );
    }

    public Map<Locale, String> getKnownLocaleFlagMap( )
    {
        return localeFlagMap;
    }

    public List<Locale> getKnownLocales( )
    {
        return List.copyOf( localeFlagMap.keySet() );
    }

    @Override
    public PrivateKeyCertificate readSettingAsPrivateKey( final PwmSetting setting )
    {
        return settingReader.readSettingAsPrivateKey( setting );
    }

    @Override
    public <E extends Enum<E>> E readSettingAsEnum( final PwmSetting setting, final Class<E> enumClass )
    {
        return settingReader.readSettingAsEnum( setting, enumClass );
    }

    @Override
    public Map<FileValue.FileInformation, FileValue.FileContent> readSettingAsFile( final PwmSetting pwmSetting )
    {
        return settingReader.readSettingAsFile( pwmSetting );
    }

    @Override
    public List<X509Certificate> readSettingAsCertificate( final PwmSetting pwmSetting )
    {
        return settingReader.readSettingAsCertificate( pwmSetting );
    }

    @Override
    public String readSettingAsLocalizedString( final PwmSetting setting, final Locale locale )
    {
        return settingReader.readSettingAsLocalizedString( setting, locale );
    }

    @Override
    public List<String> readSettingAsLocalizedStringArray( final PwmSetting setting, final Locale locale )
    {
        return settingReader.readSettingAsLocalizedStringArray( setting, locale );
    }

    @Override
    public List<UserPermission> readSettingAsUserPermission( final PwmSetting pwmSetting )
    {
        return settingReader.readSettingAsUserPermission( pwmSetting );
    }

    @Override
    public Optional<Map<Locale, String>> readLocalizedBundle( final PwmLocaleBundle className, final String keyName )
    {
        return settingReader.readLocalizedBundle( className, keyName );
    }

    public List<DataStorageMethod> readGenericStorageLocations( final PwmSetting setting )
    {
        return settingReader.readGenericStorageLocations( setting );
    }

    public Map<String, EmailServerProfile> getEmailServerProfiles( )
    {
        return settingReader.getProfileMap( ProfileDefinition.EmailServers );
    }

    public CertificateMatchingMode readCertificateMatchingMode()
    {
        final CertificateMatchingMode mode = readSettingAsEnum( PwmSetting.CERTIFICATE_VALIDATION_MODE, CertificateMatchingMode.class );
        return mode == null
                ? CertificateMatchingMode.CA_ONLY
                : mode;
    }

    public boolean hasDbConfigured( )
    {
        return StringUtil.notEmpty( readSettingAsString( PwmSetting.DATABASE_CLASS ) )
                && StringUtil.notEmpty( readSettingAsString( PwmSetting.DATABASE_URL ) )
                && StringUtil.notEmpty( readSettingAsString( PwmSetting.DATABASE_USERNAME ) )
                && readSettingAsPassword( PwmSetting.DATABASE_PASSWORD ) != null;
    }

    @Override
    public PasswordData readSettingAsPassword( final PwmSetting setting )
    {
        return settingReader.readSettingAsPassword( setting );
    }

    public boolean isMultiDomain()
    {
        return this.getDomainConfigs().size() > 1;
    }

    private static Map<AppProperty, String> makeAppPropertyOverrides( final SettingReader settingReader )
    {
        final Map<String, String> stringMap =  StringUtil.convertStringListToNameValuePair(
                settingReader.readSettingAsStringArray( PwmSetting.APP_PROPERTY_OVERRIDES ), "=" );

        final Map<AppProperty, String> appPropertyMap = new EnumMap<>( AppProperty.class );
        for ( final Map.Entry<String, String> stringEntry : stringMap.entrySet() )
        {
            AppProperty.forKey( stringEntry.getKey() )
                    .ifPresent( appProperty ->
                    {
                       final String defaultValue = appProperty.getDefaultValue();
                       final String value = stringEntry.getValue();
                       if ( !Objects.equals( defaultValue, value ) )
                       {
                           appPropertyMap.put( appProperty, value );
                       }
                    } );
        }

        return Collections.unmodifiableMap( appPropertyMap );
    }

    public boolean isSmsConfigured()
    {
        final String gatewayUrl = readSettingAsString( PwmSetting.SMS_GATEWAY_URL );
        final String gatewayUser = readSettingAsString( PwmSetting.SMS_GATEWAY_USER );
        final PasswordData gatewayPass = readSettingAsPassword( PwmSetting.SMS_GATEWAY_PASSWORD );
        if ( gatewayUrl == null || gatewayUrl.length() < 1 )
        {
            return false;
        }

        if ( gatewayUser != null && gatewayUser.length() > 0 && ( gatewayPass == null ) )
        {
            return false;
        }

        return true;
    }


    private static PwmSecurityKey makeAppSecurityKey( final AppConfig appConfig )
    {
        try
        {
            final PasswordData configValue = appConfig.readSettingAsPassword( PwmSetting.PWM_SECURITY_KEY );

            if ( configValue == null || configValue.getStringValue().isEmpty() )
            {
                final String errorMsg = "Security Key value is not configured, will generate temp value for use by runtime instance";
                final ErrorInformation errorInfo = new ErrorInformation( PwmError.ERROR_INVALID_SECURITY_KEY, errorMsg );
                LOGGER.warn( SessionLabel.SYSTEM_LABEL, errorInfo::toDebugStr );
                return new PwmSecurityKey( PwmRandom.getInstance().alphaNumericString( 1024 ) );
            }
            else
            {
                final int minSecurityKeyLength = Integer.parseInt( appConfig.readAppProperty( AppProperty.SECURITY_CONFIG_MIN_SECURITY_KEY_LENGTH ) );
                if ( configValue.getStringValue().length() < minSecurityKeyLength )
                {
                    final String errorMsg = "Security Key must be greater than 32 characters in length";
                    final ErrorInformation errorInfo = new ErrorInformation( PwmError.ERROR_INVALID_SECURITY_KEY, errorMsg );
                    throw new PwmUnrecoverableException( errorInfo );
                }

                try
                {
                    return new PwmSecurityKey( configValue.getStringValue() );
                }
                catch ( final Exception e )
                {
                    final String errorMsg = "unexpected error generating Security Key crypto: " + e.getMessage();
                    final ErrorInformation errorInfo = new ErrorInformation( PwmError.ERROR_INVALID_SECURITY_KEY, errorMsg );
                    LOGGER.error( errorInfo::toDebugStr, e );
                    throw new PwmUnrecoverableException( errorInfo );
                }
            }
        }
        catch ( final PwmUnrecoverableException e )
        {
            throw PwmInternalException.fromPwmException( "error reading security key from configuration", e );
        }
    }

    private static Map<Locale, String> makeLocaleFlagMap( final AppConfig appConfig )
    {
        final String defaultLocaleAsString = PwmConstants.DEFAULT_LOCALE.toString();

        final List<String> inputList = appConfig.readSettingAsStringArray( PwmSetting.KNOWN_LOCALES );
        final Map<String, String> inputMap = StringUtil.convertStringListToNameValuePair( inputList, "::" );

        final Map<String, String> sortedMap = new TreeMap<>( inputMap.keySet().stream()
                .collect( Collectors.toMap(
                        str -> LocaleHelper.parseLocaleString( str ).getDisplayName(),
                        Function.identity()
                ) ) );

        final List<String> returnList = new ArrayList<>( sortedMap.size() + 1 );

        //ensure default is first.
        returnList.add( defaultLocaleAsString );
        returnList.addAll( sortedMap.values().stream()
                .filter( str -> !Objects.equals( defaultLocaleAsString, str ) )
                .collect( Collectors.toList() ) );

        final Map<Locale, String> localeFlagMap = new LinkedHashMap<>( returnList.size() );
        for ( final String localeString : returnList )
        {
            final Locale loopLocale = LocaleHelper.parseLocaleString( localeString );
            if ( loopLocale != null )
            {
                final String flagCode = inputMap.getOrDefault( localeString, loopLocale.getCountry() );
                localeFlagMap.put( loopLocale, flagCode );
            }
        }
        return Collections.unmodifiableMap( localeFlagMap );
    }

    @Override
    public String getValueHash()
    {
        return settingReader.getValueHash();
    }
}
