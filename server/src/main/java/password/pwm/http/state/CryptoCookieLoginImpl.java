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

package password.pwm.http.state;

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.bean.LoginInfoBean;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.IdleTimeoutCalculator;
import password.pwm.http.PwmHttpResponseWrapper;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmResponse;
import password.pwm.ldap.auth.AuthenticationType;
import password.pwm.ldap.auth.SessionAuthenticator;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsManager;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogLevel;
import password.pwm.util.logging.PwmLogger;

import java.time.Instant;

class CryptoCookieLoginImpl implements SessionLoginProvider
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( CryptoCookieLoginImpl.class );

    private static final PwmResponse.CookiePath COOKIE_PATH = PwmHttpResponseWrapper.CookiePath.Application;
    private String cookieName = "SESSION";

    @Override
    public void init( final PwmApplication pwmApplication ) throws PwmException
    {
        cookieName = pwmApplication.getConfig().readAppProperty( AppProperty.HTTP_COOKIE_LOGIN_NAME );
    }

    @Override
    public void clearLoginSession( final PwmRequest pwmRequest ) throws PwmUnrecoverableException
    {
        pwmRequest.getPwmResponse().removeCookie( cookieName, COOKIE_PATH );
    }

    @Override
    public void saveLoginSessionState( final PwmRequest pwmRequest )
    {
        try
        {
            final LoginInfoBean loginInfoBean = pwmRequest.getPwmSession().getLoginInfoBean();
            loginInfoBean.setReqTime( Instant.now() );

            pwmRequest.getPwmResponse().writeEncryptedCookie(
                    cookieName,
                    loginInfoBean,
                    COOKIE_PATH
            );

            if ( LOGGER.isEnabled( PwmLogLevel.TRACE ) )
            {
                final String debugTxt = loginInfoBean.toDebugString();
                LOGGER.trace( pwmRequest, () -> "wrote LoginInfoBean=" + debugTxt );
            }
        }
        catch ( final PwmUnrecoverableException e )
        {
            final String errorMsg = "unexpected error writing login cookie to response: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INTERNAL, errorMsg );
            LOGGER.error( pwmRequest, errorInformation );
        }
    }

    @Override
    public void readLoginSessionState( final PwmRequest pwmRequest ) throws PwmUnrecoverableException
    {
        final LoginInfoBean remoteLoginCookie;
        try
        {
            remoteLoginCookie = pwmRequest.readEncryptedCookie( cookieName, LoginInfoBean.class );
        }
        catch ( final PwmUnrecoverableException e )
        {
            final String errorMsg = "unexpected error reading login cookie, will clear and ignore; error: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_CRYPT_ERROR, errorMsg );
            LOGGER.trace( pwmRequest, () -> errorInformation.toDebugStr() );
            clearLoginSession( pwmRequest );
            return;
        }

        if ( remoteLoginCookie != null )
        {
            try
            {
                try
                {
                    checkIfRemoteLoginCookieIsValid( pwmRequest, remoteLoginCookie );
                }
                catch ( final PwmOperationalException e )
                {
                    LOGGER.debug( pwmRequest, () -> e.getErrorInformation().toDebugStr() );
                    clearLoginSession( pwmRequest );
                    return;
                }

                checkIfLoginCookieIsForeign( pwmRequest, remoteLoginCookie );

                if ( remoteLoginCookie.getType() == AuthenticationType.AUTH_WITHOUT_PASSWORD && remoteLoginCookie.getUserCurrentPassword() == null )
                {
                    LOGGER.debug( () -> "remote session has authType " + AuthenticationType.AUTH_WITHOUT_PASSWORD.name()
                            + " and does not contain password, thus ignoring authentication so SSO process can repeat" );
                    return;
                }

                importRemoteCookie( pwmRequest, remoteLoginCookie );
            }
            catch ( final Exception e )
            {
                final String errorMsg = "unexpected error authenticating using crypto session cookie: " + e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INTERNAL, errorMsg );
                LOGGER.error( pwmRequest, errorInformation );
                throw new PwmUnrecoverableException( errorInformation );
            }
        }
    }


    private static void importRemoteCookie(
            final PwmRequest pwmRequest,
            final LoginInfoBean remoteLoginCookie
    )
            throws PwmUnrecoverableException
    {
        if ( remoteLoginCookie == null )
        {
            return;
        }

        final LoginInfoBean localLoginCookie = pwmRequest.getPwmSession().getLoginInfoBean();

        if ( remoteLoginCookie.isAuthenticated() )
        {

            if ( localLoginCookie.isAuthenticated() )
            {
                // should never get here unless one of container session and app session key are swapped between users.
                final UserIdentity remoteIdentity = remoteLoginCookie.getUserIdentity();
                final UserIdentity localIdentity = localLoginCookie.getUserIdentity();
                if ( remoteIdentity != null && localIdentity != null && !remoteIdentity.equals( localIdentity ) )
                {
                    throw new PwmUnrecoverableException(
                            new ErrorInformation( PwmError.ERROR_BAD_SESSION, "remote and local session identities differ" )
                    );
                }
            }
            else
            {
                LOGGER.debug( pwmRequest, () -> "triggering authentication because request contains an authenticated session but local session is unauthenticated" );
                final SessionAuthenticator sessionAuthenticator = new SessionAuthenticator(
                        pwmRequest.getPwmApplication(),
                        pwmRequest,
                        remoteLoginCookie.getAuthSource()
                );
                try
                {
                    if ( remoteLoginCookie.getUserIdentity() == null )
                    {
                        sessionAuthenticator.authUserWithUnknownPassword(
                                remoteLoginCookie.getUserIdentity(),
                                remoteLoginCookie.getType()
                        );

                    }
                    else
                    {
                        sessionAuthenticator.authenticateUser(
                                remoteLoginCookie.getUserIdentity(),
                                remoteLoginCookie.getUserCurrentPassword()
                        );
                    }
                    remoteLoginCookie.getAuthFlags().add( AuthenticationType.AUTH_FROM_REQ_COOKIE );
                    LOGGER.debug( pwmRequest, () -> "logged in using encrypted request cookie = " + JsonUtil.serialize( remoteLoginCookie ) );
                }
                catch ( final Exception e )
                {
                    final String errorMsg = "unexpected error reading session cookie: " + e.getMessage();
                    final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INTERNAL, errorMsg );
                    LOGGER.error( pwmRequest, errorInformation );
                    throw new PwmUnrecoverableException( errorInformation );
                }
            }
        }

        if ( pwmRequest.getConfig().isDevDebugMode() && LOGGER.isEnabled( PwmLogLevel.TRACE ) )
        {
            final String debugTxt = remoteLoginCookie.toDebugString();
            LOGGER.trace( pwmRequest, () -> "imported LoginInfoBean=" + debugTxt );
        }
        pwmRequest.getPwmSession().setLoginInfoBean( remoteLoginCookie );
    }

    private static void checkIfRemoteLoginCookieIsValid(
            final PwmRequest pwmRequest,
            final LoginInfoBean loginInfoBean
    )
            throws PwmOperationalException, PwmUnrecoverableException
    {
        if ( loginInfoBean.isAuthenticated() && loginInfoBean.getAuthTime() == null )
        {
            final String errorMsg = "decrypted login cookie does not specify a local auth time";
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_BAD_SESSION, errorMsg );
            throw new PwmOperationalException( errorInformation );
        }

        if ( loginInfoBean.getAuthTime() != null )
        {
            final long sessionMaxSeconds = pwmRequest.getConfig().readSettingAsLong( PwmSetting.SESSION_MAX_SECONDS );
            final TimeDuration sessionTotalAge = TimeDuration.fromCurrent( loginInfoBean.getAuthTime() );
            final TimeDuration sessionMaxAge = TimeDuration.of( sessionMaxSeconds, TimeDuration.Unit.SECONDS );
            if ( sessionTotalAge.isLongerThan( sessionMaxAge ) )
            {
                final String errorMsg = "decrypted login cookie age ("
                        + sessionTotalAge.asCompactString()
                        + ") is older than max session seconds ("
                        + sessionMaxAge.asCompactString()
                        + ")";
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_BAD_SESSION, errorMsg );
                throw new PwmOperationalException( errorInformation );
            }
        }
        if ( loginInfoBean.getReqTime() == null )
        {
            final String errorMsg = "decrypted login cookie does not specify a issue time";
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_BAD_SESSION, errorMsg );
            throw new PwmOperationalException( errorInformation );
        }
        {
            final TimeDuration loginCookieIssueAge = TimeDuration.fromCurrent( loginInfoBean.getReqTime() );
            final TimeDuration maxIdleDuration = IdleTimeoutCalculator.idleTimeoutForRequest( pwmRequest );
            if ( loginCookieIssueAge.isLongerThan( maxIdleDuration ) )
            {
                final String errorMsg = "decrypted login cookie issue time ("
                        + loginCookieIssueAge.asCompactString()
                        + ") is older than max idle seconds ("
                        + maxIdleDuration.asCompactString()
                        + ")";
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_BAD_SESSION, errorMsg );
                throw new PwmOperationalException( errorInformation );
            }
        }
    }

    private static void checkIfLoginCookieIsForeign( final PwmRequest pwmRequest, final LoginInfoBean remoteLoginInfoBean ) throws PwmUnrecoverableException
    {
        final String remoteGuid = remoteLoginInfoBean.getGuid();
        final String localGuid = pwmRequest.getPwmSession().getLoginInfoBean().getGuid();
        if ( remoteGuid != null && !remoteGuid.equals( localGuid ) )
        {
            final String logMsg = "login cookie session was generated by a foreign instance, seen login cookie value = "
                    + remoteLoginInfoBean.toDebugString();
            StatisticsManager.incrementStat( pwmRequest.getPwmApplication(), Statistic.FOREIGN_SESSIONS_ACCEPTED );
            LOGGER.trace( pwmRequest, () -> logMsg );
        }
    }
}
