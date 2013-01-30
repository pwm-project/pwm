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

package password.pwm.util.operations;

import com.novell.ldapchai.ChaiConstant;
import com.novell.ldapchai.ChaiFactory;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.impl.edir.entry.EdirEntries;
import com.novell.ldapchai.provider.ChaiProvider;
import password.pwm.PwmApplication;
import password.pwm.PwmPasswordPolicy;
import password.pwm.PwmSession;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.Configuration;
import password.pwm.config.PasswordStatus;
import password.pwm.config.PwmPasswordRule;
import password.pwm.config.PwmSetting;
import password.pwm.error.PwmDataValidationException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.servlet.CommandServlet;
import password.pwm.util.Helper;
import password.pwm.util.PwmLogger;
import password.pwm.util.PwmPasswordRuleValidator;
import password.pwm.util.TimeDuration;

import java.util.*;

public class UserStatusHelper {

    private static final PwmLogger LOGGER = PwmLogger.getLogger(UserStatusHelper.class);


    private UserStatusHelper() {
    }


    /**
     * Read password status values from directory
     *
     * @param pwmSession     users pwm Session
     * @param theUser        the user to check
     * @param passwordPolicy the password policy to use for checking if current password violates current policy
     * @return bean describing the status of the user's password
     * @throws com.novell.ldapchai.exception.ChaiUnavailableException
     *          of directory is unavailable
     */
    public static PasswordStatus readPasswordStatus(
            final PwmSession pwmSession,
            final String currentPassword,
            final PwmApplication pwmApplication,
            final ChaiUser theUser,
            final PwmPasswordPolicy passwordPolicy,
            final UserInfoBean userInfoBean
    )
            throws ChaiUnavailableException, PwmUnrecoverableException
    {
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
            final Date ldapPasswordExpirationTime = theUser.readPasswordExpirationDate();

            if (ldapPasswordExpirationTime != null) {
                final long diff = ldapPasswordExpirationTime.getTime() - System.currentTimeMillis();

                // now check to see if the user's expire time is within the 'preExpireTime' setting.
                final long preExpireMs = config.readSettingAsLong(PwmSetting.PASSWORD_EXPIRE_PRE_TIME) * 1000;
                if (diff < 0 && diff < preExpireMs) {
                    LOGGER.info(pwmSession, "user " + userDN + " password will expire within " + TimeDuration.asCompactString(diff) + ", marking as pre-expired");
                    returnState.setPreExpired(true);
                } else if (returnState.isExpired()) {
                    LOGGER.info(pwmSession, "user " + userDN + " password is expired, marking as pre-expired.");
                    returnState.setPreExpired(true);
                }

                // now check to see if the user's expire time is within the 'preWarnTime' setting.
                final long preWarnMs = config.readSettingAsLong(PwmSetting.PASSWORD_EXPIRE_WARN_TIME) * 1000;
                if (diff > 0 && diff < preWarnMs) {
                    LOGGER.info(pwmSession, "user " + userDN + " password will expire within " + TimeDuration.asCompactString(diff) + ", marking as within warn period");
                    returnState.setWarnPeriod(true);
                } else if (returnState.isExpired()) {
                    LOGGER.info(pwmSession, "user " + userDN + " password is expired, marking as within warn period");
                    returnState.setWarnPeriod(true);
                }
            }

        } catch (ChaiOperationException e) {
            LOGGER.info(pwmSession, "error reading user attrs for " + userDN + " while reading passwordExpirationDate(): " + e.getMessage());
        }

