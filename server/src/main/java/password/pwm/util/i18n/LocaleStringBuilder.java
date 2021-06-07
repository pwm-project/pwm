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

package password.pwm.util.i18n;

import password.pwm.config.SettingReader;
import password.pwm.i18n.PwmDisplayBundle;

import java.util.Locale;

public class LocaleStringBuilder
{
    private final SettingReader settingReader;
    private final Class<? extends PwmDisplayBundle> bundleClass;
    private final Locale locale;

    public LocaleStringBuilder(
            final Locale locale,
            final Class<? extends PwmDisplayBundle> bundleClass,
            final SettingReader settingReader
    )
    {
        this.locale = locale;
        this.bundleClass = bundleClass;
        this.settingReader = settingReader;
    }

    public String forKey( final String input, final String... values )
    {
        return LocaleHelper.getLocalizedMessage( locale, input, settingReader, bundleClass, values );
    }
}
