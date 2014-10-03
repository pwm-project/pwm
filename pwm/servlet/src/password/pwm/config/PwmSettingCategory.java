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

import java.util.Locale;

public enum PwmSettingCategory {
    GENERAL             (Type.SETTING),
    LDAP_GLOBAL         (Type.SETTING),
    LDAP_PROFILE        (Type.PROFILE),
    USER_INTERFACE      (Type.SETTING),
    PASSWORD_GLOBAL     (Type.SETTING),
    CHALLENGE           (Type.SETTING),
    EMAIL               (Type.SETTING),
    SMS                 (Type.SETTING),
    SECURITY            (Type.SETTING),
    CAPTCHA             (Type.SETTING),
    INTRUDER            (Type.SETTING),
    TOKEN               (Type.SETTING),
    OTP                 (Type.SETTING),
    LOGGING             (Type.SETTING),
    AUDITING            (Type.SETTING),
    EDIRECTORY          (Type.SETTING),
    ACTIVE_DIRECTORY    (Type.SETTING),
    ORACLE_DS           (Type.SETTING),
    DATABASE            (Type.SETTING),
    REPORTING           (Type.SETTING),
    MISC                (Type.SETTING),
    OAUTH               (Type.SETTING),

    PASSWORD_POLICY     (Type.PROFILE),
    CHALLENGE_POLICY    (Type.PROFILE),
    HELPDESK_PROFILE    (Type.PROFILE),

    CHANGE_PASSWORD     (Type.MODULE),
    ACCOUNT_INFO        (Type.MODULE),
    RECOVERY            (Type.MODULE),
    FORGOTTEN_USERNAME  (Type.MODULE),
    NEWUSER             (Type.MODULE),
    GUEST               (Type.MODULE),
    ACTIVATION          (Type.MODULE),
    UPDATE              (Type.MODULE),
    SHORTCUT            (Type.MODULE),
    PEOPLE_SEARCH       (Type.MODULE),
    HELPDESK            (Type.MODULE),

    ;

    public enum Type {
        SETTING, MODULE, PROFILE
    }

    private final Type type;

    PwmSettingCategory(final Type type) {
        this.type = type;
    }

    public String getKey() {
        return this.toString();
    }

    public PwmSetting getProfileSetting()
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

    public Type getType() {
        return type;
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
}
