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
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.*;
import com.novell.ldapchai.provider.ChaiProvider;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.PwmPasswordPolicy;
import password.pwm.PwmSession;
import password.pwm.bean.SessionStateBean;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.*;
import password.pwm.util.stats.Statistic;
import password.pwm.util.stats.StatisticsManager;

import java.util.Collections;

public class UserAuthenticator {
    private static final PwmLogger LOGGER = PwmLogger.getLogger(UserAuthenticator.class.getName());


    public static void authenticateUser(
            final String username,
            String password,
            final String context,
            final PwmSession pwmSession,
            final PwmApplication pwmApplication,
            final boolean secure
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, PwmOperationalException
    {
        final long methodStartTime = System.currentTimeMillis();
        final StatisticsManager statisticsManager = pwmApplication.getStatisticsManager();
        final IntruderManager intruderManager = pwmApplication.getIntruderManager();
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();

        final String userDN;
        try {
            final UserSearchEngine userSearchEngine = new UserSearchEngine(pwmApplication);

            //see if we need to a contextless search.
            if (userSearchEngine.checkIfStringIsDN(pwmSession, username)) {
                userDN = username;
            } else {
                final UserSearchEngine.SearchConfiguration searchConfiguration = new UserSearchEngine.SearchConfiguration();
                searchConfiguration.setUsername(username);
                searchConfiguration.setContexts(Collections.singletonList(context));
                final ChaiUser theUser = userSearchEngine.performSingleUserSearch(pwmSession, searchConfiguration);
                userDN = theUser.getEntryDN();
            }
        } catch (PwmOperationalException e) {
            intruderManager.addIntruderAttempt(username, pwmSession);
            intruderManager.checkUser(username, pwmSession);
            statisticsManager.incrementValue(Statistic.AUTHENTICATION_FAILURES);
            pwmApplication.getIntruderManager().delayPenalty(username, pwmSession);
            throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_WRONGPASSWORD,e.getErrorInformation().getDetailedErrorMsg(),e.getErrorInformation().getFieldValues()));
        }

        intruderManager.checkUser(userDN, pwmSession);
        intruderManager.checkAddress(pwmSession);

