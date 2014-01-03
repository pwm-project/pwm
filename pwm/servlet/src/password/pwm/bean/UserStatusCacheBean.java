/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2012 The PWM Project
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

package password.pwm.bean;

import password.pwm.config.option.DataStorageMethod;

import java.io.Serializable;
import java.util.Date;

public class UserStatusCacheBean implements Serializable {
    public String userDN;
    public String ldapProfile;
    public String userGUID;

    public String username;
    public String email;

    public Date cacheTimestamp = new Date();

    public PasswordStatus passwordStatus;
    public Date passwordExpirationTime;
    public Date passwordChangeTime;
    public Date lastLoginTime;

    public boolean hasResponses;
    public Date responseSetTime;
    public DataStorageMethod responseStorageMethod;

    public boolean requiresPasswordUpdate;
    public boolean requiresResponseUpdate;
    public boolean requiresProfileUpdate;

    public String getUserDN()
    {
        return userDN;
    }

    public void setUserDN(String userDN)
    {
        this.userDN = userDN;
    }

    public String getLdapProfile()
    {
        return ldapProfile;
    }

    public void setLdapProfile(String ldapProfile)
    {
        this.ldapProfile = ldapProfile;
    }

    public String getUsername()
    {
        return username;
    }

    public void setUsername(String username)
    {
        this.username = username;
    }

    public String getEmail()
    {
        return email;
    }

    public void setEmail(String email)
    {
        this.email = email;
    }

    public String getUserGUID() {
        return userGUID;
    }

    public void setUserGUID(String userGUID) {
        this.userGUID = userGUID;
    }

    public Date getCacheTimestamp() {
        return cacheTimestamp;
    }

    public void setCacheTimestamp(Date cacheTimestamp) {
        this.cacheTimestamp = cacheTimestamp;
    }

    public PasswordStatus getPasswordStatus() {
        return passwordStatus;
    }

    public void setPasswordStatus(PasswordStatus passwordStatus) {
        this.passwordStatus = passwordStatus;
    }

    public Date getPasswordExpirationTime() {
        return passwordExpirationTime;
    }

    public void setPasswordExpirationTime(Date passwordExpirationTime) {
        this.passwordExpirationTime = passwordExpirationTime;
    }

    public Date getPasswordChangeTime() {
        return passwordChangeTime;
    }

    public void setPasswordChangeTime(Date passwordChangeTime) {
        this.passwordChangeTime = passwordChangeTime;
    }

    public Date getResponseSetTime() {
        return responseSetTime;
    }

    public void setResponseSetTime(Date responseSetTime) {
        this.responseSetTime = responseSetTime;
    }

    public Date getLastLoginTime() {
        return lastLoginTime;
    }

    public void setLastLoginTime(Date lastLoginTime) {
        this.lastLoginTime = lastLoginTime;
    }

    public boolean isHasResponses() {
        return hasResponses;
    }

    public void setHasResponses(boolean hasResponses) {
        this.hasResponses = hasResponses;
    }

    public boolean isRequiresPasswordUpdate() {
        return requiresPasswordUpdate;
    }

    public void setRequiresPasswordUpdate(boolean requiresPasswordUpdate) {
        this.requiresPasswordUpdate = requiresPasswordUpdate;
    }

    public boolean isRequiresResponseUpdate() {
        return requiresResponseUpdate;
    }

    public void setRequiresResponseUpdate(boolean requiresResponseUpdate) {
        this.requiresResponseUpdate = requiresResponseUpdate;
    }

    public boolean isRequiresProfileUpdate() {
        return requiresProfileUpdate;
    }

    public void setRequiresProfileUpdate(boolean requiresProfileUpdate) {
        this.requiresProfileUpdate = requiresProfileUpdate;
    }

    public DataStorageMethod getResponseStorageMethod()
    {
        return responseStorageMethod;
    }

    public void setResponseStorageMethod(DataStorageMethod responseStorageMethod)
    {
        this.responseStorageMethod = responseStorageMethod;
    }

    public static UserStatusCacheBean cacheBeanFrmInfoBean(final UserInfoBean userInfoBean) {
        final UserStatusCacheBean userStatusCacheBean = new UserStatusCacheBean();
        userStatusCacheBean.setUserDN(userInfoBean.getUserIdentity().getUserDN());
        userStatusCacheBean.setLdapProfile(userInfoBean.getUserIdentity().getLdapProfileID());
        userStatusCacheBean.setUsername(userInfoBean.getUsername());
        userStatusCacheBean.setEmail(userInfoBean.getUserEmailAddress());
        userStatusCacheBean.setUserGUID(userInfoBean.getUserGuid());

        userStatusCacheBean.setPasswordStatus(userInfoBean.getPasswordState());

        userStatusCacheBean.setPasswordChangeTime(userInfoBean.getPasswordExpirationTime());
        userStatusCacheBean.setPasswordExpirationTime(userInfoBean.getPasswordExpirationTime());
        userStatusCacheBean.setLastLoginTime(userInfoBean.getLastLdapLoginTime());

        userStatusCacheBean.setHasResponses(userInfoBean.isRequiresResponseConfig());
        userStatusCacheBean.setResponseSetTime(
                userInfoBean.getResponseInfoBean() != null ? userInfoBean.getResponseInfoBean().getTimestamp() : null);
        userStatusCacheBean.setResponseStorageMethod(
                userInfoBean.getResponseInfoBean() != null ? userInfoBean.getResponseInfoBean().getDataStorageMethod() : null);

        userStatusCacheBean.setRequiresPasswordUpdate(userInfoBean.isRequiresNewPassword());
        userStatusCacheBean.setRequiresResponseUpdate(userInfoBean.isRequiresResponseConfig());
        userStatusCacheBean.setRequiresProfileUpdate(userInfoBean.isRequiresUpdateProfile());

        return userStatusCacheBean;
    }

}
