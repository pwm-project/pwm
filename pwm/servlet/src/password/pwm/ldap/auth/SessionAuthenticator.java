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

package password.pwm.ldap.auth;

import com.novell.ldapchai.ChaiConstant;
import com.novell.ldapchai.exception.ChaiError;
import com.novell.ldapchai.exception.ChaiException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.exception.ImpossiblePasswordPolicyException;
import com.novell.ldapchai.provider.ChaiProvider;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.SessionStateBean;
import password.pwm.bean.UserIdentity;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.PwmSetting;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmSession;
import password.pwm.ldap.LdapOperationsHelper;
import password.pwm.ldap.UserSearchEngine;
import password.pwm.ldap.UserStatusReader;
import password.pwm.util.PasswordData;
import password.pwm.util.intruder.IntruderManager;
import password.pwm.util.intruder.RecordType;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.stats.Statistic;
import password.pwm.util.stats.StatisticsManager;

import java.util.Date;

public class SessionAuthenticator {
    private static final PwmLogger LOGGER = PwmLogger.getLogger(SessionAuthenticator.class.getName());

    private final PwmApplication pwmApplication;
    private final SessionLabel sessionLabel;
    private final PwmSession pwmSession;

    public SessionAuthenticator(
            PwmApplication pwmApplication,
            PwmSession pwmSession
    )
    {
        this.pwmApplication = pwmApplication;
        this.pwmSession = pwmSession;
        this.sessionLabel = pwmSession.getLabel();
    }

