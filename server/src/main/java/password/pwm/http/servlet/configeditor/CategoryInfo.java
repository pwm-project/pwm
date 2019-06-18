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
import password.pwm.config.PwmSettingCategory;
import password.pwm.util.macro.MacroMachine;

import java.io.Serializable;
import java.util.Locale;

@Data
public class CategoryInfo implements Serializable
{
    private int level;
    private String key;
    private String description;
    private String label;
    private String parent;
    private boolean hidden;
    private boolean profiles;
    private String menuLocation;


    public static CategoryInfo forCategory(
            final PwmSettingCategory category,
            final MacroMachine macroMachine,
            final Locale locale )
    {
        final CategoryInfo categoryInfo = new CategoryInfo();
        categoryInfo.key = category.getKey();
        categoryInfo.level = category.getLevel();
        categoryInfo.description = macroMachine.expandMacros( category.getDescription( locale ) );
        categoryInfo.label = category.getLabel( locale );
        categoryInfo.hidden = category.isHidden();
        if ( category.getParent() != null )
        {
            categoryInfo.parent = category.getParent().getKey();
        }
        categoryInfo.profiles = category.hasProfiles();
        categoryInfo.menuLocation = category.toMenuLocationDebug( "PROFILE", locale );
        return categoryInfo;
    }
}
