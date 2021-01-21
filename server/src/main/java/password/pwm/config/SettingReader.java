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

package password.pwm.config;

import password.pwm.bean.PrivateKeyCertificate;
import password.pwm.config.value.FileValue;
import password.pwm.config.value.data.UserPermission;
import password.pwm.i18n.PwmLocaleBundle;
import password.pwm.util.PasswordData;

import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public interface SettingReader
{
    <E extends Enum<E>> Set<E> readSettingAsOptionList( PwmSetting pwmSetting, Class<E> enumClass );

    boolean readSettingAsBoolean( PwmSetting pwmSetting );

    List<String> readSettingAsStringArray( PwmSetting pwmSetting );

    long readSettingAsLong( PwmSetting pwmSetting );

    PrivateKeyCertificate readSettingAsPrivateKey( PwmSetting setting );

    <E extends Enum<E>> E readSettingAsEnum( PwmSetting setting, Class<E> enumClass );

    Map<FileValue.FileInformation, FileValue.FileContent> readSettingAsFile( PwmSetting pwmSetting );

    List<X509Certificate> readSettingAsCertificate( PwmSetting pwmSetting );

    List<UserPermission> readSettingAsUserPermission( PwmSetting pwmSetting );

    String readSettingAsString( PwmSetting setting );

    PasswordData readSettingAsPassword( PwmSetting setting );

    Map<Locale, String> readLocalizedBundle( PwmLocaleBundle className, String keyName );

}
