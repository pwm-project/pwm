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

package password.pwm.config.stored;

import password.pwm.PwmConstants;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingCategory;
import password.pwm.config.PwmSettingTemplate;
import password.pwm.config.PwmSettingTemplateSet;
import password.pwm.config.StoredValue;
import password.pwm.config.value.LocalizedStringValue;
import password.pwm.config.value.StringArrayValue;
import password.pwm.config.value.StringValue;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.i18n.PwmLocaleBundle;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.BCrypt;
import password.pwm.util.secure.PwmSecurityKey;
import password.pwm.util.secure.SecureEngine;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * @author Jason D. Rivard
 */
public class StoredConfigurationImpl implements StoredConfigurationSpi
{
    private final String createTime;
    private Instant modifyTime;

    private final Map<StoredConfigItemKey, StoredValue> storedValues = new TreeMap<>();
    private final Map<StoredConfigItemKey, ValueMetaData> metaValues = new TreeMap<>();

    private static final PwmLogger LOGGER = PwmLogger.forClass( StoredConfigurationImpl.class );

    private final ReentrantReadWriteLock modifyLock = new ReentrantReadWriteLock();

    StoredConfigurationImpl( final StoredConfigData storedConfigData )
    {
        this.createTime = storedConfigData.getCreateTime();
        this.modifyTime = storedConfigData.getModifyTime();

        final Map<StoredConfigItemKey, StoredValue> filteredStoredValues = new HashMap<>( storedConfigData.getStoredValues() );
        filteredStoredValues.keySet().retainAll( storedConfigData.getStoredValues().keySet().stream().filter( StoredConfigItemKey::isValid ).collect( Collectors.toList() ) );

        final Map<StoredConfigItemKey, ValueMetaData> filteredMetaDatas = new HashMap<>( storedConfigData.getMetaDatas() );
        filteredMetaDatas .keySet().retainAll( storedConfigData.getMetaDatas().keySet().stream().filter( StoredConfigItemKey::isValid ).collect( Collectors.toList() ) );

        this.storedValues.putAll( filteredStoredValues );
        this.metaValues.putAll( filteredMetaDatas );
    }

    StoredConfigurationImpl( )
    {
        this.createTime = JavaHelper.toIsoDate( Instant.now() );
        this.modifyTime = Instant.now();
    }

    @Override
    public StoredConfiguration copy()
    {
        return new StoredConfigurationImpl( new StoredConfigData( createTime, modifyTime, storedValues, metaValues ) );
    }

    @Override
    public Optional<String> readConfigProperty( final ConfigurationProperty propertyName )
    {
        final StoredConfigItemKey key = StoredConfigItemKey.fromConfigurationProperty( propertyName );
        final StoredValue storedValue = storedValues.get( key );
        if ( storedValue != null )
        {
            return Optional.of( ( ( StringValue ) storedValue ).toNativeObject() );
        }
        return Optional.empty();
    }

    @Override
    public void writeConfigProperty(
            final ConfigurationProperty propertyName,
            final String value
    )
    {
        modifyLock.writeLock().lock();
        try
        {
            final StoredConfigItemKey key = StoredConfigItemKey.fromConfigurationProperty( propertyName );

            if ( StringUtil.isEmpty( value ) )
            {
                storedValues.remove( key );
            }
            else
            {
                final StoredValue storedValue = new StringValue( value );
                storedValues.put( key, storedValue );
            }
        }
        finally
        {
            modifyLock.writeLock().unlock();
        }
    }


    @Override
    public Map<String, String> readLocaleBundleMap( final PwmLocaleBundle pwmLocaleBundle, final String keyName )
    {
        modifyLock.readLock().lock();
        try
        {
            final StoredConfigItemKey key = StoredConfigItemKey.fromLocaleBundle( pwmLocaleBundle, keyName );
            final StoredValue value = storedValues.get( key );
            if ( value != null )
            {
                return ( ( LocalizedStringValue ) value ).toNativeObject();
            }
        }
        finally
        {
            modifyLock.readLock().unlock();
        }
        return Collections.emptyMap();
    }

    @Override
    public void resetLocaleBundleMap( final PwmLocaleBundle pwmLocaleBundle, final String keyName )
    {
        preModifyActions();
        modifyLock.writeLock().lock();
        try
        {
            final StoredConfigItemKey key = StoredConfigItemKey.fromLocaleBundle( pwmLocaleBundle, keyName );
            storedValues.remove( key );
        }
        finally
        {
            modifyLock.writeLock().unlock();
        }
    }

    @Override
    public void resetSetting( final PwmSetting setting, final String profileID, final UserIdentity userIdentity )
    {
        preModifyActions();
        modifyLock.writeLock().lock();
        try
        {
            final StoredConfigItemKey key = StoredConfigItemKey.fromSetting( setting, profileID );
            storedValues.remove( key );
            metaValues.put( key, new ValueMetaData( Instant.now(), userIdentity ) );
        }
        finally
        {
            modifyLock.writeLock().unlock();
        }
    }

