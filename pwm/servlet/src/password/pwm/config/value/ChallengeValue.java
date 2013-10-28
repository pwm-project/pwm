/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2013 The PWM Project
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
import org.jdom2.Element;
import password.pwm.ChallengeItemBean;
import password.pwm.config.PwmSetting;
import password.pwm.config.StoredValue;
import password.pwm.error.PwmOperationalException;
import password.pwm.util.Helper;

import java.util.*;

public class ChallengeValue implements StoredValue {
    final Map<String,ChallengeItemBean> values;

    ChallengeValue(final Map<String, ChallengeItemBean> values) {
        this.values = values;
    }

    static ChallengeValue fromJson(final String input) {
        if (input == null) {
            return new ChallengeValue(Collections.<String,ChallengeItemBean>emptyMap());
        } else {
            final Gson gson = Helper.getGson();
            Map<String,ChallengeItemBean> srcList = gson.fromJson(input, new TypeToken<Map<String,ChallengeItemBean>>() {
            }.getType());

            srcList = srcList == null ? Collections.<String,ChallengeItemBean>emptyMap() : srcList;
            srcList.remove(null);
            return new ChallengeValue(Collections.unmodifiableMap(srcList));
        }
    }

    static ChallengeValue fromXmlElement(Element settingElement) throws PwmOperationalException {
        final Map<String,ChallengeItemBean> values = new HashMap<String,ChallengeItemBean>();
        final Gson gson = Helper.getGson();
        {
            final List valueElements = settingElement.getChildren("value");
            for (final Object loopValue : valueElements) {
                final Element loopValueElement = (Element) loopValue;
                final String value = loopValueElement.getText();
                if (value != null && value.length() > 0) {
                    String localeValue = loopValueElement.getAttribute("locale") == null ? "" : loopValueElement.getAttribute("locale").getValue();
                    values.put(localeValue, gson.fromJson(value, ChallengeItemBean.class));
                }
            }
        }
        return new ChallengeValue(values);
    }

    public List<Element> toXmlValues(final String valueElementName) {
        final List<Element> returnList = new ArrayList<Element>();
        final Gson gson = Helper.getGson();
        for (final String localeValue : values.keySet()) {
            final ChallengeItemBean emailItemBean = values.get(localeValue);
            final Element valueElement = new Element(valueElementName);
            if (localeValue.length() > 0) {
                valueElement.setAttribute("locale",localeValue);
            }
            valueElement.addContent(gson.toJson(emailItemBean));
            returnList.add(valueElement);
        }
        return returnList;
    }

    public Map<String,ChallengeItemBean> toNativeObject() {
        return Collections.unmodifiableMap(values);
    }

    public String toString() {
        return Helper.getGson().toJson(values);
    }

    public List<String> validateValue(PwmSetting pwmSetting) {
        if (pwmSetting.isRequired()) {
            if (values == null || values.size() < 1 || values.get(0) == null) {
                return Collections.singletonList("required value missing");
            }
        }

        for (final String loopLocale : values.keySet()) {
            final ChallengeItemBean emailItemBean = values.get(loopLocale);

            //@todo
        }

        return Collections.emptyList();
    }

    public String toDebugString() {
        return toString();
    }
}
