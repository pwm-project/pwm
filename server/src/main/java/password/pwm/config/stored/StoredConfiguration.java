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
import password.pwm.config.StoredValue;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.secure.PwmSecurityKey;

import java.time.Instant;

public interface StoredConfiguration
{
    String XML_ELEMENT_ROOT = "PwmConfiguration";
    String XML_ELEMENT_PROPERTIES = "properties";
    String XML_ELEMENT_PROPERTY = "property";
    String XML_ELEMENT_SETTINGS = "settings";
    String XML_ELEMENT_SETTING = "setting";
    String XML_ELEMENT_DEFAULT = "default";
    String XML_ELEMENT_LOCALEBUNDLE = "localeBundle";

    String XML_ATTRIBUTE_TYPE = "type";
    String XML_ATTRIBUTE_KEY = "key";
    String XML_ATTRIBUTE_SYNTAX = "syntax";
    String XML_ATTRIBUTE_PROFILE = "profile";
    String XML_ATTRIBUTE_VALUE_APP = "app";
    String XML_ATTRIBUTE_VALUE_CONFIG = "config";
    String XML_ATTRIBUTE_CREATE_TIME = "createTime";
    String XML_ATTRIBUTE_MODIFY_TIME = "modifyTime";
    String XML_ATTRIBUTE_MODIFY_USER = "modifyUser";
    String XML_ATTRIBUTE_MODIFY_USER_PROFILE = "modifyUserProfile";
    String XML_ATTRIBUTE_SYNTAX_VERSION = "syntaxVersion";
    String XML_ATTRIBUTE_BUNDLE = "bundle";


    PwmSecurityKey getKey( ) throws PwmUnrecoverableException;

    Instant modifyTime( );

    boolean isLocked( );

    String readConfigProperty( ConfigurationProperty propertyName );

    void writeConfigProperty(
            ConfigurationProperty propertyName,
            String value
    );

    void lock( );

    ValueMetaData readSettingMetadata( PwmSetting setting, String profileID );

    void resetSetting( PwmSetting setting, String profileID, UserIdentity userIdentity );

    boolean isDefaultValue( PwmSetting setting );

    boolean isDefaultValue( PwmSetting setting, String profileID );

    StoredValue readSetting( PwmSetting setting );

    StoredValue readSetting( PwmSetting setting, String profileID );

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


}
