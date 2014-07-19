/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2014 The PWM Project
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
import org.apache.commons.lang.StringUtils;
import org.jdom2.Element;
import password.pwm.config.PwmSetting;
import password.pwm.config.StoredConfiguration;
import password.pwm.config.StoredValue;
import password.pwm.config.UserPermission;
import password.pwm.error.PwmOperationalException;
import password.pwm.util.Helper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class UserPermissionValue extends AbstractValue implements StoredValue {
    final List<UserPermission> values;

    private boolean needsXmlUpdate;

    public UserPermissionValue(final List<UserPermission> values) {
        this.values = values;
    }

    static UserPermissionValue fromJson(final String input) {
        if (input == null) {
            return new UserPermissionValue(Collections.<UserPermission>emptyList());
        } else {
            final Gson gson = Helper.getGson();
            List<UserPermission> srcList = gson.fromJson(input, new TypeToken<List<UserPermission>>() {
            }.getType());
            srcList = srcList == null ? Collections.<UserPermission>emptyList() : srcList;
            srcList.removeAll(Collections.singletonList(null));
            return new UserPermissionValue(Collections.unmodifiableList(srcList));
        }
    }

    static UserPermissionValue fromXmlElement(Element settingElement) throws PwmOperationalException
    {
        final boolean newType = "2".equals(settingElement.getAttributeValue(StoredConfiguration.XML_ATTRIBUTE_SYNTAX_VERSION));
        final Gson gson = Helper.getGson();
        final List valueElements = settingElement.getChildren("value");
        final List<UserPermission> values = new ArrayList<>();
        for (final Object loopValue : valueElements) {
            final Element loopValueElement = (Element) loopValue;
            final String value = loopValueElement.getText();
            if (value != null && !value.isEmpty()) {
                if (newType) {
                    final UserPermission userPermission = gson.fromJson(value,UserPermission.class);
                    values.add(userPermission);
                } else {
                    values.add(new UserPermission(null, value, null));
                }
            }
        }
        final UserPermissionValue userPermissionValue = new UserPermissionValue(values);
        userPermissionValue.needsXmlUpdate = !newType;
        return userPermissionValue;
    }

    public List<Element> toXmlValues(final String valueElementName) {
        final List<Element> returnList = new ArrayList<>();
        final Gson gson = Helper.getGson();
        for (final UserPermission value : values) {
            final Element valueElement = new Element(valueElementName);
            valueElement.addContent(gson.toJson(value));
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

        int leftParens = StringUtils.countMatches(filter,"(");
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
}
