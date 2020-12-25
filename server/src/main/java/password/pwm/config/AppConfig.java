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

import password.pwm.AppProperty;
import password.pwm.PwmConstants;
import password.pwm.bean.DomainID;
import password.pwm.bean.PrivateKeyCertificate;
import password.pwm.config.profile.EmailServerProfile;
import password.pwm.config.profile.ProfileDefinition;
import password.pwm.config.stored.StoredConfiguration;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.PasswordData;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.java.LazySupplier;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogLevel;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmRandom;
import password.pwm.util.secure.PwmSecurityKey;
import password.pwm.util.secure.SecureService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class AppConfig
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( AppConfig.class );
    private final ConfigurationSuppliers configurationSuppliers = new ConfigurationSuppliers();

    private final StoredConfiguration storedConfiguration;
    private final SettingReader settingReader;
    private final Map<DomainID, DomainConfig> domainConfigMap;

    private PwmSecurityKey tempInstanceKey = null;

    public AppConfig( final StoredConfiguration storedConfiguration )
    {
        this.storedConfiguration = storedConfiguration;
        this.settingReader = new SettingReader( storedConfiguration, null, DomainID.systemId() );
        domainConfigMap = getDomainIDs().stream()
                .collect( Collectors.toUnmodifiableMap(
                        ( domainID ) -> domainID,
                        ( domainID ) -> new DomainConfig( this, domainID ) ) );
    }

    public List<DomainID> getDomainIDs()
    {
        final List<String> strings = settingReader.readSettingAsStringArray( PwmSetting.DOMAIN_LIST );
        return strings.stream().map( DomainID::create ).collect( Collectors.toUnmodifiableList() );
    }

    public Map<DomainID, DomainConfig> getDomainConfigs()
    {
        return domainConfigMap;
    }

    public DomainConfig getDefaultDomainConfig()
    {
        return domainConfigMap.get( PwmConstants.DOMAIN_ID_PLACEHOLDER );
    }

    public String readSettingAsString( final PwmSetting pwmSetting )
    {
        return settingReader.readSettingAsString( pwmSetting );
    }

    public String readAppProperty( final AppProperty property )
    {
        return configurationSuppliers.appPropertyOverrides.get().getOrDefault( property.getKey(), property.getDefaultValue() );
    }

    public Map<AppProperty, String> readAllNonDefaultAppProperties( )
    {
        final LinkedHashMap<AppProperty, String> nonDefaultProperties = new LinkedHashMap<>();
        for ( final AppProperty loopProperty : AppProperty.values() )
        {
            final String configuredValue = readAppProperty( loopProperty );
            final String defaultValue = loopProperty.getDefaultValue();
            if ( configuredValue != null && !configuredValue.equals( defaultValue ) )
            {
                nonDefaultProperties.put( loopProperty, configuredValue );
            }
        }
        return Collections.unmodifiableMap( nonDefaultProperties );
    }

    public StoredConfiguration getStoredConfiguration()
    {
        return storedConfiguration;
    }

    public PwmSecurityKey getSecurityKey() throws PwmUnrecoverableException
    {
        return configurationSuppliers.pwmSecurityKey.call();
    }

    public <E extends Enum<E>> Set<E> readSettingAsOptionList( final PwmSetting pwmSetting, final Class<E> enumClass )
    {
        return settingReader.readSettingAsOptionList( pwmSetting, enumClass );
    }

    public boolean readSettingAsBoolean( final PwmSetting pwmSetting )
    {
        return settingReader.readSettingAsBoolean( pwmSetting );
    }

    public List<String> readSettingAsStringArray( final PwmSetting pwmSetting )
    {
        return settingReader.readSettingAsStringArray( pwmSetting );
    }

    public PwmLogLevel getEventLogLocalDBLevel( )
    {
        return readSettingAsEnum( PwmSetting.EVENTS_LOCALDB_LOG_LEVEL, PwmLogLevel.class );
    }

    public boolean isDevDebugMode( )
    {
        return Boolean.parseBoolean( readAppProperty( AppProperty.LOGGING_DEV_OUTPUT ) );
    }

    public long readSettingAsLong( final PwmSetting pwmSetting )
    {
        return settingReader.readSettingAsLong( pwmSetting );
    }

    public Map<Locale, String> getKnownLocaleFlagMap( )
    {
        return configurationSuppliers.localeFlagMap.get();
    }

    public List<Locale> getKnownLocales( )
    {
        return List.copyOf( configurationSuppliers.localeFlagMap.get().keySet() );
    }

    public PrivateKeyCertificate readSettingAsPrivateKey( final PwmSetting setting )
    {
        return settingReader.readSettingAsPrivateKey( setting );
    }

    public String configurationHash( final SecureService secureService )
            throws PwmUnrecoverableException
    {
        return storedConfiguration.valueHash();
    }

    public <E extends Enum<E>> E readSettingAsEnum( final PwmSetting setting, final Class<E> enumClass )
    {
        return settingReader.readSettingAsEnum( setting, enumClass );
    }

    private class ConfigurationSuppliers
    {
        private final Supplier<Map<String, String>> appPropertyOverrides = new LazySupplier<>( () ->
                StringUtil.convertStringListToNameValuePair(
                        settingReader.readSettingAsStringArray( PwmSetting.APP_PROPERTY_OVERRIDES ), "=" ) );

        private final LazySupplier.CheckedSupplier<PwmSecurityKey, PwmUnrecoverableException> pwmSecurityKey
                = LazySupplier.checked( () ->
        {
            final PasswordData configValue = settingReader.readSettingAsPassword( PwmSetting.PWM_SECURITY_KEY );

            if ( configValue == null || configValue.getStringValue().isEmpty() )
            {
                final String errorMsg = "Security Key value is not configured, will generate temp value for use by runtime instance";
                final ErrorInformation errorInfo = new ErrorInformation( PwmError.ERROR_INVALID_SECURITY_KEY, errorMsg );
                LOGGER.warn( errorInfo::toDebugStr );
                if ( tempInstanceKey == null )
                {
                    tempInstanceKey = new PwmSecurityKey( PwmRandom.getInstance().alphaNumericString( 1024 ) );
                }
                return tempInstanceKey;
            }
            else
            {
                final int minSecurityKeyLength = Integer.parseInt( readAppProperty( AppProperty.SECURITY_CONFIG_MIN_SECURITY_KEY_LENGTH ) );
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
        } );

        private final Supplier<Map<Locale, String>> localeFlagMap = new LazySupplier<>( () ->
        {
            final String defaultLocaleAsString = PwmConstants.DEFAULT_LOCALE.toString();

            final List<String> inputList = readSettingAsStringArray( PwmSetting.KNOWN_LOCALES );
            final Map<String, String> inputMap = StringUtil.convertStringListToNameValuePair( inputList, "::" );

            // Sort the map by display name
            final Map<String, String> sortedMap = new TreeMap<>();
            for ( final String localeString : inputMap.keySet() )
            {
                final Locale theLocale = LocaleHelper.parseLocaleString( localeString );
                if ( theLocale != null )
                {
                    sortedMap.put( theLocale.getDisplayName(), localeString );
                }
            }

            final List<String> returnList = new ArrayList<>();

            //ensure default is first.
            returnList.add( defaultLocaleAsString );
            for ( final String localeString : sortedMap.values() )
            {
                if ( !defaultLocaleAsString.equals( localeString ) )
                {
                    returnList.add( localeString );
                }
            }

            final Map<Locale, String> localeFlagMap = new LinkedHashMap<>();
            for ( final String localeString : returnList )
            {
                final Locale loopLocale = LocaleHelper.parseLocaleString( localeString );
                if ( loopLocale != null )
                {
                    final String flagCode = inputMap.containsKey( localeString ) ? inputMap.get( localeString ) : loopLocale.getCountry();
                    localeFlagMap.put( loopLocale, flagCode );
                }
            }
            return Collections.unmodifiableMap( localeFlagMap );
        } );
    }

    public Map<String, EmailServerProfile> getEmailServerProfiles( )
    {
        return settingReader.getProfileMap( ProfileDefinition.EmailServers );
    }

    public boolean hasDbConfigured( )
    {
        return !StringUtil.isEmpty( readSettingAsString( PwmSetting.DATABASE_CLASS ) )
                && !StringUtil.isEmpty( readSettingAsString( PwmSetting.DATABASE_URL ) )
                && !StringUtil.isEmpty( readSettingAsString( PwmSetting.DATABASE_USERNAME ) )
                && readSettingAsPassword( PwmSetting.DATABASE_PASSWORD ) != null;
    }

    private PasswordData readSettingAsPassword( final PwmSetting setting )
    {
        return settingReader.readSettingAsPassword( setting );
    }


}
