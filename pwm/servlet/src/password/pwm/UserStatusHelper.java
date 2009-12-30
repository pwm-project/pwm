/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
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

package password.pwm;

import com.novell.ldapchai.ChaiConstant;
import com.novell.ldapchai.ChaiFactory;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.cr.ChallengeSet;
import com.novell.ldapchai.cr.ResponseSet;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.impl.edir.entry.EdirEntries;
import com.novell.ldapchai.provider.ChaiProvider;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.*;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmException;
import password.pwm.error.ValidationException;
import password.pwm.util.PwmLogger;
import password.pwm.util.TimeDuration;

import java.util.Date;
import java.util.Properties;
import java.util.Set;

public class UserStatusHelper {

    private static final PwmLogger LOGGER = PwmLogger.getLogger(UserStatusHelper.class);


    private UserStatusHelper() {
    }


    /**
     * Read password status values from directory for the actor (must be authenticated) contained in the PwmSession
     *
     * @param pwmSession users pwm Session
     * @return bean describing the status of the user's password
     * @throws com.novell.ldapchai.exception.ChaiUnavailableException of directory is unavailable
     * @throws password.pwm.error.PwmException if there is an unexpected error during the check
     */
    public static PasswordStatus readPasswordStatus(
            final PwmSession pwmSession
    )
            throws ChaiUnavailableException, PwmException
    {
        final ChaiProvider provider = pwmSession.getSessionManager().getChaiProvider();
        final ChaiUser theUser = ChaiFactory.createChaiUser(pwmSession.getUserInfoBean().getUserDN(), provider);
        final PwmPasswordPolicy passwordPolicy = pwmSession.getUserInfoBean().getPasswordPolicy();

        return readPasswordStatus(pwmSession, theUser, passwordPolicy);
    }

    /**
     * Read password status values from directory
     *
     * @param pwmSession users pwm Session
     * @param theUser the user to check
     * @param passwordPolicy the password policy to use for checking if current password violates current policy
     * @return bean describing the status of the user's password
     * @throws com.novell.ldapchai.exception.ChaiUnavailableException of directory is unavailable
     */
    public static PasswordStatus readPasswordStatus(
            final PwmSession pwmSession,
            final ChaiUser theUser,
            final PwmPasswordPolicy passwordPolicy
    )
            throws ChaiUnavailableException
    {
        final PasswordStatus returnState = new PasswordStatus();
        final Configuration config = pwmSession.getConfig();
        final String userDN = theUser.getEntryDN();

        final long startTime = System.currentTimeMillis();
        LOGGER.trace(pwmSession, "beginning password status check process");

        // check if password meets existing policy.
        if (pwmSession.getUserInfoBean().getPasswordPolicy().getRuleHelper().readBooleanValue(PwmPasswordRule.EnforceAtLogin)) {
            final String password = pwmSession.getUserInfoBean().getUserCurrentPassword();
            if (password != null && password.length() > 0) {
                try {
                    Validator.testPasswordAgainstPolicy(password, pwmSession, false, passwordPolicy);
                } catch (ValidationException e) {
                    LOGGER.info(pwmSession, "user " + userDN + " password does not conform to current password policy (" + e.getMessage() + "), marking as requiring change.");
                    returnState.setViolatesPolicy(true);
                } catch (PwmException e) {
                    LOGGER.info(pwmSession, "user " + userDN + " password does not conform to current password policy (" + e.getMessage() + "), marking as requiring change.");
                    returnState.setViolatesPolicy(true);
                }
            }
        }

        try {
            final boolean ldapPasswordExpired = theUser.isPasswordExpired();

            if (ldapPasswordExpired) {
                returnState.setExpired(true);
                LOGGER.trace(pwmSession,"password appears to be expired");
            } else {
                LOGGER.trace(pwmSession,"password does not appear to be expired");
            }
        } catch (ChaiOperationException e) {
            LOGGER.info(pwmSession, "error reading user attrs for " + userDN + " while reading isPasswordExpired(): " + e.getMessage());
        }

        try {
            final Date ldapPasswordExpirationTime = theUser.readPasswordExpirationDate();

            if (ldapPasswordExpirationTime != null) {
                final long diff = ldapPasswordExpirationTime.getTime() - System.currentTimeMillis();

                // now check to see if the user's expire time is within the 'preExpireTime' setting.
                final int preExpireMs = config.readSettingAsInt(PwmSetting.PASSWORD_EXPIRE_PRE_TIME) * 1000;
                if (diff < preExpireMs) {
                    LOGGER.info(pwmSession, "user " + userDN + " password will expire within " + TimeDuration.asCompactString(diff) + ", marking as expired");
                    returnState.setPreExpired(true);
                }

                // now check to see if the user's expire time is within the 'preWarnTime' setting.
                final int preWarnMs = config.readSettingAsInt(PwmSetting.PASSWORD_EXPIRE_WARN_TIME) * 1000;
                if (diff < preWarnMs) {
                    LOGGER.info(pwmSession, "user " + userDN + " password will expire within " + TimeDuration.asCompactString(diff) + ", marking as warn");
                    returnState.setWarnPeriod(true);
                }
            }

        } catch (ChaiOperationException e) {
            LOGGER.info(pwmSession, "error reading user attrs for " + userDN + " while reading passwordExpirationDate(): " + e.getMessage());
        }

        LOGGER.debug(pwmSession,"completed user password status check; result: " + pwmSession + " (" + TimeDuration.fromCurrent(startTime).asCompactString() + ")");
        return returnState;
    }

