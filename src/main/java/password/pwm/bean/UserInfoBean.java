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

package password.pwm.bean;

import password.pwm.config.profile.ChallengeProfile;
import password.pwm.config.profile.ProfileType;
import password.pwm.config.profile.PwmPasswordPolicy;
import password.pwm.http.bean.PwmSessionBean;
import password.pwm.util.otp.OTPUserRecord;

import java.util.*;

/**
 * A bean that is stored in the user's session.   Only information that is particular to logged in user is stored in the
 * user info bean.  Information more topical to the session is stored in {@link SessionStateBean}.
 * <p/>
 * For any given HTTP session using PWM, several {@link UserInfoBean}s may be created during
 * the life of the session, however at any given time, no more than one will be stored in
 * the HTTP session.  If the user is not authenticated (determined by {@link SessionStateBean#isAuthenticated()})
 * then there should not be a {@link UserInfoBean} in the HTTP session.
 *
 * @author Jason D. Rivard
 * @see password.pwm.ldap.UserStatusReader#populateUserInfoBean(Locale, UserIdentity)
 */
public class UserInfoBean implements PwmSessionBean {
// ------------------------------ FIELDS ------------------------------

    private UserIdentity userIdentity;
    private String username;
    private String userEmailAddress;
    private String userSmsNumber;
    private String userGuid;

    /**
     * A listing of all readable attributes on the ldap user object
     */
    private Map<String,String> cachedPasswordRuleAttributes = Collections.emptyMap();

    private Map<String,String> cachedAttributeValues = Collections.emptyMap();
    
    private Map<ProfileType,String> profileIDs = new HashMap<>();

    private PasswordStatus passwordState = new PasswordStatus();

    private PwmPasswordPolicy passwordPolicy = PwmPasswordPolicy.defaultPolicy();
    private ChallengeProfile challengeProfile = null;
    private ResponseInfoBean responseInfoBean = null;
    private OTPUserRecord otpUserRecord = null;

    private Date passwordExpirationTime;
    private Date passwordLastModifiedTime;
    private Date lastLdapLoginTime;
    private Date accountExpirationTime;

    private boolean requiresNewPassword;
    private boolean requiresResponseConfig;
    private boolean requiresOtpConfig;
    private boolean requiresUpdateProfile;


    // --------------------- GETTER / SETTER METHODS ---------------------

    public Map<String,String> getCachedPasswordRuleAttributes() {
        return this.cachedPasswordRuleAttributes;
    }

    public void setCachedPasswordRuleAttributes(final Map<String, String> userAttributes) {
        cachedPasswordRuleAttributes = userAttributes;
    }

    public Map<String, String> getCachedAttributeValues()
    {
        return cachedAttributeValues;
    }

    public void setCachedAttributeValues(Map<String, String> cachedAttributeValues)
    {
        this.cachedAttributeValues = cachedAttributeValues;
    }

    public Date getLastLdapLoginTime() {
        return lastLdapLoginTime;
    }

    public void setLastLdapLoginTime(Date lastLdapLoginTime) {
        this.lastLdapLoginTime = lastLdapLoginTime;
    }

    public ChallengeProfile getChallengeProfile() {
        return challengeProfile;
    }

    public void setChallengeSet(final ChallengeProfile challengeSet) {
        this.challengeProfile = challengeSet;
    }

    public PwmPasswordPolicy getPasswordPolicy() {
        return passwordPolicy;
    }

    public void setPasswordPolicy(final PwmPasswordPolicy passwordPolicy) {
        this.passwordPolicy = passwordPolicy;
    }

    public UserIdentity getUserIdentity() {
        return userIdentity;
    }

    public void setUserIdentity(UserIdentity userIdentity) {
        this.userIdentity = userIdentity;
    }

    public Date getPasswordExpirationTime() {
        return passwordExpirationTime;
    }

    public void setPasswordExpirationTime(final Date passwordExpirationTime) {
        this.passwordExpirationTime = passwordExpirationTime;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(final String username) {
        this.username = username;
    }

    public PasswordStatus getPasswordState() {
        return passwordState;
    }

    public void setPasswordState(final PasswordStatus passwordState) {
        this.passwordState = passwordState;
    }

    public boolean isRequiresNewPassword() {
        return requiresNewPassword;
    }

    public void setRequiresNewPassword(final boolean requiresNewPassword) {
        this.requiresNewPassword = requiresNewPassword;
    }

    public boolean isRequiresResponseConfig() {
        return requiresResponseConfig;
    }

    public void setRequiresResponseConfig(final boolean requiresResponseConfig) {
        this.requiresResponseConfig = requiresResponseConfig;
    }

    public boolean isRequiresOtpConfig() {
        return requiresOtpConfig;
    }

    public void setRequiresOtpConfig(final boolean requiresOtpConfig) {
        this.requiresOtpConfig = requiresOtpConfig;
    }

    public boolean isRequiresUpdateProfile() {
        return requiresUpdateProfile;
    }

    public void setRequiresUpdateProfile(final boolean requiresUpdateProfile) {
        this.requiresUpdateProfile = requiresUpdateProfile;
    }

    public Date getPasswordLastModifiedTime() {
        return passwordLastModifiedTime;
    }

    public void setPasswordLastModifiedTime(final Date passwordLastModifiedTime) {
        this.passwordLastModifiedTime = passwordLastModifiedTime;
    }

    public String getUserEmailAddress() {
        return userEmailAddress;
    }

    public void setUserEmailAddress(final String userEmailAddress) {
        this.userEmailAddress = userEmailAddress;
    }

    public String getUserSmsNumber() {
        return userSmsNumber;
    }

    public void setUserSmsNumber(final String userSmsNumber) {
        this.userSmsNumber = userSmsNumber;
    }

    public String getUserGuid() {
        return userGuid;
    }

    public void setUserGuid(final String userGuid) {
        this.userGuid = userGuid;
    }

    public ResponseInfoBean getResponseInfoBean() {
        return responseInfoBean;
    }

    public void setResponseInfoBean(ResponseInfoBean responseInfoBean) {
        this.responseInfoBean = responseInfoBean;
    }

    public OTPUserRecord getOtpUserRecord()
    {
        return otpUserRecord;
    }

    public void setOtpUserRecord(OTPUserRecord otpUserRecord)
    {
        this.otpUserRecord = otpUserRecord;
    }

    public Date getAccountExpirationTime() {
        return accountExpirationTime;
    }

    public void setAccountExpirationTime(Date accountExpirationTime) {
        this.accountExpirationTime = accountExpirationTime;
    }

    public Map<ProfileType, String> getProfileIDs() {
        return profileIDs;
    }
}

