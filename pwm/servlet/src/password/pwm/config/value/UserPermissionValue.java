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
import org.apache.commons.lang3.StringUtils;
import org.jdom2.Element;
import password.pwm.config.PwmSetting;
import password.pwm.config.stored.StoredConfigurationImpl;
import password.pwm.config.StoredValue;
import password.pwm.config.UserPermission;
import password.pwm.error.PwmOperationalException;
import password.pwm.i18n.Display;
import password.pwm.util.JsonUtil;
import password.pwm.util.secure.PwmSecurityKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class UserPermissionValue extends AbstractValue implements StoredValue {
    final List<UserPermission> values;

    private boolean needsXmlUpdate;

    public UserPermissionValue(final List<UserPermission> values) {
        this.values = values;
    }

    public static StoredValueFactory factory()
    {
        return new StoredValueFactory() {
            public UserPermissionValue fromJson(final String input)
            {
                if (input == null) {
                    return new UserPermissionValue(Collections.<UserPermission>emptyList());
                } else {
                    List<UserPermission> srcList = JsonUtil.deserialize(input, new TypeToken<List<UserPermission>>() {
                    });
                    srcList = srcList == null ? Collections.<UserPermission>emptyList() : srcList;
                    while (srcList.contains(null)) {
                        srcList.remove(null);
                    }
                    return new UserPermissionValue(Collections.unmodifiableList(srcList));
                }
            }

            public UserPermissionValue fromXmlElement(Element settingElement, final PwmSecurityKey key)
                    throws PwmOperationalException
            {
                final boolean newType = "2".equals(
                        settingElement.getAttributeValue(StoredConfigurationImpl.XML_ATTRIBUTE_SYNTAX_VERSION));
                final List valueElements = settingElement.getChildren("value");
                final List<UserPermission> values = new ArrayList<>();
                for (final Object loopValue : valueElements) {
                    final Element loopValueElement = (Element) loopValue;
                    final String value = loopValueElement.getText();
                    if (value != null && !value.isEmpty()) {
                        if (newType) {
                            final UserPermission userPermission = JsonUtil.deserialize(value, UserPermission.class);
                            values.add(userPermission);
                        } else {
                            values.add(new UserPermission(UserPermission.Type.ldapQuery, null, value, null));
                        }
                    }
                }
                final UserPermissionValue userPermissionValue = new UserPermissionValue(values);
                userPermissionValue.needsXmlUpdate = !newType;
                return userPermissionValue;
            }
        };
    }

    public List<Element> toXmlValues(final String valueElementName) {
        final List<Element> returnList = new ArrayList<>();
        for (final UserPermission value : values) {
            final Element valueElement = new Element(valueElementName);
            valueElement.addContent(JsonUtil.serialize(value));
            returnList.add(valueElement);
        }
        return returnList;
    }

    public List<UserPermission> toNativeObject() {
        return Collections.unmodifiableList(values);
    }

    public List<String> validateValue(PwmSetting pwmSetting) {
        final List<String> returnObj = new ArrayList<>();
        for (final UserPermission userPermission : values) {
            try {
                validateLdapSearchFilter(userPermission.getLdapQuery());
            } catch (IllegalArgumentException e) {
                returnObj.add(e.getMessage() + " for filter " + userPermission.getLdapQuery());
            }
        }
        return returnObj;
    }

    public boolean isNeedsXmlUpdate()
    {
        return needsXmlUpdate;
    }

    private void validateLdapSearchFilter(final String filter) {
        if (filter == null || filter.isEmpty()) {
            return;
        }

        int leftParens = StringUtils.countMatches(filter, "(");
        int rightParens = StringUtils.countMatches(filter,")");

        if (leftParens != rightParens) {
            throw new IllegalArgumentException("unbalanced parentheses");
        }
    }

    @Override
    public int currentSyntaxVersion()
    {
        return 2;
    }

    public String toDebugString(Locale locale) {
        if (values != null && !values.isEmpty()) {
            final StringBuilder sb = new StringBuilder();
            int counter = 0;
            for (final UserPermission userPermission : values) {
                sb.append("UserPermission");
                if (values.size() > 1) {
                    sb.append(counter);
                }
                sb.append("-");
                sb.append(userPermission.getType() == null ? UserPermission.Type.ldapQuery.toString() : userPermission.getType().toString());
                sb.append(": [");
                sb.append("Profile:").append(
                        userPermission.getLdapProfileID() == null
                                ? "All"
                                : userPermission.getLdapProfileID()
                );
                sb.append(" Base:").append(
                        userPermission.getLdapBase() == null
                                ? Display.getLocalizedMessage(locale,Display.Value_NotApplicable,null)
                                : userPermission.getLdapBase()
                );
                if (userPermission.getLdapQuery() != null) {
                    sb.append(" Query:").append(userPermission.getLdapQuery());
                }
                sb.append("]");
                counter++;
                if (counter != values.size()) {
                    sb.append("\n");
                }
            }
            return sb.toString();
        } else {
            return null;
        }
    }
}
