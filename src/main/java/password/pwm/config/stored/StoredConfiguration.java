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

import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingCategory;
import password.pwm.config.StoredValue;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.secure.PwmSecurityKey;

import java.util.Date;

public interface StoredConfiguration {
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


    PwmSecurityKey getKey() throws PwmUnrecoverableException;

    Date modifyTime();

    boolean isLocked();

    String readConfigProperty(ConfigurationProperty propertyName);

    void writeConfigProperty(
            ConfigurationProperty propertyName,
            String value
    );

    void lock();

    ValueMetaData readSettingMetadata(final PwmSetting setting, final String profileID);

    void resetSetting(PwmSetting setting, String profileID, UserIdentity userIdentity);

    boolean isDefaultValue(PwmSetting setting);

    boolean isDefaultValue(PwmSetting setting, String profileID);

    StoredValue readSetting(PwmSetting setting);

    StoredValue readSetting(PwmSetting setting, String profileID);

    void copyProfileID(PwmSettingCategory category, String sourceID, String destinationID, UserIdentity userIdentity)
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
