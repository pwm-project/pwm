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
import password.pwm.bean.EmailItemBean;
import password.pwm.config.PwmSetting;
import password.pwm.config.StoredValue;
import password.pwm.error.PwmOperationalException;
import password.pwm.util.JsonUtil;
import password.pwm.util.LocaleHelper;
import password.pwm.util.secure.PwmSecurityKey;

import java.util.*;

public class EmailValue extends AbstractValue implements StoredValue {
    final Map<String,EmailItemBean> values; //key is locale identifier

    EmailValue(final Map<String,EmailItemBean> values) {
        this.values = values;
    }

    public static StoredValueFactory factory()
    {
        return new StoredValueFactory() {
            public EmailValue fromJson(final String input)
            {
                if (input == null) {
                    return new EmailValue(Collections.<String, EmailItemBean>emptyMap());
                } else {
                    Map<String, EmailItemBean> srcList = JsonUtil.deserialize(input,
                            new TypeToken<Map<String, EmailItemBean>>() {}
                    );

                    srcList = srcList == null ? Collections.<String, EmailItemBean>emptyMap() : srcList;
                    srcList.remove(null);
                    return new EmailValue(Collections.unmodifiableMap(srcList));
                }
            }

            public EmailValue fromXmlElement(
                    Element settingElement,
                    final PwmSecurityKey input
            )
                    throws PwmOperationalException
            {
                final Map<String, EmailItemBean> values = new TreeMap<>();
                {
                    final List valueElements = settingElement.getChildren("value");
                    for (final Object loopValue : valueElements) {
                        final Element loopValueElement = (Element) loopValue;
                        final String value = loopValueElement.getText();
                        if (value != null && value.length() > 0) {
                            String localeValue = loopValueElement.getAttribute(
                                    "locale") == null ? "" : loopValueElement.getAttribute("locale").getValue();
                            values.put(localeValue, JsonUtil.deserialize(value, EmailItemBean.class));
                        }
                    }
                }
                // read old format values.  can be removed someday....      this code iterates through the entire settings xml document to find old format versions
                {
                    final Map<String, String> fromMap = new HashMap<>();
                    final Map<String, String> subjectMap = new HashMap<>();
                    final Map<String, String> bodyPlainMap = new HashMap<>();
                    final Map<String, String> bodyHtmlMap = new HashMap<>();
                    for (final Object loopSettingObj : settingElement.getParentElement().getChildren()) {
                        Element loopSetting = (Element) loopSettingObj;
                        if (loopSetting.getAttribute("key") != null) {
                            if (loopSetting.getAttribute("key").getValue().equals(
                                    settingElement.getAttribute("key").getValue() + ".from")) {
                                final List valueElements = loopSetting.getChildren("value");
                                for (final Object loopValue : valueElements) {
                                    final Element loopValueElement = (Element) loopValue;
                                    final String value = loopValueElement.getText();
                                    if (value != null && value.length() > 0) {
                                        String localeValue = settingElement.getAttribute(
                                                "locale") == null ? "" : settingElement.getAttribute(
                                                "locale").getValue();
                                        fromMap.put(localeValue, value);
                                    }
                                }
                            }
                            if (loopSetting.getAttribute("key").getValue().equals(
                                    settingElement.getAttribute("key").getValue() + ".subject")) {
                                final List valueElements = loopSetting.getChildren("value");
                                for (final Object loopValue : valueElements) {
                                    final Element loopValueElement = (Element) loopValue;
                                    final String value = loopValueElement.getText();
                                    if (value != null && value.length() > 0) {
                                        String localeValue = settingElement.getAttribute(
                                                "locale") == null ? "" : settingElement.getAttribute(
                                                "locale").getValue();
                                        subjectMap.put(localeValue, value);
                                    }
                                }
                            }
                            if (loopSetting.getAttribute("key").getValue().equals(
                                    settingElement.getAttribute("key").getValue() + ".plainBody")) {
                                final List valueElements = loopSetting.getChildren("value");
                                for (final Object loopValue : valueElements) {
                                    final Element loopValueElement = (Element) loopValue;
                                    final String value = loopValueElement.getText();
                                    if (value != null && value.length() > 0) {
                                        String localeValue = settingElement.getAttribute(
                                                "locale") == null ? "" : settingElement.getAttribute(
                                                "locale").getValue();
                                        bodyPlainMap.put(localeValue, value);
                                    }
                                }
                            }
                            if (loopSetting.getAttribute("key").getValue().equals(
                                    settingElement.getAttribute("key").getValue() + ".htmlBody")) {
                                final List valueElements = loopSetting.getChildren("value");
                                for (final Object loopValue : valueElements) {
                                    final Element loopValueElement = (Element) loopValue;
                                    final String value = loopValueElement.getText();
                                    if (value != null && value.length() > 0) {
                                        String localeValue = settingElement.getAttribute(
                                                "locale") == null ? "" : settingElement.getAttribute(
                                                "locale").getValue();
                                        bodyHtmlMap.put(localeValue, value);
                                    }
                                }
                            }
                        }
                    }
                    final Set<String> seenLocales = new HashSet<>();
                    seenLocales.addAll(fromMap.keySet());
                    seenLocales.addAll(subjectMap.keySet());
                    seenLocales.addAll(bodyPlainMap.keySet());
                    seenLocales.addAll(bodyHtmlMap.keySet());
                    //final String defaultJson = PwmSetting.forKey(settingElement.getAttribute("key").getValue()).getDefaultValue(PwmSetting.Template.NOVL);
                    //final Map<String,EmailItemBean> defaultList = gson.fromJson(defaultJson, new TypeToken<Map<String,EmailItemBean>>() {}.getType());
                    //final EmailItemBean defaultBean = defaultList.read("");
            /*
            for (final String localeStr : seenLocales) {
                values.put(localeStr,new EmailItemBean(
                        null,
                        fromMap.containsKey(localeStr) ? fromMap.read(localeStr) : defaultBean.getFrom(),
                        subjectMap.containsKey(localeStr) ? subjectMap.read(localeStr) : defaultBean.getSubject(),
                        bodyPlainMap.containsKey(localeStr)? bodyPlainMap.read(localeStr) : defaultBean.getBodyPlain(),
                        bodyHtmlMap.containsKey(localeStr) ? bodyHtmlMap.read(localeStr) : defaultBean.getBodyHtml()
                        ));
            }
            */
                }
                return new EmailValue(values);
            }
        };
    }

