/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2014 The PWM Project
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

package password.pwm.ldap;

import com.novell.ldapchai.ChaiFactory;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiProvider;
import password.pwm.PwmApplication;
import password.pwm.PwmPasswordPolicy;
import password.pwm.PwmSession;
import password.pwm.bean.PasswordStatus;
import password.pwm.bean.ResponseInfoBean;
import password.pwm.bean.UserIdentity;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.ChallengeProfile;
import password.pwm.config.Configuration;
import password.pwm.config.PwmPasswordRule;
import password.pwm.config.PwmSetting;
import password.pwm.error.PwmDataValidationException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.servlet.CommandServlet;
import password.pwm.util.Helper;
import password.pwm.util.PwmLogger;
import password.pwm.util.PwmPasswordRuleValidator;
import password.pwm.util.TimeDuration;
import password.pwm.util.localdb.LocalDBException;
import password.pwm.util.operations.CrService;
import password.pwm.util.operations.OtpService;
import password.pwm.util.operations.PasswordUtility;
import password.pwm.util.otp.OTPUserConfiguration;

import java.io.Serializable;
import java.util.*;

public class UserStatusReader {

    private static final PwmLogger LOGGER = PwmLogger.getLogger(UserStatusReader.class);

    private final PwmApplication pwmApplication;
    private final Settings settings;

    public UserStatusReader(
            final PwmApplication pwmApplication
    ) {
        this.pwmApplication = pwmApplication;
        this.settings = new Settings();
    }

    public UserStatusReader(
            PwmApplication pwmApplication,
            Settings settings
    )
    {
        this.pwmApplication = pwmApplication;
        this.settings = settings.copy();
    }

    public PasswordStatus readPasswordStatus(
            final PwmSession pwmSession,
            final String currentPassword,
            final ChaiUser theUser,
            final PwmPasswordPolicy passwordPolicy,
            final UserInfoBean userInfoBean
    )
            throws ChaiUnavailableException, PwmUnrecoverableException {
        final Configuration config = pwmApplication.getConfig();
        final PasswordStatus returnState = new PasswordStatus();
        final String userDN = theUser.getEntryDN();

        final long startTime = System.currentTimeMillis();
        LOGGER.trace(pwmSession, "beginning password status check process for " + userDN);

        // check if password meets existing policy.
        if (passwordPolicy.getRuleHelper().readBooleanValue(PwmPasswordRule.EnforceAtLogin)) {
            if (currentPassword != null && currentPassword.length() > 0) {
                try {
                    PwmPasswordRuleValidator passwordRuleValidator = new PwmPasswordRuleValidator(pwmApplication, passwordPolicy);
                    passwordRuleValidator.testPassword(currentPassword, null, userInfoBean, theUser);
                } catch (PwmDataValidationException e) {
                    LOGGER.info(pwmSession, "user " + userDN + " password does not conform to current password policy (" + e.getMessage() + "), marking as requiring change.");
                    returnState.setViolatesPolicy(true);
                } catch (PwmUnrecoverableException e) {
                    LOGGER.info(pwmSession, "user " + userDN + " password does not conform to current password policy (" + e.getMessage() + "), marking as requiring change.");
                    returnState.setViolatesPolicy(true);
                }
            }
        }

        try {
            final boolean ldapPasswordExpired = theUser.isPasswordExpired();

            if (ldapPasswordExpired) {
                returnState.setExpired(true);
                LOGGER.trace(pwmSession, "password for " + userDN + " appears to be expired");
            } else {
                LOGGER.trace(pwmSession, "password for " + userDN + " does not appear to be expired");
            }
        } catch (ChaiOperationException e) {
            LOGGER.info(pwmSession, "error reading user attrs for " + userDN + " while reading isPasswordExpired(): " + e.getMessage());
        }

        try {
            Date ldapPasswordExpirationTime = theUser.readPasswordExpirationDate();
            if (ldapPasswordExpirationTime != null && ldapPasswordExpirationTime.getTime() < 0) {
                // If ldapPasswordExpirationTime is less than 0, this may indicate an extremely late date, past the epoch.
                ldapPasswordExpirationTime = null;
            }

            if (ldapPasswordExpirationTime != null) {
                LOGGER.trace(String.format("ldapPasswordExpirationTime (%s): %s (%d ms)", userDN, ldapPasswordExpirationTime.toString(), ldapPasswordExpirationTime.getTime()));
                final long diff = ldapPasswordExpirationTime.getTime() - System.currentTimeMillis();

                // now check to see if the user's expire time is within the 'preExpireTime' setting.
                final long preExpireMs = config.readSettingAsLong(PwmSetting.PASSWORD_EXPIRE_PRE_TIME) * 1000;
                if (diff > 0 && diff < preExpireMs) {
                    LOGGER.info(pwmSession, "user " + userDN + " password will expire within " + TimeDuration.asCompactString(diff) + ", marking as pre-expired");
                    returnState.setPreExpired(true);
                } else if (returnState.isExpired()) {
                    LOGGER.info(pwmSession, "user " + userDN + " password is expired, marking as pre-expired.");
                    returnState.setPreExpired(true);
                }

                // now check to see if the user's expire time is within the 'preWarnTime' setting.
                final long preWarnMs = config.readSettingAsLong(PwmSetting.PASSWORD_EXPIRE_WARN_TIME) * 1000;
                // don't check if the 'preWarnTime' setting is zero or less than the expirePreTime
                if (!(preWarnMs == 0 || preWarnMs < preExpireMs)) {
                    if (diff > 0 && diff < preWarnMs) {
                        LOGGER.info(pwmSession, "user " + userDN + " password will expire within " + TimeDuration.asCompactString(diff) + ", marking as within warn period");
                        returnState.setWarnPeriod(true);
                    } else if (returnState.isExpired()) {
                        LOGGER.info(pwmSession, "user " + userDN + " password is expired, marking as within warn period");
                        returnState.setWarnPeriod(true);
                    }
                }
            }

        } catch (ChaiOperationException e) {
            LOGGER.info(pwmSession, "error reading user attrs for " + userDN + " while reading passwordExpirationDate(): " + e.getMessage());
        }

        LOGGER.debug(pwmSession, "completed user password status check for " + userDN + " " + returnState + " (" + TimeDuration.fromCurrent(startTime).asCompactString() + ")");
        return returnState;
    }

