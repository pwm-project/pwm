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

package password.pwm.ldap;

import com.novell.ldapchai.ChaiFactory;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiProvider;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.*;
import password.pwm.config.*;
import password.pwm.config.option.ADPolicyComplexity;
import password.pwm.config.option.ForceSetupPolicy;
import password.pwm.config.profile.*;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmDataValidationException;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmSession;
import password.pwm.util.JsonUtil;
import password.pwm.util.PasswordData;
import password.pwm.util.PwmPasswordRuleValidator;
import password.pwm.util.TimeDuration;
import password.pwm.util.localdb.LocalDBException;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.operations.CrService;
import password.pwm.util.operations.OtpService;
import password.pwm.util.operations.PasswordUtility;
import password.pwm.util.otp.OTPUserRecord;

import java.io.Serializable;
import java.util.*;

public class UserStatusReader {

    private static final PwmLogger LOGGER = PwmLogger.forClass(UserStatusReader.class);

    private final PwmApplication pwmApplication;
    private final SessionLabel sessionLabel;
    private final Settings settings;

    public UserStatusReader(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel
    ) {
        this.pwmApplication = pwmApplication;
        this.sessionLabel = sessionLabel;
        this.settings = new Settings();
    }

    public UserStatusReader(
            PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            Settings settings
    )
    {
        this.pwmApplication = pwmApplication;
        this.sessionLabel = sessionLabel;
        this.settings = settings.copy();
    }

    public PasswordStatus readPasswordStatus(
            final ChaiUser theUser,
            final PwmPasswordPolicy passwordPolicy,
            final UserInfoBean userInfoBean,
            final PasswordData currentPassword
    )
            throws ChaiUnavailableException, PwmUnrecoverableException
    {
        final Configuration config = pwmApplication.getConfig();
        final PasswordStatus passwordStatus = new PasswordStatus();
        final String userDN = theUser.getEntryDN();

        final long startTime = System.currentTimeMillis();
        LOGGER.trace(sessionLabel, "beginning password status check process for " + userDN);

        // check if password meets existing policy.
        if (passwordPolicy.getRuleHelper().readBooleanValue(PwmPasswordRule.EnforceAtLogin)) {
            if (currentPassword != null) {
                try {
                    PwmPasswordRuleValidator passwordRuleValidator = new PwmPasswordRuleValidator(pwmApplication, passwordPolicy);
                    passwordRuleValidator.testPassword(currentPassword, null, userInfoBean, theUser);
                } catch (PwmDataValidationException | PwmUnrecoverableException e) {
                    LOGGER.debug(sessionLabel, "user " + userDN + " password does not conform to current password policy (" + e.getMessage() + "), marking as requiring change.");
                    passwordStatus.setViolatesPolicy(true);
                }
            }
        }

        try {
            final boolean ldapPasswordExpired = theUser.isPasswordExpired();

            if (ldapPasswordExpired) {
                passwordStatus.setExpired(true);
                LOGGER.trace(sessionLabel, "password for " + userDN + " appears to be expired");
            } else {
                LOGGER.trace(sessionLabel, "password for " + userDN + " does not appear to be expired");
            }
        } catch (ChaiOperationException e) {
            LOGGER.info(sessionLabel, "error reading LDAP attributes for " + userDN + " while reading isPasswordExpired(): " + e.getMessage());
        }

        try {
            Date ldapPasswordExpirationTime = theUser.readPasswordExpirationDate();
            if (ldapPasswordExpirationTime != null && ldapPasswordExpirationTime.getTime() < 0) {
                // If ldapPasswordExpirationTime is less than 0, this may indicate an extremely late date, past the epoch.
                LOGGER.debug(sessionLabel, "ignoring past-dated password expiration time: " + PwmConstants.DEFAULT_DATETIME_FORMAT.format(ldapPasswordExpirationTime));
                ldapPasswordExpirationTime = null;
            }

            if (ldapPasswordExpirationTime != null) {
                TimeDuration expirationInterval = TimeDuration.fromCurrent(ldapPasswordExpirationTime);
                LOGGER.trace(sessionLabel, "read password expiration time: "
                                + PwmConstants.DEFAULT_DATETIME_FORMAT.format(ldapPasswordExpirationTime)
                                + ", " + expirationInterval.asCompactString() + " from now"
                );
                final long diff = ldapPasswordExpirationTime.getTime() - System.currentTimeMillis();

                // now check to see if the user's expire time is within the 'preExpireTime' setting.
                final long preExpireMs = config.readSettingAsLong(PwmSetting.PASSWORD_EXPIRE_PRE_TIME) * 1000;
                if (diff > 0 && diff < preExpireMs) {
                    LOGGER.debug(sessionLabel, "user " + userDN + " password will expire within " + TimeDuration.asCompactString(diff) + ", marking as pre-expired");
                    passwordStatus.setPreExpired(true);
                } else if (passwordStatus.isExpired()) {
                    LOGGER.debug(sessionLabel, "user " + userDN + " password is expired, marking as pre-expired.");
                    passwordStatus.setPreExpired(true);
                }

                // now check to see if the user's expire time is within the 'preWarnTime' setting.
                final long preWarnMs = config.readSettingAsLong(PwmSetting.PASSWORD_EXPIRE_WARN_TIME) * 1000;
                // don't check if the 'preWarnTime' setting is zero or less than the expirePreTime
                if (!passwordStatus.isExpired() && !passwordStatus.isPreExpired()) {
                    if (!(preWarnMs == 0 || preWarnMs < preExpireMs)) {
                        if (diff > 0 && diff < preWarnMs) {
                            LOGGER.debug(sessionLabel,
                                    "user " + userDN + " password will expire within " + TimeDuration.asCompactString(
                                            diff) + ", marking as within warn period");
                            passwordStatus.setWarnPeriod(true);
                        } else if (passwordStatus.isExpired()) {
                            LOGGER.debug(sessionLabel,
                                    "user " + userDN + " password is expired, marking as within warn period");
                            passwordStatus.setWarnPeriod(true);
                        }
                    }
                }
            }

        } catch (ChaiOperationException e) {
            LOGGER.info(sessionLabel, "error reading user attrs for " + userDN + " while reading passwordExpirationDate(): " + e.getMessage());
        }

        LOGGER.debug(sessionLabel, "completed user password status check for " + userDN + " " + passwordStatus + " (" + TimeDuration.fromCurrent(startTime).asCompactString() + ")");
        return passwordStatus;
    }

