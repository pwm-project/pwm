/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
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

package password.pwm.svc.report;

import com.novell.ldapchai.cr.Answer;
import password.pwm.bean.PasswordStatus;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.option.DataStorageMethod;

import java.io.Serializable;
import java.time.Instant;

public class UserCacheRecord implements Serializable {
    public String userDN;
    public String ldapProfile;
    public String userGUID;

    public String username;
    public String email;

    public Instant cacheTimestamp = Instant.now();

    public PasswordStatus passwordStatus;
    public Instant passwordExpirationTime;
    public Instant passwordChangeTime;
    public Instant lastLoginTime;
    public Instant accountExpirationTime;

    public boolean hasResponses;
    public boolean hasHelpdeskResponses;
    public Instant responseSetTime;
    public DataStorageMethod responseStorageMethod;
    public Answer.FormatType responseFormatType;

    public boolean hasOtpSecret;
    public Instant otpSecretSetTime;

    public boolean requiresPasswordUpdate;
    public boolean requiresResponseUpdate;
    public boolean requiresProfileUpdate;

    public String getUserDN()
    {
        return userDN;
    }

    public void setUserDN(final String userDN)
    {
        this.userDN = userDN;
    }

    public String getLdapProfile()
    {
        return ldapProfile;
    }

    public void setLdapProfile(final String ldapProfile)
    {
        this.ldapProfile = ldapProfile;
    }

    public String getUsername()
    {
        return username;
    }

    public void setUsername(final String username)
    {
        this.username = username;
    }

    public String getEmail()
    {
        return email;
    }

    public void setEmail(final String email)
    {
        this.email = email;
    }

    public String getUserGUID() {
        return userGUID;
    }

    public void setUserGUID(final String userGUID) {
        this.userGUID = userGUID;
    }

    public Instant getCacheTimestamp() {
        return cacheTimestamp;
    }

    public void setCacheTimestamp(final Instant cacheTimestamp) {
        this.cacheTimestamp = cacheTimestamp;
    }

    public PasswordStatus getPasswordStatus() {
        return passwordStatus;
    }

    public void setPasswordStatus(final PasswordStatus passwordStatus) {
        this.passwordStatus = passwordStatus;
    }

    public Instant getPasswordExpirationTime() {
        return passwordExpirationTime;
    }

    public void setPasswordExpirationTime(final Instant passwordExpirationTime) {
        this.passwordExpirationTime = passwordExpirationTime;
    }

    public Instant getPasswordChangeTime() {
        return passwordChangeTime;
    }

    public void setPasswordChangeTime(final Instant passwordChangeTime) {
        this.passwordChangeTime = passwordChangeTime;
    }

    public Instant getResponseSetTime() {
        return responseSetTime;
    }

    public void setResponseSetTime(final Instant responseSetTime) {
        this.responseSetTime = responseSetTime;
    }

    public Instant getLastLoginTime() {
        return lastLoginTime;
    }

    public void setLastLoginTime(final Instant lastLoginTime) {
        this.lastLoginTime = lastLoginTime;
    }

    public boolean isHasResponses() {
        return hasResponses;
    }

    public void setHasResponses(final boolean hasResponses) {
        this.hasResponses = hasResponses;
    }

    public boolean isHasHelpdeskResponses()
    {
        return hasHelpdeskResponses;
    }

    public void setHasHelpdeskResponses(final boolean hasHelpdeskResponses)
    {
        this.hasHelpdeskResponses = hasHelpdeskResponses;
    }

    public boolean isRequiresPasswordUpdate() {
        return requiresPasswordUpdate;
    }

    public void setRequiresPasswordUpdate(final boolean requiresPasswordUpdate) {
        this.requiresPasswordUpdate = requiresPasswordUpdate;
    }

    public boolean isRequiresResponseUpdate() {
        return requiresResponseUpdate;
    }

