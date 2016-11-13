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

package password.pwm.http.servlet.configeditor;

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

public class SettingInfo implements Serializable {
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

    public String getKey() {
        return key;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    public PwmSettingCategory getCategory() {
        return category;
    }

    public PwmSettingSyntax getSyntax() {
        return syntax;
    }

    public boolean isHidden() {
        return hidden;
    }

    public boolean isRequired() {
        return required;
    }

    public Map<String, String> getOptions() {
        return options;
    }

    public Map<PwmSettingProperty, String> getProperties() {
        return properties;
    }

    public String getPattern() {
        return pattern;
    }

    public String getPlaceholder() {
        return placeholder;
    }

    public int getLevel() {
        return level;
    }

    public List<PwmSettingFlag> getFlags() {
        return flags;
    }

    public static SettingInfo forSetting(
            final PwmSetting setting,
            final PwmSettingTemplateSet template,
            final Locale locale
    ) {
        final MacroMachine macroMachine = MacroMachine.forStatic();
        final SettingInfo settingInfo = new SettingInfo();
        settingInfo.key = setting.getKey();
        settingInfo.description = macroMachine.expandMacros(setting.getDescription(locale));
        settingInfo.level = setting.getLevel();
        settingInfo.label = setting.getLabel(locale);
        settingInfo.syntax = setting.getSyntax();
        settingInfo.category = setting.getCategory();
        settingInfo.properties = setting.getProperties();
        settingInfo.required = setting.isRequired();
        settingInfo.hidden = setting.isHidden();
        settingInfo.options = setting.getOptions();
        settingInfo.pattern = setting.getRegExPattern().toString();
        settingInfo.placeholder = setting.getExample(template);
        settingInfo.flags = new ArrayList<>(setting.getFlags());
        return settingInfo;
    }
}
