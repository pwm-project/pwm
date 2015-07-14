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

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringArrayValue extends AbstractValue implements StoredValue {
    final List<String> values;

    public StringArrayValue(final List<String> values) {
        this.values = values;
    }

    public static StoredValueFactory factory()
    {
        return new StoredValueFactory() {
            public StringArrayValue fromJson(final String input)
            {
                if (input == null) {
                    return new StringArrayValue(Collections.<String>emptyList());
                } else {
                    List<String> srcList = JsonUtil.deserializeStringList(input);
                    srcList = srcList == null ? Collections.<String>emptyList() : srcList;
                    while (srcList.contains(null)) {
                        srcList.remove(null);
                    }
                    return new StringArrayValue(Collections.unmodifiableList(srcList));
                }
            }

            public StringArrayValue fromXmlElement(final Element settingElement, final PwmSecurityKey key)
            {
                final List valueElements = settingElement.getChildren("value");
                final List<String> values = new ArrayList<>();
                for (final Object loopValue : valueElements) {
                    final Element loopValueElement = (Element) loopValue;
                    final String value = loopValueElement.getText();
                    values.add(value);
                }
                return new StringArrayValue(values);
            }
        };
    }

    public List<Element> toXmlValues(final String valueElementName) {
        final List<Element> returnList = new ArrayList<>();
        for (final String value : this.values) {
            final Element valueElement = new Element(valueElementName);
            valueElement.addContent(new CDATA(value));
            returnList.add(valueElement);
        }
        return returnList;
    }

    public List<String> toNativeObject() {
        return Collections.unmodifiableList(values);
    }

    public List<String> validateValue(PwmSetting pwmSetting) {
        if (pwmSetting.isRequired()) {
            if (values == null || values.size() < 1 || values.get(0).length() < 1) {
                return Collections.singletonList("required value missing");
            }
        }

        final Pattern pattern = pwmSetting.getRegExPattern();
        for (final String loopValue : values) {
            final Matcher matcher = pattern.matcher(loopValue);
            if (loopValue.length() > 0 && !matcher.matches()) {
                return Collections.singletonList("incorrect value format for value '" + loopValue + "'");
            }
        }

        return Collections.emptyList();
    }

    public String toDebugString(Locale locale) {
        if (values != null && !values.isEmpty()) {
            final StringBuilder sb = new StringBuilder();
            for (Iterator valueIterator = values.iterator() ; valueIterator.hasNext();) {
                sb.append(valueIterator.next());
                if (valueIterator.hasNext()) {
                    sb.append("\n");
                }
            }
            return sb.toString();
        } else {
            return "";
        }
    }
}
