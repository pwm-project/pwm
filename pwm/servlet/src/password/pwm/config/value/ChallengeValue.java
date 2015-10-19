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
import password.pwm.config.ChallengeItemConfiguration;
import password.pwm.config.PwmSetting;
import password.pwm.config.StoredValue;
import password.pwm.util.JsonUtil;
import password.pwm.util.LocaleHelper;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmSecurityKey;

import java.util.*;

public class ChallengeValue extends AbstractValue implements StoredValue {
    final static private PwmLogger LOGGER = PwmLogger.forClass(ChallengeValue.class);
    final Map<String, List<ChallengeItemConfiguration>> values; //locale str as key.

    ChallengeValue(final Map<String, List<ChallengeItemConfiguration>> values) {
        this.values = values;
    }

    public static StoredValueFactory factory()
    {
        return new StoredValueFactory() {

            public ChallengeValue fromJson(final String input)
            {
                if (input == null) {
                    return new ChallengeValue(Collections.<String, List<ChallengeItemConfiguration>>emptyMap());
                } else {
                    Map<String, List<ChallengeItemConfiguration>> srcMap = JsonUtil.deserialize(input,
                            new TypeToken<Map<String, List<ChallengeItemConfiguration>>>() {}
                    );
                    srcMap = srcMap == null ? Collections.<String, List<ChallengeItemConfiguration>>emptyMap() : new TreeMap<>(
                            srcMap);
                    return new ChallengeValue(Collections.unmodifiableMap(srcMap));
                }
            }

            public ChallengeValue fromXmlElement(
                    final Element settingElement,
                    final PwmSecurityKey input
            )
            {
                final List valueElements = settingElement.getChildren("value");
                final Map<String, List<ChallengeItemConfiguration>> values = new TreeMap<>();
                final boolean oldStyle = "LOCALIZED_STRING_ARRAY".equals(settingElement.getAttributeValue("syntax"));
                for (final Object loopValue : valueElements) {
                    final Element loopValueElement = (Element) loopValue;
                    final String localeString = loopValueElement.getAttributeValue(
                            "locale") == null ? "" : loopValueElement.getAttributeValue("locale");
                    final String value = loopValueElement.getText();
                    if (!values.containsKey(localeString)) {
                        values.put(localeString, new ArrayList<ChallengeItemConfiguration>());
                    }
                    final ChallengeItemConfiguration challengeItemBean;
                    if (oldStyle) {
                        challengeItemBean = parseOldVersionString(value);
                    } else {
                        challengeItemBean = JsonUtil.deserialize(value, ChallengeItemConfiguration.class);
                    }
                    if (challengeItemBean != null) {
                        values.get(localeString).add(challengeItemBean);
                    }
                }
                return new ChallengeValue(values);
            }
        };
    }

    public List<Element> toXmlValues(final String valueElementName) {
        final List<Element> returnList = new ArrayList<>();
        for (final String locale : values.keySet()) {
            for (final ChallengeItemConfiguration value : values.get(locale)) {
                if (value != null) {
                    final Element valueElement = new Element(valueElementName);
                    valueElement.addContent(new CDATA(JsonUtil.serialize(value)));
                    if (locale != null && locale.length() > 0) {
                        valueElement.setAttribute("locale", locale);
                    }
                    returnList.add(valueElement);
                }
            }
        }
        return returnList;
    }

    public Map<String, List<ChallengeItemConfiguration>> toNativeObject() {
        return Collections.unmodifiableMap(values);
    }

    public List<String> validateValue(PwmSetting pwmSetting) {
        if (pwmSetting.isRequired()) {
            if (values == null || values.size() < 1 || values.keySet().iterator().next().length() < 1) {
                return Collections.singletonList("required value missing");
            }
        }

        if (values != null) {
            for (final String localeKey : values.keySet()) {
                for (final ChallengeItemConfiguration itemBean : values.get(localeKey)) {
                    if (itemBean != null) {
                        if (itemBean.isAdminDefined() && (itemBean.getText() == null || itemBean.getText().length() < 1)) {
                            return Collections.singletonList("admin-defined challenge must contain text (locale='" + localeKey + "')");
                        }
                        if (itemBean.getMinLength() < 1) {
                            return Collections.singletonList("challenge minimum length must be greater than 0 (text=" + itemBean.getText() + ", locale='" + localeKey + "')");
                        }
                        if (itemBean.getMaxLength() > 255) {
                            return Collections.singletonList("challenge maximum length must be less than 256 (text=" + itemBean.getText() + ", locale='" + localeKey + "')");
                        }
                        if (itemBean.getMinLength() > itemBean.getMaxLength()) {
                            return Collections.singletonList("challenge minimum length must be less than maximum length (text=" + itemBean.getText() + ", locale='" + localeKey + "')");
                        }
                    }
                }
            }
        }

        return Collections.emptyList();
    }

    private static ChallengeItemConfiguration parseOldVersionString(
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

        return new ChallengeItemConfiguration(challengeText, minLength, maxLength, adminDefined);
    }

    public String toDebugString(Locale locale) {
        if (values == null) {
            return "No Actions";
        }
        final StringBuilder sb = new StringBuilder();
        for (final String localeKey : values.keySet()) {
            final List<ChallengeItemConfiguration> challengeItems = values.get(localeKey);
            sb.append("Locale: ").append(LocaleHelper.debugLabel(LocaleHelper.parseLocaleString(localeKey))).append("\n");
            for (ChallengeItemConfiguration challengeItemBean : challengeItems) {
                sb.append(" ChallengeItem: [AdminDefined: ").append(challengeItemBean.isAdminDefined());
                sb.append(" MinLength:").append(challengeItemBean.getMinLength());
                sb.append(" MaxLength:").append(challengeItemBean.getMaxLength());
                sb.append(" MaxQuestionCharsInAnswer:").append(challengeItemBean.getMaxQuestionCharsInAnswer());
                sb.append(" EnforceWordlist:").append(challengeItemBean.isEnforceWordlist());
                sb.append("]\n");
                sb.append("  Text:").append(challengeItemBean.getText()).append("\n");
            }
        }
        return sb.toString();
    }
}