    public boolean isDefaultValue( final PwmSetting setting )
    {
        return isDefaultValue( setting, null );
    }

    public boolean isDefaultValue( final PwmSetting setting, final String profileID )
    {
        modifyLock.readLock().lock();
        try
        {
            final StoredValue currentValue = readSetting( setting, profileID );
            final StoredValue defaultValue = defaultValue( setting, this.getTemplateSet() );
            final String currentHashValue = currentValue.valueHash();
            final String defaultHashValue = defaultValue.valueHash();
            return Objects.equals( currentHashValue, defaultHashValue );
        }
        finally
        {
            modifyLock.readLock().unlock();
        }
    }

    private static StoredValue defaultValue( final PwmSetting pwmSetting, final PwmSettingTemplateSet template )
    {
        return pwmSetting.getDefaultValue( template );
    }

    @Override
    public PwmSettingTemplateSet getTemplateSet()
    {
        final Set<PwmSettingTemplate> templates = new HashSet<>();
        templates.add( readTemplateValue( PwmSetting.TEMPLATE_LDAP ) );
        templates.add( readTemplateValue( PwmSetting.TEMPLATE_STORAGE ) );
        templates.add( readTemplateValue( PwmSetting.DB_VENDOR_TEMPLATE ) );
        return new PwmSettingTemplateSet( templates );
    }

    private PwmSettingTemplate readTemplateValue( final PwmSetting pwmSetting )
    {
        final StoredConfigItemKey key = StoredConfigItemKey.fromSetting( pwmSetting, null );
        final StoredValue storedValue = storedValues.get( key );

        if ( storedValue  != null )
        {
            try
            {
                final String strValue = ( String ) storedValue.toNativeObject();
                return JavaHelper.readEnumFromString( PwmSettingTemplate.class, null, strValue );
            }
            catch ( IllegalStateException e )
            {
                LOGGER.error( "error reading template", e );
            }
        }
        return null;
    }

    public void setTemplate( final PwmSettingTemplate template )
    {
        writeConfigProperty( ConfigurationProperty.LDAP_TEMPLATE, template.toString() );
    }

    public String toString( final PwmSetting setting, final String profileID )
    {
        final StoredValue storedValue = readSetting( setting, profileID );
        return setting.getKey() + "=" + storedValue.toDebugString( null );
    }


    public Set<StoredConfigItemKey> modifiedSettings( )
    {
        modifyLock.readLock().lock();
        try
        {
            final Set<StoredConfigItemKey> modifiedKeys = new HashSet<>( storedValues.keySet() );
            for ( final Iterator<StoredConfigItemKey> iterator = modifiedKeys.iterator(); iterator.hasNext(); )
            {
                final StoredConfigItemKey key = iterator.next();
                if ( key.getRecordType() == StoredConfigItemKey.RecordType.SETTING )
                {
                    if ( isDefaultValue( key.toPwmSetting(), key.getProfileID() ) )
                    {
                        iterator.remove();
                    }
                }
            }
            return Collections.unmodifiableSet( modifiedKeys );
        }
        finally
        {
            modifyLock.readLock().unlock();
        }
    }

    @Override
    public List<String> profilesForSetting( final PwmSetting pwmSetting )
    {
        modifyLock.readLock().lock();
        try
        {
            final List<String> returnObj = new ArrayList<>(  );
            for ( final StoredConfigItemKey storedConfigItemKey : storedValues.keySet() )
            {
                if ( storedConfigItemKey.getRecordType() == StoredConfigItemKey.RecordType.SETTING
                        && Objects.equals( storedConfigItemKey.getRecordID(), pwmSetting.getKey() ) )
                {
                    returnObj.add( storedConfigItemKey.getProfileID() );
                }
            }
            return Collections.unmodifiableList( returnObj );
        }
        finally
        {
            modifyLock.readLock().unlock();
        }
    }


    public ValueMetaData readSettingMetadata( final PwmSetting setting, final String profileID )
    {
        final StoredConfigItemKey key = StoredConfigItemKey.fromSetting( setting, profileID );
        return metaValues.get( key );
    }

    public StoredValue readSetting( final PwmSetting setting )
    {
        return readSetting( setting, null );
    }

    public StoredValue readSetting( final PwmSetting setting, final String profileID )
    {
        modifyLock.readLock().lock();
        try
        {
            final StoredConfigItemKey key = StoredConfigItemKey.fromSetting( setting, profileID );
            final StoredValue storedValue = storedValues.get( key );
            if ( storedValue == null )
            {
                return defaultValue( setting, getTemplateSet() );
            }

            return storedValue;
        }
        finally
        {
            modifyLock.readLock().unlock();
        }
    }

