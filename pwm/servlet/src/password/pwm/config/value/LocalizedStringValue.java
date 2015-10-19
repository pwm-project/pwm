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

import com.google.gson.reflect.TypeToken;
import org.jdom2.CDATA;
import org.jdom2.Element;
import password.pwm.config.PwmSetting;
import password.pwm.config.StoredValue;
import password.pwm.util.JsonUtil;
import password.pwm.util.LocaleHelper;
import password.pwm.util.secure.PwmSecurityKey;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LocalizedStringValue extends AbstractValue implements StoredValue {
    final Map<String, String> value;

    public LocalizedStringValue(final Map<String, String> values) {
        this.value = Collections.unmodifiableMap(values);
    }

    public static StoredValueFactory factory()
    {
        return new StoredValueFactory() {
            public LocalizedStringValue fromJson(final String input)
            {
                if (input == null) {
                    return new LocalizedStringValue(Collections.<String, String>emptyMap());
                } else {
                    Map<String, String> srcMap = JsonUtil.deserialize(input, new TypeToken<Map<String, String>>() {});
                    srcMap = srcMap == null ? Collections.<String, String>emptyMap() : new TreeMap<>(srcMap);
                    return new LocalizedStringValue(Collections.unmodifiableMap(srcMap));
                }
            }

            public LocalizedStringValue fromXmlElement(Element settingElement, final PwmSecurityKey key)
            {
                final List elements = settingElement.getChildren("value");
                final Map<String, String> values = new TreeMap<>();
                for (final Object loopValue : elements) {
                    final Element loopValueElement = (Element) loopValue;
                    final String localeString = loopValueElement.getAttributeValue("locale");
                    final String value = loopValueElement.getText();
                    values.put(localeString == null ? "" : localeString, value);
                }
                return new LocalizedStringValue(values);
            }
        };
    }

    public List<Element> toXmlValues(final String valueElementName) {
        final List<Element> returnList = new ArrayList<>();
        for (final String locale : value.keySet()) {
            final String value = this.value.get(locale);
            final Element valueElement = new Element(valueElementName);
            valueElement.addContent(new CDATA(value));
            if (locale != null && locale.length() > 0) {
                valueElement.setAttribute("locale", locale);
            }
            returnList.add(valueElement);
        }
        return returnList;
    }

    public Map<String, String> toNativeObject() {
        return Collections.unmodifiableMap(value);
    }

    public List<String> validateValue(PwmSetting pwmSetting) {
        if (pwmSetting.isRequired()) {
            if (value == null || value.size() < 1 || value.values().iterator().next().length() < 1) {
                return Collections.singletonList("required value missing");
            }
        }

        final Pattern pattern = pwmSetting.getRegExPattern();
        for (final String locale : value.keySet()) {
            final String loopValue = value.get(locale);
            final Matcher matcher = pattern.matcher(loopValue);
            if (loopValue.length() > 0 && !matcher.matches()) {
                return Collections.singletonList("incorrect value format for value '" + loopValue + "'");
            }
        }

        return Collections.emptyList();
    }

    @Override
    public String toDebugString(Locale locale) {
        final StringBuilder sb = new StringBuilder();
        for (final String localeKey : value.keySet()) {
            if (value.size() > 1) {
                sb.append("Locale: ").append(LocaleHelper.debugLabel(LocaleHelper.parseLocaleString(localeKey))).append("\n");
            }
            sb.append(" ").append(value.get(localeKey)).append("\n");
        }
        return sb.toString();
    }
}
