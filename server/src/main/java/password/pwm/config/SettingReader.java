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

package password.pwm.config;

import password.pwm.config.value.data.ActionConfiguration;
import password.pwm.config.value.data.FormConfiguration;
import password.pwm.util.PasswordData;

import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public interface SettingReader
{
    List<String> readSettingAsStringArray( PwmSetting setting );

    List<FormConfiguration> readSettingAsForm( PwmSetting pwmSetting );

    <E extends Enum<E>> Set<E> readSettingAsOptionList( PwmSetting setting, Class<E> enumClass );

    <E extends Enum<E>> E readSettingAsEnum( PwmSetting setting, Class<E> enumClass );

    List<X509Certificate> readSettingAsCertificate( PwmSetting setting );

    boolean readSettingAsBoolean( PwmSetting setting );

    long readSettingAsLong( PwmSetting setting );

    String readSettingAsLocalizedString( PwmSetting setting, Locale locale );

    List<ActionConfiguration> readSettingAsAction( PwmSetting setting );

    PasswordData readSettingAsPassword( PwmSetting setting );

    String readSettingAsString( PwmSetting oauthIdLoginUrl );
}
