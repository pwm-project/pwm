/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2015 The PWM Project
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

import org.jdom2.Attribute;
import org.jdom2.Element;
import password.pwm.i18n.Config;
import password.pwm.i18n.ConfigEditor;
import password.pwm.util.LocaleHelper;

import java.util.*;

public enum PwmSettingCategory {

    LDAP                        (null),
    SETTINGS                    (null),
    PROFILES                    (null),
    MODULES                     (null),

    LDAP_PROFILE                (LDAP),
    LDAP_SETTINGS               (LDAP),
    LDAP_GLOBAL                 (LDAP_SETTINGS),

    EDIRECTORY                  (LDAP_SETTINGS),
    EDIR_SETTINGS               (EDIRECTORY),
    EDIR_CR_SETTINGS            (EDIRECTORY),

    ACTIVE_DIRECTORY            (LDAP_SETTINGS),
    ORACLE_DS                   (LDAP_SETTINGS),

    GENERAL                     (SETTINGS),

    AUDITING                    (SETTINGS),
    AUDIT_CONFIG                (AUDITING),
    USER_HISTORY                (AUDITING),
    AUDIT_FORWARD               (AUDITING),

    CAPTCHA                     (SETTINGS),

    INTRUDER                    (SETTINGS),
    INTRUDER_SETTINGS           (INTRUDER),
    INTRUDER_TIMEOUTS           (INTRUDER),

    USER_INTERFACE              (SETTINGS),
    UI_FEATURES                 (USER_INTERFACE),
    UI_WEB                      (USER_INTERFACE),

    EMAIL                       (SETTINGS),
    EMAIL_SETTINGS              (EMAIL),
    EMAIL_TEMPLATES             (EMAIL),

    SMS                         (SETTINGS),
    SMS_GATEWAY                 (SMS),
    SMS_MESSAGES                (SMS),

    SECURITY                    (SETTINGS),
    APP_SECURITY                (SECURITY),
    WEB_SECURITY                (SECURITY),


    TOKEN                       (SETTINGS),
    OTP                         (SETTINGS),
    LOGGING                     (SETTINGS),

    DATABASE                    (SETTINGS),
    REPORTING                   (SETTINGS),
    NAAF                        (SETTINGS),
    
    SSO                         (SETTINGS),
    OAUTH                       (SSO),
    HTTP_SSO                    (SSO),
    CAS_SSO                     (SSO),

    WEB_SERVICES                (SETTINGS),
    REST_SERVER                 (WEB_SERVICES),
    REST_CLIENT                 (WEB_SERVICES),

    PASSWORD_GLOBAL             (PROFILES),
    PASSWORD_POLICY             (PROFILES),
    CHALLENGE                   (PROFILES),
    CHALLENGE_POLICY            (PROFILES),

    ADMINISTRATION              (MODULES),

    ACCOUNT_INFO                (MODULES),
    CHANGE_PASSWORD             (MODULES),
    RECOVERY                    (MODULES),
    RECOVERY_SETTINGS           (RECOVERY),
    RECOVERY_PROFILE            (RECOVERY),

    
    FORGOTTEN_USERNAME          (MODULES),
    
    NEWUSER                     (MODULES),
    NEWUSER_SETTINGS            (NEWUSER),
    NEWUSER_PROFILE             (NEWUSER),
    
    GUEST                       (MODULES),
    ACTIVATION                  (MODULES),
    UPDATE                      (MODULES),
    SHORTCUT                    (MODULES),
    PEOPLE_SEARCH               (MODULES),
    HELPDESK_PROFILE            (MODULES),

    HTTPS_SERVER                (SETTINGS),

    ;

    private final PwmSettingCategory parent;
    private static final Map<PwmSettingCategory,PwmSetting> CACHE_PROFILE_SETTING = new HashMap<>();
    private static List<PwmSettingCategory> cached_sortedSettings;

    private Integer level;
    private Boolean hidden;


    PwmSettingCategory(PwmSettingCategory parent) {
        this.parent = parent;
    }

    public PwmSettingCategory getParent() {
        return parent;
    }

    public String getKey() {
        return this.toString();
    }

    public PwmSetting getProfileSetting()
    {
        if (!CACHE_PROFILE_SETTING.containsKey(this)) {
            CACHE_PROFILE_SETTING.put(this, readProfileSettingFromXml());
        }
        return CACHE_PROFILE_SETTING.get(this);
    }

    public boolean hasProfiles() {
        return getProfileSetting() != null;
    }

    public String getLabel(final Locale locale) {
        final String key = "Category_Label_" + this.getKey();
        return LocaleHelper.getLocalizedMessage(locale, key, null, ConfigEditor.class);
    }

