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
import password.pwm.config.PwmSettingTemplateSet;
import password.pwm.config.value.StoredValue;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.i18n.PwmLocaleBundle;
import password.pwm.util.secure.PwmSecurityKey;

import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

public interface StoredConfiguration
{
    PwmSecurityKey getKey( ) throws PwmUnrecoverableException;

    String createTime();

    Instant modifyTime( );

    Optional<String> readConfigProperty( ConfigurationProperty propertyName );

    Map<DomainID, PwmSettingTemplateSet> getTemplateSets();

    Optional<ValueMetaData> readSettingMetadata( StoredConfigKey storedConfigKey );

    Map<String, String> readLocaleBundleMap( PwmLocaleBundle bundleName, String keyName, DomainID domainID );

    Iterator<StoredConfigKey> keys();

    Optional<ValueMetaData> readMetaData( StoredConfigKey storedConfigKey );

    Optional<StoredValue> readStoredValue( StoredConfigKey storedConfigKey );

    StoredConfiguration copy();
}
