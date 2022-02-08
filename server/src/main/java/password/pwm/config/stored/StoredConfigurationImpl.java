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

package password.pwm.config.stored;

import password.pwm.bean.DomainID;
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingTemplate;
import password.pwm.config.PwmSettingTemplateSet;
import password.pwm.config.value.LocalizedStringValue;
import password.pwm.config.value.StoredValue;
import password.pwm.config.value.StringValue;
import password.pwm.config.value.ValueTypeConverter;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.i18n.PwmLocaleBundle;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmSecurityKey;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private final Map<DomainID, PwmSettingTemplateSet> templateSets;

    private static final PwmLogger LOGGER = PwmLogger.forClass( StoredConfigurationImpl.class );

    StoredConfigurationImpl( final StoredConfigData storedConfigData )
    {
        this.createTime = storedConfigData.getCreateTime();
        this.modifyTime = storedConfigData.getModifyTime();
        this.metaValues = Collections.unmodifiableMap( new TreeMap<>( storedConfigData.getMetaDatas() ) );
        this.templateSets = TemplateSetReader.readTemplateSet( storedConfigData.getStoredValues() );
        this.storedValues = Collections.unmodifiableMap(  new TreeMap<>( storedConfigData.getStoredValues() ) );
    }

    StoredConfigurationImpl()
    {
        this.createTime = JavaHelper.toIsoDate( Instant.now() );
        this.modifyTime = Instant.now();
        this.storedValues = Collections.emptyMap();
        this.metaValues = Collections.emptyMap();
        this.templateSets = TemplateSetReader.readTemplateSet( Collections.emptyMap() );
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
    public Map<String, String> readLocaleBundleMap( final PwmLocaleBundle pwmLocaleBundle, final String keyName, final DomainID domainID )
    {
        final StoredConfigKey key = StoredConfigKey.forLocaleBundle( pwmLocaleBundle, keyName, domainID );
        final StoredValue value = storedValues.get( key );
        if ( value != null )
        {
            return ( ( LocalizedStringValue ) value ).toNativeObject();
        }
        return Collections.emptyMap();
    }

    @Override
    public Map<DomainID, PwmSettingTemplateSet> getTemplateSets()
    {
        return templateSets;
    }

    private static class TemplateSetReader
    {
        private static Map<DomainID, PwmSettingTemplateSet> readTemplateSet( final Map<StoredConfigKey, StoredValue> valueMap )
        {
            final List<String> domainStrList = ValueTypeConverter.valueToStringArray( valueMap.getOrDefault(
                    StoredConfigKey.forSetting( PwmSetting.DOMAIN_LIST, null, DomainID.systemId() ),
                    PwmSetting.DOMAIN_LIST.getDefaultValue( PwmSettingTemplateSet.getDefault() ) ) );

            final List<DomainID> domainIDList = domainStrList.stream().map( DomainID::create ).collect( Collectors.toList() );

            final Map<DomainID, PwmSettingTemplateSet> templateSets = domainIDList.stream().collect( Collectors.toMap(
                    Function.identity(),
                    domainID -> readTemplateSet( valueMap, domainID )
            ) );
            templateSets.put( DomainID.systemId(), PwmSettingTemplateSet.getDefault() );
            return Collections.unmodifiableMap( new TreeMap<>( templateSets ) );
        }

        private static PwmSettingTemplateSet readTemplateSet( final Map<StoredConfigKey, StoredValue> valueMap, final DomainID domain )
        {
            final Set<PwmSettingTemplate> templates = EnumSet.noneOf( PwmSettingTemplate.class );
            readTemplateValue( valueMap, domain, PwmSetting.TEMPLATE_LDAP ).ifPresent( templates::add );
            readTemplateValue( valueMap, domain, PwmSetting.TEMPLATE_STORAGE ).ifPresent( templates::add );
            readTemplateValue( valueMap, domain, PwmSetting.DB_VENDOR_TEMPLATE ).ifPresent( templates::add );
            return new PwmSettingTemplateSet( templates );
        }

        private static Optional<PwmSettingTemplate> readTemplateValue(
                final Map<StoredConfigKey, StoredValue> valueMap,
                final DomainID domainID,
                final PwmSetting pwmSetting )
        {
            final StoredConfigKey key = StoredConfigKey.forSetting( pwmSetting, null, domainID );
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
    }

    @Override
    public Iterator<StoredConfigKey> keys()
    {
        return storedValues.keySet().iterator();
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