    public void searchAndAuthenticateUser(
            final String username,
            final PasswordData password,
            final String context,
            final String ldapProfile
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, PwmOperationalException
    {
        pwmApplication.getIntruderManager().check(RecordType.USERNAME, username);
        UserIdentity userIdentity = null;
        try {
            final UserSearchEngine userSearchEngine = new UserSearchEngine(pwmApplication, sessionLabel);
            userIdentity = userSearchEngine.resolveUsername(username, context, ldapProfile);

            AuthenticationRequest authEngine = AuthenticationUtility.createLDAPAuthenticationRequest(pwmApplication, sessionLabel, userIdentity, AuthenticationType.AUTHENTICATED);
            AuthenticationResult authResult = authEngine.authenticateUser(password);
            postAuthenticationSequence(userIdentity, authResult);
        } catch (PwmOperationalException e) {
            postFailureSequence(e, username, userIdentity);
            throw e;
        }

    }


    public void authenticateUser(
            final UserIdentity userIdentity,
            final PasswordData password
    )
            throws PwmUnrecoverableException, ChaiUnavailableException, PwmOperationalException
    {
        try {
            AuthenticationRequest authEngine = AuthenticationUtility.createLDAPAuthenticationRequest(pwmApplication, sessionLabel, userIdentity, AuthenticationType.AUTHENTICATED);
            AuthenticationResult authResult = authEngine.authenticateUser(password);
            postAuthenticationSequence(userIdentity, authResult);
        } catch (PwmOperationalException e) {
            postFailureSequence(e, null, userIdentity);
            throw e;
        }
    }


    public void authUserWithUnknownPassword(
            final String username,
            final AuthenticationType requestedAuthType
    )
            throws ChaiUnavailableException, ImpossiblePasswordPolicyException, PwmUnrecoverableException, PwmOperationalException
    {
        pwmApplication.getIntruderManager().check(RecordType.USERNAME, username);

        UserIdentity userIdentity = null;
        try {
            final UserSearchEngine userSearchEngine = new UserSearchEngine(pwmApplication, sessionLabel);
            userIdentity = userSearchEngine.resolveUsername(username, null, null);

            AuthenticationRequest authEngine = AuthenticationUtility.createLDAPAuthenticationRequest(pwmApplication, sessionLabel, userIdentity, requestedAuthType);
            AuthenticationResult authResult = authEngine.authUsingUnknownPw();
            postAuthenticationSequence(userIdentity, authResult);
        } catch (PwmOperationalException e) {
            postFailureSequence(e, username, userIdentity);
            throw e;
        }
    }

    public void authUserWithUnknownPassword(
            final UserIdentity userIdentity,
            final AuthenticationType requestedAuthType
    )
            throws PwmUnrecoverableException, ChaiUnavailableException
    {
        AuthenticationRequest authEngine = AuthenticationUtility.createLDAPAuthenticationRequest(pwmApplication, sessionLabel, userIdentity, requestedAuthType);
        AuthenticationResult authResult = authEngine.authUsingUnknownPw();
        postAuthenticationSequence(userIdentity, authResult);
    }



    public void simulateBadPassword(
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException
    {
        if (!pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.SECURITY_SIMULATE_LDAP_BAD_PASSWORD)) {
            return;
        } else {
            LOGGER.trace(sessionLabel, "performing bad-password login attempt against ldap directory as a result of forgotten password recovery invalid attempt against " + userIdentity);
        }

        if (userIdentity == null || userIdentity.getUserDN() == null || userIdentity.getUserDN().length() < 1) {
            LOGGER.error(sessionLabel, "attempt to simulateBadPassword with null userDN");
            return;
        }

        LOGGER.trace(sessionLabel, "beginning simulateBadPassword process");

        final PasswordData bogusPassword = new PasswordData(PwmConstants.DEFAULT_BAD_PASSWORD_ATTEMPT);

        //try authenticating the user using a normal ldap BIND operation.
        LOGGER.trace(sessionLabel, "attempting authentication using ldap BIND");

        ChaiProvider provider = null;
        try {
            //read a provider using the user's DN and password.

            provider = LdapOperationsHelper.createChaiProvider(
                    sessionLabel,
                    userIdentity.getLdapProfile(pwmApplication.getConfig()),
                    pwmApplication.getConfig(),
                    userIdentity.getUserDN(),
                    bogusPassword
            );

            //issue a read operation to trigger a bind.
            provider.readStringAttribute(userIdentity.getUserDN(), ChaiConstant.ATTR_LDAP_OBJECTCLASS);

            LOGGER.debug(sessionLabel, "bad-password login attempt succeeded for " + userIdentity);
        } catch (ChaiException e) {
            if (e.getErrorCode() == ChaiError.PASSWORD_BADPASSWORD) {
                LOGGER.trace(sessionLabel, "bad-password login simulation succeeded for; " + userIdentity + " result: " + e.getMessage());
            } else {
                LOGGER.debug(sessionLabel, "unexpected error during simulated bad-password login attempt for " + userIdentity + "; result: " + e.getMessage());
            }
        } finally {
            if (provider != null){
                try {
                    provider.close();
                } catch (Throwable e) {
                    LOGGER.error(sessionLabel,
                            "unexpected error closing invalid ldap connection after simulated bad-password failed login attempt: " + e.getMessage());
                }
            }
        }
    }

    private void postFailureSequence(
            final PwmOperationalException exception,
            final String username,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException
    {
        final IntruderManager intruderManager = pwmApplication.getIntruderManager();
        intruderManager.convenience().markAddressAndSession(pwmSession);
        if (username != null) {
            pwmApplication.getIntruderManager().mark(RecordType.USERNAME, username, pwmSession.getLabel());
        }
        if (userIdentity != null) {
            intruderManager.convenience().markUserIdentity(userIdentity, sessionLabel);
        }

    }

    private void postAuthenticationSequence(
            final UserIdentity userIdentity,
            final AuthenticationResult authenticationResult
    )
            throws PwmUnrecoverableException, ChaiUnavailableException
    {
        final IntruderManager intruderManager = pwmApplication.getIntruderManager();
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();

        // auth succeed
        ssBean.setAuthenticated(true);

        //update the session connection
        pwmSession.getSessionManager().setChaiProvider(authenticationResult.getUserProvider());

        // update the actor user info bean
        {
            final UserInfoBean userInfoBean = pwmSession.getUserInfoBean();
            final UserStatusReader userStatusReader = new UserStatusReader(pwmApplication, pwmSession.getLabel());

            if (authenticationResult.getAuthenticationType() == AuthenticationType.AUTH_BIND_INHIBIT) {
                userStatusReader.populateUserInfoBean(
                        userInfoBean,
                        ssBean.getLocale(),
                        userIdentity,
                        pwmApplication.getProxyChaiProvider(userIdentity.getLdapProfileID())
                );
            } else {
                userStatusReader.populateActorUserInfoBean(
                        pwmSession,
                        userIdentity
                );
            }
        }

        //mark the auth time
        pwmSession.getLoginInfoBean().setLocalAuthTime(new Date());

        //update the resulting authType
        pwmSession.getLoginInfoBean().setAuthenticationType(authenticationResult.getAuthenticationType());

        // save the password in the login bean
        final PasswordData userPassword = authenticationResult.getUserPassword();
        pwmSession.getLoginInfoBean().setUserCurrentPassword(userPassword);

        //notify the intruder manager with a successful login
        intruderManager.clear(RecordType.USERNAME, pwmSession.getUserInfoBean().getUsername());
        intruderManager.convenience().clearUserIdentity(userIdentity);
        intruderManager.convenience().clearAddressAndSession(pwmSession);

        if (pwmApplication.getStatisticsManager() != null) {
            final StatisticsManager statisticsManager = pwmApplication.getStatisticsManager();
            if (pwmSession.getUserInfoBean().getPasswordState().isWarnPeriod()) {
                statisticsManager.incrementValue(Statistic.AUTHENTICATION_EXPIRED_WARNING);
            } else if (pwmSession.getUserInfoBean().getPasswordState().isPreExpired()) {
                statisticsManager.incrementValue(Statistic.AUTHENTICATION_PRE_EXPIRED);
            } else if (pwmSession.getUserInfoBean().getPasswordState().isExpired()) {
                statisticsManager.incrementValue(Statistic.AUTHENTICATION_EXPIRED);
            }
        }

        //clear permission cache - needs rechecking after login
        LOGGER.debug(pwmSession,"clearing permission cache");
        pwmSession.getLoginInfoBean().clearPermissions();

    }
}
