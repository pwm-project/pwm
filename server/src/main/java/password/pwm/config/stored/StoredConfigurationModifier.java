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

import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingCategory;
import password.pwm.config.value.StoredValue;
import password.pwm.config.value.LocalizedStringValue;
import password.pwm.config.value.StringArrayValue;
import password.pwm.config.value.StringValue;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.i18n.PwmLocaleBundle;
import password.pwm.util.java.StringUtil;
import password.pwm.util.secure.BCrypt;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public class StoredConfigurationModifier
{
    private final AtomicReference<StoredConfigData> ref = new AtomicReference<>( );

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
            final PwmSetting setting,
            final String profileID,
            final StoredValue value,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException
    {
        writeSettingAndMetaData( setting, profileID, value, new ValueMetaData( Instant.now(), userIdentity ) );
    }

    void writeSettingAndMetaData(
            final PwmSetting setting,
            final String profileID,
            final StoredValue value,
            final ValueMetaData valueMetaData
    )
            throws PwmUnrecoverableException
    {
        Objects.requireNonNull( setting );
        Objects.requireNonNull( value );

        update( ( storedConfigData ) ->
        {
            if ( StringUtil.isEmpty( profileID ) && setting.getCategory().hasProfiles() )
            {
                throw new IllegalArgumentException( "writing of setting " + setting.getKey() + " requires a non-null profileID" );
            }
            if ( !StringUtil.isEmpty( profileID ) && !setting.getCategory().hasProfiles() )
            {
                throw new IllegalArgumentException( "cannot specify profile for non-profile setting" );
            }

            final StoredConfigItemKey key = StoredConfigItemKey.fromSetting( setting, profileID );


            return storedConfigData.toBuilder()
                    .storedValue( key, value )
                    .metaData( key, valueMetaData )
                    .build();
        } );
    }

    public void writeConfigProperty(
            final ConfigurationProperty propertyName,
            final String value
    )
            throws PwmUnrecoverableException
    {
        update( ( storedConfigData ) ->
        {
            final StoredConfigItemKey key = StoredConfigItemKey.fromConfigurationProperty( propertyName );
            final Map<StoredConfigItemKey, StoredValue> existingStoredValues = new HashMap<>( storedConfigData.getStoredValues() );

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

    public void resetLocaleBundleMap( final PwmLocaleBundle pwmLocaleBundle, final String keyName )
            throws PwmUnrecoverableException
    {
        update( ( storedConfigData ) ->
        {
            final Map<StoredConfigItemKey, StoredValue> existingStoredValues = new HashMap<>( storedConfigData.getStoredValues() );

            final StoredConfigItemKey key = StoredConfigItemKey.fromLocaleBundle( pwmLocaleBundle, keyName );
            existingStoredValues.remove( key );

            return storedConfigData.toBuilder()
                    .clearStoredValues()
                    .storedValues( existingStoredValues )
                    .build();
        } );
    }

    public void resetSetting( final PwmSetting setting, final String profileID, final UserIdentity userIdentity )
            throws PwmUnrecoverableException
    {
        update( ( storedConfigData ) ->
        {
            final Map<StoredConfigItemKey, StoredValue> existingStoredValues = new HashMap<>( storedConfigData.getStoredValues() );

            final StoredConfigItemKey key = StoredConfigItemKey.fromSetting( setting, profileID );
            existingStoredValues.remove( key );

            return storedConfigData.toBuilder()
                    .clearStoredValues()
                    .storedValues( existingStoredValues )
                    .metaData( key, new ValueMetaData( Instant.now(), userIdentity ) )
                    .build();
        } );
    }

    public void deleteKey( final StoredConfigItemKey key )
            throws PwmUnrecoverableException
    {
        update( ( storedConfigData ) ->
        {
            final Map<StoredConfigItemKey, StoredValue> existingStoredValues = new HashMap<>( storedConfigData.getStoredValues() );
            final Map<StoredConfigItemKey, ValueMetaData> existingMetaValues = new HashMap<>( storedConfigData.getMetaDatas() );

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
            final PwmLocaleBundle pwmLocaleBundle,
            final String keyName,
            final Map<String, String> localeMap
    )
            throws PwmUnrecoverableException
    {
        update( ( storedConfigData ) ->
        {
            final StoredConfigItemKey key = StoredConfigItemKey.fromLocaleBundle( pwmLocaleBundle, keyName );
            final StoredValue value = new LocalizedStringValue( localeMap );

            return storedConfigData.toBuilder()
                    .storedValue( key, value )
                    .build();
        } );
    }

    public void copyProfileID(
            final PwmSettingCategory category,
            final String sourceID,
            final String destinationID,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException
    {

        if ( !category.hasProfiles() )
        {
            throw PwmUnrecoverableException.newException(
                    PwmError.ERROR_INVALID_CONFIG, "can not copy profile ID for category " + category + ", category does not have profiles" );
        }

        update( ( storedConfigData ) ->
        {
            final StoredConfiguration oldStoredConfiguration = new StoredConfigurationImpl( storedConfigData );

            final PwmSetting profileSetting = category.getProfileSetting().orElseThrow( IllegalStateException::new );
            final List<String> existingProfiles = StoredConfigurationUtil.profilesForSetting( profileSetting, oldStoredConfiguration );
            if ( !existingProfiles.contains( sourceID ) )
            {
                throw PwmUnrecoverableException.newException(
                        PwmError.ERROR_INVALID_CONFIG, "can not copy profile ID for category, source profileID '" + sourceID + "' does not exist" );
            }

            if ( existingProfiles.contains( destinationID ) )
            {
                throw PwmUnrecoverableException.newException(
                        PwmError.ERROR_INVALID_CONFIG, "can not copy profile ID for category, destination profileID '" + destinationID + "' already exists" );
            }

            final Collection<PwmSettingCategory> interestedCategories = PwmSettingCategory.associatedProfileCategories( category );
            final Map<StoredConfigItemKey, StoredValue> newValues = new LinkedHashMap<>();

            for ( final PwmSettingCategory interestedCategory : interestedCategories )
            {
                for ( final PwmSetting pwmSetting : interestedCategory.getSettings() )
                {
                    if ( !oldStoredConfiguration.isDefaultValue( pwmSetting, sourceID ) )
                    {
                        final StoredValue value = oldStoredConfiguration.readSetting( pwmSetting, sourceID );
                        final StoredConfigItemKey key = StoredConfigItemKey.fromSetting( pwmSetting, destinationID );
                        newValues.put( key, value );
                    }
                }
            }

            {
                final List<String> newProfileIDList = new ArrayList<>( existingProfiles );
                newProfileIDList.add( destinationID );
                final StoredConfigItemKey key = StoredConfigItemKey.fromSetting( profileSetting, null );
                final StoredValue value = new StringArrayValue( newProfileIDList );
                newValues.put( key, value );
            }

            final StoredConfigItemKey key = StoredConfigItemKey.fromSetting( category.getProfileSetting().orElseThrow( IllegalStateException::new ), null );
            final ValueMetaData valueMetaData = new ValueMetaData( Instant.now(), userIdentity );

            return storedConfigData.toBuilder()
                    .storedValues( newValues )
                    .metaData( key, valueMetaData )
                    .build();

        } );
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
        }
        catch ( final RuntimeException e )
        {
            throw ( PwmUnrecoverableException ) e.getCause();
        }
        ref.updateAndGet( storedConfigData -> storedConfigData.toBuilder().modifyTime( Instant.now() ).build() );
    }

    interface FunctionWithException<T>
    {
        T applyThrows( T value ) throws PwmUnrecoverableException;
    }
}
