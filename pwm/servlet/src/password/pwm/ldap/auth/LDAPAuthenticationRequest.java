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
import com.novell.ldapchai.ChaiFactory;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.*;
import com.novell.ldapchai.impl.oracleds.entry.OracleDSEntries;
import com.novell.ldapchai.provider.ChaiProvider;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.config.profile.LdapProfile;
import password.pwm.config.profile.PwmPasswordPolicy;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.event.AuditEvent;
import password.pwm.ldap.LdapOperationsHelper;
import password.pwm.util.PasswordData;
import password.pwm.util.RandomPasswordGenerator;
import password.pwm.util.TimeDuration;
import password.pwm.util.intruder.IntruderManager;
import password.pwm.util.intruder.RecordType;
import password.pwm.util.logging.PwmLogLevel;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.operations.PasswordUtility;
import password.pwm.util.stats.Statistic;
import password.pwm.util.stats.StatisticsManager;

import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

class LDAPAuthenticationRequest implements AuthenticationRequest {
    private static final PwmLogger LOGGER = PwmLogger.forClass(LDAPAuthenticationRequest.class);
    private static final String ORACLE_ATTR_PW_ALLOW_CHG_TIME = "passwordAllowChangeTime";

    private final PwmApplication pwmApplication;
    private final SessionLabel sessionLabel;
    private final UserIdentity userIdentity;
    private final AuthenticationType requestedAuthType;

    private ChaiProvider userProvider;
    private AuthenticationStrategy strategy = AuthenticationStrategy.BIND;
    private Date startTime;

    private static int counter = 0;
    private int operationNumber = 0;


    LDAPAuthenticationRequest(
            PwmApplication pwmApplication,
            SessionLabel sessionLabel,
            UserIdentity userIdentity,
            AuthenticationType requestedAuthType
    )
    {
        this.pwmApplication = pwmApplication;
        this.sessionLabel = sessionLabel;
        this.userIdentity = userIdentity;
        this.requestedAuthType = requestedAuthType;
        this.operationNumber = counter++;
    }

