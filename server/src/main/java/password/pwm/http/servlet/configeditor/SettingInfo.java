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

package password.pwm.http.servlet.configeditor;

import lombok.Data;
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingCategory;
import password.pwm.config.PwmSettingFlag;
import password.pwm.config.PwmSettingProperty;
import password.pwm.config.PwmSettingSyntax;
import password.pwm.config.PwmSettingTemplateSet;
import password.pwm.util.macro.MacroMachine;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Data
public class SettingInfo implements Serializable
{
    private String key;
    private String label;
    private String description;
    private PwmSettingCategory category;
    private PwmSettingSyntax syntax;
    private boolean hidden;
    private boolean required;
    private Map<String, String> options;
    private Map<PwmSettingProperty, String> properties;
    private String pattern;
    private String placeholder;
    private int level;
    private List<PwmSettingFlag> flags;

    static SettingInfo forSetting(
            final PwmSetting setting,
            final PwmSettingTemplateSet template,
            final MacroMachine macroMachine,
            final Locale locale
    )
    {
        final SettingInfo settingInfo = new SettingInfo();
        settingInfo.key = setting.getKey();
        settingInfo.description = macroMachine.expandMacros( setting.getDescription( locale ) );
        settingInfo.level = setting.getLevel();
        settingInfo.label = setting.getLabel( locale );
        settingInfo.syntax = setting.getSyntax();
        settingInfo.category = setting.getCategory();
        settingInfo.properties = setting.getProperties();
        settingInfo.required = setting.isRequired();
        settingInfo.hidden = setting.isHidden();
        settingInfo.options = setting.getOptions();
        settingInfo.pattern = setting.getRegExPattern().toString();
        settingInfo.placeholder = setting.getExample( template );
        settingInfo.flags = new ArrayList<>( setting.getFlags() );
        return settingInfo;
    }
}
