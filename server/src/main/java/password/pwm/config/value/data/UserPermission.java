/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2021 The PWM Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package password.pwm.config.value.data;

import org.jetbrains.annotations.NotNull;
import password.pwm.bean.ProfileID;
import password.pwm.ldap.permission.UserPermissionType;
import password.pwm.util.java.StringUtil;

import java.util.Comparator;

/**
 * Represents a user permission configuration value.
 */
public record UserPermission(
        UserPermissionType type,
        ProfileID ldapProfileID,
        String ldapQuery,
        String ldapBase

)
        implements Comparable<UserPermission>
{
    public UserPermission(
            final UserPermissionType type,
            final ProfileID ldapProfileID,
            final String ldapQuery,
            final String ldapBase
    )
    {
        this.type = type == null ? UserPermissionType.ldapQuery : type;
        this.ldapProfileID = ldapProfileID;
        this.ldapQuery = ldapQuery;
        this.ldapBase = ldapBase;
    }

    private static final Comparator<UserPermission> COMPARATOR = Comparator.comparing(
                    UserPermission::type,
                    Comparator.nullsLast( Comparator.naturalOrder() ) )
            .thenComparing(
                    UserPermission::ldapProfileID,
                    ProfileID.comparator() )
            .thenComparing(
                    UserPermission::ldapBase,
                    Comparator.nullsLast( Comparator.naturalOrder() ) )
            .thenComparing(
                    UserPermission::ldapQuery,
                    Comparator.nullsLast( Comparator.naturalOrder() ) );



    public String debugString()
    {
        return type().getLabel()
                +  ": [Profile: "
                + ( ldapProfileID() == null ?  "All" : '\'' + this.ldapProfileID().stringValue() + '\'' )
                + ( StringUtil.isEmpty( ldapBase() ) ?  "" : " " + type().getBaseLabel() + ": " + this.ldapBase() )
                + ( StringUtil.isEmpty( ldapQuery() ) ?  "" : " Filter: " + this.ldapQuery() )
                + "]";
    }

    public String toString()
    {
        return debugString();
    }

    @Override
    public int compareTo( @NotNull final UserPermission o )
    {
        return COMPARATOR.compare( this, o );
    }
}
