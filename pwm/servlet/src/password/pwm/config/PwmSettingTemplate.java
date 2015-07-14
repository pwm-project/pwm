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
import password.pwm.i18n.ConfigEditor;
import password.pwm.i18n.LocaleHelper;

import java.util.*;

public enum PwmSettingTemplate {
    NOVL,
    AD,
    ORACLE_DS,
    DEFAULT,
    NOVL_IDM,

    ;

    public String getLabel(final Locale locale) {
        final String key = "Template_Label_" + this.toString();
        return LocaleHelper.getLocalizedMessage(locale, key, null, ConfigEditor.class);
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

    public static List<PwmSettingTemplate> sortedValues(final Locale locale) {
        final Map<String,PwmSettingTemplate> sortedValues = new TreeMap<>();

        for (final PwmSettingTemplate pwmSettingTemplate : values()) {
            sortedValues.put(pwmSettingTemplate.getLabel(locale), pwmSettingTemplate);
        }

        return Collections.unmodifiableList(new ArrayList<>(sortedValues.values()));
    }
}