        try {
            testCredentials(userDN, password, pwmSession, pwmApplication);
        } catch (PwmOperationalException e) {
            if (PwmError.PASSWORD_NEW_PASSWORD_REQUIRED == e.getError() // handle stupid ad case where it denies bind with valid password
                    && pwmApplication.getProxyChaiProvider().getDirectoryVendor() == ChaiProvider.DIRECTORY_VENDOR.MICROSOFT_ACTIVE_DIRECTORY
                    && pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.AD_ALLOW_AUTH_REQUIRE_NEW_PWD)) {
                LOGGER.info("auth bind failed, but will allow login due to 'require new password AD error', error: " + e.getErrorInformation().toDebugStr());
                password = null;
            } else {
                // auth failed, presumably due to wrong password.
                ssBean.setAuthenticated(false);
                intruderManager.addIntruderAttempt(userDN, pwmSession);
                LOGGER.info(pwmSession, "login attempt for " + userDN + " failed: " + e.getErrorInformation().toDebugStr());
                statisticsManager.incrementValue(Statistic.AUTHENTICATION_FAILURES);
                pwmApplication.getIntruderManager().delayPenalty(userDN, pwmSession);
                throw e;
            }
        }

        final StringBuilder debugMsg = new StringBuilder();
        debugMsg.append("successful ");
        debugMsg.append(secure ? "ssl" : "plaintext");
        debugMsg.append(" authentication for ").append(userDN);
        debugMsg.append(" (").append(TimeDuration.fromCurrent(methodStartTime).asCompactString()).append(")");
        LOGGER.info(pwmSession, debugMsg);
        statisticsManager.incrementValue(Statistic.AUTHENTICATIONS);
        statisticsManager.updateEps(Statistic.EpsType.AUTHENTICATION, 1);

        postAuthenticationSequence(pwmApplication, pwmSession, userDN, password, methodStartTime);
    }

    public static void testCredentials(
            final String userDN,
            final String password,
            final PwmSession pwmSession,
            final PwmApplication pwmApplication
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, PwmOperationalException {
        LOGGER.trace(pwmSession, "beginning testCredentials process");

        if (userDN == null || userDN.length() < 1) {
            final String errorMsg = "attempt to authenticate with null userDN";
            LOGGER.debug(pwmSession, errorMsg);
            throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_WRONGPASSWORD,errorMsg));
        }

        if (password == null || password.length() < 1) {
            final String errorMsg = "attempt to authenticate with null password";
            LOGGER.debug(pwmSession, errorMsg);
            throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_WRONGPASSWORD,errorMsg));
        }

        //try authenticating the user using a normal ldap BIND operation.
        LOGGER.trace(pwmSession, "attempting authentication using ldap BIND");
        try {
            //get a provider using the user's DN and password.
            final ChaiProvider testProvider = pwmSession.getSessionManager().getChaiProvider(userDN, password);

            //issue a read operation to trigger a bind.
            testProvider.readStringAttribute(userDN, ChaiConstant.ATTR_LDAP_OBJECTCLASS);
        } catch (ChaiException e) {
            if (e.getErrorCode() != null && e.getErrorCode() == ChaiError.INTRUDER_LOCKOUT) {
                final String errorMsg = "intruder lockout detected for user " + userDN + " marking session as locked out: " + e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_INTRUDER_USER, errorMsg);
                LOGGER.warn(pwmSession, errorInformation.toDebugStr());
                pwmApplication.getIntruderManager().addIntruderAttempt(userDN, pwmSession);
                pwmSession.getSessionStateBean().setSessionError(errorInformation);
                throw new PwmUnrecoverableException(errorInformation);
            }
            final PwmError pwmError = PwmError.forChaiError(e.getErrorCode());
            final ErrorInformation errorInformation;
            if (pwmError != null && PwmError.ERROR_UNKNOWN != pwmError) {
                errorInformation = new ErrorInformation(pwmError, e.getMessage());
            } else {
                errorInformation = new ErrorInformation(PwmError.ERROR_WRONGPASSWORD, "ldap error during password check: " + e.getMessage());
            }
            LOGGER.debug(pwmSession, errorInformation.toDebugStr());
            throw new PwmOperationalException(errorInformation);
        }
    }

    /**
     * Caused by various modules, this method will cause the PWM session to become
     * authenticated without having the users password.  Depending on configuration
     * and nmas availability this may cause the users ldap password to be set to a random
     * value.  Typically the user would be redirectde to the change password servlet immediately
     * after this method is called.
     * <p/>
     * It is up to the caller to insure that any security requirements have been met BEFORE calling
     * this method, such as validiting challenge/responses.
     *
     * @param theUser    User to authenticate
     * @param pwmSession A PwmSession instance
     * @throws com.novell.ldapchai.exception.ChaiUnavailableException
     *          If ldap becomes unreachable
     * @throws password.pwm.error.PwmUnrecoverableException
     *          If there is some reason the session can't be authenticated
     *          If the user's password policy is determined to be impossible to satisfy
     * @throws com.novell.ldapchai.exception.ImpossiblePasswordPolicyException
     *          if the temporary password generated can't be due to an impossible policy
     */
    public static void authUserWithUnknownPassword(
            final ChaiUser theUser,
            final PwmSession pwmSession,
            final PwmApplication pwmApplication,
            final boolean secure
    )
            throws ChaiUnavailableException, ImpossiblePasswordPolicyException, PwmUnrecoverableException
    {
        LOGGER.trace(pwmSession, "beginning auth processes for user with unknown password");
        long startAuthenticationTimestamp = System.currentTimeMillis();

        if (theUser == null || theUser.getEntryDN() == null) {
            throw new NullPointerException("invalid user (null)");
        }

        // use chai (nmas) to retrieve user password
        String currentPass = null;
        if (pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.EDIRECTORY_READ_USER_PWD)) {
            try {
                final String readPassword = theUser.readPassword();
                if (readPassword != null && readPassword.length() > 0) {
                    currentPass = readPassword;
                    LOGGER.debug(pwmSession, "successfully retrieved user's current password from ldap");
                }
            } catch (Exception e) {
                LOGGER.debug(pwmSession, "unable to retrieve user password from ldap; " + e.getMessage());
            }
        } else {
            LOGGER.trace(pwmSession, "skipping attempt to read user password, option disabled");
        }

        boolean authWithoutUserPw = false;
        if (currentPass == null) {
            if (pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.AD_USE_PROXY_FOR_FORGOTTEN)) {
                if (pwmApplication.getProxyChaiProvider().getDirectoryVendor() == ChaiProvider.DIRECTORY_VENDOR.MICROSOFT_ACTIVE_DIRECTORY) {
                    authWithoutUserPw = true;
                }
            }
        }

        if (!authWithoutUserPw) {
            if (currentPass == null || currentPass.length() <= 0) {
                LOGGER.debug(pwmSession, "attempting to set temporary random password");
                try {
                    final PwmPasswordPolicy passwordPolicy = PasswordUtility.readPasswordPolicyForUser(pwmApplication, pwmSession, theUser, pwmSession.getSessionStateBean().getLocale());
                    pwmSession.getUserInfoBean().setPasswordPolicy(passwordPolicy);

                    // createSharedHistoryManager random password for user
                    currentPass = RandomPasswordGenerator.createRandomPassword(pwmSession, pwmApplication);

                    // write the random password for the user.
                    try {
                        theUser.setPassword(currentPass);
                        LOGGER.info(pwmSession, "user " + theUser.getEntryDN() + " password has been set to random value for pwm to use for user authentication");
                        Helper.pause(PwmConstants.PASSWORD_UPDATE_INITIAL_DELAY_MS);
                    } catch (ChaiPasswordPolicyException e) {
                        final String errorStr = "error setting random password for user " + theUser.getEntryDN() + " " + e.getMessage();
                        LOGGER.warn(pwmSession, errorStr);
                        throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_BAD_SESSION_PASSWORD, errorStr));
                    } catch (ChaiOperationException e) {
                        final String errorStr = "error setting random password for user " + theUser.getEntryDN() + " " + e.getMessage();
                        LOGGER.warn(pwmSession, errorStr);
                        throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_BAD_SESSION_PASSWORD, errorStr));
                    }
                } finally {
                    pwmSession.getUserInfoBean().setPasswordPolicy(PwmPasswordPolicy.defaultPolicy());
                }
            }

            // actually do the authentication since we have user pw.
            try {
                authenticateUser(theUser.getEntryDN(), currentPass, null, pwmSession, pwmApplication, secure);
            } catch (PwmOperationalException e) {
                final String errorStr = "unable to authenticate user with temporary or retrieved password, check proxy rights, ldap logs, and ensure " + PwmSetting.LDAP_NAMING_ATTRIBUTE.getKey() + " setting is correct";
                LOGGER.error(errorStr);
                throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_BAD_SESSION_PASSWORD, errorStr));
            }

            //close any outstanding ldap connections (since they cache the old password)
            pwmSession.getSessionManager().closeConnections();

        } else {
            postAuthenticationSequence(pwmApplication, pwmSession, theUser.getEntryDN(), null, startAuthenticationTimestamp);

            pwmSession.getUserInfoBean().setCurrentPasswordUnknownToPwm(true);
        }



        // get the uib out of the session again (it may have been replaced) and mark
        // the password as expired to force a user password change.
        pwmSession.getUserInfoBean().setRequiresNewPassword(true);

        // mark the uib as coming from unknown pw.
        pwmSession.getUserInfoBean().setCurrentPasswordUnknownToUser(true);
    }

    private static void postAuthenticationSequence(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final String userDN,
            final String userPassword,
            final long startAuthenticationTimestamp
    )
            throws PwmUnrecoverableException, ChaiUnavailableException
    {
        final IntruderManager intruderManager = pwmApplication.getIntruderManager();
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();

        //notify the intruder manager with a successfull login
        intruderManager.addGoodAddressAttempt(pwmSession);
        intruderManager.addGoodUserAttempt(userDN, pwmSession);

        if (pwmApplication.getStatisticsManager() != null) {
            final StatisticsManager statisticsManager = pwmApplication.getStatisticsManager();

            pwmApplication.getStatisticsManager().updateAverageValue(Statistic.AVG_AUTHENTICATION_TIME, TimeDuration.fromCurrent(startAuthenticationTimestamp).getTotalMilliseconds());

            if (pwmSession.getUserInfoBean().getPasswordState().isWarnPeriod()) {
                statisticsManager.incrementValue(Statistic.AUTHENTICATION_EXPIRED_WARNING);
            } else if (pwmSession.getUserInfoBean().getPasswordState().isPreExpired()) {
                statisticsManager.incrementValue(Statistic.AUTHENTICATION_PRE_EXPIRED);
            } else if (pwmSession.getUserInfoBean().getPasswordState().isExpired()) {
                statisticsManager.incrementValue(Statistic.AUTHENTICATION_EXPIRED);
            }
        }

        // auth succeed
        ssBean.setAuthenticated(true);

        // update the actor user info bean
        final boolean passwordIsUnknownToPwm = userPassword == null || userPassword.length() < 1;
        if (passwordIsUnknownToPwm) {
            final UserInfoBean userInfoBean = pwmSession.getUserInfoBean();
            UserStatusHelper.populateUserInfoBean(pwmSession, userInfoBean, pwmApplication, ssBean.getLocale(), userDN, null, pwmApplication.getProxyChaiProvider());
            userInfoBean.setCurrentPasswordUnknownToPwm(true);
        } else {
            UserStatusHelper.populateActorUserInfoBean(pwmSession, pwmApplication, userDN, userPassword);
        }

    }
}