    public String getDescription(final Locale locale) {
        final String key = "Category_Description_" + this.getKey();
        return LocaleHelper.getLocalizedMessage(locale, key, null, ConfigEditor.class);
    }

    public int getLevel() {
        if (level == null) {
            final Element settingElement = PwmSettingXml.readCategoryXml(this);
            final Attribute levelAttribute = settingElement.getAttribute("level");
            level = levelAttribute != null ? Integer.parseInt(levelAttribute.getValue()) : 0;
        }
        return level;
    }

    public boolean isHidden() {
        if (hidden == null) {
            final Element settingElement = PwmSettingXml.readCategoryXml(this);
            final Attribute hiddenElement = settingElement.getAttribute("hidden");
            if (hiddenElement != null && "true".equalsIgnoreCase(hiddenElement.getValue())) {
                hidden = true;
            } else {
                for (final PwmSettingCategory parentCategory : getParents()) {
                    if (parentCategory.isHidden()) {
                        hidden = true;
                    }
                }
            }
            if (hidden == null) {
                hidden = false;
            }
        }
        return hidden;
    }

    public boolean isTopCategory() {
        return getParent() == null;
    }

    public Collection<PwmSettingCategory> getParents() {
        final ArrayList<PwmSettingCategory> returnObj = new ArrayList<>();
        PwmSettingCategory currentCategory = this.getParent();
        while (currentCategory != null) {
            returnObj.add(0,currentCategory);
            currentCategory = currentCategory.getParent();
        }
        return returnObj;
    }

    public Collection<PwmSettingCategory> getChildCategories() {
        final ArrayList<PwmSettingCategory> returnObj = new ArrayList<>();
        for (final PwmSettingCategory category : values()) {
            if (this == category.getParent()) {
                returnObj.add(category);
            }
        }
        return returnObj;
    }

    public static Collection<PwmSettingCategory> topCategories() {
        final ArrayList<PwmSettingCategory> returnObj = new ArrayList<>();
        for (final PwmSettingCategory category : values()) {
            if (category.isTopCategory()) {
                returnObj.add(category);
            }
        }
        return returnObj;
    }

    private PwmSetting readProfileSettingFromXml()
    {
        final Element categoryElement = PwmSettingXml.readCategoryXml(this);
        final Element profileElement = categoryElement.getChild("profile");
        if (profileElement != null) {
            final String settingKey = profileElement.getAttributeValue("setting");
            if (settingKey != null) {
                return PwmSetting.forKey(settingKey);
            }
        }

        return null;
    }

    public List<PwmSetting> getSettings() {
        final List<PwmSetting> returnList = new ArrayList<>();
        for (final PwmSetting setting : PwmSetting.values()) {
            if (setting.getCategory() == this) {
                returnList.add(setting);
            }
        }
        return Collections.unmodifiableList(returnList);
    }

    public String toMenuLocationDebug(
            final String profileID,
            final Locale locale
    ) {
        final String SEPARATOR = LocaleHelper.getLocalizedMessage(locale, Config.Display_SettingNavigationSeparator, null);
        final StringBuilder sb = new StringBuilder();

        PwmSettingCategory nextCategory = this;
        while (nextCategory != null) {
            if (nextCategory != this) {
                sb.insert(0, nextCategory.getLabel(locale) + SEPARATOR);
            } else {
                sb.insert(0, nextCategory.getLabel(locale));
            }
            nextCategory = nextCategory.getParent();
        }

        if (this.hasProfiles()) {
            if (profileID != null) {
                sb.append(SEPARATOR);
                sb.append(profileID);
            } else {
                final String NULL_PROFILE = LocaleHelper.getLocalizedMessage(locale, Config.Display_SettingNavigationNullProfile, null);
                sb.append(SEPARATOR);
                sb.append(NULL_PROFILE);
            }
        }

        return sb.toString();
    }

    public static List<PwmSettingCategory> sortedValues(final Locale locale) {
        if (cached_sortedSettings == null) {
            int counter = 0; // prevents dupes from being eliminated;
            final Map<String, PwmSettingCategory> sortedCategories = new TreeMap<String, PwmSettingCategory>();
            for (final PwmSettingCategory category : PwmSettingCategory.values()) {
                final String sortValue = category.toMenuLocationDebug(null, locale) + (counter++);
                sortedCategories.put(sortValue, category);
            }
            cached_sortedSettings = Collections.unmodifiableList(new ArrayList<>(sortedCategories.values()));
        }
        return cached_sortedSettings;
    }
}
