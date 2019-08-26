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

package password.pwm.config.stored.ng;

import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingCategory;
import password.pwm.config.StoredValue;
import password.pwm.config.stored.ConfigurationProperty;
import password.pwm.config.stored.StoredConfigReference;
import password.pwm.config.stored.StoredConfigReferenceBean;
import password.pwm.config.stored.StoredConfiguration;
import password.pwm.config.stored.ValueMetaData;
import password.pwm.config.value.StringValue;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmSecurityKey;

import java.time.Instant;

public class NGStoredConfiguration implements StoredConfiguration
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( NGStoredConfiguration.class );
    private final PwmSecurityKey configurationSecurityKey;
    private final NGStorageEngineImpl engine;
    private boolean readOnly = false;

    NGStoredConfiguration(
            final NGStorageEngineImpl storageEngine,
            final PwmSecurityKey pwmSecurityKey )
    {
        engine = storageEngine;
        configurationSecurityKey = pwmSecurityKey;
    }

    public String readConfigProperty( final ConfigurationProperty configurationProperty )
    {
        final StoredConfigReference storedConfigReference = new StoredConfigReferenceBean(
                StoredConfigReference.RecordType.PROPERTY,
                configurationProperty.getKey(),
                null
        );
        final StoredValue storedValue = engine.read( storedConfigReference );
        if ( storedValue == null | !( storedValue instanceof StringValue ) )
        {
            return null;
        }
        return ( String ) storedValue.toNativeObject();
    }

    public void writeConfigProperty(
            final ConfigurationProperty configurationProperty,
            final String value )
    {
        final StoredConfigReference storedConfigReference = new StoredConfigReferenceBean(
                StoredConfigReference.RecordType.PROPERTY,
                configurationProperty.getKey(),
                null
        );
        final StoredValue storedValue = new StringValue( value );
        engine.write( storedConfigReference, storedValue, null  );
    }

    public void resetSetting( final PwmSetting setting, final String profileID, final UserIdentity userIdentity )
    {
        final StoredConfigReference storedConfigReference = new StoredConfigReferenceBean(
                StoredConfigReference.RecordType.SETTING,
                setting.getKey(),
                profileID
        );
        engine.reset( storedConfigReference, userIdentity );
    }

    public boolean isDefaultValue( final PwmSetting setting )
    {
        return isDefaultValue( setting, null );
    }

    public boolean isDefaultValue( final PwmSetting setting, final String profileID )
    {
        final StoredConfigReference storedConfigReference = new StoredConfigReferenceBean(
                StoredConfigReference.RecordType.SETTING,
                setting.getKey(),
                profileID
        );
        final StoredValue value = engine.read( storedConfigReference );
        return value == null;
    }

    public StoredValue readSetting( final PwmSetting setting )
    {
        return readSetting( setting, null );
    }

    public StoredValue readSetting( final PwmSetting setting, final String profileID )
    {
        final StoredConfigReference storedConfigReference = new StoredConfigReferenceBean(
                StoredConfigReference.RecordType.SETTING,
                setting.getKey(),
                profileID
        );
        return engine.read( storedConfigReference );
    }

    public void copyProfileID( final PwmSettingCategory category, final String sourceID, final String destinationID, final UserIdentity userIdentity )
            throws PwmUnrecoverableException
    {
        //@todo
        throw new IllegalStateException( "not implemented" );
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
            throws PwmUnrecoverableException
    {
        final StoredConfigReference storedConfigReference = new StoredConfigReferenceBean(
                StoredConfigReference.RecordType.SETTING,
                setting.getKey(),
                profileID
        );
        engine.write( storedConfigReference, value, userIdentity );
    }

    @Override
    public PwmSecurityKey getKey( ) throws PwmUnrecoverableException
    {
        return configurationSecurityKey;
    }

    @Override
    public boolean isLocked( )
    {
        return readOnly;
    }

    @Override
    public void lock( )
    {
        readOnly = true;
    }

    @Override
    public ValueMetaData readSettingMetadata( final PwmSetting setting, final String profileID )
    {
        final StoredConfigReference storedConfigReference = new StoredConfigReferenceBean(
                StoredConfigReference.RecordType.SETTING,
                setting.getKey(),
                profileID
        );
        return engine.readMetaData( storedConfigReference );
    }

    public Instant modifyTime( )
    {
        final String modifyTimeString = readConfigProperty( ConfigurationProperty.MODIFIFICATION_TIMESTAMP );
        if ( modifyTimeString != null )
        {
            try
            {
                return JavaHelper.parseIsoToInstant( ( modifyTimeString ) );
            }
            catch ( Exception e )
            {
                LOGGER.error( "error parsing last modified timestamp property: " + e.getMessage() );
            }
        }
        return null;
    }

}