    public void populateActorUserInfoBean(
            final PwmSession pwmSession,
            final UserIdentity userIdentity,
            final String userCurrentPassword
    )
            throws ChaiUnavailableException, PwmUnrecoverableException {
        final String userLdapProfile = userIdentity.getLdapProfileID();
        final ChaiProvider provider = pwmApplication.getProxyChaiProvider(userLdapProfile);
        final UserInfoBean uiBean = pwmSession.getUserInfoBean();
        populateUserInfoBean(pwmSession, uiBean, pwmSession.getSessionStateBean().getLocale(), userIdentity, userCurrentPassword, provider);
    }

    public void populateUserInfoBean(
            final PwmSession pwmSession,
            final UserInfoBean uiBean,
            final Locale userLocale,
            final UserIdentity userIdentity,
            final String userCurrentPassword
    )
            throws PwmUnrecoverableException, ChaiUnavailableException
    {
        final ChaiProvider provider = pwmApplication.getProxyChaiProvider(userIdentity.getLdapProfileID());
        populateUserInfoBean(pwmSession, uiBean, userLocale, userIdentity, userCurrentPassword, provider);
    }

    public void populateUserInfoBean(
            final PwmSession pwmSession,
            final UserInfoBean uiBean,
            final Locale userLocale,
            final UserIdentity userIdentity,
            final String userCurrentPassword,
            final ChaiProvider provider
    )
            throws ChaiUnavailableException, PwmUnrecoverableException {
        final Configuration config = pwmApplication.getConfig();
        final long methodStartTime = System.currentTimeMillis();

        if (userIdentity == null || userIdentity.getUserDN() == null || userIdentity.getUserDN().length() < 1) {
            throw new NullPointerException("userDN can not be null");
        }

        //attempt to add the object class to the user
        LdapOperationsHelper.addConfiguredUserObjectClass(userIdentity, pwmApplication);

        uiBean.setUserCurrentPassword(userCurrentPassword);

        final ChaiUser theUser = ChaiFactory.createChaiUser(userIdentity.getUserDN(), provider);
        final UserDataReader userDataReader = new UserDataReader(userIdentity, theUser);

        try {
            uiBean.setUserIdentity(new UserIdentity(theUser.readCanonicalDN(),userIdentity.getLdapProfileID()));
        } catch (ChaiOperationException e) {
            LOGGER.warn("error reading canonical DN" + e);
            uiBean.setUserIdentity(userIdentity);
        }

        populateLocaleSpecificUserInfoBean(pwmSession, uiBean, userLocale);

        //populate OTP data
        if (config.readSettingAsBoolean(PwmSetting.OTP_ENABLED)){
            final OtpService otpService = pwmApplication.getOtpService();
            final OTPUserConfiguration otpConfig = otpService.readOTPUserConfiguration(userIdentity);
            uiBean.setRequiresOtpConfig(otpService.checkIfOtpSetupNeeded(pwmSession, userIdentity, otpConfig));
        }

        //populate cached password rule attributes
        try {
            final Set<String> interestingUserAttributes = figurePasswordRuleAttributes(uiBean);
            final Map<String, String> allUserAttrs = userDataReader.readStringAttributes(interestingUserAttributes);
            uiBean.setCachedPasswordRuleAttributes(allUserAttrs);
        } catch (ChaiOperationException e) {
            LOGGER.warn("error retrieving user cached password rule attributes " + e);
        }

        //populate cached attributes.
        {
            final List<String> cachedAttributeNames = config.readSettingAsStringArray(PwmSetting.CACHED_USER_ATTRIBUTES);
            if (cachedAttributeNames != null && !cachedAttributeNames.isEmpty()) {
                try {
                    final Map<String,String> attributeValues = userDataReader.readStringAttributes(cachedAttributeNames);
                    uiBean.setCachedAttributeValues(Collections.unmodifiableMap(attributeValues));
                } catch (ChaiOperationException e) {
                    LOGGER.warn("error retrieving user cache attributes: " + e);
                }
            }

        }

        {// set userID
            final String uIDattr = config.getUsernameAttribute(userIdentity.getLdapProfileID());
            try {
                uiBean.setUsername(userDataReader.readStringAttribute(uIDattr));
            } catch (ChaiOperationException e) {
                LOGGER.error(pwmSession, "error reading userID attribute: " + e.getMessage());
            }
        }

        { // set guid
            final String userGuid = LdapOperationsHelper.readLdapGuidValue(pwmApplication, userIdentity);
            uiBean.setUserGuid(userGuid);
        }

        { // set email address
            final String ldapEmailAttribute = config.readSettingAsString(PwmSetting.EMAIL_USER_MAIL_ATTRIBUTE);
            try {
                uiBean.setUserEmailAddress(userDataReader.readStringAttribute(ldapEmailAttribute));
            } catch (ChaiOperationException e) {
                LOGGER.error(pwmSession, "error reading email address attribute: " + e.getMessage());
            }
        }

        { // set SMS number
            final String ldapSmsAttribute = config.readSettingAsString(PwmSetting.SMS_USER_PHONE_ATTRIBUTE);
            try {
                uiBean.setUserSmsNumber(userDataReader.readStringAttribute(ldapSmsAttribute));
            } catch (ChaiOperationException e) {
                LOGGER.error(pwmSession, "error reading sms number attribute: " + e.getMessage());
            }
        }

        // read password expiration time
        Date ldapPasswordExpirationTime = null;
        try {
            ldapPasswordExpirationTime = theUser.readPasswordExpirationDate();
            if (ldapPasswordExpirationTime != null && ldapPasswordExpirationTime.getTime() < 0) {
                // If ldapPasswordExpirationTime is less than 0, this may indicate an extremely late date, past the epoch.
                ldapPasswordExpirationTime = null;
            }
            uiBean.setPasswordExpirationTime(ldapPasswordExpirationTime);
        } catch (Exception e) {
            LOGGER.warn(pwmSession, "error reading password expiration time: " + e.getMessage());
        }

        // read password state
        uiBean.setPasswordState(readPasswordStatus(pwmSession, userCurrentPassword, theUser, uiBean.getPasswordPolicy(), uiBean));

        // mark if new pw required
        if (uiBean.getPasswordState().isExpired() || uiBean.getPasswordState().isPreExpired()) {
            uiBean.setRequiresNewPassword(true);
        }

        // check if responses need to be updated
        uiBean.setRequiresUpdateProfile(CommandServlet.checkProfile(pwmSession, pwmApplication, uiBean));

        // fetch last password modification time;
        final Date pwdLastModifedDate = determinePwdLastModified(pwmSession, userIdentity);
        uiBean.setPasswordLastModifiedTime(pwdLastModifedDate);

        // read user last login time:
        try {
            uiBean.setLastLdapLoginTime(theUser.readLastLoginTime());
        } catch (ChaiOperationException e) {
            LOGGER.warn(pwmSession, "error reading user's last ldap login time: " + e.getMessage());
        }

        // update report engine.
        if (!settings.isSkipReportUpdate()) {
            try {
                pwmApplication.getUserReportService().updateCache(uiBean);
            } catch (LocalDBException e) {
                LOGGER.error(pwmSession, "error updating report cache data ldap login time: " + e.getMessage());
            }
        }

        LOGGER.trace(pwmSession, "populateUserInfoBean for " + userIdentity + " completed in " + TimeDuration.fromCurrent(methodStartTime).asCompactString());
    }