    @Override
    public AuthenticationResult authUsingUnknownPw()
            throws ChaiUnavailableException, PwmUnrecoverableException
    {
        initialize();

        log(PwmLogLevel.TRACE, "beginning authentication using unknown password procedure");

        PasswordData userPassword = null;
        final boolean configAlwaysUseProxy = pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.AD_USE_PROXY_FOR_FORGOTTEN);
        if (configAlwaysUseProxy) {
            strategy = AuthenticationStrategy.ADMIN_PROXY;
        } else {
            userPassword = learnUserPassword();
            if (userPassword != null) {
                strategy = AuthenticationStrategy.READ_THEN_BIND;
            } else {
                userPassword = setTempUserPassword();
                if (userPassword != null) {
                    strategy = AuthenticationStrategy.WRITE_THEN_BIND;
                }
            }
            if (userPassword == null) {
                throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_UNKNOWN,"no available unknown-pw authentication method"));
            }
        }

        try {
            return authenticateUserImpl(userPassword);
        } catch (PwmOperationalException e) {
            if (strategy == AuthenticationStrategy.READ_THEN_BIND) {
                final String errorStr = "unable to authenticate with password read from directory, check proxy rights, ldap logs; error: " + e.getMessage();
                throw new PwmUnrecoverableException(
                        new ErrorInformation(PwmError.ERROR_BAD_SESSION_PASSWORD, errorStr));
            } else if (strategy == AuthenticationStrategy.WRITE_THEN_BIND) {
                final String errorStr = "unable to authenticate with temporary password, check proxy rights, ldap logs; error: " + e.getMessage();
                throw new PwmUnrecoverableException(
                        new ErrorInformation(PwmError.ERROR_BAD_SESSION_PASSWORD, errorStr));
            }
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_UNKNOWN,"unable to authenticate via authWithUnknownPw method: " + e.getMessage()));
        }
    }

    @Override
    public AuthenticationResult authenticateUser(final PasswordData password)
            throws PwmUnrecoverableException, ChaiUnavailableException, PwmOperationalException
    {
        initialize();
        return authenticateUserImpl(password);
    }

    private AuthenticationResult authenticateUserImpl(
            final PasswordData password
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, PwmOperationalException
    {
        if (startTime == null) {
            startTime = new Date();
        }

        log(PwmLogLevel.DEBUG, "preparing to authenticate user using authenticationType=" + this.requestedAuthType + " using strategy " + this.strategy);

        final StatisticsManager statisticsManager = pwmApplication.getStatisticsManager();
        final IntruderManager intruderManager = pwmApplication.getIntruderManager();
        intruderManager.convenience().checkUserIdentity(userIdentity);
        intruderManager.check(RecordType.ADDRESS, sessionLabel.getSrcAddress());

        boolean allowBindAsUser = true;
        if (strategy == AuthenticationStrategy.ADMIN_PROXY) {
            allowBindAsUser = false;
        }

        if (allowBindAsUser) {
            try {
                testCredentials(userIdentity, password);
            } catch (PwmOperationalException e) {
                boolean permitAuthDespiteError = false;
                final ChaiProvider.DIRECTORY_VENDOR vendor = pwmApplication.getProxyChaiProvider(
                        userIdentity.getLdapProfileID()).getDirectoryVendor();
                if (PwmError.PASSWORD_NEW_PASSWORD_REQUIRED == e.getError()) {
                    if (vendor == ChaiProvider.DIRECTORY_VENDOR.MICROSOFT_ACTIVE_DIRECTORY) {
                        if (pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.AD_ALLOW_AUTH_REQUIRE_NEW_PWD)) {
                            log(PwmLogLevel.INFO,
                                    "auth bind failed, but will allow login due to 'must change password on next login AD error', error: " + e.getErrorInformation().toDebugStr());
                            allowBindAsUser = false;
                            permitAuthDespiteError = true;
                        }
                    } else if (vendor == ChaiProvider.DIRECTORY_VENDOR.ORACLE_DS) {
                        if (pwmApplication.getConfig().readSettingAsBoolean(
                                PwmSetting.ORACLE_DS_ALLOW_AUTH_REQUIRE_NEW_PWD)) {
                            log(PwmLogLevel.INFO,
                                    "auth bind failed, but will allow login due to 'pwdReset' user attribute, error: " + e.getErrorInformation().toDebugStr());
                            allowBindAsUser = false;
                            permitAuthDespiteError = true;
                        }
                    }
                } else if (PwmError.PASSWORD_EXPIRED == e.getError()) { // handle ad case where password is expired
                    if (vendor == ChaiProvider.DIRECTORY_VENDOR.MICROSOFT_ACTIVE_DIRECTORY) {
                        if (pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.AD_ALLOW_AUTH_REQUIRE_NEW_PWD)) {
                            if (!pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.AD_ALLOW_AUTH_EXPIRED)) {
                                throw e;
                            }
                            log(PwmLogLevel.INFO,
                                    "auth bind failed, but will allow login due to 'password expired AD error', error: " + e.getErrorInformation().toDebugStr());
                            allowBindAsUser = false;
                            permitAuthDespiteError = true;
                        }
                    }
                }

                if (!permitAuthDespiteError) {    // auth failed, presumably due to wrong password.
                    statisticsManager.incrementValue(Statistic.AUTHENTICATION_FAILURES);
                    throw e;
                }
            }
        } else {
            // verify user is not account disabled
            AuthenticationUtility.checkIfUserEligibleToAuthentication(pwmApplication, userIdentity);
        }

        statisticsManager.incrementValue(Statistic.AUTHENTICATIONS);
        statisticsManager.updateEps(Statistic.EpsType.AUTHENTICATION, 1);
        statisticsManager.updateAverageValue(Statistic.AVG_AUTHENTICATION_TIME,
                TimeDuration.fromCurrent(startTime).getTotalMilliseconds());

        final AuthenticationType returnAuthType;
        if (!allowBindAsUser) {
            returnAuthType = AuthenticationType.AUTH_BIND_INHIBIT;
        } else {
            if (requestedAuthType == null) {
                returnAuthType = AuthenticationType.AUTHENTICATED;
            } else {
                if (requestedAuthType == AuthenticationType.AUTH_WITHOUT_PASSWORD) {
                    returnAuthType = AuthenticationType.AUTHENTICATED;
                } else if (requestedAuthType == AuthenticationType.AUTH_FROM_PUBLIC_MODULE) {
                    returnAuthType =  AuthenticationType.AUTH_FROM_PUBLIC_MODULE;
                }  else {
                    returnAuthType = requestedAuthType;
                }
            }
        }

        final boolean useProxy = determineIfLdapProxyNeeded(returnAuthType, password);
        final ChaiProvider returnProvider = useProxy ? makeProxyProvider() : userProvider;
        final AuthenticationResult authenticationResult = new AuthenticationResult(returnProvider, returnAuthType, password);

        final StringBuilder debugMsg = new StringBuilder();
        debugMsg.append("successful ldap authentication for ").append(userIdentity);
        debugMsg.append(" (").append(TimeDuration.fromCurrent(startTime).asCompactString()).append(")");
        debugMsg.append(" type: ").append(returnAuthType).append(", using strategy ").append(strategy);
        debugMsg.append(", using proxy connection: ").append(useProxy);
        log(PwmLogLevel.INFO, debugMsg);
        pwmApplication.getAuditManager().submit(pwmApplication.getAuditManager().createUserAuditRecord(
                AuditEvent.AUTHENTICATE,
                this.userIdentity,
                returnAuthType.toString(),
                sessionLabel.getSrcAddress(),
                sessionLabel.getSrcHostname()
        ));

        return authenticationResult;
    }

    private void initialize() {
        if (startTime != null) {
            throw new IllegalStateException("AuthenticationRequest can not be used more than once");
        }
        startTime = new Date();
    }

    private void testCredentials(
            final UserIdentity userIdentity,
            final PasswordData password
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, PwmOperationalException
    {
        log(PwmLogLevel.TRACE, "beginning testCredentials process");

        if (userIdentity == null || userIdentity.getUserDN() == null || userIdentity.getUserDN().length() < 1) {
            final String errorMsg = "attempt to authenticate with null userDN";
            log(PwmLogLevel.DEBUG, errorMsg);
            throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_WRONGPASSWORD,errorMsg));
        }

        if (password == null) {
            final String errorMsg = "attempt to authenticate with null password";
            log(PwmLogLevel.DEBUG, errorMsg);
            throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_WRONGPASSWORD,errorMsg));
        }

        //try authenticating the user using a normal ldap BIND operation.
        log(PwmLogLevel.TRACE, "attempting authentication using ldap BIND");

        boolean bindSucceeded = false;
        try {
            //read a provider using the user's DN and password.
            userProvider = LdapOperationsHelper.createChaiProvider(
                    sessionLabel,
                    userIdentity.getLdapProfile(pwmApplication.getConfig()),
                    pwmApplication.getConfig(),
                    userIdentity.getUserDN(),
                    password
            );

            //issue a read operation to trigger a bind.
            userProvider.readStringAttribute(userIdentity.getUserDN(), ChaiConstant.ATTR_LDAP_OBJECTCLASS);

            bindSucceeded = true;
        } catch (ChaiException e) {
            if (e.getErrorCode() != null && e.getErrorCode() == ChaiError.INTRUDER_LOCKOUT) {
                final String errorMsg = "intruder lockout detected for user " + userIdentity + " marking session as locked out: " + e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_INTRUDER_LDAP, errorMsg);
                log(PwmLogLevel.WARN, errorInformation.toDebugStr());
                throw new PwmUnrecoverableException(errorInformation);
            }
            final PwmError pwmError = PwmError.forChaiError(e.getErrorCode());
            final ErrorInformation errorInformation;
            if (pwmError != null && PwmError.ERROR_UNKNOWN != pwmError) {
                errorInformation = new ErrorInformation(pwmError, e.getMessage());
            } else {
                errorInformation = new ErrorInformation(PwmError.ERROR_WRONGPASSWORD, "ldap error during password check: " + e.getMessage());
            }
            log(PwmLogLevel.DEBUG, errorInformation.toDebugStr());
            throw new PwmOperationalException(errorInformation);
        } finally {
            if (!bindSucceeded && userProvider != null){
                try {
                    userProvider.close();
                    userProvider = null;
                } catch (Throwable e) {
                    log(PwmLogLevel.ERROR, "unexpected error closing invalid ldap connection after failed login attempt: " + e.getMessage());
                }
            }
        }
    }


    private PasswordData learnUserPassword()
            throws ChaiUnavailableException,  PwmUnrecoverableException
    {
        log(PwmLogLevel.TRACE, "beginning auth processes for user with unknown password");

        if (userIdentity == null || userIdentity.getUserDN() == null || userIdentity.getUserDN().length() < 1) {
            throw new NullPointerException("invalid user (null)");
        }

        final ChaiProvider chaiProvider = pwmApplication.getProxyChaiProvider(userIdentity.getLdapProfileID());
        final ChaiUser chaiUser = ChaiFactory.createChaiUser(userIdentity.getUserDN(), chaiProvider);

        // use chai (nmas) to retrieve user password
        if (pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.EDIRECTORY_READ_USER_PWD)) {
            String currentPass = null;
            try {
                final String readPassword = chaiUser.readPassword();
                if (readPassword != null && readPassword.length() > 0) {
                    currentPass = readPassword;
                    log(PwmLogLevel.DEBUG, "successfully retrieved user's current password from ldap, now conducting standard authentication");
                }
            } catch (Exception e) {
                log(PwmLogLevel.ERROR, "unable to retrieve user password from ldap: " + e.getMessage());
            }

            // actually do the authentication since we have user pw.
            if (currentPass != null && currentPass.length() > 0) {
                return new PasswordData(currentPass);
            }
        } else {
            log(PwmLogLevel.TRACE, "skipping attempt to read user password, option disabled");
        }
        return null;
    }

    private PasswordData setTempUserPassword(
    )
            throws ChaiUnavailableException, ImpossiblePasswordPolicyException, PwmUnrecoverableException
    {

        final boolean configAlwaysUseProxy = pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.AD_USE_PROXY_FOR_FORGOTTEN);

        final ChaiProvider chaiProvider = pwmApplication.getProxyChaiProvider(userIdentity.getLdapProfileID());
        final ChaiUser chaiUser = ChaiFactory.createChaiUser(userIdentity.getUserDN(), chaiProvider);

        // try setting a random password on the account to authenticate.
        if (!configAlwaysUseProxy && requestedAuthType == AuthenticationType.AUTH_FROM_PUBLIC_MODULE) {
            log(PwmLogLevel.DEBUG, "attempting to set temporary random password");

            PwmPasswordPolicy passwordPolicy = PasswordUtility.readPasswordPolicyForUser(
                    pwmApplication,
                    sessionLabel,
                    userIdentity,
                    chaiUser,
                    PwmConstants.DEFAULT_LOCALE
            );

            // create random password for user
            RandomPasswordGenerator.RandomGeneratorConfig randomGeneratorConfig = new RandomPasswordGenerator.RandomGeneratorConfig();
            randomGeneratorConfig.setSeedlistPhrases(RandomPasswordGenerator.DEFAULT_SEED_PHRASES);
            randomGeneratorConfig.setPasswordPolicy(passwordPolicy);

            final PasswordData currentPass = RandomPasswordGenerator.createRandomPassword(sessionLabel, randomGeneratorConfig, pwmApplication);

            try {
                final String oracleDS_PrePasswordAllowChangeTime = oraclePreTemporaryPwHandler(chaiProvider,
                        chaiUser);

                // write the random password for the user.
                chaiUser.setPassword(currentPass.getStringValue());

                oraclePostTemporaryPwHandler(chaiProvider, chaiUser, oracleDS_PrePasswordAllowChangeTime);

                log(PwmLogLevel.INFO, "user " + userIdentity + " password has been set to random value to use for user authentication");
            } catch (ChaiOperationException e) {
                final String errorStr = "error setting random password for user " + userIdentity + " " + e.getMessage();
                log(PwmLogLevel.ERROR, errorStr);
                throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_BAD_SESSION_PASSWORD, errorStr));
            }

            return currentPass;
        }
        return null;
    }

    private String oraclePreTemporaryPwHandler(
            final ChaiProvider chaiProvider,
            final ChaiUser chaiUser
    )
            throws PwmUnrecoverableException, ChaiUnavailableException, ChaiOperationException
    {
        if (!pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.ORACLE_DS_ENABLE_MANIP_ALLOWCHANGETIME)) {
            return null;
        }

        if (ChaiProvider.DIRECTORY_VENDOR.ORACLE_DS != chaiUser.getChaiProvider().getDirectoryVendor()) {
            return null;
        }

        // oracle DS special case: passwordAllowChangeTime handler
        final String oracleDS_PrePasswordAllowChangeTime = chaiProvider.readStringAttribute(
                chaiUser.getEntryDN(),
                ORACLE_ATTR_PW_ALLOW_CHG_TIME);
        log(PwmLogLevel.TRACE,"read OracleDS value of passwordAllowChangeTime value=" + oracleDS_PrePasswordAllowChangeTime);

        if (oracleDS_PrePasswordAllowChangeTime != null && !oracleDS_PrePasswordAllowChangeTime.isEmpty()) {
            final Date date = OracleDSEntries.convertZuluToDate(oracleDS_PrePasswordAllowChangeTime);
            if (new Date().before(date)) {
                final String errorMsg = "change not permitted until " + PwmConstants.DEFAULT_DATETIME_FORMAT.format(
                        date);
                throw new PwmUnrecoverableException(
                        new ErrorInformation(PwmError.PASSWORD_TOO_SOON, errorMsg));
            }
        }

        return oracleDS_PrePasswordAllowChangeTime;
    }

    private void oraclePostTemporaryPwHandler(
            final ChaiProvider chaiProvider,
            final ChaiUser chaiUser,
            final String oracleDS_PrePasswordAllowChangeTime
    )
            throws ChaiUnavailableException, ChaiOperationException
    {
        if (!pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.ORACLE_DS_ENABLE_MANIP_ALLOWCHANGETIME)) {
            return;
        }

        // oracle DS special case: passwordAllowChangeTime handler
        if (ChaiProvider.DIRECTORY_VENDOR.ORACLE_DS != chaiUser.getChaiProvider().getDirectoryVendor()) {
            return;
        }

        if (oracleDS_PrePasswordAllowChangeTime != null && !oracleDS_PrePasswordAllowChangeTime.isEmpty()) {
            // write back the original pre-password allow change time.
            final Set<String> values = new HashSet<>(
                    Collections.singletonList(oracleDS_PrePasswordAllowChangeTime));
            chaiProvider.writeStringAttribute(chaiUser.getEntryDN(), ORACLE_ATTR_PW_ALLOW_CHG_TIME,
                    values,
                    true);
            log(PwmLogLevel.TRACE,"re-wrote passwordAllowChangeTime attribute to user " + chaiUser.getEntryDN() + ", value=" + oracleDS_PrePasswordAllowChangeTime);
        } else {
            final String oracleDS_PostPasswordAllowChangeTime = chaiProvider.readStringAttribute(
                    chaiUser.getEntryDN(),
                    ORACLE_ATTR_PW_ALLOW_CHG_TIME);
            if (oracleDS_PostPasswordAllowChangeTime != null && !oracleDS_PostPasswordAllowChangeTime.isEmpty()) {
                // password allow change time has appeared, but wasn't present previously, so delete it.
                log(PwmLogLevel.TRACE, "a new value for passwordAllowChangeTime attribute to user " + chaiUser.getEntryDN() + " has appeared, will remove");
                chaiProvider.deleteStringAttributeValue(chaiUser.getEntryDN(), ORACLE_ATTR_PW_ALLOW_CHG_TIME,
                        oracleDS_PostPasswordAllowChangeTime);
                log(PwmLogLevel.TRACE, "deleted attribute value for passwordAllowChangeTime attribute on user " + chaiUser.getEntryDN());
            }
        }
    }

    private boolean determineIfLdapProxyNeeded(final AuthenticationType authenticationType, final PasswordData userPassword)
            throws ChaiUnavailableException, PwmUnrecoverableException
    {
        if (userProvider != null) {
            return true;
        }

        final boolean authIsBindInhibit = authenticationType == AuthenticationType.AUTH_BIND_INHIBIT;
        final boolean authIsFromForgottenPw = authenticationType == AuthenticationType.AUTH_FROM_PUBLIC_MODULE;
        final boolean alwaysUseProxyIsEnabled = pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.AD_USE_PROXY_FOR_FORGOTTEN);
        final boolean passwordNotPresent = userPassword == null;

        return authIsBindInhibit || authIsFromForgottenPw && (alwaysUseProxyIsEnabled || passwordNotPresent);

    }

    private ChaiProvider makeProxyProvider()
            throws ChaiUnavailableException, PwmUnrecoverableException
    {
        final LdapProfile profile = pwmApplication.getConfig().getLdapProfiles().get(userIdentity.getLdapProfileID());
        final String proxyDN = profile.readSettingAsString(PwmSetting.LDAP_PROXY_USER_DN);
        final PasswordData proxyPassword = profile.readSettingAsPassword(PwmSetting.LDAP_PROXY_USER_PASSWORD);
        return LdapOperationsHelper.createChaiProvider(sessionLabel, profile, pwmApplication.getConfig(), proxyDN, proxyPassword);
    }

    private void log(final PwmLogLevel level, final CharSequence message) {
        LOGGER.log(level, sessionLabel,"authID=" + operationNumber + ", " + message);
    }
}