    public void populateActorUserInfoBean(
            final PwmSession pwmSession,
            final UserIdentity userIdentity
    )
            throws ChaiUnavailableException, PwmUnrecoverableException
    {
        final String userLdapProfile = userIdentity.getLdapProfileID();
        final ChaiProvider provider = pwmApplication.getProxyChaiProvider(userLdapProfile);
        final UserInfoBean uiBean = pwmSession.getUserInfoBean();
        final PasswordData currentPassword = pwmSession.getLoginInfoBean().getUserCurrentPassword();
        populateUserInfoBean(
                uiBean,
                pwmSession.getSessionStateBean().getLocale(),
                userIdentity,
                provider,
                currentPassword
        );
    }

    public UserInfoBean populateUserInfoBean(
            final Locale userLocale,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException
    {
        final ChaiProvider provider = pwmApplication.getProxyChaiProvider(userIdentity.getLdapProfileID());
        final UserInfoBean userInfoBean = new UserInfoBean();
        populateUserInfoBean(userInfoBean, userLocale, userIdentity, provider, null);
        return userInfoBean;
    }

    public void populateUserInfoBean(
            final UserInfoBean uiBean,
            final Locale userLocale,
            final UserIdentity userIdentity,
            final ChaiProvider provider
    )
            throws PwmUnrecoverableException
    {
        try {
            populateUserInfoBeanImpl(uiBean, userLocale, userIdentity, provider, null);
        } catch (ChaiUnavailableException e) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_DIRECTORY_UNAVAILABLE,e.getMessage()));
        }
    }

    public void populateUserInfoBean(
            final UserInfoBean uiBean,
            final Locale userLocale,
            final UserIdentity userIdentity,
            final ChaiProvider provider,
            final PasswordData currentPassword
    )
            throws PwmUnrecoverableException
    {
        try {
            populateUserInfoBeanImpl(uiBean, userLocale, userIdentity, provider, currentPassword);
        } catch (ChaiUnavailableException e) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_DIRECTORY_UNAVAILABLE,e.getMessage()));
        }
    }

    private void populateUserInfoBeanImpl(
            final UserInfoBean uiBean,
            final Locale userLocale,
            final UserIdentity userIdentity,
            final ChaiProvider provider,
            final PasswordData currentPassword
    )
            throws PwmUnrecoverableException, ChaiUnavailableException
    {
        final Configuration config = pwmApplication.getConfig();
        final long methodStartTime = System.currentTimeMillis();

        if (userIdentity == null || userIdentity.getUserDN() == null || userIdentity.getUserDN().length() < 1) {
            throw new NullPointerException("userDN can not be null");
        }

        //attempt to add the object class to the user
        LdapOperationsHelper.addConfiguredUserObjectClass(sessionLabel, userIdentity, pwmApplication);

        final ChaiUser theUser = ChaiFactory.createChaiUser(userIdentity.getUserDN(), provider);
        final UserDataReader userDataReader = new LdapUserDataReader(userIdentity, theUser);

        uiBean.setUserIdentity(userIdentity.canonicalized(pwmApplication));

        populateLocaleSpecificUserInfoBean(uiBean, userLocale);

        //populate OTP data
        if (config.readSettingAsBoolean(PwmSetting.OTP_ENABLED)){
            final OtpService otpService = pwmApplication.getOtpService();
            final OTPUserRecord otpUserRecord = otpService.readOTPUserConfiguration(sessionLabel,userIdentity);
            uiBean.setOtpUserRecord(otpUserRecord);
            uiBean.setRequiresOtpConfig(checkIfOtpUpdateNeeded(uiBean, otpUserRecord));
        }

        //populate cached password rule attributes
        try {
            final Set<String> interestingUserAttributes = figurePasswordRuleAttributes(uiBean);
            final Map<String, String> allUserAttrs = userDataReader.readStringAttributes(interestingUserAttributes);
            uiBean.setCachedPasswordRuleAttributes(allUserAttrs);
        } catch (ChaiOperationException e) {
            LOGGER.warn(sessionLabel, "error retrieving user cached password rule attributes " + e);
        }

        //populate cached attributes.
        {
            final List<String> cachedAttributeNames = config.readSettingAsStringArray(PwmSetting.CACHED_USER_ATTRIBUTES);
            if (cachedAttributeNames != null && !cachedAttributeNames.isEmpty()) {
                try {
                    final Map<String,String> attributeValues = userDataReader.readStringAttributes(cachedAttributeNames);
                    uiBean.setCachedAttributeValues(Collections.unmodifiableMap(attributeValues));
                } catch (ChaiOperationException e) {
                    LOGGER.warn(sessionLabel, "error retrieving user cache attributes: " + e);
                }
            }

        }

        {// set userID
            final String ldapProfileID = userIdentity.getLdapProfileID();
            final LdapProfile ldapProfile = config.getLdapProfiles().get(ldapProfileID);
            final String uIDattr = ldapProfile.getUsernameAttribute();
            try {
                uiBean.setUsername(userDataReader.readStringAttribute(uIDattr));
            } catch (ChaiOperationException e) {
                LOGGER.error(sessionLabel, "error reading userID attribute: " + e.getMessage());
            }
        }

        { // set guid
            final String userGuid = LdapOperationsHelper.readLdapGuidValue(pwmApplication, sessionLabel, userIdentity, false);
            uiBean.setUserGuid(userGuid);
        }

        { // set email address
            final String ldapEmailAttribute = config.readSettingAsString(PwmSetting.EMAIL_USER_MAIL_ATTRIBUTE);
            try {
                uiBean.setUserEmailAddress(userDataReader.readStringAttribute(ldapEmailAttribute));
            } catch (ChaiOperationException e) {
                LOGGER.error(sessionLabel, "error reading email address attribute: " + e.getMessage());
            }
        }

        { // set SMS number
            final String ldapSmsAttribute = config.readSettingAsString(PwmSetting.SMS_USER_PHONE_ATTRIBUTE);
            try {
                uiBean.setUserSmsNumber(userDataReader.readStringAttribute(ldapSmsAttribute));
            } catch (ChaiOperationException e) {
                LOGGER.error(sessionLabel, "error reading sms number attribute: " + e.getMessage());
            }
        }

        // read password expiration time
        try {
            Date ldapPasswordExpirationTime = theUser.readPasswordExpirationDate();
            if (ldapPasswordExpirationTime != null && ldapPasswordExpirationTime.getTime() < 0) {
                // If ldapPasswordExpirationTime is less than 0, this may indicate an extremely late date, past the epoch.
                ldapPasswordExpirationTime = null;
            }
            uiBean.setPasswordExpirationTime(ldapPasswordExpirationTime);
        } catch (Exception e) {
            LOGGER.warn(sessionLabel, "error reading password expiration time: " + e.getMessage());
        }

        // read password state
        uiBean.setPasswordState(readPasswordStatus(theUser, uiBean.getPasswordPolicy(), uiBean, currentPassword));

        // mark if new pw required
        uiBean.setRequiresNewPassword(checkIfNewPasswordRequired(userIdentity, uiBean.getPasswordState()));

        // check if responses need to be updated
        uiBean.setRequiresUpdateProfile(checkIfProfileUpdateNeeded(config, uiBean, userDataReader, userLocale));

        // fetch last password modification time;
        final Date pwdLastModifedDate = PasswordUtility.determinePwdLastModified(pwmApplication, sessionLabel, userIdentity);
        uiBean.setPasswordLastModifiedTime(pwdLastModifedDate);

        // read user last login time:
        try {
            uiBean.setLastLdapLoginTime(theUser.readLastLoginTime());
        } catch (ChaiOperationException e) {
            LOGGER.warn(sessionLabel, "error reading user's last ldap login time: " + e.getMessage());
        }

        try {
            uiBean.setAccountExpirationTime(theUser.readAccountExpirationDate());
        } catch (Exception e) {
            LOGGER.error(sessionLabel, "error reading account expired date for user '" + userIdentity + "', " + e.getMessage());
        }

        // read authenticated profiles
        for (final ProfileType profileType : ProfileType.values()) {
            if (profileType.isAuthenticated()) {
                final String profileID = ProfileUtility.discoverProfileIDforUser(pwmApplication, sessionLabel, userIdentity, profileType);
                uiBean.getProfileIDs().put(profileType, profileID);
                if (profileID != null) {
                    LOGGER.debug(sessionLabel, "assigned " + profileType.toString() + " profileID \"" + profileID + "\" to " + userIdentity.toDisplayString());
                } else {
                    LOGGER.debug(sessionLabel, profileType.toString() + " has no matching profiles for user " + userIdentity.toDisplayString());
                }
            }
        }

        // update report engine.
        if (!settings.isSkipReportUpdate()) {
            try {
                pwmApplication.getReportService().updateCachedRecordFromLdap(uiBean);
            } catch (LocalDBException e) {
                LOGGER.error(sessionLabel, "error updating report cache data ldap login time: " + e.getMessage());
            }
        }

        LOGGER.trace(sessionLabel, "populateUserInfoBean for " + userIdentity + " completed in " + TimeDuration.fromCurrent(methodStartTime).asCompactString());
    }

    public void populateLocaleSpecificUserInfoBean(
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
        uiBean.setPasswordPolicy(PasswordUtility.readPasswordPolicyForUser(pwmApplication, sessionLabel, uiBean.getUserIdentity(), theUser, userLocale));

        //populate c/r challenge set.
        {
            final CrService crService = pwmApplication.getCrService();
            final ResponseInfoBean responseInfoBean = crService.readUserResponseInfo(sessionLabel, uiBean.getUserIdentity(), theUser);
            final ChallengeProfile challengeProfile = crService.readUserChallengeProfile(
                    sessionLabel,
                    uiBean.getUserIdentity(),
                    theUser,
                    uiBean.getPasswordPolicy(),
                    userLocale
            );
            uiBean.setChallengeSet(challengeProfile);
            uiBean.setResponseInfoBean(responseInfoBean);
            uiBean.setRequiresResponseConfig(crService.checkIfResponseConfigNeeded(pwmApplication, sessionLabel, uiBean.getUserIdentity(), challengeProfile.getChallengeSet(), responseInfoBean));
        }

        LOGGER.trace(sessionLabel, "finished population of locale specific UserInfoBean in " + TimeDuration.fromCurrent(startTime).asCompactString());
    }

    private static Set<String> figurePasswordRuleAttributes(
            final UserInfoBean uiBean
    ) {
        final Set<String> interestingUserAttributes = new HashSet<>();
        interestingUserAttributes.addAll(uiBean.getPasswordPolicy().getRuleHelper().getDisallowedAttributes());
        if (uiBean.getPasswordPolicy().getRuleHelper().getADComplexityLevel() == ADPolicyComplexity.AD2003
                || uiBean.getPasswordPolicy().getRuleHelper().getADComplexityLevel() == ADPolicyComplexity.AD2008) {
            interestingUserAttributes.add("sAMAccountName");
            interestingUserAttributes.add("displayName");
            interestingUserAttributes.add("fullname");
            interestingUserAttributes.add("cn");
        }
        return interestingUserAttributes;
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
            return JsonUtil.deserialize(JsonUtil.serialize(this),this.getClass());
        }
    }

    public boolean checkIfProfileUpdateNeeded(
            final Configuration configuration,
            final UserInfoBean uiBean,
            final UserDataReader userDataReader,
            final Locale locale
    )
            throws ChaiUnavailableException, PwmUnrecoverableException
    {

        final UserIdentity userIdentity = uiBean.getUserIdentity();

        if (!pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.UPDATE_PROFILE_ENABLE)) {
            LOGGER.debug(sessionLabel, "checkProfiles: " + userIdentity.toString() + " profile module is not enabled");
            return false;
        }

        if (!pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.UPDATE_PROFILE_FORCE_SETUP)) {
            LOGGER.debug(sessionLabel, "checkProfiles: " + userIdentity.toString() + " profile force setup is not enabled");
            return false;
        }

        final List<UserPermission> updateProfilePermission = pwmApplication.getConfig().readSettingAsUserPermission(PwmSetting.UPDATE_PROFILE_QUERY_MATCH);
        if (!LdapPermissionTester.testUserPermissions(pwmApplication, sessionLabel, uiBean.getUserIdentity(),updateProfilePermission)) {
            LOGGER.debug(sessionLabel,
                    "checkProfiles: " + userIdentity.toString() + " is not eligible for checkProfile due to query match");
            return false;
        }

        final List<UserPermission> checkProfileQueryMatch = pwmApplication.getConfig().readSettingAsUserPermission(PwmSetting.UPDATE_PROFILE_CHECK_QUERY_MATCH);
        if (checkProfileQueryMatch != null && !checkProfileQueryMatch.isEmpty()) {
            if (LdapPermissionTester.testUserPermissions(pwmApplication, sessionLabel, userIdentity, checkProfileQueryMatch)) {
                LOGGER.info(sessionLabel, "checkProfiles: " + userIdentity.toString() + " matches 'checkProfiles query match', update profile will be required by user");
                return true;
            } else {
                LOGGER.info(sessionLabel, "checkProfiles: " + userIdentity.toString() + " does not match 'checkProfiles query match', update profile not required by user");
                return false;
            }
        } else {
            LOGGER.trace("no checkProfiles query match configured, will check to see if form attributes have values");
            final List<FormConfiguration> updateFormFields = pwmApplication.getConfig().readSettingAsForm(PwmSetting.UPDATE_PROFILE_FORM);

            // populate the map with attribute values from the uiBean, which was populated through ldap.
            final Map<FormConfiguration,String> formValues = new HashMap<>();
            for (final FormConfiguration formItem : updateFormFields) {
                try {
                    final String ldapValue = userDataReader.readStringAttribute(formItem.getName());
                    formValues.put(formItem, ldapValue);
                } catch (ChaiOperationException e) {
                    LOGGER.error(sessionLabel,"error reading attribute while executing checkProfile, attribute=" + formItem.getName() + ", error: " + e.getMessage());
                }
            }

            try {
                FormUtility.validateFormValues(configuration, formValues, locale);
                LOGGER.debug(sessionLabel, "checkProfile: " + userIdentity + " has value for attributes, update profile will not be required");
                return false;
            } catch (PwmDataValidationException e) {
                LOGGER.debug(sessionLabel, "checkProfile: " + userIdentity + " does not have good attributes (" + e.getMessage() + "), update profile will be required");
                return true;
            }
        }
    }

    public boolean checkIfOtpUpdateNeeded(
            final UserInfoBean uiBean,
            final OTPUserRecord otpUserRecord
    )
            throws ChaiUnavailableException, PwmUnrecoverableException
    {

        final UserIdentity userIdentity = uiBean.getUserIdentity();

        if (!pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.UPDATE_PROFILE_ENABLE)) {
            return false;
        }

        final boolean hasStoredOtp = otpUserRecord != null && otpUserRecord.getSecret() != null;

        if (hasStoredOtp) {
            return false;
        }

        final List<UserPermission> setupOtpPermission = pwmApplication.getConfig().readSettingAsUserPermission(PwmSetting.OTP_SETUP_USER_PERMISSION);
        if (!LdapPermissionTester.testUserPermissions(pwmApplication, sessionLabel, uiBean.getUserIdentity(), setupOtpPermission)) {
            LOGGER.debug(sessionLabel,
                    "checkOtp: " + userIdentity.toString() + " is not eligible for checkOtp due to query match");
            return false;
        }

        final ForceSetupPolicy policy = pwmApplication.getConfig().readSettingAsEnum(PwmSetting.OTP_FORCE_SETUP,ForceSetupPolicy.class);

        // hasStoredOtp is always true at this point, so if forced then update needed
        return policy == ForceSetupPolicy.FORCE || policy == ForceSetupPolicy.FORCE_ALLOW_SKIP;
    }

    public boolean checkIfNewPasswordRequired(
            final UserIdentity userIdentity,
            final PasswordStatus passwordStatus
    )
            throws ChaiUnavailableException, PwmUnrecoverableException
    {
        final List<UserPermission> updateProfilePermission = pwmApplication.getConfig().readSettingAsUserPermission(
                PwmSetting.QUERY_MATCH_CHANGE_PASSWORD);
        if (!LdapPermissionTester.testUserPermissions(pwmApplication, sessionLabel, userIdentity, updateProfilePermission)) {
            LOGGER.debug(sessionLabel,
                    "checkPassword: " + userIdentity.toString() + " user does not have permission to change password");
            return false;
        }

        if (passwordStatus.isExpired()) {
            LOGGER.debug(sessionLabel, "checkPassword: password is expired, marking new password as required");
            return true;
        }

        if (passwordStatus.isPreExpired()) {
            LOGGER.debug(sessionLabel, "checkPassword: password is pre-expired, marking new password as required");
            return true;
        }

        if (passwordStatus.isWarnPeriod()) {
            LOGGER.debug(sessionLabel, "checkPassword: password is within warn period, marking new password as required");
            return true;
        }

        if (passwordStatus.isViolatesPolicy()) {
            LOGGER.debug(sessionLabel, "checkPassword: current password violates password policy, marking new password as required");
            return true;
        }

        return false;
    }
}