    public void setRequiresResponseUpdate(final boolean requiresResponseUpdate) {
        this.requiresResponseUpdate = requiresResponseUpdate;
    }

    public boolean isRequiresProfileUpdate() {
        return requiresProfileUpdate;
    }

    public void setRequiresProfileUpdate(final boolean requiresProfileUpdate) {
        this.requiresProfileUpdate = requiresProfileUpdate;
    }

    public DataStorageMethod getResponseStorageMethod()
    {
        return responseStorageMethod;
    }

    public void setResponseStorageMethod(final DataStorageMethod responseStorageMethod)
    {
        this.responseStorageMethod = responseStorageMethod;
    }

    public Answer.FormatType getResponseFormatType()
    {
        return responseFormatType;
    }

    public void setResponseFormatType(final Answer.FormatType responseFormatType)
    {
        this.responseFormatType = responseFormatType;
    }

    public boolean isHasOtpSecret()
    {
        return hasOtpSecret;
    }

    public void setHasOtpSecret(final boolean hasOtpSecret)
    {
        this.hasOtpSecret = hasOtpSecret;
    }

    public Instant getOtpSecretSetTime()
    {
        return otpSecretSetTime;
    }

    public void setOtpSecretSetTime(final Instant otpSecretSetTime)
    {
        this.otpSecretSetTime = otpSecretSetTime;
    }

    public Instant getAccountExpirationTime() {
        return accountExpirationTime;
    }

    public void setAccountExpirationTime(final Instant accountExpirationTime) {
        this.accountExpirationTime = accountExpirationTime;
    }

    public void addUiBeanData(final UserInfoBean userInfoBean) {
        this.setUserDN(userInfoBean.getUserIdentity().getUserDN());
        this.setLdapProfile(userInfoBean.getUserIdentity().getLdapProfileID());
        this.setUsername(userInfoBean.getUsername());
        this.setEmail(userInfoBean.getUserEmailAddress());
        this.setUserGUID(userInfoBean.getUserGuid());

        this.setPasswordStatus(userInfoBean.getPasswordState());

        this.setPasswordChangeTime(userInfoBean.getPasswordLastModifiedTime());
        this.setPasswordExpirationTime(userInfoBean.getPasswordExpirationTime());
        this.setLastLoginTime(userInfoBean.getLastLdapLoginTime());
        this.setAccountExpirationTime(userInfoBean.getAccountExpirationTime());

        this.setHasResponses(userInfoBean.getResponseInfoBean() != null);
        this.setResponseSetTime(userInfoBean.getResponseInfoBean() != null
                        ? userInfoBean.getResponseInfoBean().getTimestamp()
                        : null
        );
        this.setResponseStorageMethod(userInfoBean.getResponseInfoBean() != null
                        ? userInfoBean.getResponseInfoBean().getDataStorageMethod()
                        : null
        );
        this.setResponseFormatType(userInfoBean.getResponseInfoBean() != null
                        ? userInfoBean.getResponseInfoBean().getFormatType()
                        : null
        );

        this.setRequiresPasswordUpdate(userInfoBean.isRequiresNewPassword());
        this.setRequiresResponseUpdate(userInfoBean.isRequiresResponseConfig());
        this.setRequiresProfileUpdate(userInfoBean.isRequiresUpdateProfile());
        this.setCacheTimestamp(Instant.now());

        this.setHasOtpSecret(userInfoBean.getOtpUserRecord() != null);
        this.setOtpSecretSetTime(userInfoBean.getOtpUserRecord() != null && userInfoBean.getOtpUserRecord().getTimestamp() != null
                        ? userInfoBean.getOtpUserRecord().getTimestamp().toInstant()
                        : null
        );

        this.setHasHelpdeskResponses(userInfoBean.getResponseInfoBean() != null
                        && userInfoBean.getResponseInfoBean().getHelpdeskCrMap() != null
                        && !userInfoBean.getResponseInfoBean().getHelpdeskCrMap().isEmpty()
        );
    }

}
