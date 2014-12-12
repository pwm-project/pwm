/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2014 The PWM Project
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

import java.util.*;

public enum PwmSettingCategory {

    SETTINGS(null),
    PROFILES(null),
    MODULES(null),

    GENERAL                     (SETTINGS),
    LDAP                        (SETTINGS),

    LDAP_GLOBAL                 (LDAP),
    EDIRECTORY                  (LDAP),
    ACTIVE_DIRECTORY            (LDAP),
    ORACLE_DS                   (LDAP),

    USER_INTERFACE              (SETTINGS),
    UI_FEATURES                 (USER_INTERFACE),
    UI_WEB                      (USER_INTERFACE),

    PASSWORD_GLOBAL             (SETTINGS),
    CHALLENGE                   (SETTINGS),

    EMAIL                       (SETTINGS),
    EMAIL_SETTINGS              (EMAIL),
    EMAIL_TEMPLATES             (EMAIL),

    SMS                         (SETTINGS),
    SMS_GATEWAY                 (SMS),
    SMS_MESSAGES                (SMS),

    SECURITY                    (SETTINGS),
    APP_SECURITY                (SECURITY),
    WEB_SECURITY                (SECURITY),

    CAPTCHA                     (SETTINGS),
    INTRUDER                    (SETTINGS),
    INTRUDER_SETTINGS           (INTRUDER),
    INTRUDER_TIMEOUTS           (INTRUDER),

    TOKEN                       (SETTINGS),
    OTP                         (SETTINGS),
    LOGGING                     (SETTINGS),

    AUDITING                    (SETTINGS),
    AUDIT_CONFIG                (AUDITING),
    USER_HISTORY                (AUDITING),
    AUDIT_FORWARD               (AUDITING),

    DATABASE                    (SETTINGS),
    REPORTING                   (SETTINGS),
    SSO                         (SETTINGS),
    OAUTH                       (SSO),
    MISC                        (SETTINGS),
    REST_SERVER                 (MISC),
    REST_CLIENT                 (MISC),

    LDAP_PROFILE                (PROFILES),
    PASSWORD_POLICY             (PROFILES),
    CHALLENGE_POLICY            (PROFILES),
    HELPDESK_PROFILE            (PROFILES),
    RECOVERY_PROFILE(PROFILES),

    CHANGE_PASSWORD             (MODULES),
    ACCOUNT_INFO                (MODULES),
    RECOVERY                    (MODULES),
    FORGOTTEN_USERNAME          (MODULES),
    NEWUSER                     (MODULES),
    GUEST                       (MODULES),
    ACTIVATION                  (MODULES),
    UPDATE                      (MODULES),
    SHORTCUT                    (MODULES),
    PEOPLE_SEARCH               (MODULES),
    HELPDESK                    (MODULES),

    ;

    private final PwmSettingCategory parent;
    private static final Map<PwmSettingCategory,PwmSetting> CACHE_PROFILE_SETTING = new HashMap<>();

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
        final Element categoryElement = PwmSettingXml.readCategoryXml(this);
        if (categoryElement == null) {
            throw new IllegalStateException("missing descriptor element for category " + this.toString());
        }
        final Element labelElement = categoryElement.getChild("label");
        if (labelElement == null) {
            throw new IllegalStateException("missing descriptor label for category " + this.toString());
        }
        return labelElement.getText();
    }

    public String getDescription(final Locale locale) {
        Element categoryElement = PwmSettingXml.readCategoryXml(this);
        Element description = categoryElement.getChild("description");
        return description.getText();
    }

    public int getLevel() {
        final Element settingElement = PwmSettingXml.readCategoryXml(this);
        final Attribute levelAttribute = settingElement.getAttribute("level");
        return levelAttribute != null ? Integer.parseInt(levelAttribute.getValue()) : 0;
    }

    public boolean isHidden() {
        final Element settingElement = PwmSettingXml.readCategoryXml(this);
        final Attribute requiredAttribute = settingElement.getAttribute("hidden");
        return requiredAttribute != null && "true".equalsIgnoreCase(requiredAttribute.getValue());
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
}
