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

package password.pwm.svc.report;

import com.novell.ldapchai.cr.Answer;
import password.pwm.bean.PasswordStatus;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.option.DataStorageMethod;

import java.io.Serializable;
import java.util.Date;

public class UserCacheRecord implements Serializable {
    public String userDN;
    public String ldapProfile;
    public String userGUID;

    public String username;
    public String email;

    public String summaryEpoch;

    public Date cacheTimestamp = new Date();

    public PasswordStatus passwordStatus;
    public Date passwordExpirationTime;
    public Date passwordChangeTime;
    public Date lastLoginTime;
    public Date accountExpirationTime;

    public boolean hasResponses;
    public boolean hasHelpdeskResponses;
    public Date responseSetTime;
    public DataStorageMethod responseStorageMethod;
    public Answer.FormatType responseFormatType;

    public boolean hasOtpSecret;
    public Date otpSecretSetTime;

    public boolean requiresPasswordUpdate;
    public boolean requiresResponseUpdate;
    public boolean requiresProfileUpdate;

    public String getSummaryEpoch()
    {
        return summaryEpoch;
    }

    public void setSummaryEpoch(String summaryEpoch)
    {
        this.summaryEpoch = summaryEpoch;
    }

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

    public boolean isHasHelpdeskResponses()
    {
        return hasHelpdeskResponses;
    }

    public void setHasHelpdeskResponses(boolean hasHelpdeskResponses)
    {
        this.hasHelpdeskResponses = hasHelpdeskResponses;
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

    public Answer.FormatType getResponseFormatType()
    {
        return responseFormatType;
    }

    public void setResponseFormatType(Answer.FormatType responseFormatType)
    {
        this.responseFormatType = responseFormatType;
    }

    public boolean isHasOtpSecret()
    {
        return hasOtpSecret;
    }

    public void setHasOtpSecret(boolean hasOtpSecret)
    {
        this.hasOtpSecret = hasOtpSecret;
    }

    public Date getOtpSecretSetTime()
    {
        return otpSecretSetTime;
    }

    public void setOtpSecretSetTime(Date otpSecretSetTime)
    {
        this.otpSecretSetTime = otpSecretSetTime;
    }

    public Date getAccountExpirationTime() {
        return accountExpirationTime;
    }

    public void setAccountExpirationTime(Date accountExpirationTime) {
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
        this.setCacheTimestamp(new Date());

        this.setHasOtpSecret(userInfoBean.getOtpUserRecord() != null);
        this.setOtpSecretSetTime(userInfoBean.getOtpUserRecord() != null && userInfoBean.getOtpUserRecord().getTimestamp() != null
                        ? userInfoBean.getOtpUserRecord().getTimestamp()
                        : null
        );

        this.setHasHelpdeskResponses(userInfoBean.getResponseInfoBean() != null
                        && userInfoBean.getResponseInfoBean().getHelpdeskCrMap() != null
                        && !userInfoBean.getResponseInfoBean().getHelpdeskCrMap().isEmpty()
        );
    }

}
