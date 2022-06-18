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

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiProvider;
import password.pwm.PwmApplication;
import password.pwm.PwmDomain;
import password.pwm.bean.DomainID;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.AppConfig;
import password.pwm.config.profile.LdapProfile;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.ldap.LdapOperationsHelper;
import password.pwm.ldap.auth.AuthenticationType;
import password.pwm.svc.httpclient.PwmHttpClient;
import password.pwm.svc.httpclient.PwmHttpClientConfiguration;
import password.pwm.util.PasswordData;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;


public class ClientConnectionHolder
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( ClientConnectionHolder.class );

    private final PwmApplication pwmApplication;
    private final Supplier<SessionLabel> sessionLabel;
    private final DomainID domainID;
    private final UserIdentity userIdentity;
    private final AuthenticationType authenticationType;

    private ChaiProvider actorChaiProvider;
    private final Map<DomainID, Map<String, ChaiProvider>> proxyChaiProviders = new HashMap<>();
    private final Map<PwmHttpClientConfiguration, PwmHttpClient> httpClients = new HashMap<>();

    private ClientConnectionHolder(
            final PwmApplication pwmApplication,
            final UserIdentity userIdentity,
            final Supplier<SessionLabel> sessionLabel,
            final ChaiProvider chaiProvider,
            final AuthenticationType authenticationType,
            final DomainID domainID
    )
    {
        this.actorChaiProvider = chaiProvider;
        this.userIdentity = userIdentity;
        this.authenticationType = Objects.requireNonNull( authenticationType );
        this.pwmApplication = Objects.requireNonNull( pwmApplication );
        this.sessionLabel = Objects.requireNonNull( sessionLabel );
        this.domainID = Objects.requireNonNull( domainID );
    }

    public static ClientConnectionHolder authenticatedClientConnectionContext(
            final PwmApplication pwmApplication,
            final UserIdentity userIdentity,
            final Supplier<SessionLabel> sessionLabel,
            final ChaiProvider chaiProvider,
            final AuthenticationType authenticationType
    )
    {
        return new ClientConnectionHolder( pwmApplication, userIdentity, sessionLabel, chaiProvider, authenticationType, userIdentity.getDomainID() );
    }

    public static ClientConnectionHolder unauthenticatedClientConnectionContext(
            final PwmApplication pwmApplication,
            final DomainID domainID,
            final Supplier<SessionLabel> sessionLabel
    )
    {
        return new ClientConnectionHolder( pwmApplication, null, sessionLabel, null, AuthenticationType.AUTHENTICATED, domainID );
    }

    public Optional<UserIdentity> getUserIdentity()
    {
        return Optional.of( userIdentity );
    }

    public ChaiProvider getActorChaiProvider( )
            throws PwmUnrecoverableException
    {
        if ( actorChaiProvider == null )
        {
            if ( isAuthenticatedWithoutPasswordAndBind() )
            {
                throw PwmUnrecoverableException.newException( PwmError.ERROR_PASSWORD_REQUIRED, "password required for this operation" );
            }
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_AUTHENTICATION_REQUIRED, "ldap connection is not available for session" ) );
        }
        return actorChaiProvider;
    }

    public ChaiProvider getProxyChaiProvider( final LdapProfile ldapProfile )
            throws PwmUnrecoverableException
    {
        return getProxyChaiProvider( ldapProfile.getIdentifier() );
    }

    public ChaiProvider getProxyChaiProvider( final String ldapProfileID )
            throws PwmUnrecoverableException
    {
        final PwmDomain pwmDomain = pwmApplication.domains().get( domainID );

        proxyChaiProviders.computeIfAbsent( domainID, domainID -> new HashMap<>() );
        final ChaiProvider existingProvider = proxyChaiProviders.get( pwmDomain.getDomainID() ).get( ldapProfileID );
        if ( existingProvider != null )
        {
            return existingProvider;
        }
        final ChaiProvider newProvider = pwmDomain.getProxyChaiProvider( sessionLabel.get(), ldapProfileID );
        proxyChaiProviders.get( pwmDomain.getDomainID() ).put( ldapProfileID, newProvider );
        return newProvider;
    }

    public ChaiUser getProxiedChaiUser( final UserIdentity userIdentity )
            throws PwmUnrecoverableException
    {
        try
        {
            final ChaiProvider proxiedProvider = getProxyChaiProvider( userIdentity.getLdapProfileID() );
            return proxiedProvider.getEntryFactory().newChaiUser( userIdentity.getUserDN() );
        }
        catch ( final ChaiUnavailableException e )
        {
            throw PwmUnrecoverableException.fromChaiException( e );
        }
    }


    public boolean isAuthenticatedWithoutPasswordAndBind()
    {
        return authenticationType == AuthenticationType.AUTH_BIND_INHIBIT;
    }

    public void updateUserLdapPassword(
            final UserIdentity userIdentity,
            final PasswordData userPassword
    )
            throws PwmUnrecoverableException
    {

        this.actorChaiProvider = null;

        final PwmDomain pwmDomain = pwmApplication.domains().get( userIdentity.getDomainID() );

        try
        {
            final AppConfig appConfig = pwmDomain.getConfig().getAppConfig();
            this.actorChaiProvider = LdapOperationsHelper.createChaiProvider(
                    pwmDomain,
                    sessionLabel.get(),
                    userIdentity.getLdapProfile( appConfig ),
                    pwmDomain.getConfig(),
                    userIdentity.getUserDN(),
                    userPassword
            );
            final String userDN = userIdentity.getUserDN();
            actorChaiProvider.getEntryFactory().newChaiEntry( userDN ).exists();
        }
        catch ( final ChaiUnavailableException e )
        {
            final ErrorInformation errorInformation = new ErrorInformation(
                    PwmError.ERROR_DIRECTORY_UNAVAILABLE,
                    "error updating cached chaiProvider connection/password: " + e.getMessage() );
            throw new PwmUnrecoverableException( errorInformation );
        }
    }


    public void closeConnections( )
    {
        if ( actorChaiProvider != null )
        {
            try
            {
                LOGGER.debug( sessionLabel.get(), () -> "closing user ldap connection" );
                actorChaiProvider.close();
                actorChaiProvider = null;
            }
            catch ( final Exception e )
            {
                LOGGER.error( sessionLabel.get(), () -> "error while closing user connection: " + e.getMessage() );
            }
        }

        for ( final PwmHttpClient pwmHttpClient : httpClients.values() )
        {
            LOGGER.debug( sessionLabel.get(), () -> "closing user http client connection: " + pwmHttpClient.toString() );
            try
            {
                pwmHttpClient.close();
            }
            catch ( final Exception e )
            {
                LOGGER.error( sessionLabel.get(), () -> "error while http client connection: " + e.getMessage() );
            }
        }

        httpClients.clear();
        proxyChaiProviders.clear();
    }

    public ChaiUser getActor( )
            throws ChaiUnavailableException, PwmUnrecoverableException
    {

        if ( userIdentity == null )
        {
            throw new IllegalStateException( "user not logged in" );
        }

        final String userDN = userIdentity.getUserDN();

        if ( StringUtil.isEmpty( userDN ) )
        {
            throw new IllegalStateException( "user not logged in" );
        }

        return this.getActorChaiProvider().getEntryFactory().newChaiUser( userDN );
    }

    public ChaiUser getActor( final UserIdentity otherIdentity )
            throws PwmUnrecoverableException
    {
        try
        {
            if ( !isAuthenticated() )
            {
                throw new PwmUnrecoverableException( PwmError.ERROR_AUTHENTICATION_REQUIRED );
            }
            if ( userIdentity.getLdapProfileID() == null || otherIdentity.getLdapProfileID() == null )
            {
                throw new PwmUnrecoverableException( PwmError.ERROR_NO_LDAP_CONNECTION );
            }
            final ChaiProvider provider = this.getActorChaiProvider();
            return provider.getEntryFactory().newChaiUser( otherIdentity.getUserDN() );
        }
        catch ( final ChaiUnavailableException e )
        {
            throw PwmUnrecoverableException.fromChaiException( e );
        }
    }

    public PwmHttpClient getPwmHttpClient(
            final PwmHttpClientConfiguration pwmHttpClientConfiguration
    )
            throws PwmUnrecoverableException
    {
        final PwmHttpClientConfiguration mapKey = pwmHttpClientConfiguration == null
                ? PwmHttpClientConfiguration.builder().build()
                : pwmHttpClientConfiguration;

        PwmHttpClient existingClient = httpClients.get( mapKey );
        if ( existingClient != null && !existingClient.isOpen() )
        {
            existingClient = null;
        }
        if ( existingClient == null )
        {
            final PwmHttpClient newClient = pwmApplication.getHttpClientService().getPwmHttpClient( pwmHttpClientConfiguration, sessionLabel.get() );
            httpClients.put( mapKey, newClient );
            return newClient;
        }
        return existingClient;
    }

    private boolean isAuthenticated()
    {
        return userIdentity != null;
    }
}