    public void populateLocaleSpecificUserInfoBean(
            final PwmSession pwmSession,
            final UserInfoBean uiBean,
            final Locale userLocale
    )
            throws PwmUnrecoverableException, ChaiUnavailableException {
        final long startTime = System.currentTimeMillis();

        if (uiBean == null || uiBean.getUserIdentity() == null) {
            return;
        }

        final ChaiUser theUser = pwmApplication.getProxiedChaiUser(uiBean.getUserIdentity());

        //populate password policy
        uiBean.setPasswordPolicy(PasswordUtility.readPasswordPolicyForUser(pwmApplication, pwmSession, uiBean.getUserIdentity(), theUser, userLocale));

        //populate c/r challenge set.
        {
            final CrService crService = pwmApplication.getCrService();
            final ResponseInfoBean responseInfoBean = crService.readUserResponseInfo(pwmSession, uiBean.getUserIdentity(), theUser);
            final ChallengeProfile challengeProfile = crService.readUserChallengeProfile(uiBean.getUserIdentity(),
                    theUser, uiBean.getPasswordPolicy(), userLocale);
            uiBean.setChallengeSet(challengeProfile);
            uiBean.setResponseInfoBean(responseInfoBean);
            uiBean.setRequiresResponseConfig(crService.checkIfResponseConfigNeeded(pwmSession, theUser, challengeProfile.getChallengeSet(), responseInfoBean));
        }

        LOGGER.trace(pwmSession, "finished population of locale specific UserInfoBean in " + TimeDuration.fromCurrent(startTime).asCompactString());
    }

