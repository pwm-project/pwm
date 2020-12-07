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
import password.pwm.config.stored.StoredConfiguration;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.PasswordData;
import password.pwm.util.java.LazySupplier;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmRandom;
import password.pwm.util.secure.PwmSecurityKey;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class AppConfig
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( AppConfig.class );
    private final ConfigurationSuppliers configurationSuppliers = new ConfigurationSuppliers();

    private final StoredConfiguration storedConfiguration;
    private final SettingReader settingReader;
    private final Map<String, DomainConfig> domainConfigMap;

    private PwmSecurityKey tempInstanceKey = null;

    public AppConfig( final StoredConfiguration storedConfiguration )
    {
        this.storedConfiguration = storedConfiguration;
        this.settingReader = new SettingReader( storedConfiguration, null, null );
        domainConfigMap = getDomainIDs().stream().collect( Collectors.toUnmodifiableMap(
                ( domainID ) -> domainID,
                ( domainID ) -> new DomainConfig( storedConfiguration ) ) );
    }

    public List<String> getDomainIDs()
    {
        return settingReader.readSettingAsStringArray( PwmSetting.DOMAIN_LIST );
    }

    public Map<String, DomainConfig> domainConfigMap()
    {
        return domainConfigMap;
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
        return nonDefaultProperties;
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

    public boolean isDevDebugMode( )
    {
        return Boolean.parseBoolean( readAppProperty( AppProperty.LOGGING_DEV_OUTPUT ) );
    }

    public long readSettingAsLong( final PwmSetting pwmSetting )
    {
        return settingReader.readSettingAsLong( pwmSetting );
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

    }
}