    @Override
    public void writeLocaleBundleMap( final PwmLocaleBundle pwmLocaleBundle, final String keyName, final Map<String, String> localeMap )
    {
        preModifyActions();

        try
        {
            modifyLock.writeLock().lock();
            final StoredConfigItemKey key = StoredConfigItemKey.fromLocaleBundle( pwmLocaleBundle, keyName );
            final StoredValue value = new LocalizedStringValue( localeMap );
            storedValues.put( key, value );
        }
        finally
        {
            modifyLock.writeLock().unlock();
        }
    }


    public void copyProfileID( final PwmSettingCategory category, final String sourceID, final String destinationID, final UserIdentity userIdentity )
            throws PwmUnrecoverableException
    {

        if ( !category.hasProfiles() )
        {
            throw PwmUnrecoverableException.newException(
                    PwmError.ERROR_INVALID_CONFIG, "can not copy profile ID for category " + category + ", category does not have profiles" );
        }
        final List<String> existingProfiles = this.profilesForSetting( category.getProfileSetting() );
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

        {
            final Collection<PwmSettingCategory> interestedCategories = PwmSettingCategory.associatedProfileCategories( category );
            for ( final PwmSettingCategory interestedCategory : interestedCategories )
            {
                for ( final PwmSetting pwmSetting : interestedCategory.getSettings() )
                {
                    if ( !isDefaultValue( pwmSetting, sourceID ) )
                    {
                        final StoredValue value = readSetting( pwmSetting, sourceID );
                        writeSetting( pwmSetting, destinationID, value, userIdentity );
                    }
                }
            }
        }

        final List<String> newProfileIDList = new ArrayList<>();
        newProfileIDList.addAll( existingProfiles );
        newProfileIDList.add( destinationID );
        writeSetting( category.getProfileSetting(), new StringArrayValue( newProfileIDList ), userIdentity );
    }


    public void writeSetting(
            final PwmSetting setting,
            final StoredValue value,
            final UserIdentity userIdentity
    ) throws PwmUnrecoverableException
    {
        writeSetting( setting, null, value, userIdentity );
    }

    public void writeSetting(
            final PwmSetting setting,
            final String profileID,
            final StoredValue value,
            final UserIdentity userIdentity
    )
    {
        if ( profileID == null && setting.getCategory().hasProfiles() )
        {
            throw new IllegalArgumentException( "writing of setting " + setting.getKey() + " requires a non-null profileID" );
        }
        if ( profileID != null && !setting.getCategory().hasProfiles() )
        {
            throw new IllegalArgumentException( "cannot specify profile for non-profile setting" );
        }

        preModifyActions();

        modifyLock.writeLock().lock();
        try
        {
            final StoredConfigItemKey key = StoredConfigItemKey.fromSetting( setting, profileID );
            if ( value == null )
            {
                storedValues.remove( key );
            }
            else
            {
                storedValues.put( key, value );
            }
            metaValues.put( key, new ValueMetaData( Instant.now(), userIdentity ) );
        }
        finally
        {
            modifyLock.writeLock().unlock();
        }
    }

    public String settingChecksum( )
            throws PwmUnrecoverableException
    {
        final Instant startTime = Instant.now();

        final Set<StoredConfigItemKey> modifiedSettings = modifiedSettings();
        final StringBuilder sb = new StringBuilder();
        sb.append( "PwmSettingsChecksum" );
        for ( final StoredConfigItemKey storedConfigItemKey : modifiedSettings )
        {
            final StoredValue storedValue = storedValues.get( storedConfigItemKey );
            sb.append( storedValue.valueHash() );
        }


        final String result = SecureEngine.hash( sb.toString(), PwmConstants.SETTING_CHECKSUM_HASH_METHOD );
        LOGGER.trace( () -> "computed setting checksum in " + TimeDuration.fromCurrent( startTime ).asCompactString() );
        return result;
    }


    private void preModifyActions( )
    {
        modifyTime = Instant.now();
    }

    public void setPassword( final String password )
            throws PwmOperationalException
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

    private PwmSecurityKey cachedKey;

    public PwmSecurityKey getKey( ) throws PwmUnrecoverableException
    {
        if ( cachedKey == null )
        {
            cachedKey = new PwmSecurityKey( createTime + "StoredConfiguration" );
        }
        return cachedKey;
    }

    public boolean isModified( )
    {
        return true;
    }

    @Override
    public Instant modifyTime()
    {
        return modifyTime;
    }

    @Override
    public String createTime()
    {
        return createTime;
    }

    @Override
    public Optional<ValueMetaData> readMetaData( final StoredConfigItemKey storedConfigItemKey )
    {
        return Optional.ofNullable( metaValues.get( storedConfigItemKey ) );
    }

    @Override
    public Optional<StoredValue> readStoredValue( final StoredConfigItemKey storedConfigItemKey )
    {
        return Optional.ofNullable( storedValues.get( storedConfigItemKey ) );
    }
}
