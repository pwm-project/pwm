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

import password.pwm.config.PwmSettingCategory;

import java.util.Locale;

public record CategoryInfo(
        int level,
        String key,
        String description,
        String label,
        String parent,
        boolean hidden,
        boolean profiles,
        String menuLocation
)
{
    public static CategoryInfo forCategory(
            final PwmSettingCategory category,
            final Locale locale
    )
    {
        return new CategoryInfo(
                category.getLevel(),
                category.getKey(),
                category.getDescription( locale ),
                category.getLabel( locale ),
                category.getParent() != null ? category.getParent().getKey() : null,
                category.isHidden(),
                category.hasProfiles(),
                category.toMenuLocationDebug( null, locale ) );
    }
}
