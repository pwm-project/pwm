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

package password.pwm.config.stored;

import password.pwm.bean.DomainID;
import password.pwm.bean.UserIdentity;
import password.pwm.config.value.LocalizedStringValue;
import password.pwm.config.value.StoredValue;
import password.pwm.config.value.StringValue;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.i18n.PwmLocaleBundle;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.StringUtil;
import password.pwm.util.secure.BCrypt;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class StoredConfigurationModifier
{
    private final AtomicReference<StoredConfigData> ref = new AtomicReference<>( );
    private final AtomicInteger modifications = new AtomicInteger();

    private StoredConfigurationModifier( final StoredConfiguration storedConfiguration )
    {
        this.ref.set( ( ( StoredConfigurationImpl ) storedConfiguration ).asStoredConfigData() );
    }

    public static StoredConfigurationModifier newModifier( final StoredConfiguration storedConfiguration )
    {
        return new StoredConfigurationModifier( storedConfiguration );
    }

    public StoredConfiguration newStoredConfiguration()
    {
        return new StoredConfigurationImpl( ref.get() );
    }

    public void writeSetting(
            final StoredConfigKey key,
            final StoredValue value,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException
    {
        writeSettingAndMetaData( key, value, new ValueMetaData( Instant.now(), userIdentity ) );
    }

    void writeSettingAndMetaData(
            final StoredConfigKey key,
            final StoredValue value,
            final ValueMetaData valueMetaData
    )
            throws PwmUnrecoverableException
    {
        Objects.requireNonNull( key );
        Objects.requireNonNull( value );

        update( ( storedConfigData ) ->
                storedConfigData.toBuilder()
                        .storedValue( key, value )
                        .metaData( key, valueMetaData )
                        .build() );
    }

    public void writeConfigProperty(
            final ConfigurationProperty propertyName,
            final String value
    )
            throws PwmUnrecoverableException
    {
        update( ( storedConfigData ) ->
        {
            final StoredConfigKey key = StoredConfigKey.forConfigurationProperty( propertyName );
            final Map<StoredConfigKey, StoredValue> existingStoredValues = new HashMap<>( storedConfigData.getStoredValues() );

            if ( StringUtil.isEmpty( value ) )
            {
                existingStoredValues.remove( key );
            }
            else
            {
                final StoredValue storedValue = new StringValue( value );
                existingStoredValues.put( key, storedValue );
            }

            return storedConfigData.toBuilder()
                    .clearStoredValues()
                    .storedValues( existingStoredValues )
                    .build();
        } );
    }

    public void resetLocaleBundleMap( final PwmLocaleBundle pwmLocaleBundle, final String keyName, final DomainID domainID )
            throws PwmUnrecoverableException
    {
        update( ( storedConfigData ) ->
        {
            final Map<StoredConfigKey, StoredValue> existingStoredValues = new HashMap<>( storedConfigData.getStoredValues() );

            final StoredConfigKey key = StoredConfigKey.forLocaleBundle( pwmLocaleBundle, keyName, domainID );
            existingStoredValues.remove( key );

            return storedConfigData.toBuilder()
                    .clearStoredValues()
                    .storedValues( existingStoredValues )
                    .build();
        } );
    }

    public void resetSetting( final StoredConfigKey key, final UserIdentity userIdentity )
            throws PwmUnrecoverableException
    {
        update( ( storedConfigData ) ->
        {
            final Map<StoredConfigKey, StoredValue> existingStoredValues = new HashMap<>( storedConfigData.getStoredValues() );
            existingStoredValues.remove( key );

            return storedConfigData.toBuilder()
                    .clearStoredValues()
                    .storedValues( existingStoredValues )
                    .metaData( key, new ValueMetaData( Instant.now(), userIdentity ) )
                    .build();
        } );
    }

    public void deleteKey( final StoredConfigKey key )
            throws PwmUnrecoverableException
    {
        update( ( storedConfigData ) ->
        {
            final Map<StoredConfigKey, StoredValue> existingStoredValues = new HashMap<>( storedConfigData.getStoredValues() );
            final Map<StoredConfigKey, ValueMetaData> existingMetaValues = new HashMap<>( storedConfigData.getMetaDatas() );

            existingStoredValues.remove( key );
            existingMetaValues.remove( key );

            return storedConfigData.toBuilder()
                    .clearStoredValues()
                    .storedValues( existingStoredValues )
                    .metaDatas( existingMetaValues )
                    .build();
        } );
    }

    public void writeLocaleBundleMap(
            final DomainID domainID,
            final PwmLocaleBundle pwmLocaleBundle,
            final String keyName,
            final Map<String, String> localeMap
    )
            throws PwmUnrecoverableException
    {
        update( ( storedConfigData ) ->
        {
            final StoredConfigKey key = StoredConfigKey.forLocaleBundle( pwmLocaleBundle, keyName, domainID );
            final StoredValue value = new LocalizedStringValue( localeMap );

            return storedConfigData.toBuilder()
                    .storedValue( key, value )
                    .build();
        } );
    }

    public int modifications()
    {
        return modifications.get();
    }


    public void setPassword( final String password )
            throws PwmOperationalException, PwmUnrecoverableException
    {
        if ( password == null || password.isEmpty() )
        {
            throw new PwmOperationalException( new ErrorInformation( PwmError.CONFIG_FORMAT_ERROR, null, new String[]
                    {
                            "can not set blank password",
                    }
            ) );
        }
        final String trimmedPassword = password.trim();
        if ( trimmedPassword.length() < 1 )
        {
            throw new PwmOperationalException( new ErrorInformation( PwmError.CONFIG_FORMAT_ERROR, null, new String[]
                    {
                            "can not set blank password",
                    }
            ) );
        }


        final String passwordHash = BCrypt.hashPassword( password );
        this.writeConfigProperty( ConfigurationProperty.PASSWORD_HASH, passwordHash );
    }

    private void update( final FunctionWithException<StoredConfigData> function ) throws PwmUnrecoverableException
    {
        try
        {
            ref.updateAndGet( storedConfigData ->
            {
                try
                {
                    return function.applyThrows( storedConfigData );
                }
                catch ( final PwmUnrecoverableException e )
                {
                    throw new RuntimeException( e );
                }
            } );
            modifications.incrementAndGet();
        }
        catch ( final RuntimeException e )
        {
            if ( e.getCause() != null && e.getCause().getClass().equals( PwmUnrecoverableException.class ) )
            {
                throw ( PwmUnrecoverableException ) e.getCause();
            }
            final String errorMsg = "unexpected error modifying storedConfiguration: " + JavaHelper.readHostileExceptionMessage( e );
            throw PwmUnrecoverableException.newException( PwmError.ERROR_INTERNAL, errorMsg );
        }
        ref.updateAndGet( storedConfigData -> storedConfigData.toBuilder().modifyTime( Instant.now() ).build() );
    }

    interface FunctionWithException<T>
    {
        T applyThrows( T value ) throws PwmUnrecoverableException;
    }
}
