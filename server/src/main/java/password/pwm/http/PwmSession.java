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

package password.pwm.http;

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.LocalSessionStateBean;
import password.pwm.bean.LoginInfoBean;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.bean.UserSessionDataCacheBean;
import password.pwm.ldap.UserInfo;
import password.pwm.ldap.UserInfoBean;
import password.pwm.ldap.UserInfoFactory;
import password.pwm.ldap.auth.AuthenticationType;
import password.pwm.svc.stats.Statistic;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmRandom;
import password.pwm.util.secure.PwmSecurityKey;

import java.io.Serializable;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * @author Jason D. Rivard
 */
public class PwmSession implements Serializable
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( PwmSession.class );

    private final LocalSessionStateBean sessionStateBean;

    private LoginInfoBean loginInfoBean;
    private transient UserInfo userInfo;
    private UserSessionDataCacheBean userSessionDataCacheBean;

    private static final Object CREATION_LOCK = new Object();

    private transient SessionManager sessionManager;

    public static PwmSession createPwmSession( final PwmApplication pwmApplication )
            throws PwmUnrecoverableException
    {
        synchronized ( CREATION_LOCK )
        {
            return new PwmSession( pwmApplication );
        }
    }


    private PwmSession( final PwmApplication pwmApplication )
            throws PwmUnrecoverableException
    {
        if ( pwmApplication == null )
        {
            throw new IllegalStateException( "PwmApplication must be available during session creation" );
        }

        sessionStateBean = new LocalSessionStateBean();
        this.sessionStateBean.setSessionID( pwmApplication.getSessionTrackService().generateNewSessionID() );

        this.sessionStateBean.setSessionLastAccessedTime( Instant.now() );

        if ( pwmApplication.getStatisticsManager() != null )
        {
            pwmApplication.getStatisticsManager().incrementValue( Statistic.HTTP_SESSIONS );
        }

        pwmApplication.getSessionTrackService().addSessionData( this );

        LOGGER.trace( this, () -> "created new session" );
    }


    public SessionManager getSessionManager( )
    {
        if ( sessionManager == null )
        {
            sessionManager = new SessionManager( this );
        }
        return sessionManager;
    }

    public LocalSessionStateBean getSessionStateBean( )
    {
        return sessionStateBean;
    }

    public UserInfo getUserInfo( )
    {
        if ( !isAuthenticated() )
        {
            throw new IllegalStateException( "attempt to read user info bean, but session not authenticated" );
        }
        if ( userInfo == null )
        {
            userInfo = UserInfoBean.builder().build();
        }
        return userInfo;
    }

    public void setUserInfo( final UserInfo userInfo )
    {
        this.userInfo = userInfo;
    }

    public void reloadUserInfoBean( final PwmApplication pwmApplication ) throws PwmUnrecoverableException
    {
        LOGGER.trace( this, () -> "performing reloadUserInfoBean" );
        final UserInfo oldUserInfoBean = getUserInfo();

        final UserInfo userInfo;
        if ( getLoginInfoBean().getAuthFlags().contains( AuthenticationType.AUTH_BIND_INHIBIT ) )
        {
            userInfo = UserInfoFactory.newUserInfo(
                    pwmApplication,
                    getLabel(),
                    getSessionStateBean().getLocale(),
                    oldUserInfoBean.getUserIdentity(),
                    pwmApplication.getProxyChaiProvider( oldUserInfoBean.getUserIdentity().getLdapProfileID() )
            );
        }
        else
        {
            userInfo = UserInfoFactory.newUserInfoUsingProxy(
                    pwmApplication,
                    getLabel(),
                    oldUserInfoBean.getUserIdentity(),
                    getSessionStateBean().getLocale(),
                    loginInfoBean.getUserCurrentPassword()
            );
        }

        setUserInfo( userInfo );
    }

    public LoginInfoBean getLoginInfoBean( )
    {
        if ( loginInfoBean == null )
        {
            loginInfoBean = new LoginInfoBean();
        }
        if ( loginInfoBean.getGuid() == null )
        {
            loginInfoBean.setGuid( ( Long.toString( new Date().getTime(), 36 ) + PwmRandom.getInstance().alphaNumericString( 64 ) ) );
        }

        return loginInfoBean;
    }

    public void setLoginInfoBean( final LoginInfoBean loginInfoBean )
    {
        this.loginInfoBean = loginInfoBean;
    }

    public UserSessionDataCacheBean getUserSessionDataCacheBean( )
    {
        if ( userSessionDataCacheBean == null )
        {
            userSessionDataCacheBean = new UserSessionDataCacheBean();
        }
        return userSessionDataCacheBean;
    }

    public SessionLabel getLabel( )
    {
        final LocalSessionStateBean ssBean = this.getSessionStateBean();

        String userID = null;
        try
        {
            userID = isAuthenticated()
                    ? this.getUserInfo().getUsername()
                    : null;
        }
        catch ( PwmUnrecoverableException e )
        {
            LOGGER.error( "unexpected error reading username: " + e.getMessage(), e );
        }
        final UserIdentity userIdentity = isAuthenticated() ? this.getUserInfo().getUserIdentity() : null;
        return new SessionLabel( ssBean.getSessionID(), userIdentity, userID, ssBean.getSrcAddress(), ssBean.getSrcAddress() );
    }

    /**
     * Unauthenticate the pwmSession.
     *
     * @param pwmRequest current request of the user
     */
    public void unauthenticateUser( final PwmRequest pwmRequest )
    {
        final LocalSessionStateBean ssBean = getSessionStateBean();

        if ( getLoginInfoBean().isAuthenticated() )
        {
            // try to tear out a session normally.
            getUserSessionDataCacheBean().clearPermissions();

            final StringBuilder sb = new StringBuilder();

            sb.append( "unauthenticate session from " ).append( ssBean.getSrcAddress() );
            if ( getUserInfo().getUserIdentity() != null )
            {
                sb.append( " (" ).append( getUserInfo().getUserIdentity() ).append( ")" );
            }

            // mark the session state bean as no longer being authenticated
            this.getLoginInfoBean().setAuthenticated( false );

            // close out any outstanding connections
            getSessionManager().closeConnections();

            LOGGER.debug( this, () -> sb.toString() );
        }

        if ( pwmRequest != null )
        {

            final String nonceCookieName = pwmRequest.getConfig().readAppProperty( AppProperty.HTTP_COOKIE_NONCE_NAME );
            pwmRequest.setAttribute( PwmRequestAttribute.CookieNonce, null );
            pwmRequest.getPwmResponse().removeCookie( nonceCookieName, PwmHttpResponseWrapper.CookiePath.Application );

            try
            {
                pwmRequest.getPwmApplication().getSessionStateService().clearLoginSession( pwmRequest );
            }
            catch ( PwmUnrecoverableException e )
            {
                final String errorMsg = "unexpected error writing removing login cookie from response: " + e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INTERNAL, errorMsg );
                LOGGER.error( pwmRequest, errorInformation );
            }

            pwmRequest.getHttpServletRequest().setAttribute( PwmConstants.SESSION_ATTR_BEANS, null );
        }

        userInfo = null;
        loginInfoBean = null;
        userSessionDataCacheBean = null;
    }

    public TimeDuration getIdleTime( )
    {
        return TimeDuration.fromCurrent( sessionStateBean.getSessionLastAccessedTime() );
    }

    public String toString( )
    {
        final Map<String, Object> debugData = new LinkedHashMap<>();
        try
        {
            debugData.put( "sessionID", getSessionStateBean().getSessionID() );
            debugData.put( "auth", this.isAuthenticated() );
            if ( this.isAuthenticated() )
            {
                debugData.put( "passwordStatus", getUserInfo().getPasswordStatus() );
                debugData.put( "guid", getUserInfo().getUserGuid() );
                debugData.put( "dn", getUserInfo().getUserIdentity() );
                debugData.put( "authType", getLoginInfoBean().getType() );
                debugData.put( "needsNewPW", getUserInfo().isRequiresNewPassword() );
                debugData.put( "needsNewCR", getUserInfo().isRequiresResponseConfig() );
                debugData.put( "needsNewProfile", getUserInfo().isRequiresUpdateProfile() );
                debugData.put( "hasCRPolicy", getUserInfo().getChallengeProfile() != null && getUserInfo().getChallengeProfile().getChallengeSet() != null );
            }
            debugData.put( "locale", getSessionStateBean().getLocale() );
            debugData.put( "theme", getSessionStateBean().getTheme() );
        }
        catch ( Exception e )
        {
            return "exception generating PwmSession.toString(): " + e.getMessage();
        }

        return "PwmSession instance: " + JsonUtil.serializeMap( debugData );
    }

    public boolean setLocale( final PwmApplication pwmApplication, final String localeString )
            throws PwmUnrecoverableException
    {
        if ( pwmApplication == null )
        {
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_APP_UNAVAILABLE, "unable to read context manager" ) );
        }

        final LocalSessionStateBean ssBean = this.getSessionStateBean();
        final List<Locale> knownLocales = pwmApplication.getConfig().getKnownLocales();
        final Locale requestedLocale = LocaleHelper.parseLocaleString( localeString );
        if ( knownLocales.contains( requestedLocale ) || "default".equalsIgnoreCase( localeString ) )
        {
            LOGGER.debug( this, () -> "setting session locale to '" + localeString + "'" );
            ssBean.setLocale( "default".equalsIgnoreCase( localeString )
                    ? PwmConstants.DEFAULT_LOCALE
                    : requestedLocale );
            if ( this.isAuthenticated() )
            {
                this.reloadUserInfoBean( pwmApplication );
            }
            return true;
        }
        else
        {
            LOGGER.error( this, "ignoring unknown locale value set request for locale '" + localeString + "'" );
            ssBean.setLocale( PwmConstants.DEFAULT_LOCALE );
            return false;
        }
    }

    public boolean isAuthenticated( )
    {
        return getLoginInfoBean().isAuthenticated();
    }

    public int size( )
    {
        return ( int ) JavaHelper.sizeof( this );
    }

    synchronized PwmSecurityKey getSecurityKey( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException
    {
        final int length = Integer.parseInt( pwmRequest.getConfig().readAppProperty( AppProperty.HTTP_COOKIE_NONCE_LENGTH ) );
        final String cookieName =  pwmRequest.getConfig().readAppProperty( AppProperty.HTTP_COOKIE_NONCE_NAME );

        String nonce = (String) pwmRequest.getAttribute( PwmRequestAttribute.CookieNonce );
        if ( nonce == null || nonce.length() < length )
        {
            nonce = pwmRequest.readCookie( cookieName );
        }

        boolean newNonce = false;
        if ( nonce == null || nonce.length() < length )
        {
            // random value
            final String random = pwmRequest.getPwmApplication().getSecureService().pwmRandom().alphaNumericString( length );

            // timestamp component for uniqueness
            final String prefix = Long.toString( System.currentTimeMillis(), Character.MAX_RADIX );

            nonce = random + prefix;
            newNonce = true;
        }

        final PwmSecurityKey securityKey = pwmRequest.getConfig().getSecurityKey();
        final String concatValue = securityKey.keyHash( pwmRequest.getPwmApplication().getSecureService() ) + nonce;
        final String hashValue = pwmRequest.getPwmApplication().getSecureService().hash( concatValue );
        final PwmSecurityKey pwmSecurityKey = new PwmSecurityKey( hashValue );

        if ( newNonce )
        {
            pwmRequest.setAttribute( PwmRequestAttribute.CookieNonce, nonce );
            pwmRequest.getPwmResponse().writeCookie( cookieName, nonce, -1, PwmHttpResponseWrapper.CookiePath.Application );
        }

        return pwmSecurityKey;
    }
}
