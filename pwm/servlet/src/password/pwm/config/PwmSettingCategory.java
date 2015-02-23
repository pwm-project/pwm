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

import java.util.*;

public enum PwmSettingCategory {

    LDAP                        (null),
    SETTINGS                    (null),
    PROFILES                    (null),
    MODULES                     (null),

    GENERAL                     (SETTINGS),

    LDAP_PROFILE                (LDAP),
    LDAP_GLOBAL                 (LDAP),
    EDIRECTORY                  (LDAP),
    ACTIVE_DIRECTORY            (LDAP),
    ORACLE_DS                   (LDAP),

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
        return description == null ? "" : description.getText();
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

    public String toMenuLocationDebug(
            final String profileID,
            final Locale locale
    ) {
        final String SEPARATOR = " -> ";
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

        if (profileID != null) {
            sb.append(SEPARATOR);
            sb.append(profileID);
        }

        return sb.toString();
    }

}
