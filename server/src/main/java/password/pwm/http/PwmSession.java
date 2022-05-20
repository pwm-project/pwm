/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2021 The PWM Project
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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.Data;
import password.pwm.AppProperty;
import password.pwm.PwmConstants;
import password.pwm.PwmDomain;
import password.pwm.bean.DomainID;
import password.pwm.bean.LocalSessionStateBean;
import password.pwm.bean.LoginInfoBean;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.profile.ChallengeProfile;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.bean.UserSessionDataCacheBean;
import password.pwm.user.UserInfo;
import password.pwm.user.UserInfoBean;
import password.pwm.ldap.UserInfoFactory;
import password.pwm.ldap.auth.AuthenticationType;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsClient;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.java.MiscUtil;
import password.pwm.util.json.JsonFactory;
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
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Jason D. Rivard
 */
@Data
public class PwmSession implements Serializable
{
    private static final long serialVersionUID = 1L;

    private static final PwmLogger LOGGER = PwmLogger.forClass( PwmSession.class );

    @SuppressFBWarnings( "SE_TRANSIENT_FIELD_NOT_RESTORED" )
    private final transient LocalSessionStateBean sessionStateBean = new LocalSessionStateBean();

    @SuppressFBWarnings( "SE_TRANSIENT_FIELD_NOT_RESTORED" )
    private final transient UserSessionDataCacheBean userSessionDataCacheBean = new UserSessionDataCacheBean();

    private final DomainID domainID;
    private LoginInfoBean loginInfoBean;
    private transient UserInfo userInfo;

    private static final Lock CREATION_LOCK = new ReentrantLock();

    private final Lock securityKeyLock = new ReentrantLock();
    private final transient SessionManager sessionManager;

    public static PwmSession createPwmSession( final PwmDomain pwmDomain )
    {
        CREATION_LOCK.lock();
        try
        {
            return new PwmSession( pwmDomain );
        }
        finally
        {
            CREATION_LOCK.unlock();
        }
    }

    private PwmSession( final PwmDomain pwmDomain )
    {
        Objects.requireNonNull( pwmDomain );
        this.domainID = pwmDomain.getDomainID();

        this.sessionStateBean.setSessionID( pwmDomain.getSessionTrackService().generateNewSessionID() );

        this.sessionStateBean.setSessionLastAccessedTime( Instant.now() );

        StatisticsClient.incrementStat( pwmDomain.getPwmApplication(), Statistic.HTTP_SESSIONS );

        pwmDomain.getSessionTrackService().addSessionData( this );
        this.sessionManager = new SessionManager( pwmDomain, this );

        LOGGER.trace( () -> "created new session" );
    }


    public SessionManager getSessionManager( )
    {
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

    public void reloadUserInfoBean( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException
    {
        LOGGER.trace( () -> "performing reloadUserInfoBean" );
        final UserInfo oldUserInfoBean = getUserInfo();
        final PwmDomain pwmDomain = pwmRequest.getPwmDomain();

        final UserInfo userInfo;
        if ( getLoginInfoBean().getAuthFlags().contains( AuthenticationType.AUTH_BIND_INHIBIT ) )
        {
            userInfo = UserInfoFactory.newUserInfo(
                    pwmRequest.getPwmApplication(),
                    pwmRequest.getLabel(),
                    getSessionStateBean().getLocale(),
                    oldUserInfoBean.getUserIdentity(),
                    pwmDomain.getProxyChaiProvider( pwmRequest.getLabel(), oldUserInfoBean.getUserIdentity().getLdapProfileID() )
            );
        }
        else
        {
            userInfo = UserInfoFactory.newUserInfoUsingProxy(
                    pwmRequest.getPwmApplication(),
                    pwmRequest.getLabel(),
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
        return userSessionDataCacheBean;
    }

    public SessionLabel getLabel( )
    {
        final LocalSessionStateBean ssBean = this.getSessionStateBean();

        UserIdentity userIdentity = null;
        String userID = null;
        String profile = null;

        if ( isAuthenticated() )
        {
            try
            {
                final UserInfo userInfo = getUserInfo();
                userIdentity = userInfo.getUserIdentity();
                userID = userInfo.getUsername();
                profile = userIdentity.getLdapProfileID();
            }
            catch ( final PwmUnrecoverableException e )
            {
                LOGGER.error( () -> "unexpected error reading username: " + e.getMessage(), e );
            }
        }

        return SessionLabel.builder()
                .sessionID( ssBean.getSessionID() )
                .userID( userIdentity == null ? null : userIdentity.toDelimitedKey() )
                .username( userID )
                .domain( domainID.stringValue() )
                .profile( profile )
                .sourceAddress( ssBean.getSrcAddress() )
                .sourceHostname( ssBean.getSrcHostname() )
                .build();
    }

    /**
     * Unauthenticate the pwmSession.
     *
     * @param pwmRequest current request of the user
     */
    public void unauthenticateUser( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException
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
                sb.append( " (" ).append( getUserInfo().getUserIdentity() ).append( ')' );
            }

            // mark the session state bean as no longer being authenticated
            this.getLoginInfoBean().setAuthenticated( false );

            // close out any outstanding connections
            getSessionManager().closeConnections();

            LOGGER.debug( pwmRequest, sb::toString );
        }

        if ( pwmRequest != null )
        {

            final String nonceCookieName = pwmRequest.getDomainConfig().readAppProperty( AppProperty.HTTP_COOKIE_NONCE_NAME );
            pwmRequest.setAttribute( PwmRequestAttribute.CookieNonce, null );
            pwmRequest.getPwmResponse().removeCookie( nonceCookieName, PwmCookiePath.Domain );

            try
            {
                pwmRequest.getPwmDomain().getSessionStateService().clearLoginSession( pwmRequest );
            }
            catch ( final PwmUnrecoverableException e )
            {
                final String errorMsg = "unexpected error writing removing login cookie from response: " + e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INTERNAL, errorMsg );
                LOGGER.error( pwmRequest, errorInformation );
            }

            pwmRequest.getHttpServletRequest().setAttribute( PwmConstants.SESSION_ATTR_BEANS, null );
        }

        userInfo = null;
        loginInfoBean = null;
        userSessionDataCacheBean.clearPermissions();
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

                final ChallengeProfile challengeProfile = getUserInfo().getChallengeProfile();
                debugData.put( "hasCRPolicy", challengeProfile != null && challengeProfile.getChallengeSet().isPresent() );
            }
            debugData.put( "locale", getSessionStateBean().getLocale() );
            debugData.put( "theme", getSessionStateBean().getTheme() );
        }
        catch ( final Exception e )
        {
            return "exception generating PwmSession.toString(): " + e.getMessage();
        }

