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

package password.pwm.http.servlet.configeditor.data;

import lombok.AccessLevel;
import lombok.Builder;
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingCategory;
import password.pwm.config.PwmSettingFlag;
import password.pwm.config.PwmSettingProperty;
import password.pwm.config.PwmSettingSyntax;
import password.pwm.config.PwmSettingTemplateSet;
import password.pwm.util.java.CollectionUtil;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public record SettingInfo(
        String key,
        String label,
        String description,
        PwmSettingCategory category,
        PwmSettingSyntax syntax,
        boolean hidden,
        boolean required,
        Map<String, String> options,
        Map<PwmSettingProperty, String> properties,
        String pattern,
        String placeholder, int level,
        Set<PwmSettingFlag> flags
)
{
    @Builder( access = AccessLevel.PRIVATE )
    public SettingInfo
    {
    }


    static SettingInfo forSetting(
            final PwmSetting setting,
            final PwmSettingTemplateSet template,
            final Locale locale
    )
    {
        return SettingInfo.builder()
                .key( setting.getKey() )
                .label( setting.getLabel( locale ) )
                .description( setting.getDescription( locale ) )
                .category( setting.getCategory() )
                .syntax( setting.getSyntax() )
                .hidden( setting.isHidden() )
                .required( setting.isRequired() )
                .options( setting.getOptions() )
                .properties( setting.getProperties() )
                .pattern( setting.getRegExPattern().toString() )
                .placeholder( setting.getExample( template ) )
                .level( setting.getLevel() )
                .flags( Collections.unmodifiableSet( CollectionUtil.copyToEnumSet( setting.getFlags(), PwmSettingFlag.class ) ) )
                .build();
    }
}
