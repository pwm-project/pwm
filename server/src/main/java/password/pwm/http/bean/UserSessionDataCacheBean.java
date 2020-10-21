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

package password.pwm.http.bean;

import password.pwm.Permission;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class UserSessionDataCacheBean implements Serializable
{
    private Map<Permission, Permission.PermissionStatus> permissions = new HashMap<>();

    public void clearPermissions( )
    {
        permissions.clear();
    }

    public Permission.PermissionStatus getPermission( final Permission permission )
    {
        final Permission.PermissionStatus status = permissions.get( permission );
        return status == null ? Permission.PermissionStatus.UNCHECKED : status;
    }

    public void setPermission(
            final Permission permission,
            final Permission.PermissionStatus status
    )
    {
        permissions.put( permission, status );
    }

    public Map<Permission, Permission.PermissionStatus> getPermissions( )
    {
        return permissions;
    }

    public void setPermissions( final Map<Permission, Permission.PermissionStatus> permissions )
    {
        this.permissions = permissions;
    }
}
