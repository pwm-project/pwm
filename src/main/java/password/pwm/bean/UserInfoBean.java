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

package password.pwm.bean;

import password.pwm.config.profile.ChallengeProfile;
import password.pwm.config.profile.ProfileType;
import password.pwm.config.profile.PwmPasswordPolicy;
import password.pwm.util.operations.otp.OTPUserRecord;

import java.io.Serializable;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * A bean that is stored in the user's session.   Only information that is particular to logged in user is stored in the
 * user info bean.  Information more topical to the session is stored in {@link LocalSessionStateBean}.
 * <p/>
 * For any given HTTP session using PWM, several {@link UserInfoBean}s may be created during
 * the life of the session, however at any given time, no more than one will be stored in
 * the HTTP session.  If the user is not authenticated (determined by {@link LocalSessionStateBean})
 * then there should not be a {@link UserInfoBean} in the HTTP session.
 *
 * @author Jason D. Rivard
 * @see password.pwm.ldap.UserStatusReader#populateUserInfoBean
 */
public class UserInfoBean implements Serializable {
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

    private Instant passwordExpirationTime;
    private Instant passwordLastModifiedTime;
    private Instant lastLdapLoginTime;
    private Instant accountExpirationTime;

    private boolean requiresNewPassword;
    private boolean requiresResponseConfig;
    private boolean requiresOtpConfig;
    private boolean requiresUpdateProfile;

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

    public void setCachedAttributeValues(final Map<String, String> cachedAttributeValues)
    {
        this.cachedAttributeValues = cachedAttributeValues;
    }

    public Instant getLastLdapLoginTime() {
        return lastLdapLoginTime;
    }

    public void setLastLdapLoginTime(final Instant lastLdapLoginTime) {
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

    public void setUserIdentity(final UserIdentity userIdentity) {
        this.userIdentity = userIdentity;
    }

    public Instant getPasswordExpirationTime() {
        return passwordExpirationTime;
    }

    public void setPasswordExpirationTime(final Instant passwordExpirationTime) {
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

    public Instant getPasswordLastModifiedTime() {
        return passwordLastModifiedTime;
    }

    public void setPasswordLastModifiedTime(final Instant passwordLastModifiedTime) {
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

    public void setResponseInfoBean(final ResponseInfoBean responseInfoBean) {
        this.responseInfoBean = responseInfoBean;
    }

    public OTPUserRecord getOtpUserRecord()
    {
        return otpUserRecord;
    }

    public void setOtpUserRecord(final OTPUserRecord otpUserRecord)
    {
        this.otpUserRecord = otpUserRecord;
    }

    public Instant getAccountExpirationTime() {
        return accountExpirationTime;
    }

    public void setAccountExpirationTime(final Instant accountExpirationTime) {
        this.accountExpirationTime = accountExpirationTime;
    }

    public Map<ProfileType, String> getProfileIDs() {
        return profileIDs;
    }
}

