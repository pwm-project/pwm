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

package password.pwm.ldap;

import com.google.gson.reflect.TypeToken;
import com.novell.ldapchai.provider.ChaiProvider;
import com.novell.ldapchai.provider.ChaiProviderFactory;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.config.profile.LdapProfile;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthRecord;
import password.pwm.svc.PwmService;
import password.pwm.util.java.AtomicLoopIntIncrementer;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.logging.PwmLogger;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LdapConnectionService implements PwmService
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( LdapConnectionService.class );

    private final Map<LdapProfile, Map<Integer, ChaiProvider>> proxyChaiProviders = new ConcurrentHashMap<>();
    private final Map<LdapProfile, ErrorInformation> lastLdapErrors = new ConcurrentHashMap<>();

    private boolean useThreadLocal;
    private PwmApplication pwmApplication;
    private STATUS status = STATUS.NEW;
    private AtomicLoopIntIncrementer slotIncrementer;
    private final ThreadLocal<Map<LdapProfile, ChaiProvider>> threadLocalProvider = new ThreadLocal<>();
    private ChaiProviderFactory chaiProviderFactory;

    public STATUS status( )
    {
        return status;
    }

    public void init( final PwmApplication pwmApplication )
            throws PwmException
    {
        this.pwmApplication = pwmApplication;

        chaiProviderFactory = ChaiProviderFactory.newProviderFactory();

        useThreadLocal = Boolean.parseBoolean( pwmApplication.getConfig().readAppProperty( AppProperty.LDAP_PROXY_USE_THREAD_LOCAL ) );

        // read the lastLoginTime
        this.lastLdapErrors.putAll( readLastLdapFailure( pwmApplication ) );

        final int connectionsPerProfile = maxSlotsPerProfile( pwmApplication );
        LOGGER.trace( () -> "allocating " + connectionsPerProfile + " ldap proxy connections per profile" );
        slotIncrementer = new AtomicLoopIntIncrementer( connectionsPerProfile );

        for ( final LdapProfile ldapProfile : pwmApplication.getConfig().getLdapProfiles().values() )
        {
            proxyChaiProviders.put( ldapProfile, new ConcurrentHashMap<>() );
        }

        status = STATUS.OPEN;
    }

    public void close( )
    {
        status = STATUS.CLOSED;
        LOGGER.trace( () -> "closing ldap proxy connections" );
        if ( chaiProviderFactory != null )
        {
            try
            {
                chaiProviderFactory.close();
            }
            catch ( Exception e )
            {
                LOGGER.error( "error closing ldap proxy connection: " + e.getMessage(), e );
            }
        }
        proxyChaiProviders.clear();
    }

    public List<HealthRecord> healthCheck( )
    {
        return null;
    }

    public ServiceInfoBean serviceInfo( )
    {
        final Map<String, String> debugProperties = new LinkedHashMap<>();
        debugProperties.putAll( chaiProviderFactory.getGlobalStatistics() );
        debugProperties.putAll( connectionDebugInfo() );
        return new ServiceInfoBean(
                Collections.singletonList( DataStorageMethod.LDAP ),
                Collections.unmodifiableMap( debugProperties )
        );
    }


    public ChaiProvider getProxyChaiProvider( final String identifier )
            throws PwmUnrecoverableException
    {
        final LdapProfile ldapProfile = pwmApplication.getConfig().getLdapProfiles().get( identifier );
        return getProxyChaiProvider( ldapProfile );
    }

    public ChaiProvider getProxyChaiProvider( final LdapProfile ldapProfile )
            throws PwmUnrecoverableException
    {
        final LdapProfile effectiveProfile = ldapProfile == null
                ? pwmApplication.getConfig().getDefaultLdapProfile()
                : ldapProfile;

        if ( useThreadLocal )
        {
            if ( threadLocalProvider.get() != null && threadLocalProvider.get().containsKey( effectiveProfile ) )
            {
                return threadLocalProvider.get().get( effectiveProfile );
            }
        }

        final ChaiProvider chaiProvider = getNewProxyChaiProvider( effectiveProfile );

        if ( useThreadLocal )
        {
            if ( threadLocalProvider.get() == null )
            {
                threadLocalProvider.set( new ConcurrentHashMap<>() );
            }
            threadLocalProvider.get().put( effectiveProfile, chaiProvider );
        }

        return chaiProvider;
    }

    private ChaiProvider getNewProxyChaiProvider( final LdapProfile ldapProfile )
            throws PwmUnrecoverableException
    {
        if ( ldapProfile == null )
        {
            throw new NullPointerException( "ldapProfile must not be null" );
        }

        final int slot = slotIncrementer.next();

        final ChaiProvider proxyChaiProvider = proxyChaiProviders.get( ldapProfile ).get( slot );

        if ( proxyChaiProvider != null )
        {
            return proxyChaiProvider;
        }

        try
        {
            final ChaiProvider newProvider = LdapOperationsHelper.openProxyChaiProvider(
                    pwmApplication,
                    null,
                    ldapProfile,
                    pwmApplication.getConfig(),
                    pwmApplication.getStatisticsManager()
            );
            proxyChaiProviders.get( ldapProfile ).put( slot, newProvider );

            return newProvider;
        }
        catch ( PwmUnrecoverableException e )
        {
            setLastLdapFailure( ldapProfile, e.getErrorInformation() );
            throw e;
        }
        catch ( Exception e )
        {
            final String errorMsg = "unexpected error creating new proxy ldap connection: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INTERNAL, errorMsg );
            LOGGER.error( errorInformation );
            throw new PwmUnrecoverableException( errorInformation );
        }
    }

    public void setLastLdapFailure( final LdapProfile ldapProfile, final ErrorInformation errorInformation )
    {
        lastLdapErrors.put( ldapProfile, errorInformation );
        final HashMap<String, ErrorInformation> outputMap = new HashMap<>();
        for ( final Map.Entry<LdapProfile, ErrorInformation> entry : lastLdapErrors.entrySet() )
        {
            final LdapProfile loopProfile = entry.getKey();
            outputMap.put( loopProfile.getIdentifier(), entry.getValue() );
        }
        final String jsonString = JsonUtil.serialize( outputMap );
        pwmApplication.writeAppAttribute( PwmApplication.AppAttribute.LAST_LDAP_ERROR, jsonString );
    }

    public Map<LdapProfile, ErrorInformation> getLastLdapFailure( )
    {
        return Collections.unmodifiableMap( lastLdapErrors );
    }

    public Instant getLastLdapFailureTime( final LdapProfile ldapProfile )
    {
        final ErrorInformation errorInformation = lastLdapErrors.get( ldapProfile );
        if ( errorInformation != null )
        {
            return errorInformation.getDate();
        }
        return null;
    }

    private static Map<LdapProfile, ErrorInformation> readLastLdapFailure( final PwmApplication pwmApplication )
    {
        String lastLdapFailureStr = null;
        try
        {
            lastLdapFailureStr = pwmApplication.readAppAttribute( PwmApplication.AppAttribute.LAST_LDAP_ERROR, String.class );
            if ( lastLdapFailureStr != null && lastLdapFailureStr.length() > 0 )
            {
                final Map<String, ErrorInformation> fromJson = JsonUtil.deserialize( lastLdapFailureStr, new TypeToken<Map<String, ErrorInformation>>()
                {
                } );
                final Map<LdapProfile, ErrorInformation> returnMap = new HashMap<>();
                for ( final Map.Entry<String, ErrorInformation> entry : fromJson.entrySet() )
                {
                    final String id = entry.getKey();
                    final LdapProfile ldapProfile = pwmApplication.getConfig().getLdapProfiles().get( id );
                    if ( ldapProfile != null )
                    {
                        returnMap.put( ldapProfile, entry.getValue() );
                    }
                }
                return returnMap;
            }
        }
        catch ( Exception e )
        {
            LOGGER.error( "unexpected error loading cached lastLdapFailure statuses: " + e.getMessage() + ", input=" + lastLdapFailureStr );
        }
        return Collections.emptyMap();
    }

    private int maxSlotsPerProfile( final PwmApplication pwmApplication )
    {
        final int maxConnections = Integer.parseInt( pwmApplication.getConfig().readAppProperty( AppProperty.LDAP_PROXY_MAX_CONNECTIONS ) );
        final int perProfile = Integer.parseInt( pwmApplication.getConfig().readAppProperty( AppProperty.LDAP_PROXY_CONNECTION_PER_PROFILE ) );
        final int profileCount = pwmApplication.getConfig().getLdapProfiles().size();

        if ( ( perProfile * profileCount ) >= maxConnections )
        {
            final int adjustedConnections = Math.min( 1, ( maxConnections / profileCount ) );
            LOGGER.warn( "connections per profile (" + perProfile + ") multiplied by number of profiles ("
                    + profileCount + ") exceeds max connections (" + maxConnections + "), will limit to " + adjustedConnections );
            return adjustedConnections;
        }

        return perProfile;
    }

    public int connectionCount( )
    {
        int count = 0;
        if ( chaiProviderFactory != null )
        {
            for ( final ChaiProvider chaiProvider : chaiProviderFactory.activeProviders() )
            {
                if ( chaiProvider.isConnected() )
                {
                    count++;
                }
            }
        }
        return count;
    }

    public ChaiProviderFactory getChaiProviderFactory( )
    {
        return chaiProviderFactory;
    }

    private enum DebugKey
    {
        ALLOCATED_CONNECTIONS,
        ACTIVE_CONNECTIONS,
        IDLE_CONNECTIONS,
    }

    private Map<String, String> connectionDebugInfo( )
    {
        int allocatedConnections = 0;
        int activeConnections = 0;
        int idleConnections = 0;
        if ( chaiProviderFactory != null )
        {
            for ( final ChaiProvider chaiProvider : chaiProviderFactory.activeProviders() )
            {
                allocatedConnections++;
                if ( chaiProvider.isConnected() )
                {
                    activeConnections++;
                }
                else
                {
                    idleConnections++;
                }
            }
        }
        final Map<String, String> debugInfo = new HashMap<>();
        debugInfo.put( DebugKey.ALLOCATED_CONNECTIONS.name(), String.valueOf( allocatedConnections ) );
        debugInfo.put( DebugKey.ACTIVE_CONNECTIONS.name(), String.valueOf( activeConnections ) );
        debugInfo.put( DebugKey.IDLE_CONNECTIONS.name(), String.valueOf( idleConnections ) );
        return Collections.unmodifiableMap( debugInfo );
    }
}
