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

import password.pwm.PwmConstants;
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingTemplate;
import password.pwm.config.PwmSettingTemplateSet;
import password.pwm.config.value.LocalizedStringValue;
import password.pwm.config.value.StoredValue;
import password.pwm.config.value.StringValue;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.i18n.PwmLocaleBundle;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmSecurityKey;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Immutable in-memory configuration.
 *
 * @author Jason D. Rivard
 */
public class StoredConfigurationImpl implements StoredConfiguration
{
    private final String createTime;
    private final Instant modifyTime;
    private final Map<StoredConfigKey, StoredValue> storedValues;
    private final Map<StoredConfigKey, ValueMetaData> metaValues;
    private final PwmSettingTemplateSet templateSet;

    private static final PwmLogger LOGGER = PwmLogger.forClass( StoredConfigurationImpl.class );

    StoredConfigurationImpl( final StoredConfigData storedConfigData )
    {
        this.createTime = storedConfigData.getCreateTime();
        this.modifyTime = storedConfigData.getModifyTime();
        this.metaValues = Map.copyOf( storedConfigData.getMetaDatas() );
        this.templateSet = readTemplateSet( storedConfigData.getStoredValues() );

        final Map<StoredConfigKey, StoredValue> tempMap = removeDefaultSettingValues( storedConfigData.getStoredValues(), templateSet );
        this.storedValues = Map.copyOf( tempMap );
    }

    StoredConfigurationImpl()
    {
        this.createTime = JavaHelper.toIsoDate( Instant.now() );
        this.modifyTime = Instant.now();
        this.storedValues = Collections.emptyMap();
        this.metaValues = Collections.emptyMap();
        this.templateSet = readTemplateSet( Collections.emptyMap() );
    }

    private static Map<StoredConfigKey, StoredValue> removeDefaultSettingValues(
            final Map<StoredConfigKey, StoredValue> valueMap,
            final PwmSettingTemplateSet pwmSettingTemplateSet
    )
    {
        final Predicate<Map.Entry<StoredConfigKey, StoredValue>> checkIfValueIsDefault = entry ->
        {
            if ( StoredConfigKey.RecordType.SETTING.equals( entry.getKey().getRecordType() ) )
            {
                final String loopHash = entry.getValue().valueHash();
                final String defaultHash = entry.getKey().toPwmSetting().getDefaultValue( pwmSettingTemplateSet ).valueHash();
                return !Objects.equals( loopHash, defaultHash );
            }
            return true;
        };

        final Map<StoredConfigKey, StoredValue> results = valueMap.entrySet()
                .parallelStream()
                .filter( checkIfValueIsDefault )
                .collect( Collectors.toMap( Map.Entry::getKey, Map.Entry::getValue ) );

        return Collections.unmodifiableMap( results );
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
        final StoredConfigKey key = StoredConfigKey.forConfigurationProperty( propertyName );
        final StoredValue storedValue = storedValues.get( key );
        if ( storedValue != null )
        {
            return Optional.of( ( ( StringValue ) storedValue ).toNativeObject() );
        }
        return Optional.empty();
    }

    @Override
    public Map<String, String> readLocaleBundleMap( final PwmLocaleBundle pwmLocaleBundle, final String keyName )
    {
        final StoredConfigKey key = StoredConfigKey.forLocaleBundle( pwmLocaleBundle, keyName );
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

    private static PwmSettingTemplateSet readTemplateSet( final Map<StoredConfigKey, StoredValue> valueMap )
    {
        final Set<PwmSettingTemplate> templates = EnumSet.noneOf( PwmSettingTemplate.class );
        readTemplateValue( valueMap, PwmSetting.TEMPLATE_LDAP ).ifPresent( templates::add );
        readTemplateValue( valueMap, PwmSetting.TEMPLATE_STORAGE ).ifPresent( templates::add );
        readTemplateValue( valueMap, PwmSetting.DB_VENDOR_TEMPLATE ).ifPresent( templates::add );
        return new PwmSettingTemplateSet( templates );
    }

    private static Optional<PwmSettingTemplate> readTemplateValue( final Map<StoredConfigKey, StoredValue> valueMap, final PwmSetting pwmSetting )
    {
        final StoredConfigKey key = StoredConfigKey.forSetting( pwmSetting, null, PwmConstants.DOMAIN_ID_PLACEHOLDER );
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

    @Override
    public Stream<StoredConfigKey> keys()
    {
        return storedValues.keySet().stream();
    }

    @Override
    public Optional<ValueMetaData> readSettingMetadata( final StoredConfigKey key )
    {
        return Optional.ofNullable( metaValues.get( key ) );
    }

    private PwmSecurityKey cachedKey;

    @Override
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
    public Optional<ValueMetaData> readMetaData( final StoredConfigKey storedConfigKey )
    {
        return Optional.ofNullable( metaValues.get( storedConfigKey ) );
    }

    @Override
    public Optional<StoredValue> readStoredValue( final StoredConfigKey storedConfigKey )
    {
        return Optional.ofNullable( storedValues.get( storedConfigKey ) );
    }
}
