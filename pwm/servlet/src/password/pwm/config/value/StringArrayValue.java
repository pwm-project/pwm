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
import com.google.gson.reflect.TypeToken;
import org.jdom2.CDATA;
import org.jdom2.Element;
import password.pwm.config.PwmSetting;
import password.pwm.config.StoredValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringArrayValue implements StoredValue {
    final List<String> value;

    public StringArrayValue(final List<String> values) {
        this.value = values;
    }

    static StringArrayValue fromJson(final String input) {
        if (input == null) {
            return new StringArrayValue(Collections.<String>emptyList());
        } else {
            final Gson gson = new Gson();
            List<String> srcList = gson.fromJson(input, new TypeToken<List<String>>() {
            }.getType());
            srcList = srcList == null ? Collections.<String>emptyList() : srcList;
            srcList.removeAll(Collections.singletonList(null));
            return new StringArrayValue(Collections.unmodifiableList(srcList));
        }
    }

    static StringArrayValue fromXmlElement(final Element settingElement) {
        final List valueElements = settingElement.getChildren("value");
        final List<String> values = new ArrayList<String>();
        for (final Object loopValue : valueElements) {
            final Element loopValueElement = (Element) loopValue;
            final String value = loopValueElement.getText();
            values.add(value);
        }
        return new StringArrayValue(values);
    }

    public List<Element> toXmlValues(final String valueElementName) {
        final List<Element> returnList = new ArrayList<Element>();
        for (final String value : this.value) {
            final Element valueElement = new Element(valueElementName);
            valueElement.addContent(new CDATA(value));
            returnList.add(valueElement);
        }
        return returnList;
    }

    public List<String> toNativeObject() {
        return Collections.unmodifiableList(value);
    }

    public String toString() {
        return new Gson().toJson(value);
    }

    public String toDebugString() {
        return toString();
    }

    public List<String> validateValue(PwmSetting pwmSetting) {
        if (pwmSetting.isRequired()) {
            if (value == null || value.size() < 1 || value.get(0).length() < 1) {
                return Collections.singletonList("required value missing");
            }
        }

        final Pattern pattern = pwmSetting.getRegExPattern();
        for (final String loopValue : value) {
            final Matcher matcher = pattern.matcher(loopValue);
            if (loopValue != null && loopValue.length() > 0 && !matcher.matches()) {
                return Collections.singletonList("incorrect value format for value '" + loopValue + "'");
            }
        }

        return Collections.emptyList();
    }
}
