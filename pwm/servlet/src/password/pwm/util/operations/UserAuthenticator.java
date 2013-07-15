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
import password.pwm.event.AuditEvent;
import password.pwm.event.AuditRecord;
import password.pwm.util.*;
import password.pwm.util.stats.Statistic;
import password.pwm.util.stats.StatisticsManager;

import java.util.Collections;
import java.util.Date;

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

        final String userDN = resolveUsername(pwmApplication, pwmSession, username, context);

        intruderManager.check(username, userDN, pwmSession);

        boolean allowBindAsUser = true;
        try {
            testCredentials(userDN, password, pwmSession);
        } catch (PwmOperationalException e) {
            final boolean configAlwaysUseProxy = pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.AD_ALLOW_AUTH_REQUIRE_NEW_PWD);
            final boolean ldapVendorIsAD = pwmApplication.getProxyChaiProvider().getDirectoryVendor() == ChaiProvider.DIRECTORY_VENDOR.MICROSOFT_ACTIVE_DIRECTORY;
            if (PwmError.PASSWORD_NEW_PASSWORD_REQUIRED == e.getError() // handle stupid ad case where it denies bind with valid password
                    && ldapVendorIsAD
                    && configAlwaysUseProxy) {
                LOGGER.info("auth bind failed, but will allow login due to 'must change password on next login AD error', error: " + e.getErrorInformation().toDebugStr());
                allowBindAsUser = false;
            } else if (PwmError.PASSWORD_EXPIRED == e.getError() // handle ad case where password is expired
                    && ldapVendorIsAD
                    && configAlwaysUseProxy) {
                LOGGER.info("auth bind failed, but will allow login due to 'password expired AD error', error: " + e.getErrorInformation().toDebugStr());
                allowBindAsUser = false;
            } else {
                // auth failed, presumably due to wrong password.
                LOGGER.info(pwmSession, "login attempt for " + userDN + " failed: " + e.getErrorInformation().toDebugStr());
                statisticsManager.incrementValue(Statistic.AUTHENTICATION_FAILURES);
                intruderManager.mark(username, userDN, pwmSession);
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

        postAuthenticationSequence(pwmApplication, pwmSession, userDN, password, allowBindAsUser, methodStartTime);
        final UserInfoBean.AuthenticationType authenticationType = allowBindAsUser ? UserInfoBean.AuthenticationType.AUTHENTICATED : UserInfoBean.AuthenticationType.AUTH_BIND_INHIBIT;
        pwmSession.getUserInfoBean().setAuthenticationType(authenticationType);
        LOGGER.debug(pwmSession, "user authenticated with authentication type: " + authenticationType);
        if (PwmConstants.ENABLE_AUDIT_AUTHENTICATION_TYPE) {
            pwmApplication.getAuditManager().submitAuditRecord(new AuditRecord(
                    AuditEvent.AUTHENTICATE,
                    pwmSession.getUserInfoBean().getUserID(),
                    pwmSession.getUserInfoBean().getUserDN(),
                    new Date(),
                    authenticationType.toString(),
                    pwmSession.getUserInfoBean().getUserID(),
                    pwmSession.getUserInfoBean().getUserDN(),
                    pwmSession.getSessionStateBean().getSrcAddress(),
                    pwmSession.getSessionStateBean().getSrcHostname()
            ));
        }
    }

    public static void authUserWithUnknownPassword(
            final String username,
            final PwmSession pwmSession,
            final PwmApplication pwmApplication,
            final boolean secure,
            final UserInfoBean.AuthenticationType authenticationType
    )
            throws ChaiUnavailableException, ImpossiblePasswordPolicyException, PwmUnrecoverableException, PwmOperationalException
    {
        final String userDN = resolveUsername(pwmApplication, pwmSession, username, null);
        final ChaiUser theUser = ChaiFactory.createChaiUser(userDN, pwmApplication.getProxyChaiProvider());
        authUserWithUnknownPassword(theUser, pwmSession, pwmApplication, secure, authenticationType);
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
            final boolean secure,
            final UserInfoBean.AuthenticationType authenticationType
    )
            throws ChaiUnavailableException, ImpossiblePasswordPolicyException, PwmUnrecoverableException {
        LOGGER.trace(pwmSession, "beginning auth processes for user with unknown password");
        long startAuthenticationTimestamp = System.currentTimeMillis();

        if (theUser == null || theUser.getEntryDN() == null) {
            throw new NullPointerException("invalid user (null)");
        }

        // use chai (nmas) to retrieve user password
        if (pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.EDIRECTORY_READ_USER_PWD)) {
            String currentPass = null;
            try {
                final String readPassword = theUser.readPassword();
                if (readPassword != null && readPassword.length() > 0) {
                    currentPass = readPassword;
                    LOGGER.debug(pwmSession, "successfully retrieved user's current password from ldap, now conducting standard authentication");
                }
            } catch (Exception e) {
                LOGGER.debug(pwmSession, "unable to retrieve user password from ldap; " + e.getMessage());
            }

            // actually do the authentication since we have user pw.
            if (currentPass != null && currentPass.length() > 0) {
                try {
                    authenticateUser(theUser.getEntryDN(), currentPass, null, pwmSession, pwmApplication, secure);
                    return;
                } catch (PwmOperationalException e) {
                    final String errorStr = "unable to authenticate with admin retrieved password, check proxy rights, ldap logs, and ensure " + PwmSetting.LDAP_NAMING_ATTRIBUTE.getKey() + " setting is correct";
                    LOGGER.error(errorStr);
                    throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_BAD_SESSION_PASSWORD, errorStr));
                }
            }
        } else {
            LOGGER.trace(pwmSession, "skipping attempt to read user password, option disabled");
        }


        final boolean configAlwaysUseProxy = pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.AD_USE_PROXY_FOR_FORGOTTEN);

        // try setting a random password on the account to authenticate.
        if (!configAlwaysUseProxy && authenticationType == UserInfoBean.AuthenticationType.AUTH_FROM_FORGOTTEN ) {
            LOGGER.debug(pwmSession, "attempting to set temporary random password");
            String currentPass = null;
            try {
                final PwmPasswordPolicy passwordPolicy = PasswordUtility.readPasswordPolicyForUser(pwmApplication, pwmSession, theUser, pwmSession.getSessionStateBean().getLocale());
                pwmSession.getUserInfoBean().setPasswordPolicy(passwordPolicy);

                // createSharedHistoryManager random password for user
                currentPass = RandomPasswordGenerator.createRandomPassword(pwmSession, pwmApplication);

                // write the random password for the user.
                try {
                    theUser.setPassword(currentPass);
                    LOGGER.info(pwmSession, "user " + theUser.getEntryDN() + " password has been set to random value for pwm to use for user authentication");
                    // force a user password change.
                    pwmSession.getUserInfoBean().setRequiresNewPassword(true);
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

            // actually do the authentication since we have user pw.
            try {
                authenticateUser(theUser.getEntryDN(), currentPass, null, pwmSession, pwmApplication, secure);

                // close any outstanding ldap connections (since they cache the old password)
                pwmSession.getSessionManager().closeConnections();

                return;
            } catch (PwmOperationalException e) {
                final String errorStr = "unable to authenticate user with temporary password, check proxy rights, ldap logs, and ensure " + PwmSetting.LDAP_NAMING_ATTRIBUTE.getKey() + " setting is correct";
                LOGGER.error(errorStr);
                throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_BAD_SESSION_PASSWORD, errorStr));
            }
        }


        postAuthenticationSequence(pwmApplication, pwmSession, theUser.getEntryDN(), null, false, startAuthenticationTimestamp);

        pwmSession.getUserInfoBean().setAuthenticationType(authenticationType);
        LOGGER.debug(pwmSession,"user authenticated with authentication type: " + authenticationType);

        if (PwmConstants.ENABLE_AUDIT_AUTHENTICATION_TYPE) {
            pwmApplication.getAuditManager().submitAuditRecord(new AuditRecord(
                    AuditEvent.AUTHENTICATE,
                    pwmSession.getUserInfoBean().getUserID(),
                    pwmSession.getUserInfoBean().getUserDN(),
                    new Date(),
                    authenticationType.toString(),
                    pwmSession.getUserInfoBean().getUserID(),
                    pwmSession.getUserInfoBean().getUserDN(),
                    pwmSession.getSessionStateBean().getSrcAddress(),
                    pwmSession.getSessionStateBean().getSrcHostname()
            ));
        }
    }

    private static String resolveUsername(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final String username,
            final String context

    )
            throws ChaiUnavailableException, PwmUnrecoverableException, PwmOperationalException
    {
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
            pwmApplication.getStatisticsManager().incrementValue(Statistic.AUTHENTICATION_FAILURES);
            pwmApplication.getIntruderManager().mark(username, null, pwmSession);
            throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_WRONGPASSWORD,e.getErrorInformation().getDetailedErrorMsg(),e.getErrorInformation().getFieldValues()));
        }

        return userDN;
    }

    public static void testCredentials(
            final String userDN,
            final String password,
            final PwmSession pwmSession
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


    private static void postAuthenticationSequence(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final String userDN,
            final String userPassword,
            final boolean bindAsUser,
            final long startAuthenticationTimestamp
    )
            throws PwmUnrecoverableException, ChaiUnavailableException
    {
        final IntruderManager intruderManager = pwmApplication.getIntruderManager();
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();

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
        final UserInfoBean userInfoBean = pwmSession.getUserInfoBean();
        if (!bindAsUser) {
            UserStatusHelper.populateUserInfoBean(
                    pwmSession,
                    userInfoBean,
                    pwmApplication,
                    ssBean.getLocale(),
                    userDN,
                    userPassword,
                    pwmApplication.getProxyChaiProvider()
            );
        } else {
            UserStatusHelper.populateActorUserInfoBean(
                    pwmSession,
                    pwmApplication,
                    userDN,
                    userPassword);
        }

        //notify the intruder manager with a successful login
        intruderManager.clear(pwmSession.getUserInfoBean().getUserID(), userDN, pwmSession);

        //mark the auth time
        userInfoBean.setAuthTime(new Date());

    }
}
