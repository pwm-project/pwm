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

import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.PwmEnvironment;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingCategory;
import password.pwm.config.StoredValue;
import password.pwm.config.stored.StoredConfigurationImpl;
import password.pwm.i18n.PwmLocaleBundle;
import password.pwm.util.StringUtil;

import java.io.Serializable;
import java.util.*;

class NavTreeHelper {
    static Set<String> determineModifiedKeysSettings(
            final PwmLocaleBundle bundle,
            final Configuration config,
            final StoredConfigurationImpl storedConfiguration
    ) {
        final Set<String> modifiedKeys = new TreeSet<>();
        for (final String key : bundle.getKeys()) {
            final Map<String,String> storedBundle = storedConfiguration.readLocaleBundleMap(bundle.getTheClass().getName(),key);
            if (!storedBundle.isEmpty()) {
                for (final Locale locale : config.getKnownLocales()) {
                    final ResourceBundle defaultBundle = ResourceBundle.getBundle(bundle.getTheClass().getName(), locale);
                    final String localeKeyString = PwmConstants.DEFAULT_LOCALE.toString().equals(locale.toString()) ? "" : locale.toString();
                    if (storedBundle.containsKey(localeKeyString)) {
                        final String value = storedBundle.get(localeKeyString);
                        if (value != null && !value.equals(defaultBundle.getString(key))) {
                            modifiedKeys.add(key);
                        }
                    }
                }
            }
        }
        return modifiedKeys;
    }

    static boolean categoryMatcher(
            PwmApplication pwmApplication,
            PwmSettingCategory category,
            StoredConfigurationImpl storedConfiguration,
            final boolean modifiedOnly,
            final int minLevel,
            final String text
    ) {
        if (category.isHidden()) {
            return false;
        }

        if (category == PwmSettingCategory.HTTPS_SERVER) {
            if (!pwmApplication.getPwmEnvironment().getFlags().contains(PwmEnvironment.ApplicationFlag.ManageHttps)) {
                return false;
            }
        }

        for (PwmSettingCategory childCategory : category.getChildCategories()) {
            if (categoryMatcher(pwmApplication, childCategory, storedConfiguration, modifiedOnly, minLevel, text)) {
                return true;
            }
        }

        if (category.hasProfiles()) {
            final List<String> profileIDs = storedConfiguration.profilesForSetting(category.getProfileSetting());
            if (profileIDs == null || profileIDs.isEmpty()) {
                return true;
            }
            for (String profileID : profileIDs) {
                for (final PwmSetting setting : category.getSettings()) {
                    if (settingMatches(storedConfiguration,setting,profileID,modifiedOnly,minLevel,text)) {
                        return true;
                    }
                }
            }

        } else {
            for (final PwmSetting setting : category.getSettings()) {
                if (settingMatches(storedConfiguration,setting,null,modifiedOnly,minLevel,text)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean settingMatches(
            final StoredConfigurationImpl storedConfiguration,
            final PwmSetting setting,
            final String profileID,
            final boolean modifiedOnly,
            final int level,
            final String text
    ) {
        if (setting.isHidden()) {
            return false;
        }

        if (modifiedOnly) {
            if (storedConfiguration.isDefaultValue(setting,profileID)) {
                return false;
            }
        }

        if (level > 0 && setting.getLevel() > level) {
            return false;
        }


        if (text == null || text.isEmpty()) {
            return true;
        } else {
            final StoredValue storedValue = storedConfiguration.readSetting(setting,profileID);
            for (final String term : StringUtil.whitespaceSplit(text)) {
                if (storedConfiguration.matchSetting(setting, storedValue, term, PwmConstants.DEFAULT_LOCALE)) {
                    return true;
                }
            }
        }

        return false;
    }

    static void moveNavItemToTopOfList(final String categoryID, final List<NavTreeItem> navigationData) {
        { // put templates on top
            NavTreeItem templateEntry = null;
            for (NavTreeItem entry : navigationData) {
                if (categoryID.equals(entry.id)) {
                    templateEntry = entry;
                }
            }
            if (templateEntry != null) {
                navigationData.remove(templateEntry);
                navigationData.add(0, templateEntry);
            }
        }
    }

    enum NavItemType {
        category,
        navigation,
        displayText,
        profile,
        profileDefinition,
    }

    static class NavTreeItem implements Serializable {
        private String id;
        private String name;
        private String parent;
        private String category;
        private NavItemType type;
        private String profileSetting;
        private String menuLocation;
        private Set<String> keys;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getParent() {
            return parent;
        }

        public void setParent(String parent) {
            this.parent = parent;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public NavItemType getType() {
            return type;
        }

        public void setType(NavItemType type) {
            this.type = type;
        }

        public String getProfileSetting() {
            return profileSetting;
        }

        public void setProfileSetting(String profileSetting) {
            this.profileSetting = profileSetting;
        }

        public String getMenuLocation() {
            return menuLocation;
        }

        public void setMenuLocation(String menuLocation) {
            this.menuLocation = menuLocation;
        }

        public Set<String> getKeys() {
            return keys;
        }

        public void setKeys(Set<String> keys) {
            this.keys = keys;
        }
    }
}
