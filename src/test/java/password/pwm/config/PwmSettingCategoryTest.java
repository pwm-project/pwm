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

package password.pwm.config;

import org.junit.Assert;
import org.junit.Test;
import password.pwm.PwmConstants;

import java.util.HashSet;
import java.util.Set;

public class PwmSettingCategoryTest {
    @Test
    public void testLabels() {
        for (final PwmSettingCategory category : PwmSettingCategory.values()) {
            category.getLabel(PwmConstants.DEFAULT_LOCALE);
        }
    }

    @Test
    public void testDescriptions() {
        for (final PwmSettingCategory category : PwmSettingCategory.values()) {
            category.getDescription(PwmConstants.DEFAULT_LOCALE);
        }
    }

    @Test
    public void testProfileSetting() {
        for (final PwmSettingCategory category : PwmSettingCategory.values()) {
            if (category.hasProfiles()) {
                category.getProfileSetting();
            }
        }
    }


    @Test
    public void testProfileCategoryHasSettingsOrChildren() {
        for (final PwmSettingCategory category : PwmSettingCategory.values()) {
            if (category.hasProfiles()) {
                boolean hasChildren = !category.getChildCategories().isEmpty();
                boolean hasSettings = !category.getSettings().isEmpty();
                Assert.assertTrue(hasChildren || hasSettings);
                Assert.assertFalse(category.getKey() + " has both child categories and settings", hasChildren && hasSettings);
            }
        }
    }

    @Test
    public void testProfileSettingUniqueness() {
        final Set<PwmSetting> seenSettings = new HashSet<>();
        /*
        for (final PwmSettingCategory category : PwmSettingCategory.values()) {
            if (category.hasProfiles()) {
                Assert.assertTrue(!seenSettings.contains(category.getProfileSetting())); // duplicate category
                seenSettings.add(category.getProfileSetting());
            }
        }
        */  //@todo removed during multi-level profiled-category introduction
    }
}
