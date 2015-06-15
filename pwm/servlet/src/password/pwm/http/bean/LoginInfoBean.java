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

package password.pwm.http.bean;

import password.pwm.Permission;
import password.pwm.ldap.auth.AuthenticationType;
import password.pwm.util.BasicAuthInfo;
import password.pwm.util.PasswordData;
import password.pwm.util.PostChangePasswordAction;

import java.util.*;

public class
        LoginInfoBean implements PwmSessionBean {
    private transient PasswordData userCurrentPassword;

    private Map<Permission, Permission.PERMISSION_STATUS> permissions = new HashMap<>();
    private AuthenticationType authenticationType = AuthenticationType.UNAUTHENTICATED;
    private Date localAuthTime;
    private Map<String, PostChangePasswordAction> postChangePasswordActions = new HashMap<>();

    private transient BasicAuthInfo originalBasicAuthInfo;

    private Date oauthExpiration;
    private transient String oauthRefreshToken;
    
    private boolean authRecordCookieSet;
    
    public Date getLocalAuthTime()
    {
        return localAuthTime;
    }

    public void setLocalAuthTime(final Date localAuthTime)
    {
        this.localAuthTime = localAuthTime;
    }

    public void addPostChangePasswordActions(
            final String key,
            final PostChangePasswordAction postChangePasswordAction
    )
    {
        if (postChangePasswordAction == null) {
            postChangePasswordActions.remove(key);
        } else {
            postChangePasswordActions.put(key, postChangePasswordAction);
        }
    }

    public List<PostChangePasswordAction> removePostChangePasswordActions()
    {
        final List<PostChangePasswordAction> copiedList = new ArrayList<>();
        copiedList.addAll(postChangePasswordActions.values());
        postChangePasswordActions.clear();
        return copiedList;
    }

    public AuthenticationType getAuthenticationType()
    {
        return authenticationType;
    }

    public void setAuthenticationType(AuthenticationType authenticationType)
    {
        this.authenticationType = authenticationType;
    }

    public void clearPermissions()
    {
        permissions.clear();
    }

    public Permission.PERMISSION_STATUS getPermission(final Permission permission)
    {
        final Permission.PERMISSION_STATUS status = permissions.get(permission);
        return status == null ? Permission.PERMISSION_STATUS.UNCHECKED : status;
    }

    public void setPermission(
            final Permission permission,
            final Permission.PERMISSION_STATUS status
    )
    {
        permissions.put(permission, status);
    }

    public Map<Permission, Permission.PERMISSION_STATUS> getPermissions()
    {
        return permissions;
    }

    public void setPermissions(final Map<Permission, Permission.PERMISSION_STATUS> permissions)
    {
        this.permissions = permissions;
    }

    public PasswordData getUserCurrentPassword()
    {
        return userCurrentPassword;
    }

    public void setUserCurrentPassword(PasswordData userCurrentPassword)
    {
        this.userCurrentPassword = userCurrentPassword;
    }

    public BasicAuthInfo getOriginalBasicAuthInfo()
    {
        return originalBasicAuthInfo;
    }

    public void setOriginalBasicAuthInfo(final BasicAuthInfo originalBasicAuthInfo)
    {
        this.originalBasicAuthInfo = originalBasicAuthInfo;
    }

    public Date getOauthExpiration()
    {
        return oauthExpiration;
    }

    public void setOauthExpiration(Date oauthExpiration)
    {
        this.oauthExpiration = oauthExpiration;
    }

    public String getOauthRefreshToken()
    {
        return oauthRefreshToken;
    }

    public void setOauthRefreshToken(String oauthRefreshToken)
    {
        this.oauthRefreshToken = oauthRefreshToken;
    }

    public boolean isAuthRecordCookieSet() {
        return authRecordCookieSet;
    }

    public void setAuthRecordCookieSet(boolean authRecordCookieSet) {
        this.authRecordCookieSet = authRecordCookieSet;
    }
}