        LOGGER.debug(pwmSession, "completed user password status check for " + userDN + " " + returnState + " (" + TimeDuration.fromCurrent(startTime).asCompactString() + ")");
        return returnState;
    }


    public static void populateActorUserInfoBean(
            final PwmSession pwmSession,
            final PwmApplication pwmApplication,
            final String userDN,
            final String userCurrentPassword
    )
            throws ChaiUnavailableException, PwmUnrecoverableException {
        final ChaiProvider provider = pwmSession.getSessionManager().getChaiProvider();
        final UserInfoBean uiBean = pwmSession.getUserInfoBean();
        populateUserInfoBean(pwmSession, uiBean, pwmApplication, pwmSession.getSessionStateBean().getLocale(), userDN, userCurrentPassword, provider);
    }


    public static void populateUserInfoBean(
            final PwmSession pwmSession,
            final UserInfoBean uiBean,
            final PwmApplication pwmApplication,
            final Locale userLocale,
            final String userDN,
            final String userCurrentPassword,
            final ChaiProvider provider
    )
            throws ChaiUnavailableException, PwmUnrecoverableException
    {
        populateUserInfoBean(
                pwmSession,
                uiBean,
                pwmApplication,
                userLocale,
                userDN,
                userCurrentPassword,
                provider,
                Collections.<String>emptySet());
    }
     public static void populateUserInfoBean(
           final PwmSession pwmSession,
            final UserInfoBean uiBean,
            final PwmApplication pwmApplication,
            final Locale userLocale,
            final String userDN,
            final String userCurrentPassword,
            final ChaiProvider provider,
            final Set<String> additionalAttributes
    )
            throws ChaiUnavailableException, PwmUnrecoverableException
    {
        final Configuration config = pwmApplication.getConfig();
        final long methodStartTime = System.currentTimeMillis();

        if (userDN != null && userDN.length() < 1) {
            throw new NullPointerException("userDN can not be null");
        }

        //attempt to add the object class to the user
        Helper.addConfiguredUserObjectClass(userDN, pwmSession, pwmApplication);

        uiBean.setUserCurrentPassword(userCurrentPassword);

        final ChaiUser theUser = ChaiFactory.createChaiUser(userDN, provider);

        try {
            uiBean.setUserDN(theUser.readCanonicalDN());
        } catch (ChaiOperationException e) {
            LOGGER.warn("error reading canonical DN" + e);
            uiBean.setUserDN(userDN);
        }

        //populate password policy
        uiBean.setPasswordPolicy(PasswordUtility.readPasswordPolicyForUser(pwmApplication, pwmSession, theUser, userLocale));

        //populate c/r challenge set. 
        uiBean.setChallengeSet(CrUtility.readUserChallengeSet(pwmSession, config, theUser, uiBean.getPasswordPolicy(), userLocale));

        //populate all user attributes.
        try {
            final Set<String> interestingUserAttributes = figureInterestingAttributes(config,uiBean,additionalAttributes);
            final Map<String,String> allUserAttrs = theUser.readStringAttributes(interestingUserAttributes);
            uiBean.setAllUserAttributes(allUserAttrs);
        } catch (ChaiOperationException e) {
            LOGGER.warn("error retrieving user attributes " + e);
        }

        {// set userID
            final String configUsernameAttr = config.readSettingAsString(PwmSetting.LDAP_USERNAME_ATTRIBUTE);
            final String ldapNamingAttribute = config.readSettingAsString(PwmSetting.LDAP_NAMING_ATTRIBUTE);
            final String uIDattr = configUsernameAttr != null && configUsernameAttr.length() > 0 ? configUsernameAttr : ldapNamingAttribute;
            uiBean.setUserID(uiBean.getAllUserAttributes().get(uIDattr));
        }

        { // set guid
            final String userGuid = Helper.readLdapGuidValue(pwmApplication, userDN);
            uiBean.setUserGuid(userGuid);
        }

        { // set email address
            final String ldapEmailAttribute = config.readSettingAsString(PwmSetting.EMAIL_USER_MAIL_ATTRIBUTE);
            uiBean.setUserEmailAddress(uiBean.getAllUserAttributes().get(ldapEmailAttribute));
        }

        { // set SMS number
            final String ldapSmsAttribute = config.readSettingAsString(PwmSetting.SMS_USER_PHONE_ATTRIBUTE);
            uiBean.setUserSmsNumber(uiBean.getAllUserAttributes().get(ldapSmsAttribute));
        }

        // read  password state
        uiBean.setPasswordState(readPasswordStatus(pwmSession, userCurrentPassword, pwmApplication, theUser, uiBean.getPasswordPolicy(), uiBean));

        final String userPasswordExpireTime = uiBean.getAllUserAttributes().get(ChaiConstant.ATTR_LDAP_PASSWORD_EXPIRE_TIME);
        if (userPasswordExpireTime != null && userPasswordExpireTime.length() > 0) {
            uiBean.setPasswordExpirationTime(EdirEntries.convertZuluToDate(userPasswordExpireTime));
        } else {
            uiBean.setPasswordExpirationTime(null);
        }

        // read response state
        uiBean.setRequiresResponseConfig(CrUtility.checkIfResponseConfigNeeded(pwmSession, pwmApplication, theUser, uiBean.getChallengeSet()));

        // check if responses need to be updated
        uiBean.setRequiresUpdateProfile(CommandServlet.checkProfile(pwmSession, pwmApplication, uiBean));

        // fetch last password modification time;
        final Date pwdLastModifedDate = determinePwdLastModified(pwmSession, config, theUser);
        uiBean.setPasswordLastModifiedTime(pwdLastModifedDate);

        LOGGER.trace(pwmSession, "populateUserInfoBean for " + userDN + " completed in " + TimeDuration.fromCurrent(methodStartTime).asCompactString());
    }

    private static Set<String> figureInterestingAttributes(
            final Configuration config,
            final UserInfoBean uiBean,
            final Collection additionalReadAttributes
    )
    {
        final Set<String> interestingUserAttributes = new HashSet<String>(config.getAllUsedLdapAttributes());
        interestingUserAttributes.addAll(uiBean.getPasswordPolicy().getRuleHelper().getDisallowedAttributes());
        interestingUserAttributes.add(ChaiConstant.ATTR_LDAP_PASSWORD_EXPIRE_TIME);
        interestingUserAttributes.add(config.readSettingAsString(PwmSetting.LDAP_NAMING_ATTRIBUTE));
        interestingUserAttributes.add(config.readSettingAsString(PwmSetting.LDAP_USERNAME_ATTRIBUTE));
        interestingUserAttributes.add(config.readSettingAsString(PwmSetting.LDAP_GUID_ATTRIBUTE));
        if (uiBean.getPasswordPolicy().getRuleHelper().readBooleanValue(PwmPasswordRule.ADComplexity)) {
            interestingUserAttributes.add("sAMAccountName");
            interestingUserAttributes.add("displayName");
            interestingUserAttributes.add("fullname");
            interestingUserAttributes.add("cn");
        }
        if (additionalReadAttributes != null) {
            interestingUserAttributes.addAll(additionalReadAttributes);
        }
        return interestingUserAttributes;
    }

    public static Date determinePwdLastModified(
            final PwmSession pwmSession,
            final Configuration config,
            final ChaiUser theUser
    )
            throws ChaiUnavailableException
    {
        // fetch last password modification time from pwm last update attribute operation
        try {
            final Date chaiReadDate = theUser.readPasswordModificationDate();
            if (chaiReadDate != null) {
                LOGGER.trace(pwmSession, "read last user password change timestamp (via chai) as: " + chaiReadDate.toString());
                return chaiReadDate;
            }
        } catch (ChaiOperationException e) {
            LOGGER.error(pwmSession, "unexpected error reading password last modified timestamp: " + e.getMessage());
        }

        final String pwmLastSetAttr = config.readSettingAsString(PwmSetting.PASSWORD_LAST_UPDATE_ATTRIBUTE);
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
     * Update the user's "lastUpdated" attribute.  By default this is "pwmLastUpdate" attribute
     *
     * @param pwmSession to lookup session info
     * @param theUser    ldap user to operate on
     * @return true if successful;
     * @throws com.novell.ldapchai.exception.ChaiUnavailableException if the directory is unavailable
     */
    public static boolean updateLastUpdateAttribute(final PwmSession pwmSession, final PwmApplication pwmApplication, final ChaiUser theUser)
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


}
