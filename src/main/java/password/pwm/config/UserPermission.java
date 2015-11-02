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

package password.pwm.config;

import password.pwm.PwmConstants;

import java.io.Serializable;

public class UserPermission implements Serializable {
    public enum Type {
        ldapQuery,
        ldapGroup,
    }

    private String ldapProfileID = PwmConstants.PROFILE_ID_ALL;
    private String ldapQuery;
    private String ldapBase;
    private Type type;

    public UserPermission(
            final Type type,
            final String ldapProfileID,
            final String ldapQuery,
            final String ldapBase
    )
    {
        this.type = type;
        this.ldapProfileID = ldapProfileID;
        this.ldapQuery = ldapQuery;
        this.ldapBase = ldapBase;
    }

    public String getLdapProfileID()
    {
        return ldapProfileID == null ? null : ldapProfileID.trim();
    }

    public String getLdapQuery()
    {
        return ldapQuery;
    }

    public String getLdapBase()
    {
        return ldapBase;
    }

    public Type getType()
    {
        return type == null ? Type.ldapQuery : type;
    }
}
