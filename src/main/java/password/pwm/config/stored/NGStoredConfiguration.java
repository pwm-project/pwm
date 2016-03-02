/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2016 The PWM Project
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package password.pwm.config.stored;

import password.pwm.PwmConstants;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingCategory;
import password.pwm.config.StoredValue;
import password.pwm.config.value.StringValue;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmSecurityKey;

import java.text.ParseException;
import java.util.Date;
import java.util.Map;

class NGStoredConfiguration implements StoredConfiguration {
    final static private PwmLogger LOGGER = PwmLogger.forClass(NGStoredConfiguration.class);
    private final PwmSecurityKey configurationSecurityKey;
    private final StorageEngine engine;

    NGStoredConfiguration(
            final Map<StoredConfigReference, StoredValue> values,
            final Map<StoredConfigReference, ValueMetaData> metaValues,
            final PwmSecurityKey pwmSecurityKey)
    {
        engine = new NGStorageEngineImpl(values, metaValues);
        configurationSecurityKey = pwmSecurityKey;
    }

    public String readConfigProperty(final ConfigurationProperty configurationProperty) {
        StoredConfigReference storedConfigReference = new StoredConfigReferenceBean(
                StoredConfigReference.RecordType.PROPERTY,
                configurationProperty.getKey(),
                null
        );
        final StoredValue storedValue = engine.read(storedConfigReference);
        if (storedValue == null | !(storedValue instanceof StringValue)) {
            return null;
        }
        return (String) storedValue.toNativeObject();
    }

    public void writeConfigProperty(final ConfigurationProperty configurationProperty, final String value)
    {
        StoredConfigReference storedConfigReference = new StoredConfigReferenceBean(
                StoredConfigReference.RecordType.PROPERTY,
                configurationProperty.getKey(),
                null
        );
        final StoredValue storedValue = new StringValue(value);
        engine.write(storedConfigReference, storedValue, null);
    }

    public void resetSetting(PwmSetting setting, String profileID, UserIdentity userIdentity) {
        final StoredConfigReference storedConfigReference = new StoredConfigReferenceBean(
                StoredConfigReference.RecordType.SETTING,
                setting.getKey(),
                profileID
        );
        engine.reset(storedConfigReference, userIdentity);
    }

    public boolean isDefaultValue(PwmSetting setting) {
        return isDefaultValue(setting, null);
    }

    public boolean isDefaultValue(PwmSetting setting, String profileID) {
        final StoredConfigReference storedConfigReference = new StoredConfigReferenceBean(
                StoredConfigReference.RecordType.SETTING,
                setting.getKey(),
                profileID
        );
        StoredValue value = engine.read(storedConfigReference);
        return value == null;
    }

    public StoredValue readSetting(PwmSetting setting) {
        return readSetting(setting, null);
    }

    public StoredValue readSetting(PwmSetting setting, String profileID) {
        final StoredConfigReference storedConfigReference = new StoredConfigReferenceBean(
                StoredConfigReference.RecordType.SETTING,
                setting.getKey(),
                profileID
        );
        return engine.read(storedConfigReference);
    }

    public void copyProfileID(PwmSettingCategory category, String sourceID, String destinationID, UserIdentity userIdentity)
            throws PwmUnrecoverableException {
        throw new IllegalStateException("not implemented"); //@todo
    }

    public void writeSetting(
            PwmSetting setting,
            StoredValue value,
            UserIdentity userIdentity
    ) throws PwmUnrecoverableException {
        writeSetting(setting, null, value, userIdentity);
    }

    public void writeSetting(
            PwmSetting setting,
            String profileID,
            StoredValue value,
            UserIdentity userIdentity
    )
            throws PwmUnrecoverableException
    {
        final StoredConfigReference storedConfigReference = new StoredConfigReferenceBean(
                StoredConfigReference.RecordType.SETTING,
                setting.getKey(),
                profileID
        );
        engine.write(storedConfigReference, value, userIdentity);
    }

    @Override
    public PwmSecurityKey getKey() throws PwmUnrecoverableException {
        return configurationSecurityKey;
    }

    @Override
    public boolean isLocked() {
        return engine.isWriteLocked();
    }

    @Override
    public void lock() {
        engine.writeLock();
    }

    @Override
    public ValueMetaData readSettingMetadata(PwmSetting setting, String profileID) {
        final StoredConfigReference storedConfigReference = new StoredConfigReferenceBean(
                StoredConfigReference.RecordType.SETTING,
                setting.getKey(),
                profileID
        );
        return engine.readMetaData(storedConfigReference);
    }

    public Date modifyTime() {
        final String modifyTimeString = readConfigProperty(ConfigurationProperty.MODIFIFICATION_TIMESTAMP);
        if (modifyTimeString != null) {
            try {
                return PwmConstants.DEFAULT_DATETIME_FORMAT.parse(modifyTimeString);
            } catch (ParseException e) {
                LOGGER.error("error parsing last modified timestamp property: " + e.getMessage());
            }
        }
        return null;
    }

}