    private static Set<String> figurePasswordRuleAttributes(
            final UserInfoBean uiBean
    ) {
        final Set<String> interestingUserAttributes = new HashSet<String>();
        interestingUserAttributes.addAll(uiBean.getPasswordPolicy().getRuleHelper().getDisallowedAttributes());
        if (uiBean.getPasswordPolicy().getRuleHelper().readBooleanValue(PwmPasswordRule.ADComplexity)) {
            interestingUserAttributes.add("sAMAccountName");
            interestingUserAttributes.add("displayName");
            interestingUserAttributes.add("fullname");
            interestingUserAttributes.add("cn");
        }
        return interestingUserAttributes;
    }

    public Date determinePwdLastModified(
            final PwmSession pwmSession,
            final UserIdentity userIdentity
    )
            throws ChaiUnavailableException, PwmUnrecoverableException {
        // fetch last password modification time from pwm last update attribute operation
        final ChaiUser theUser = pwmApplication.getProxiedChaiUser(userIdentity);
        try {
            final Date chaiReadDate = theUser.readPasswordModificationDate();
            if (chaiReadDate != null) {
                LOGGER.trace(pwmSession, "read last user password change timestamp (via chai) as: " + chaiReadDate.toString());
                return chaiReadDate;
            }
        } catch (ChaiOperationException e) {
            LOGGER.error(pwmSession, "unexpected error reading password last modified timestamp: " + e.getMessage());
        }

        final String pwmLastSetAttr = pwmApplication.getConfig().readSettingAsString(PwmSetting.PASSWORD_LAST_UPDATE_ATTRIBUTE);
        if (pwmLastSetAttr != null && pwmLastSetAttr.length() > 0) {
            try {
                final Date pwmPwdLastModified = theUser.readDateAttribute(pwmLastSetAttr);
                LOGGER.trace(pwmSession, "read pwmPassswordChangeTime as: " + pwmPwdLastModified);
                return pwmPwdLastModified;
            } catch (ChaiOperationException e) {
                LOGGER.error(pwmSession, "error parsing password last modified PWM password value for user " + theUser.getEntryDN() + "; error: " + e.getMessage());
            }
        }

        LOGGER.debug(pwmSession, "unable to determine time of user's last password modification");
        return null;
    }

    /**
     * Update the user's "lastUpdated" attribute. By default this is
     * "pwmLastUpdate" attribute
     *
     * @param pwmSession to lookup session info
     * @param theUser ldap user to operate on
     * @return true if successful;
     * @throws com.novell.ldapchai.exception.ChaiUnavailableException if the
     * directory is unavailable
     */
    public boolean updateLastUpdateAttribute(final PwmSession pwmSession, final ChaiUser theUser)
            throws ChaiUnavailableException, PwmUnrecoverableException {
        boolean success = false;

        final String updateAttribute = pwmApplication.getConfig().readSettingAsString(PwmSetting.PASSWORD_LAST_UPDATE_ATTRIBUTE);

        if (updateAttribute != null && updateAttribute.length() > 0) {
            try {
                theUser.writeDateAttribute(updateAttribute, new Date());
                LOGGER.debug(pwmSession, "wrote pwdLastModified update attribute for " + theUser.getEntryDN());
                success = true;
            } catch (ChaiOperationException e) {
                LOGGER.debug(pwmSession, "error writing update attribute for user '" + theUser.getEntryDN() + "' " + e.getMessage());
            }
        }

        return success;
    }

    public static class Settings implements Serializable {
        private boolean skipReportUpdate;

        public boolean isSkipReportUpdate()
        {
            return skipReportUpdate;
        }

        public void setSkipReportUpdate(boolean skipReportUpdate)
        {
            this.skipReportUpdate = skipReportUpdate;
        }

        private Settings copy() {
            return Helper.getGson().fromJson(Helper.getGson().toJson(this),this.getClass());
        }
    }

}
