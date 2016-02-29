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

import org.jdom2.Attribute;
import org.jdom2.Element;
import password.pwm.util.Helper;

public enum PwmSettingTemplate {
    NOVL(Type.LDAP_VENDOR),
    AD(Type.LDAP_VENDOR),
    ORACLE_DS(Type.LDAP_VENDOR),
    DEFAULT(Type.LDAP_VENDOR),
    NOVL_IDM(Type.LDAP_VENDOR),
    OPEN_LDAP(Type.LDAP_VENDOR),

    LOCALDB(Type.STORAGE),
    DB(Type.STORAGE),
    LDAP(Type.STORAGE),

    DB_ORACLE(Type.DB_VENDOR),
    DB_OTHER(Type.DB_VENDOR),

    ;

    private final Type type;

    PwmSettingTemplate(final Type type) {
        this.type = type;
    }

    public Type getType() {
        return type;
    }

    public static PwmSettingTemplate templateForString(final String input, final Type type) {
        final PwmSettingTemplate template = Helper.readEnumFromString(PwmSettingTemplate.class, type.getDefaultValue(), input);
        return template == null || template.getType() != type ? type.getDefaultValue() : template;
    }

    public boolean isHidden() {
        final Element templateElement = readTemplateElement(this);
        final Attribute requiredAttribute = templateElement.getAttribute("hidden");
        return requiredAttribute != null && "true".equalsIgnoreCase(requiredAttribute.getValue());
    }

    private static Element readTemplateElement(PwmSettingTemplate pwmSettingTemplate) {
        final Element element = PwmSettingXml.readTemplateXml(pwmSettingTemplate);
        if (element == null) {
            throw new IllegalStateException("missing PwmSetting.xml template element for " + pwmSettingTemplate);
        }
        return element;
    }

    public enum Type {
        LDAP_VENDOR,
        STORAGE,
        DB_VENDOR,

        ;

        static {
            LDAP_VENDOR.defaultValue = DEFAULT;
            STORAGE.defaultValue = LDAP;
            DB_VENDOR.defaultValue = DB_OTHER;
        }

        private PwmSettingTemplate defaultValue;

        public PwmSettingTemplate getDefaultValue() {
            return defaultValue;
        }
    }
}
