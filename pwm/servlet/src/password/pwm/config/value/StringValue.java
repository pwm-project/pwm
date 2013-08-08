/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2012 The PWM Project
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

import com.google.gson.Gson;
import org.jdom2.CDATA;
import org.jdom2.Element;
import password.pwm.config.PwmSetting;
import password.pwm.config.StoredValue;

import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringValue implements StoredValue {
    protected String value;

    StringValue() {
    }

    public StringValue(final String value) {
        this.value = value == null ? "" : value;
    }

    static StringValue fromJson(final String input) {
        final String newValue = new Gson().fromJson(input,String.class);
        return new StringValue(newValue);
    }

    static StringValue fromXmlElement(final Element settingElement) {
        final Element valueElement = settingElement.getChild("value");
        final String value = valueElement.getText();
        final StringValue stringValue = new StringValue();
        stringValue.value = value;
        return stringValue;
    }

    public List<Element> toXmlValues(final String valueElementName) {
        final Element valueElement = new Element(valueElementName);
        valueElement.addContent(new CDATA(value));
        return Collections.singletonList(valueElement);
    }

    public String toNativeObject() {
        return value;
    }

    public String toDebugString() {
        return value;
    }

    public String toString() {
        return new Gson().toJson(value);
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
}
