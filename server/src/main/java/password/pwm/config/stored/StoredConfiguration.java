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

import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingCategory;
import password.pwm.config.PwmSettingTemplateSet;
import password.pwm.config.StoredValue;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.i18n.PwmLocaleBundle;
import password.pwm.util.secure.PwmSecurityKey;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface StoredConfiguration
{
    PwmSecurityKey getKey( ) throws PwmUnrecoverableException;

    String createTime();

    Instant modifyTime( );

    boolean isLocked( );

    StoredConfiguration copy() throws PwmUnrecoverableException;

    Optional<String> readConfigProperty( ConfigurationProperty propertyName );

    void writeConfigProperty( ConfigurationProperty propertyName, String value );

    void lock( );

    PwmSettingTemplateSet getTemplateSet();

    List<String> profilesForSetting( PwmSetting pwmSetting );

    ValueMetaData readSettingMetadata( PwmSetting setting, String profileID );

    Map<String, String> readLocaleBundleMap( PwmLocaleBundle bundleName, String keyName );

    void resetLocaleBundleMap( PwmLocaleBundle bundleName, String keyName );

    void resetSetting( PwmSetting setting, String profileID, UserIdentity userIdentity );

    boolean isDefaultValue( PwmSetting setting );

    boolean isDefaultValue( PwmSetting setting, String profileID );

    StoredValue readSetting( PwmSetting setting );

    StoredValue readSetting( PwmSetting setting, String profileID );

    void writeLocaleBundleMap( PwmLocaleBundle bundleName, String keyName, Map<String, String> localeMap );

    void copyProfileID( PwmSettingCategory category, String sourceID, String destinationID, UserIdentity userIdentity )
            throws PwmUnrecoverableException;

    void writeSetting(
            PwmSetting setting,
            StoredValue value,
            UserIdentity userIdentity
    ) throws PwmUnrecoverableException;

    void writeSetting(
            PwmSetting setting,
            String profileID,
            StoredValue value,
            UserIdentity userIdentity
    ) throws PwmUnrecoverableException;


    String settingChecksum() throws PwmUnrecoverableException;

    Set<StoredConfigItemKey> modifiedSettings();
}
