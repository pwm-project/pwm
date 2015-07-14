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

package password.pwm.config.value;

import org.jdom2.CDATA;
import org.jdom2.Element;
import password.pwm.config.PwmSetting;
import password.pwm.config.StoredValue;
import password.pwm.util.JsonUtil;
import password.pwm.util.secure.PwmSecurityKey;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringValue extends AbstractValue implements StoredValue {
    protected String value;

    StringValue() {
    }

    public StringValue(final String value) {
        this.value = value == null ? "" : value;
    }

    public static StoredValueFactory factory()
    {
        return new StoredValueFactory() {
            public StringValue fromJson(final String input)
            {
                final String newValue = JsonUtil.deserialize(input, String.class);
                return new StringValue(newValue);
            }

            public StringValue fromXmlElement(final Element settingElement, final PwmSecurityKey key)
            {
                final Element valueElement = settingElement.getChild("value");
                return new StringValue(valueElement == null ? "" : valueElement.getText());
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

    public List<String> validateValue(PwmSetting pwmSetting) {
        if (pwmSetting.isRequired()) {
            if (value == null || value.length() < 1) {
                return Collections.singletonList("required value missing");
            }
        }

        final Pattern pattern = pwmSetting.getRegExPattern();
        if (pattern != null && value != null) {
            final Matcher matcher = pattern.matcher(value);
            if (value != null && value.length() > 0 && !matcher.matches()) {
                return Collections.singletonList("incorrect value format for value '" + value + "'");
            }
        }

        return Collections.emptyList();
    }

    @Override
    public String toDebugString(
            Locale locale
    )
    {
        return value == null ? "" : value;
    }
}
