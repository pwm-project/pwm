/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2019 The PWM Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package password.pwm.ldap.auth;

import com.google.gson.reflect.TypeToken;
import com.novell.ldapchai.ChaiConstant;
import com.novell.ldapchai.exception.ChaiError;
import com.novell.ldapchai.exception.ChaiException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.exception.ImpossiblePasswordPolicyException;
import com.novell.ldapchai.provider.ChaiProvider;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.LocalSessionStateBean;
import password.pwm.bean.LoginInfoBean;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.CommonValues;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmSession;
import password.pwm.ldap.LdapOperationsHelper;
import password.pwm.ldap.UserInfo;
import password.pwm.ldap.UserInfoFactory;
import password.pwm.ldap.search.UserSearchEngine;
import password.pwm.svc.intruder.IntruderManager;
import password.pwm.svc.intruder.RecordType;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsManager;
import password.pwm.util.PasswordData;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SessionAuthenticator
{
    private static final PwmLogger LOGGER = PwmLogger.getLogger( SessionAuthenticator.class.getName() );

    private final PwmApplication pwmApplication;
    private final SessionLabel sessionLabel;
    private final PwmRequest pwmRequest;
    private final PwmAuthenticationSource authenticationSource;

    public SessionAuthenticator(
            final PwmApplication pwmApplication,
            final PwmRequest pwmRequest,
            final PwmAuthenticationSource authenticationSource
    )
    {
        this.pwmApplication = pwmApplication;
        this.pwmRequest = pwmRequest;
        this.sessionLabel = pwmRequest.getLabel();
        this.authenticationSource = authenticationSource;
    }

    public void searchAndAuthenticateUser(
            final String username,
            final PasswordData password,
            final String context,
            final String ldapProfile
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, PwmOperationalException
    {
        pwmApplication.getIntruderManager().check( RecordType.USERNAME, username );
        UserIdentity userIdentity = null;
        try
        {
            final UserSearchEngine userSearchEngine = pwmApplication.getUserSearchEngine();
            userIdentity = userSearchEngine.resolveUsername( username, context, ldapProfile, sessionLabel );

            final AuthenticationRequest authEngine = LDAPAuthenticationRequest.createLDAPAuthenticationRequest(
                    pwmApplication,
                    sessionLabel,
                    userIdentity,
                    AuthenticationType.AUTHENTICATED,
                    authenticationSource
            );
            final AuthenticationResult authResult = authEngine.authenticateUser( password );
            postAuthenticationSequence( userIdentity, authResult );
        }
        catch ( final PwmOperationalException e )
        {
            postFailureSequence( e, username, userIdentity );

            if ( readHiddenErrorTypes().contains( e.getError() ) )
            {
                if ( pwmApplication.determineIfDetailErrorMsgShown() )
                {
                    LOGGER.debug( pwmRequest, () -> "allowing error " + e.getError() + " to be returned though it is configured as a hidden type; "
                            + "app is currently permitting detailed error messages" );
                }
                else
                {
                    final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_WRONGPASSWORD );
                    LOGGER.debug( pwmRequest, () -> "converting error from ldap " + e.getError() + " to " + PwmError.ERROR_WRONGPASSWORD
                            + " due to app property " + AppProperty.SECURITY_LOGIN_HIDDEN_ERROR_TYPES.getKey() );
                    throw new PwmOperationalException( errorInformation );
                }
            }

            throw e;
        }
    }

    private Set<PwmError> readHiddenErrorTypes( )
    {
        final String appProperty = pwmApplication.getConfig().readAppProperty( AppProperty.SECURITY_LOGIN_HIDDEN_ERROR_TYPES );
        final Set<PwmError> returnSet = new HashSet<>();
        if ( !StringUtil.isEmpty( appProperty ) )
        {
            try
            {
                final List<Integer> configuredNumbers = JsonUtil.deserialize( appProperty, new TypeToken<List<Integer>>()
                {
                } );
                for ( final Integer errorCode : configuredNumbers )
                {
                    final PwmError pwmError = PwmError.forErrorNumber( errorCode );
                    returnSet.add( pwmError );
                }
            }
            catch ( final Exception e )
            {
                LOGGER.error( pwmRequest, () -> "error parsing app property " + AppProperty.SECURITY_LOGIN_HIDDEN_ERROR_TYPES.getKey()
                        + ", error: " + e.getMessage() );
            }
        }
        return returnSet;
    }


    public void authenticateUser(
            final UserIdentity userIdentity,
            final PasswordData password
    )
            throws PwmUnrecoverableException, PwmOperationalException
    {
        try
        {
            final AuthenticationRequest authEngine = LDAPAuthenticationRequest.createLDAPAuthenticationRequest(
                    pwmApplication,
                    sessionLabel,
                    userIdentity,
                    AuthenticationType.AUTHENTICATED,
                    authenticationSource
            );
            final AuthenticationResult authResult = authEngine.authenticateUser( password );
            postAuthenticationSequence( userIdentity, authResult );
        }
        catch ( final ChaiUnavailableException e )
        {
            throw PwmUnrecoverableException.fromChaiException( e );
        }
        catch ( final PwmOperationalException e )
        {
            postFailureSequence( e, null, userIdentity );
            throw e;
        }
    }


    public void authUserWithUnknownPassword(
            final String username,
            final AuthenticationType requestedAuthType
    )
            throws ImpossiblePasswordPolicyException, PwmUnrecoverableException, PwmOperationalException
    {
        pwmApplication.getIntruderManager().check( RecordType.USERNAME, username );

        UserIdentity userIdentity = null;
        try
        {
            final UserSearchEngine userSearchEngine = pwmApplication.getUserSearchEngine();
            userIdentity = userSearchEngine.resolveUsername( username, null, null, sessionLabel );

            final AuthenticationRequest authEngine = LDAPAuthenticationRequest.createLDAPAuthenticationRequest(
                    pwmApplication,
                    sessionLabel,
                    userIdentity,
                    requestedAuthType,
                    authenticationSource
            );
            final AuthenticationResult authResult = authEngine.authUsingUnknownPw();
            postAuthenticationSequence( userIdentity, authResult );
        }
        catch ( final ChaiUnavailableException e )
        {
            throw PwmUnrecoverableException.fromChaiException( e );
        }
        catch ( final PwmOperationalException e )
        {
            postFailureSequence( e, username, userIdentity );
            throw e;
        }
    }

    public void authUserWithUnknownPassword(
            final UserIdentity userIdentity,
            final AuthenticationType requestedAuthType
    )
            throws PwmUnrecoverableException
    {
        try
        {
            final AuthenticationRequest authEngine = LDAPAuthenticationRequest.createLDAPAuthenticationRequest(
                    pwmApplication,
                    sessionLabel,
                    userIdentity,
                    requestedAuthType,
                    authenticationSource
            );
            final AuthenticationResult authResult = authEngine.authUsingUnknownPw();
            postAuthenticationSequence( userIdentity, authResult );
        }
        catch ( final ChaiUnavailableException e )
        {
            throw PwmUnrecoverableException.fromChaiException( e );
        }
    }

    public void simulateBadPassword(
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException
    {
        final CommonValues commonValues = new CommonValues( pwmApplication, sessionLabel, null, null );
        simulateBadPassword( commonValues, userIdentity );
    }

    public static void simulateBadPassword( final CommonValues commonValues, final UserIdentity userIdentity ) throws PwmUnrecoverableException
    {
        final PwmApplication pwmApplication = commonValues.getPwmApplication();
        final SessionLabel sessionLabel = commonValues.getSessionLabel();

        if ( !pwmApplication.getConfig().readSettingAsBoolean( PwmSetting.SECURITY_SIMULATE_LDAP_BAD_PASSWORD ) )
        {
            return;
        }
        else
        {
            LOGGER.trace( sessionLabel, () -> "performing bad-password login attempt against ldap directory as a result of "
                    + "forgotten password recovery invalid attempt against " + userIdentity );
        }

        if ( userIdentity == null || userIdentity.getUserDN() == null || userIdentity.getUserDN().length() < 1 )
        {
            LOGGER.error( sessionLabel, () -> "attempt to simulateBadPassword with null userDN" );
            return;
        }

        LOGGER.trace( sessionLabel, () -> "beginning simulateBadPassword process" );

        final PasswordData bogusPassword = new PasswordData( PwmConstants.DEFAULT_BAD_PASSWORD_ATTEMPT );

        //try authenticating the user using a normal ldap BIND operation.
        LOGGER.trace( sessionLabel, () -> "attempting authentication using ldap BIND" );

        ChaiProvider provider = null;
        try
        {
            //read a provider using the user's DN and password.

            provider = LdapOperationsHelper.createChaiProvider(
                    pwmApplication,
                    sessionLabel,
                    userIdentity.getLdapProfile( pwmApplication.getConfig() ),
                    pwmApplication.getConfig(),
                    userIdentity.getUserDN(),
                    bogusPassword
            );

            //issue a read operation to trigger a bind.
            provider.readStringAttribute( userIdentity.getUserDN(), ChaiConstant.ATTR_LDAP_OBJECTCLASS );

            LOGGER.debug( sessionLabel, () -> "bad-password login attempt succeeded for " + userIdentity );
        }
        catch ( final ChaiException e )
        {
            if ( e.getErrorCode() == ChaiError.PASSWORD_BADPASSWORD )
            {
                LOGGER.trace( sessionLabel, () -> "bad-password login simulation succeeded for; " + userIdentity + " result: " + e.getMessage() );
            }
            else
            {
                LOGGER.debug( sessionLabel, () -> "unexpected error during simulated bad-password login attempt for " + userIdentity + "; result: " + e.getMessage() );
            }
        }
        finally
        {
            if ( provider != null )
            {
                try
                {
                    provider.close();
                }
                catch ( final Throwable e )
                {
                    LOGGER.error( sessionLabel,
                            () -> "unexpected error closing invalid ldap connection after simulated bad-password failed login attempt: " + e.getMessage() );
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
        LOGGER.error( sessionLabel, () -> "ldap error during search: " + exception.getMessage() );

        final IntruderManager intruderManager = pwmApplication.getIntruderManager();
        if ( intruderManager != null )
        {
            intruderManager.convenience().markAddressAndSession( pwmRequest );

            if ( username != null )
            {
                intruderManager.mark( RecordType.USERNAME, username, pwmRequest.getLabel() );
            }

            if ( userIdentity != null )
            {
                intruderManager.convenience().markUserIdentity( userIdentity, sessionLabel );
            }
        }
    }

    private void postAuthenticationSequence(
            final UserIdentity userIdentity,
            final AuthenticationResult authenticationResult
    )
            throws PwmUnrecoverableException
    {
        final IntruderManager intruderManager = pwmApplication.getIntruderManager();
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final LocalSessionStateBean ssBean = pwmRequest.getPwmSession().getSessionStateBean();
        final LoginInfoBean loginInfoBean = pwmRequest.getPwmSession().getLoginInfoBean();

        // auth succeed
        loginInfoBean.setAuthenticated( true );
        loginInfoBean.setUserIdentity( userIdentity );

        //update the session connection
        pwmSession.getSessionManager().setChaiProvider( authenticationResult.getUserProvider() );

        // update the actor user info bean
        {
            final UserInfo userInfoBean;
            if ( authenticationResult.getAuthenticationType() == AuthenticationType.AUTH_BIND_INHIBIT )
            {
                userInfoBean = UserInfoFactory.newUserInfo(
                        pwmApplication,
                        pwmRequest.getLabel(),
                        ssBean.getLocale(),
                        userIdentity,
                        pwmApplication.getProxyChaiProvider( userIdentity.getLdapProfileID() )
                );
            }
            else
            {
                userInfoBean = UserInfoFactory.newUserInfoUsingProxy(
                        pwmApplication,
                        pwmRequest.getLabel(),
                        userIdentity,
                        ssBean.getLocale(),
                        authenticationResult.getUserPassword()
                );
            }
            pwmSession.setUserInfo( userInfoBean );
        }

        //mark the auth time
        pwmSession.getLoginInfoBean().setAuthTime( Instant.now() );

        //update the resulting authType
        pwmSession.getLoginInfoBean().setType( authenticationResult.getAuthenticationType() );

        pwmSession.getLoginInfoBean().setAuthSource( authenticationSource );

        // save the password in the login bean
        final PasswordData userPassword = authenticationResult.getUserPassword();
        pwmSession.getLoginInfoBean().setUserCurrentPassword( userPassword );

        //notify the intruder manager with a successful login
        intruderManager.clear( RecordType.USERNAME, pwmSession.getUserInfo().getUsername() );
        intruderManager.convenience().clearUserIdentity( userIdentity );
        intruderManager.convenience().clearAddressAndSession( pwmSession );

        if ( pwmApplication.getStatisticsManager() != null )
        {
            final StatisticsManager statisticsManager = pwmApplication.getStatisticsManager();
            if ( pwmSession.getUserInfo().getPasswordStatus().isWarnPeriod() )
            {
                statisticsManager.incrementValue( Statistic.AUTHENTICATION_EXPIRED_WARNING );
            }
            else if ( pwmSession.getUserInfo().getPasswordStatus().isPreExpired() )
            {
                statisticsManager.incrementValue( Statistic.AUTHENTICATION_PRE_EXPIRED );
            }
            else if ( pwmSession.getUserInfo().getPasswordStatus().isExpired() )
            {
                statisticsManager.incrementValue( Statistic.AUTHENTICATION_EXPIRED );
            }
        }

        //clear permission cache - needs rechecking after login
        LOGGER.debug( pwmRequest, () -> "clearing permission cache" );
        pwmSession.getUserSessionDataCacheBean().clearPermissions();

        // update the users ldap attribute.
        LdapOperationsHelper.processAutoUpdateLanguageAttribute( pwmApplication, sessionLabel, ssBean.getLocale(), userIdentity );
    }
}
