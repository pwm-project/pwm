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

import password.pwm.config.PwmSettingCategory;

import java.io.Serializable;
import java.util.Locale;

public class CategoryInfo implements Serializable {
    private int level;
    private String key;
    private String description;
    private String label;
    private String parent;
    private boolean hidden;
    private boolean profiles;
    private String menuLocation;

    public int getLevel() {
        return level;
    }

    public String getKey() {
        return key;
    }

    public String getDescription() {
        return description;
    }

    public String getLabel() {
        return label;
    }

    public String getParent() {
        return parent;
    }

    public boolean isHidden() {
        return hidden;
    }

    public boolean isProfiles() {
        return profiles;
    }

    public String getMenuLocation() {
        return menuLocation;
    }

    public static CategoryInfo forCategory(final PwmSettingCategory category, final Locale locale) {
        final CategoryInfo categoryInfo = new CategoryInfo();
        categoryInfo.key = category.getKey();
        categoryInfo.level = category.getLevel();
        categoryInfo.description = category.getDescription(locale);
        categoryInfo.label = category.getLabel(locale);
        categoryInfo.hidden = category.isHidden();
        if (category.getParent() != null) {
            categoryInfo.parent = category.getParent().getKey();
        }
        categoryInfo.profiles = category.hasProfiles();
        categoryInfo.menuLocation = category.toMenuLocationDebug("PROFILE",locale);
        return categoryInfo;
    }
}
