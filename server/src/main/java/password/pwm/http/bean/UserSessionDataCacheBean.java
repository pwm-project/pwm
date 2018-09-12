/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
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

package password.pwm.http.bean;

import password.pwm.Permission;
import password.pwm.util.PostChangePasswordAction;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UserSessionDataCacheBean implements Serializable
{
    private Map<Permission, Permission.PermissionStatus> permissions = new HashMap<>();
    private Map<String, PostChangePasswordAction> postChangePasswordActions = new HashMap<>();

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

    public void addPostChangePasswordActions(
            final String key,
            final PostChangePasswordAction postChangePasswordAction
    )
    {
        if ( postChangePasswordAction == null )
        {
            postChangePasswordActions.remove( key );
        }
        else
        {
            postChangePasswordActions.put( key, postChangePasswordAction );
        }
    }

    public List<PostChangePasswordAction> removePostChangePasswordActions( )
    {
        final List<PostChangePasswordAction> copiedList = new ArrayList<>();
        copiedList.addAll( postChangePasswordActions.values() );
        postChangePasswordActions.clear();
        return copiedList;
    }

}
