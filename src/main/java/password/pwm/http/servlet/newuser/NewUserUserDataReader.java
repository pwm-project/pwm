/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2016 The PWM Project
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

package password.pwm.http.servlet.newuser;

import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.ldap.UserDataReader;

import java.util.*;

class NewUserUserDataReader implements UserDataReader {
    private final Map<String, String> attributeData;

    NewUserUserDataReader(final Map<String, String> attributeData)
    {
        this.attributeData = attributeData;
    }

    @Override
    public String getUserDN()
    {
        return null;
    }

    @Override
    public String readStringAttribute(
            String attribute,
            Flag...flags
    )
            throws ChaiUnavailableException, ChaiOperationException
    {
        return attributeData.get(attribute);
    }

    @Override
    public Date readDateAttribute(String attribute)
            throws ChaiUnavailableException, ChaiOperationException
    {
        return null;
    }

    @Override
    public Map<String, String> readStringAttributes(
            Collection<String> attributes,
            Flag... flags
    )
            throws ChaiUnavailableException, ChaiOperationException
    {
        final Map<String, String> returnObj = new LinkedHashMap<>();
        for (final String key : attributes) {
            returnObj.put(key, readStringAttribute(key));
        }
        return Collections.unmodifiableMap(returnObj);
    }

    @Override
    public List<String> readMultiStringAttribute(String attribute, Flag... flags) throws ChaiUnavailableException, ChaiOperationException {
        final String value = readStringAttribute(attribute, flags);
        return value == null ? null : Collections.singletonList(value);
    }
}
