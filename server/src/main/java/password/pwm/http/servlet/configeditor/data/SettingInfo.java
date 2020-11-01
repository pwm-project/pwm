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

package password.pwm.http.servlet.configeditor.data;

import lombok.Builder;
import lombok.Value;
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingCategory;
import password.pwm.config.PwmSettingFlag;
import password.pwm.config.PwmSettingProperty;
import password.pwm.config.PwmSettingSyntax;
import password.pwm.config.PwmSettingTemplateSet;
import password.pwm.util.java.JavaHelper;

import java.io.Serializable;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Value
@Builder
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
    private Set<PwmSettingFlag> flags;

    static SettingInfo forSetting(
            final PwmSetting setting,
            final PwmSettingTemplateSet template,
            final Locale locale
    )
    {
        return SettingInfo.builder()
                .key( setting.getKey() )
                .description( setting.getDescription( locale ) )
                .level( setting.getLevel() )
                .label( setting.getLabel( locale ) )
                .syntax( setting.getSyntax() )
                .category( setting.getCategory() )
                .properties( setting.getProperties() )
                .required( setting.isRequired() )
                .hidden( setting.isHidden() )
                .options( setting.getOptions() )
                .pattern( setting.getRegExPattern().toString() )
                .placeholder( setting.getExample( template ) )
                .flags( Collections.unmodifiableSet( JavaHelper.copiedEnumSet( setting.getFlags(), PwmSettingFlag.class ) ) )
                .build();
    }
}
