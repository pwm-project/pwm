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
import org.jdom2.Element;
import password.pwm.config.PwmSetting;
import password.pwm.config.StoredValue;
import password.pwm.error.PwmOperationalException;
import password.pwm.util.JsonUtil;
import password.pwm.util.secure.PwmSecurityKey;

import java.util.*;

public class OptionListValue extends AbstractValue  implements StoredValue {
    final Set<String> values;

    public OptionListValue(final Set<String> values) {
        this.values = new TreeSet(values);
    }

    public static StoredValueFactory factory()
    {
        return new StoredValueFactory() {
            public OptionListValue fromJson(final String input)
            {
                if (input == null) {
                    return new OptionListValue(Collections.<String>emptySet());
                } else {
                    Set<String> srcList = JsonUtil.deserialize(input, new TypeToken<Set<String>>() {});
                    srcList = srcList == null ? Collections.<String>emptySet() : srcList;
                    while (srcList.contains(null)) {
                        srcList.remove(null);
                    }
                    return new OptionListValue(Collections.unmodifiableSet(srcList));
                }
            }

            public OptionListValue fromXmlElement(Element settingElement, final PwmSecurityKey key)
                    throws PwmOperationalException
            {
                final List valueElements = settingElement.getChildren("value");
                final Set<String> values = new TreeSet<>();
                for (final Object loopValue : valueElements) {
                    final Element loopValueElement = (Element) loopValue;
                    final String value = loopValueElement.getText();
                    if (value != null && !value.trim().isEmpty()) {
                        values.add(value);
                    }
                }
                return new OptionListValue(values);
            }
        };
    }

    public List<Element> toXmlValues(final String valueElementName) {
        final List<Element> returnList = new ArrayList<>();
        for (final String value : values) {
            final Element valueElement = new Element(valueElementName);
            valueElement.addContent(value);
            returnList.add(valueElement);
        }
        return returnList;
    }

    public Set<String> toNativeObject() {
        return Collections.unmodifiableSet(values);
    }

    public List<String> validateValue(PwmSetting pwmSetting) {
        return Collections.emptyList();
    }

    public String toDebugString(Locale locale) {
        final StringBuilder sb = new StringBuilder();
        for (Iterator valueIterator = values.iterator() ; valueIterator.hasNext();) {
            sb.append(valueIterator.next());
            if (valueIterator.hasNext()) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }
}
