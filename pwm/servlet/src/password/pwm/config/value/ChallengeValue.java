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
import org.jdom2.CDATA;
import org.jdom2.Element;
import password.pwm.ChallengeItemBean;
import password.pwm.config.PwmSetting;
import password.pwm.config.StoredValue;
import password.pwm.util.Helper;
import password.pwm.util.PwmLogger;

import java.util.*;

public class ChallengeValue implements StoredValue {
    final static private PwmLogger LOGGER = PwmLogger.getLogger(ChallengeValue.class);
    final Map<String, List<ChallengeItemBean>> values;

    ChallengeValue(final Map<String, List<ChallengeItemBean>> values) {
        this.values = values;
    }

    static ChallengeValue fromJson(final String input) {
        if (input == null) {
            return new ChallengeValue(Collections.<String,List<ChallengeItemBean>>emptyMap());
        } else {
            final Gson gson = Helper.getGson();
            Map<String, List<ChallengeItemBean>> srcMap = gson.fromJson(input, new TypeToken<Map<String, List<ChallengeItemBean>>>() {
            }.getType());
            srcMap = srcMap == null ? Collections.<String,List<ChallengeItemBean>>emptyMap() : new TreeMap<String, List<ChallengeItemBean>>(srcMap);
            return new ChallengeValue(Collections.unmodifiableMap(srcMap));
        }
    }

    static ChallengeValue fromXmlElement(final Element settingElement) {
        final List valueElements = settingElement.getChildren("value");
        final Map<String, List<ChallengeItemBean>> values = new TreeMap<String, List<ChallengeItemBean>>();
        final boolean oldStyle = "LOCALIZED_STRING_ARRAY".equals(settingElement.getAttributeValue("syntax"));
        for (final Object loopValue : valueElements) {
            final Element loopValueElement = (Element) loopValue;
            final String localeString = loopValueElement.getAttributeValue("locale") == null ? "" : loopValueElement.getAttributeValue("locale");
            final String value = loopValueElement.getText();
            if (!values.containsKey(localeString)) {
                values.put(localeString, new ArrayList<ChallengeItemBean>());
            }
            final ChallengeItemBean challengeItemBean;
            if (oldStyle) {
                challengeItemBean = parseOldVersionString(value);
            } else {
                challengeItemBean = Helper.getGson().fromJson(value,ChallengeItemBean.class);
            }
            if (challengeItemBean != null) {
                values.get(localeString).add(challengeItemBean);
            }
        }
        return new ChallengeValue(values);
    }

    public List<Element> toXmlValues(final String valueElementName) {
        final List<Element> returnList = new ArrayList<Element>();
        for (final String locale : values.keySet()) {
            for (final ChallengeItemBean value : values.get(locale)) {
                final Element valueElement = new Element(valueElementName);
                valueElement.addContent(new CDATA(Helper.getGson().toJson(value)));
                if (locale != null && locale.length() > 0) {
                    valueElement.setAttribute("locale", locale);
                }
                returnList.add(valueElement);
            }
        }
        return returnList;
    }

    public Map<String, List<ChallengeItemBean>> toNativeObject() {
        return Collections.unmodifiableMap(values);
    }

    public String toString() {
        return Helper.getGson().toJson(values);
    }

    public List<String> validateValue(PwmSetting pwmSetting) {
        if (pwmSetting.isRequired()) {
            if (values == null || values.size() < 1 || values.keySet().iterator().next().length() < 1) {
                return Collections.singletonList("required value missing");
            }
        }

        if (values != null) {
            for (final String localeKey : values.keySet()) {
                for (final ChallengeItemBean itemBean : values.get(localeKey)) {
                    if (itemBean.isAdminDefined() && (itemBean.getText() == null || itemBean.getText().length() < 1)) {
                        return Collections.singletonList("admin-defined challenge must contain text (locale='" + localeKey + "')");
                    }
                }
            }
        }

        return Collections.emptyList();
    }

    public String toDebugString() {
        return toString();
    }


    private static ChallengeItemBean parseOldVersionString(
            final String inputString
    ) {
        if (inputString == null || inputString.length() < 1) {
            return null;
        }

        int minLength = 2;
        int maxLength = 255;

        String challengeText = "";
        final String[] s1 = inputString.split("::");
        if (s1.length > 0) {
            challengeText = s1[0].trim();
        }
        if (s1.length > 1) {
            try {
                minLength = Integer.parseInt(s1[1]);
            } catch (Exception e) {
                LOGGER.debug("unexpected error parsing config input '" + inputString + "' " + e.getMessage());
            }
        }
        if (s1.length > 2) {
            try {
                maxLength = Integer.parseInt(s1[2]);
            } catch (Exception e) {
                LOGGER.debug("unexpected error parsing config input '" + inputString + "' " + e.getMessage());
            }
        }

        boolean adminDefined = true;
        if ("%user%".equalsIgnoreCase(challengeText)) {
            challengeText = "";
            adminDefined = false;
        }

        return new ChallengeItemBean(challengeText, minLength, maxLength, adminDefined);
    }

}