    public static boolean checkIfResponseConfigNeeded(final PwmSession pwmSession)
            throws ChaiUnavailableException, PwmException
    {
        if (!pwmSession.getSessionStateBean().isAuthenticated()) {
            throw PwmException.createPwmException(new ErrorInformation(Message.ERROR_AUTHENTICATION_REQUIRED,"user must be authenticated to check if response configuration is needed"));
        }

        LOGGER.trace(pwmSession,"beginning check to determine if responses need to be configured for user");

        final String userDN = pwmSession.getUserInfoBean().getUserDN();

        if (!Helper.testUserMatchQueryString(pwmSession, userDN, pwmSession.getConfig().readSettingAsString(PwmSetting.QUERY_MATCH_CHECK_RESPONSES))) {
            LOGGER.debug(pwmSession, "checkIfResponseConfigNeeded: " + userDN + " is not eligible for checkIfResponseConfigNeeded due to query match");
            return false;
        }

        // read the assigned challenge set from memory
        final ChallengeSet challengeSet = pwmSession.getUserInfoBean().getChallengeSet();

        // check to be sure there are actually challenges in the challenge set
        if (challengeSet.getChallenges().isEmpty()) {
            LOGGER.debug(pwmSession, "checkIfResponseConfigNeeded: no challenge sets configured for user " + userDN);
            return false;
        }

        // this checking is performed using the proxy user
        final ChaiProvider provider = pwmSession.getSessionManager().getChaiProvider();
        final ChaiUser actor = ChaiFactory.createChaiUser(pwmSession.getUserInfoBean().getUserDN(), provider);

        // read the user's response
        final ResponseSet usersResponses = PasswordUtility.readUserResponseSet(pwmSession, actor);

        try {
            // check if responses exist
            if (usersResponses == null) {
                throw new Exception("no responses configured");
            }

            // check if responses meet the challenge set policy for the user
            usersResponses.meetsChallengeSetRequirements(challengeSet);

            LOGGER.debug(pwmSession, "checkIfResponseConfigNeeded: " + userDN + " has good responses");
            return false;
        } catch (Exception e) {
            LOGGER.debug(pwmSession, "checkIfResponseConfigNeeded: " + userDN + " does not have good responses: " + e.getMessage());
            return true;
        }
    }

    public static void populateUserInfoBean(
            final PwmSession pwmSession,
            final String userDN,
            final String userCurrentPassword
    )
            throws ChaiUnavailableException, PwmException
    {
        final long methodStartTime = System.currentTimeMillis();

        if (userDN != null && userDN.length() < 1) {
            throw new NullPointerException("userDN can not be null");
        }

        if (userCurrentPassword != null && userCurrentPassword.length() < 1) {
            throw new NullPointerException("userCurrentPassword can not be null");
        }

        final UserInfoBean uiBean = pwmSession.getUserInfoBean();

        uiBean.setUserDN(userDN);
        uiBean.setUserCurrentPassword(userCurrentPassword);

        final ChaiProvider provider = pwmSession.getSessionManager().getChaiProvider();

        final ChaiUser theUser = ChaiFactory.createChaiUser(userDN, provider);

        //populate password policy
        uiBean.setPasswordPolicy(PwmPasswordPolicy.createPwmPasswordPolicy(pwmSession, theUser));

        //populate c/r challenge set.
        uiBean.setChallengeSet(PasswordUtility.readUserChallengeSet(pwmSession, theUser, uiBean.getPasswordPolicy(),pwmSession.getSessionStateBean().getLocale()));

        //populate all user attributes.
        try {
            final Set<String> interestingUserAttributes = pwmSession.getConfig().getAllUsedLdapAttributes();
            interestingUserAttributes.addAll(uiBean.getPasswordPolicy().getRuleHelper().getDisallowedAttributes());
            interestingUserAttributes.add(ChaiConstant.ATTR_LDAP_PASSWORD_EXPIRE_TIME);
            interestingUserAttributes.add(pwmSession.getContextManager().getParameter(Constants.CONTEXT_PARAM.LDAP_NAMING_ATTRIBUTE));
            if (uiBean.getPasswordPolicy().getRuleHelper().readBooleanValue(PwmPasswordRule.ADComplexity)) {
                interestingUserAttributes.add("cn");
                interestingUserAttributes.add("displayName");
                interestingUserAttributes.add("fullname");
            }
            final Properties allUserAttrs = theUser.readStringAttributes(null);
            uiBean.setAllUserAttributes(allUserAttrs);
            uiBean.setUserID(allUserAttrs.getProperty(pwmSession.getContextManager().getParameter(Constants.CONTEXT_PARAM.LDAP_NAMING_ATTRIBUTE)));
        } catch (ChaiOperationException e) {
            LOGGER.warn("error retreiving user attributes " + e);
        }

        // write password state
        pwmSession.getUserInfoBean().setPasswordState(readPasswordStatus(pwmSession));

        final String userPasswordExpireTime = uiBean.getAllUserAttributes().getProperty(ChaiConstant.ATTR_LDAP_PASSWORD_EXPIRE_TIME, "");
        if (userPasswordExpireTime.length() > 0)  {
            uiBean.setPasswordExpirationTime(EdirEntries.convertZuluToDate(userPasswordExpireTime));
        } else {
            uiBean.setPasswordExpirationTime(null);
        }

        // write response state
        uiBean.setRequiresResponseConfig(checkIfResponseConfigNeeded(pwmSession));

        LOGGER.trace(pwmSession, "populateUserInfoBean completed in " + TimeDuration.fromCurrent(methodStartTime).asCompactString());
    }
}