        return "PwmSession instance: " + JsonFactory.get().serializeMap( debugData );
    }

    public boolean setLocale( final PwmRequest pwmRequest, final String localeString )
            throws PwmUnrecoverableException
    {
        final PwmDomain pwmDomain = pwmRequest.getPwmDomain();
        if ( pwmDomain == null )
        {
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_APP_UNAVAILABLE, "unable to read context manager" ) );
        }

        final LocalSessionStateBean ssBean = this.getSessionStateBean();
        final List<Locale> knownLocales = pwmRequest.getAppConfig().getKnownLocales();
        final Locale requestedLocale = LocaleHelper.parseLocaleString( localeString );
        if ( knownLocales.contains( requestedLocale ) || "default".equalsIgnoreCase( localeString ) )
        {
            LOGGER.debug( pwmRequest, () -> "setting session locale to '" + localeString + "'" );
            ssBean.setLocale( "default".equalsIgnoreCase( localeString )
                    ? PwmConstants.DEFAULT_LOCALE
                    : requestedLocale );
            if ( this.isAuthenticated() )
            {
                this.reloadUserInfoBean( pwmRequest );
            }
            return true;
        }
        else
        {
            LOGGER.error( pwmRequest, () -> "ignoring unknown locale value set request for locale '" + localeString + "'" );
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
        return ( int ) MiscUtil.sizeof( this );
    }

    PwmSecurityKey getSecurityKey( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException
    {
        securityKeyLock.lock();
        try
        {
            final int length = Integer.parseInt( pwmRequest.getDomainConfig().readAppProperty( AppProperty.HTTP_COOKIE_NONCE_LENGTH ) );
            final String cookieName = pwmRequest.getDomainConfig().readAppProperty( AppProperty.HTTP_COOKIE_NONCE_NAME );

            String nonce = ( String ) pwmRequest.getAttribute( PwmRequestAttribute.CookieNonce );
            if ( nonce == null || nonce.length() < length )
            {
                nonce = pwmRequest.readCookie( cookieName ).orElse( null );
            }

            boolean newNonce = false;
            if ( nonce == null || nonce.length() < length )
            {
                // random value
                final String random = pwmRequest.getPwmDomain().getSecureService().pwmRandom().alphaNumericString( length );

                // timestamp component for uniqueness
                final String prefix = Long.toString( System.currentTimeMillis(), Character.MAX_RADIX );

                nonce = random + prefix;
                newNonce = true;
            }

            final PwmSecurityKey securityKey = pwmRequest.getDomainConfig().getSecurityKey();
            final String concatValue = securityKey.keyHash( pwmRequest.getPwmDomain().getSecureService() ) + nonce;
            final String hashValue = pwmRequest.getPwmDomain().getSecureService().hash( concatValue );
            final PwmSecurityKey pwmSecurityKey = new PwmSecurityKey( hashValue );

            if ( newNonce )
            {
                pwmRequest.setAttribute( PwmRequestAttribute.CookieNonce, nonce );
                pwmRequest.getPwmResponse().writeCookie( cookieName, nonce, -1, PwmCookiePath.Domain );
            }

            return pwmSecurityKey;
        }
        finally
        {
            securityKeyLock.unlock();
        }
    }
}
