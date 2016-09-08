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

package password.pwm.config.value;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.apache.commons.validator.routines.EmailValidator;
import org.jdom2.CDATA;
import org.jdom2.Element;

import password.pwm.config.PwmSetting;
import password.pwm.config.StoredValue;
import password.pwm.util.JsonUtil;
import password.pwm.util.secure.PwmSecurityKey;

public class EmailAddressValue extends AbstractValue implements StoredValue {
    private static final long serialVersionUID = 1L;

    protected String value;

    EmailAddressValue() {
    }

    public EmailAddressValue(final String value) {
        this.value = value == null ? "" : value;
    }

    public static StoredValueFactory factory() {
        return new StoredValueFactory() {
            public EmailAddressValue fromJson(final String input) {
                final String newValue = JsonUtil.deserialize(input, String.class);
                return new EmailAddressValue(newValue);
            }

            public EmailAddressValue fromXmlElement(final Element settingElement, final PwmSecurityKey key) {
                final Element valueElement = settingElement.getChild("value");
                return new EmailAddressValue(valueElement == null ? "" : valueElement.getText());
            }
        };
    }

    public List<Element> toXmlValues(final String valueElementName) {
        final Element valueElement = new Element(valueElementName);
        valueElement.addContent(new CDATA(value));
        return Collections.singletonList(valueElement);
    }

    public String toNativeObject() {
        return value;
    }

    @Override
    public List<String> validateValue(PwmSetting pwmSetting) {
        if (pwmSetting.isRequired()) {
            if (value == null || value.length() < 1) {
                return Collections.singletonList("required value missing");
            }
        }

        if (value != null) {
            if (!EmailValidator.getInstance(true, true).isValid(value)) {
                return Collections.singletonList("Invalid email address format: '" + value + "'");
            }
        }

        return Collections.emptyList();
    }

    @Override
    public String toDebugString(Locale locale) {
        return value == null ? "" : value;
    }
}