    public List<Element> toXmlValues(final String valueElementName) {
        final List<Element> returnList = new ArrayList<>();
        for (final String localeValue : values.keySet()) {
            final EmailItemBean emailItemBean = values.get(localeValue);
            final Element valueElement = new Element(valueElementName);
            if (localeValue.length() > 0) {
                valueElement.setAttribute("locale",localeValue);
            }
            valueElement.addContent(JsonUtil.serialize(emailItemBean));
            returnList.add(valueElement);
        }
        return returnList;
    }

    public Map<String,EmailItemBean> toNativeObject() {
        return Collections.unmodifiableMap(values);
    }

    public List<String> validateValue(PwmSetting pwmSetting) {
        if (pwmSetting.isRequired()) {
            if (values == null || values.isEmpty() || values.values().iterator().next() == null) {
                return Collections.singletonList("required value missing");
            }
        }

        for (final String loopLocale : values.keySet()) {
            final EmailItemBean emailItemBean = values.get(loopLocale);

            if (emailItemBean.getSubject() == null || emailItemBean.getSubject().length() < 1) {
                return Collections.singletonList("subject field is required " + (loopLocale.length() > 0 ? " for locale " + loopLocale:""));
            }

            if (emailItemBean.getFrom() == null || emailItemBean.getFrom().length() < 1) {
                return Collections.singletonList("from field is required" + (loopLocale.length() > 0 ? " for locale " + loopLocale:""));
            }

            if (emailItemBean.getBodyPlain() == null || emailItemBean.getBodyPlain().length() < 1) {
                return Collections.singletonList("plain body field is required" + (loopLocale.length() > 0 ? " for locale " + loopLocale:""));
            }
        }

        return Collections.emptyList();
    }

    public String toDebugString(Locale locale) {
        if (values == null) {
            return "No Email Item";
        }
        final StringBuilder sb = new StringBuilder();
        for (final String localeKey : values.keySet()) {
            final EmailItemBean emailItemBean = values.get(localeKey);
            sb.append("EmailItem ").append(LocaleHelper.debugLabel(LocaleHelper.parseLocaleString(localeKey))).append(": \n");
            sb.append("  To:").append(emailItemBean.getTo()).append("\n");
            sb.append("From:").append(emailItemBean.getFrom()).append("\n");
            sb.append("Subj:").append(emailItemBean.getSubject()).append("\n");
            sb.append("Body:").append(emailItemBean.getBodyPlain()).append("\n");
            sb.append("Html:").append(emailItemBean.getBodyHtml()).append("\n");
        }
        return sb.toString();
    }
}
