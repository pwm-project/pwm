/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2020 The PWM Project
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

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import password.pwm.ldap.permission.UserPermissionType;
import password.pwm.util.java.StringUtil;

import java.io.Serializable;
import java.util.Comparator;

@Value
@Builder
/**
 * Represents a user permission configuration value.
 */
public class UserPermission implements Serializable, Comparable<UserPermission>
{
    @Builder.Default
    private UserPermissionType type = UserPermissionType.ldapQuery;

    private String ldapProfileID;
    private String ldapQuery;
    private String ldapBase;

    private static final Comparator<UserPermission> COMPARATOR = Comparator.comparing(
            UserPermission::getType,
            Comparator.nullsLast( Comparator.naturalOrder() ) )
            .thenComparing(
                    UserPermission::getLdapProfileID,
                    Comparator.nullsLast( Comparator.naturalOrder() ) )
            .thenComparing(
                    UserPermission::getLdapBase,
                    Comparator.nullsLast( Comparator.naturalOrder() ) )
            .thenComparing(
                    UserPermission::getLdapQuery,
                    Comparator.nullsLast( Comparator.naturalOrder() ) );

    public UserPermissionType getType( )
    {
        return type == null ? UserPermissionType.ldapQuery : type;
    }

    public String debugString()
    {
        return getType().getLabel()
                +  ": [Profile: "
                + ( StringUtil.isEmpty( getLdapProfileID() ) ?  "All" : '\'' + this.getLdapProfileID() + '\'' )
                + ( StringUtil.isEmpty( getLdapBase() ) ?  "" : " " + getType().getBaseLabel() + ": " + this.getLdapBase() )
                + ( StringUtil.isEmpty( getLdapQuery() ) ?  "" : " Filter: " + this.getLdapQuery() )
                + "]";
    }

    @Override
    public int compareTo( @NotNull final UserPermission o )
    {
        return COMPARATOR.compare( this, o );
    }
}
