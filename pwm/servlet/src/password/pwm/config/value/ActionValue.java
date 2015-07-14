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
import password.pwm.config.ActionConfiguration;
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingSyntax;
import password.pwm.config.StoredValue;
import password.pwm.error.PwmOperationalException;
import password.pwm.util.JsonUtil;
import password.pwm.util.secure.PwmSecurityKey;

import java.util.*;

public class ActionValue extends AbstractValue implements StoredValue {
    final List<ActionConfiguration> values;

    public ActionValue(final List<ActionConfiguration> values) {
        this.values = Collections.unmodifiableList(values);
    }


    public static StoredValueFactory factory()
    {
        return new StoredValueFactory() {
            public ActionValue fromJson(final String input)
            {
                if (input == null) {
                    return new ActionValue(Collections.<ActionConfiguration>emptyList());
                } else {
                    List<ActionConfiguration> srcList = JsonUtil.deserialize(input,
                            new TypeToken<List<ActionConfiguration>>() {
                            }
                    );

                    srcList = srcList == null ? Collections.<ActionConfiguration>emptyList() : srcList;
                    while (srcList.contains(null)) {
                        srcList.remove(null);
                    }
                    return new ActionValue(Collections.unmodifiableList(srcList));
                }
            }

            public ActionValue fromXmlElement(
                    Element settingElement,
                    final PwmSecurityKey input
            )
                    throws PwmOperationalException
            {
                final boolean oldType = PwmSettingSyntax.STRING_ARRAY.toString().equals(
                        settingElement.getAttributeValue("syntax"));
                final List valueElements = settingElement.getChildren("value");
                final List<ActionConfiguration> values = new ArrayList<>();
                for (final Object loopValue : valueElements) {
                    final Element loopValueElement = (Element) loopValue;
                    final String value = loopValueElement.getText();
                    if (value != null && value.length() > 0) {
                        if (oldType) {
                            if (loopValueElement.getAttribute("locale") == null) {
                                values.add(ActionConfiguration.parseOldConfigString(value));
                            }
                        } else {
                            values.add(JsonUtil.deserialize(value, ActionConfiguration.class));
                        }
                    }
                }
                return new ActionValue(values);
            }
        };
    }

    public List<Element> toXmlValues(final String valueElementName) {
        final List<Element> returnList = new ArrayList<>();
        for (final ActionConfiguration value : values) {
            final Element valueElement = new Element(valueElementName);
            valueElement.addContent(JsonUtil.serialize(value));
            returnList.add(valueElement);
        }
        return returnList;
    }

    public List<ActionConfiguration> toNativeObject() {
        return Collections.unmodifiableList(values);
    }

    public List<String> validateValue(PwmSetting pwmSetting) {
        if (pwmSetting.isRequired()) {
            if (values == null || values.size() < 1 || values.get(0) == null) {
                return Collections.singletonList("required value missing");
            }
        }

        final Set<String> seenNames = new HashSet<>();
        for (final ActionConfiguration actionConfiguration : values) {
            if (seenNames.contains(actionConfiguration.getName().toLowerCase())) {
                return Collections.singletonList("each action name must be unique: " + actionConfiguration.getName());
            }
            seenNames.add(actionConfiguration.getName().toLowerCase());
        }


        for (final ActionConfiguration loopConfig : values) {
            try {
                loopConfig.validate();
            } catch (PwmOperationalException e) {
                return Collections.singletonList("format error: " + e.getErrorInformation().toDebugStr());
            }
        }

        return Collections.emptyList();
    }

    public String toDebugString(Locale locale) {
        final StringBuilder sb = new StringBuilder();
        int counter = 0;
        for (final ActionConfiguration actionConfiguration : values) {
            sb.append("Action");
            if (values.size() > 1) {
                sb.append(counter);
            }
            sb.append("-");
            sb.append(actionConfiguration.getType() == null ? ActionConfiguration.Type.ldap.toString() : actionConfiguration.getType().toString());
            sb.append(": [");
            switch (actionConfiguration.getType()) {
                case webservice: {
                    sb.append("WebService: ");
                    sb.append("method=" + actionConfiguration.getMethod());
                    sb.append(" url=" + actionConfiguration.getUrl());
                    sb.append(" headers=" + JsonUtil.serializeMap(actionConfiguration.getHeaders()));
                    sb.append(" body=" + actionConfiguration.getBody());
                }
                break;

                case ldap: {
                    sb.append("LDAP: ");
                    sb.append("method=" + actionConfiguration.getLdapMethod());
                    sb.append(" attribute=" + actionConfiguration.getAttributeName());
                    sb.append(" value=" + actionConfiguration.getAttributeValue());

                }
            }
            sb.append("]");
            counter++;
            if (counter != values.size()) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }

}
