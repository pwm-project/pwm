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

import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingTemplate;
import password.pwm.config.PwmSettingTemplateSet;
import password.pwm.config.StoredValue;
import password.pwm.config.value.LocalizedStringValue;
import password.pwm.config.value.StringValue;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.i18n.PwmLocaleBundle;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.LazySupplier;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.HmacAlgorithm;
import password.pwm.util.secure.PwmSecurityKey;
import password.pwm.util.secure.SecureEngine;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;

/**
 * Immutable in-memory configuration.
 *
 * @author Jason D. Rivard
 */
public class StoredConfigurationImpl implements StoredConfiguration
{
    private final String createTime;
    private final Instant modifyTime;
    private final Map<StoredConfigItemKey, StoredValue> storedValues;
    private final Map<StoredConfigItemKey, ValueMetaData> metaValues;
    private final PwmSettingTemplateSet templateSet;

    private final transient Supplier<String> valueHashSupplier = new LazySupplier<>( this::valueHashImpl );

    private static final PwmLogger LOGGER = PwmLogger.forClass( StoredConfigurationImpl.class );

    StoredConfigurationImpl( final StoredConfigData storedConfigData )
    {
        this.createTime = storedConfigData.getCreateTime();
        this.modifyTime = storedConfigData.getModifyTime();
        this.metaValues = Collections.unmodifiableMap(  new TreeMap<>( storedConfigData.getMetaDatas() ) );
        this.templateSet = readTemplateSet( storedConfigData.getStoredValues() );

        final Map<StoredConfigItemKey, StoredValue> tempMap = new TreeMap<>( storedConfigData.getStoredValues() );
        removeAllDefaultValues( tempMap, templateSet );
        this.storedValues = Collections.unmodifiableMap( tempMap );
    }

    StoredConfigurationImpl()
    {
        this.createTime = JavaHelper.toIsoDate( Instant.now() );
        this.modifyTime = Instant.now();
        this.storedValues = Collections.emptyMap();
        this.metaValues = Collections.emptyMap();
        this.templateSet = readTemplateSet( Collections.emptyMap() );
    }

    private static void removeAllDefaultValues( final Map<StoredConfigItemKey, StoredValue> valueMap, final PwmSettingTemplateSet pwmSettingTemplateSet )
    {
        valueMap.entrySet().removeIf( entry ->
        {
            final StoredConfigItemKey key = entry.getKey();
            if ( key.getRecordType() == StoredConfigItemKey.RecordType.SETTING )
            {
                final StoredValue loopValue = entry.getValue();
                final StoredValue defaultValue = key.toPwmSetting().getDefaultValue( pwmSettingTemplateSet );
                return Objects.equals( loopValue.valueHash(), defaultValue.valueHash() );
            }
            return false;
        } );
    }



    @Override
    public StoredConfiguration copy()
    {
        return new StoredConfigurationImpl( asStoredConfigData() );
    }

    StoredConfigData asStoredConfigData()
    {
        return new StoredConfigData( createTime, modifyTime, storedValues, metaValues );
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

    public boolean isDefaultValue( final PwmSetting setting, final String profileID )
    {
        final StoredConfigItemKey key = StoredConfigItemKey.fromSetting( setting, profileID );
        return !storedValues.containsKey( key );
    }

    @Override
    public Map<String, String> readLocaleBundleMap( final PwmLocaleBundle pwmLocaleBundle, final String keyName )
    {
        final StoredConfigItemKey key = StoredConfigItemKey.fromLocaleBundle( pwmLocaleBundle, keyName );
        final StoredValue value = storedValues.get( key );
        if ( value != null )
        {
            return ( ( LocalizedStringValue ) value ).toNativeObject();
        }
        return Collections.emptyMap();
    }

    @Override
    public PwmSettingTemplateSet getTemplateSet()
    {
        return templateSet;
    }

    private static PwmSettingTemplateSet readTemplateSet( final Map<StoredConfigItemKey, StoredValue> valueMap )
    {
        final Set<PwmSettingTemplate> templates = new HashSet<>();
        readTemplateValue( valueMap, PwmSetting.TEMPLATE_LDAP ).ifPresent( templates::add );
        readTemplateValue( valueMap, PwmSetting.TEMPLATE_STORAGE ).ifPresent( templates::add );
        readTemplateValue( valueMap, PwmSetting.DB_VENDOR_TEMPLATE ).ifPresent( templates::add );
        return new PwmSettingTemplateSet( templates );
    }

    private static Optional<PwmSettingTemplate> readTemplateValue( final Map<StoredConfigItemKey, StoredValue> valueMap, final PwmSetting pwmSetting )
    {
        final StoredConfigItemKey key = StoredConfigItemKey.fromSetting( pwmSetting, null );
        final StoredValue storedValue = valueMap.get( key );

        if ( storedValue != null )
        {
            try
            {
                final String strValue = ( String ) storedValue.toNativeObject();
                return Optional.ofNullable( JavaHelper.readEnumFromString( PwmSettingTemplate.class, null, strValue ) );
            }
            catch ( final IllegalStateException e )
            {
                LOGGER.error( () -> "error reading template", e );
            }
        }

        return Optional.empty();
    }

    public String toString( final PwmSetting setting, final String profileID )
    {
        final StoredValue storedValue = readSetting( setting, profileID );
        return setting.getKey() + "=" + storedValue.toDebugString( null );
    }

    public Set<StoredConfigItemKey> modifiedItems()
    {
        return Collections.unmodifiableSet( storedValues.keySet() );
    }

    @Override
    public List<String> profilesForSetting( final PwmSetting pwmSetting )
    {
        final List<String> returnObj = new ArrayList<>();
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

    public ValueMetaData readSettingMetadata( final PwmSetting setting, final String profileID )
    {
        final StoredConfigItemKey key = StoredConfigItemKey.fromSetting( setting, profileID );
        return metaValues.get( key );
    }

    public StoredValue readSetting( final PwmSetting setting, final String profileID )
    {
        final StoredConfigItemKey key = StoredConfigItemKey.fromSetting( setting, profileID );
        final StoredValue storedValue = storedValues.get( key );
        if ( storedValue == null )
        {
            return setting.getDefaultValue( getTemplateSet() );
        }

        return storedValue;
    }

    public String valueHash()
    {
        return valueHashSupplier.get();
    }

    private String valueHashImpl()
    {
        final Set<StoredConfigItemKey> modifiedSettings = modifiedItems();
        final StringBuilder sb = new StringBuilder();

        for ( final StoredConfigItemKey storedConfigItemKey : modifiedSettings )
        {
            final StoredValue storedValue = storedValues.get( storedConfigItemKey );
            sb.append( storedValue.valueHash() );
        }

        try
        {
            return SecureEngine.hmac( HmacAlgorithm.HMAC_SHA_512, getKey(), sb.toString() );
        }
        catch ( final PwmUnrecoverableException e )
        {
            throw new IllegalStateException( e );
        }
    }

    private PwmSecurityKey cachedKey;

    public PwmSecurityKey getKey() throws PwmUnrecoverableException
    {
        if ( cachedKey == null )
        {
            cachedKey = new PwmSecurityKey( createTime + "StoredConfiguration" );
        }
        return cachedKey;
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
