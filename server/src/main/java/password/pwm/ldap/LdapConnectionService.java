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
import com.novell.ldapchai.provider.ChaiSetting;
import com.novell.ldapchai.provider.ProviderStatistics;
import lombok.Builder;
import lombok.Data;
import lombok.Value;
import password.pwm.AppAttribute;
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
import password.pwm.util.PwmScheduler;
import password.pwm.util.java.AtomicLoopIntIncrementer;
import password.pwm.util.java.ConditionalTaskExecutor;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public class LdapConnectionService implements PwmService
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( LdapConnectionService.class );

    private final Map<String, ErrorInformation> lastLdapErrors = new ConcurrentHashMap<>();
    private final ThreadLocal<ThreadLocalContainer> threadLocalProvider = new ThreadLocal<>();
    private final Set<ThreadLocalContainer> threadLocalContainers = Collections.synchronizedSet( Collections.newSetFromMap( new WeakHashMap<>() ) );
    private final ReentrantLock reentrantLock = new ReentrantLock();
    private final ConditionalTaskExecutor debugLogger = ConditionalTaskExecutor.forPeriodicTask( this::logDebugInfo, TimeDuration.MINUTE );
    private final ChaiProviderFactory chaiProviderFactory = ChaiProviderFactory.newProviderFactory();
    private final Map<String, Map<Integer, ChaiProvider>> proxyChaiProviders = new HashMap<>();

    private PwmApplication pwmApplication;
    private ExecutorService executorService;
    private AtomicLoopIntIncrementer slotIncrementer;

    private volatile STATUS status = STATUS.NEW;

    private boolean useThreadLocal;
    private final AtomicLoopIntIncrementer statCreatedProxies = new AtomicLoopIntIncrementer();
    private final AtomicLoopIntIncrementer statClearedThreadLocals = new AtomicLoopIntIncrementer();

    private enum DebugKey
    {
        /** Providers created since application start. */
        CreatedProviders,

        /** Currently allocated providers. */
        Allocated,

        /** Currently allocated providers that have a live connection. */
        CurrentActive,

        /** Currently allocated thread locals. */
        ThreadLocals,

        /** Providers discarded since application start. */
        DiscardedThreadLocals,
    }

    public STATUS status( )
    {
        return status;
    }

    public void init( final PwmApplication pwmApplication )
            throws PwmException
    {
        this.pwmApplication = pwmApplication;

        useThreadLocal = Boolean.parseBoolean( pwmApplication.getConfig().readAppProperty( AppProperty.LDAP_PROXY_USE_THREAD_LOCAL ) );
        LOGGER.trace( () -> "threadLocal enabled: " + useThreadLocal );

        // read the lastLoginTime
        this.lastLdapErrors.putAll( readLastLdapFailure( pwmApplication ) );

        final long idleWeakTimeoutMS = JavaHelper.silentParseLong(
                pwmApplication.getConfig().readAppProperty( AppProperty.LDAP_PROXY_IDLE_THREAD_LOCAL_TIMEOUT_MS ),
                60_000 );
        final TimeDuration idleWeakTimeout = TimeDuration.of( idleWeakTimeoutMS, TimeDuration.Unit.MILLISECONDS );
        this.executorService = PwmScheduler.makeBackgroundExecutor( pwmApplication, this.getClass() );
        this.pwmApplication.getPwmScheduler().scheduleFixedRateJob( new ThreadLocalCleaner(), executorService, idleWeakTimeout, idleWeakTimeout );

        final int connectionsPerProfile = maxSlotsPerProfile( pwmApplication );
        LOGGER.trace( () -> "allocating " + connectionsPerProfile + " ldap proxy connections per profile" );
        slotIncrementer = AtomicLoopIntIncrementer.builder().ceiling( connectionsPerProfile ).build();

        for ( final LdapProfile ldapProfile : pwmApplication.getConfig().getLdapProfiles().values() )
        {
            proxyChaiProviders.put( ldapProfile.getIdentifier(), new ConcurrentHashMap<>() );
        }

        status = STATUS.OPEN;
    }

    public void close( )
    {
        status = STATUS.CLOSED;
        logDebugInfo();
        LOGGER.trace( () -> "closing ldap proxy connections" );

        try
        {
            chaiProviderFactory.close();
        }
        catch ( final Exception e )
        {
            LOGGER.error( () -> "error closing ldap proxy connection: " + e.getMessage(), e );
        }

        proxyChaiProviders.clear();

        iterateThreadLocals( container -> container.getProviderMap().clear() );
        executorService.shutdown();
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
        if ( status != STATUS.OPEN )
        {
            throw new IllegalStateException( "unable to obtain proxy chai provider from closed LdapConnectionService" );
        }

        debugLogger.conditionallyExecuteTask();

        final LdapProfile effectiveProfile = ldapProfile == null
                ? pwmApplication.getConfig().getDefaultLdapProfile()
                : ldapProfile;

        if ( useThreadLocal )
        {
            return getThreadLocalChaiProvider( effectiveProfile );
        }

        return getSharedLocalChaiProvider( effectiveProfile );
    }

    private ChaiProvider getSharedLocalChaiProvider( final LdapProfile ldapProfile )
            throws PwmUnrecoverableException
    {
        final int slot = slotIncrementer.next();
        final ChaiProvider proxyChaiProvider = proxyChaiProviders.get( ldapProfile.getIdentifier() ).get( slot );

        if ( proxyChaiProvider == null )
        {
            final ChaiProvider newProvider = newProxyChaiProvider( ldapProfile );
            proxyChaiProviders.get( ldapProfile.getIdentifier() ).put( slot, newProvider );
            return newProvider;
        }

        return proxyChaiProvider;
    }

    private ChaiProvider getThreadLocalChaiProvider( final LdapProfile ldapProfile )
            throws PwmUnrecoverableException
    {
        reentrantLock.lock();
        try
        {
            if ( threadLocalProvider.get() == null )
            {
                final ThreadLocalContainer threadLocalContainer = new ThreadLocalContainer();
                threadLocalProvider.set( threadLocalContainer );
                threadLocalContainers.add( threadLocalContainer );
            }

            final String profileID = ldapProfile.getIdentifier();
            final ThreadLocalContainer threadLocalContainer = threadLocalProvider.get();

            if ( !threadLocalContainer.getProviderMap().containsKey( profileID ) )
            {
                final ChaiProvider chaiProvider = newProxyChaiProvider( ldapProfile );
                threadLocalContainer.getProviderMap().put( profileID, chaiProvider );
            }

            threadLocalContainer.setTimestamp( Instant.now() );
            threadLocalContainer.setThreadName( Thread.currentThread().getName() );
            return threadLocalContainer.getProviderMap().get( profileID );
        }
        finally
        {
            reentrantLock.unlock();
        }
    }

    private ChaiProvider newProxyChaiProvider( final LdapProfile ldapProfile )
            throws PwmUnrecoverableException
    {
        Objects.requireNonNull( ldapProfile, "ldapProfile must not be null" );

        try
        {
            final ChaiProvider chaiProvider = LdapOperationsHelper.openProxyChaiProvider(
                    pwmApplication,
                    null,
                    ldapProfile,
                    pwmApplication.getConfig(),
                    pwmApplication.getStatisticsManager()
            );
            LOGGER.trace( () -> "created new system proxy chaiProvider id=" + chaiProvider.toString()
                    + " for ldap profile '" + ldapProfile.getIdentifier() + "'"
                    + " thread=" + Thread.currentThread().getName() );
            statCreatedProxies.next();
            return chaiProvider;
        }
        catch ( final PwmUnrecoverableException e )
        {
            setLastLdapFailure( ldapProfile, e.getErrorInformation() );
            throw e;
        }
        catch ( final Exception e )
        {
            final String errorMsg = "unexpected error creating new proxy ldap connection: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INTERNAL, errorMsg );
            LOGGER.error( errorInformation );
            throw new PwmUnrecoverableException( errorInformation );
        }
    }

    public void setLastLdapFailure( final LdapProfile ldapProfile, final ErrorInformation errorInformation )
    {
        lastLdapErrors.put( ldapProfile.getIdentifier(), errorInformation );
        final String jsonString = JsonUtil.serializeMap( lastLdapErrors );
        pwmApplication.writeAppAttribute( AppAttribute.LAST_LDAP_ERROR, jsonString );
    }

    public Map<String, ErrorInformation> getLastLdapFailure( )
    {
        return Collections.unmodifiableMap( lastLdapErrors );
    }

    public Instant getLastLdapFailureTime( final LdapProfile ldapProfile )
    {
        final ErrorInformation errorInformation = lastLdapErrors.get( ldapProfile.getIdentifier() );
        if ( errorInformation != null )
        {
            return errorInformation.getDate();
        }
        return null;
    }

    private static Map<String, ErrorInformation> readLastLdapFailure( final PwmApplication pwmApplication )
    {
        String lastLdapFailureStr = null;
        try
        {
            lastLdapFailureStr = pwmApplication.readAppAttribute( AppAttribute.LAST_LDAP_ERROR, String.class );
            if ( lastLdapFailureStr != null && lastLdapFailureStr.length() > 0 )
            {
                final Map<String, ErrorInformation> fromJson = JsonUtil.deserialize( lastLdapFailureStr, new TypeToken<Map<String, ErrorInformation>>()
                {
                } );
                final Map<String, ErrorInformation> returnMap = new HashMap<>( fromJson );
                returnMap.keySet().retainAll( pwmApplication.getConfig().getLdapProfiles().keySet() );
                return returnMap;
            }
        }
        catch ( final Exception e )
        {
            final  String lastFailureStrFinal = lastLdapFailureStr;
            LOGGER.error( () -> "unexpected error loading cached lastLdapFailure statuses: " + e.getMessage() + ", input=" + lastFailureStrFinal );
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
            LOGGER.warn( () -> "connections per profile (" + perProfile + ") multiplied by number of profiles ("
                    + profileCount + ") exceeds max connections (" + maxConnections + "), will limit to " + adjustedConnections );
            return adjustedConnections;
        }

        return perProfile;
    }

    public int connectionCount( )
    {
        int count = 0;
        for ( final ChaiProvider chaiProvider : chaiProviderFactory.activeProviders() )
        {
            if ( chaiProvider.isConnected() )
            {
                count++;
            }
        }
        return count;
    }

    public ChaiProviderFactory getChaiProviderFactory( )
    {
        if ( status != STATUS.OPEN )
        {
            throw new IllegalStateException( "unable to obtain chai provider factory from closed LdapConnectionService" );
        }

        return chaiProviderFactory;
    }

    private void logDebugInfo()
    {
        LOGGER.trace( () -> "status: " + StringUtil.mapToString( connectionDebugInfo() ) );
    }

    public List<ConnectionInfo> getConnectionInfos()
    {
        final Map<String, ConnectionInfo> returnData = new TreeMap<>(  );
        for ( final ChaiProvider chaiProvider : chaiProviderFactory.activeProviders() )
        {
            final String bindDN = chaiProvider.getChaiConfiguration().getSetting( ChaiSetting.BIND_DN );
            final ConnectionInfo connectionInfo = ConnectionInfo.builder()
                    .bindDN( bindDN )
                    .active( chaiProvider.isConnected() )
                    .operationCount( chaiProvider.getProviderStatistics().getIncrementorStatistic( ProviderStatistics.IncrementerStatistic.OPERATION_COUNT ) )
                    .modifyCount( chaiProvider.getProviderStatistics().getIncrementorStatistic( ProviderStatistics.IncrementerStatistic.MODIFY_COUNT ) )
                    .readCount( chaiProvider.getProviderStatistics().getIncrementorStatistic( ProviderStatistics.IncrementerStatistic.READ_COUNT ) )
                    .searchCount( chaiProvider.getProviderStatistics().getIncrementorStatistic( ProviderStatistics.IncrementerStatistic.SEARCH_COUNT ) )
                    .build();

            returnData.put( bindDN, connectionInfo );
        }
        return Collections.unmodifiableList( new ArrayList<>( returnData.values() ) );
    }

    @Value
    @Builder
    public static class ConnectionInfo implements Serializable
    {
        private final String bindDN;
        private final boolean active;
        private final long operationCount;
        private final long modifyCount;
        private final long readCount;
        private final long searchCount;
    }

    private Map<String, String> connectionDebugInfo( )
    {
        final int allocatedConnections = chaiProviderFactory.activeProviders().size();
        final int activeConnections = connectionCount();

        final AtomicInteger threadLocalConnections = new AtomicInteger( 0 );
        iterateThreadLocals( container -> threadLocalConnections.set( threadLocalConnections.intValue() + container.getProviderMap().size() ) );

        final Map<DebugKey, String> debugInfo = new TreeMap<>();
        debugInfo.put( DebugKey.Allocated, String.valueOf( allocatedConnections ) );
        debugInfo.put( DebugKey.CurrentActive, String.valueOf( activeConnections ) );
        debugInfo.put( DebugKey.ThreadLocals, String.valueOf( threadLocalConnections.get( ) ) );
        debugInfo.put( DebugKey.CreatedProviders, String.valueOf( statCreatedProxies.get() ) );
        debugInfo.put( DebugKey.DiscardedThreadLocals, String.valueOf( statClearedThreadLocals.get() ) );
        return Collections.unmodifiableMap( JavaHelper.enumMapToStringMap( debugInfo ) );
    }

    @Data
    private static class ThreadLocalContainer
    {
        private final Map<String, ChaiProvider> providerMap = new ConcurrentHashMap<>();
        private volatile Instant timestamp = Instant.now();
        private volatile String threadName;
    }

    private class ThreadLocalCleaner implements Runnable
    {
        @Override
        public void run()
        {
            cleanupIssuedThreadLocals();
            debugLogger.conditionallyExecuteTask();
        }

        private void cleanupIssuedThreadLocals()
        {
            final TimeDuration maxIdleTime = TimeDuration.MINUTE;

            iterateThreadLocals( container ->
            {
                if ( !container.getProviderMap().isEmpty() )
                {
                    final Instant timestamp = container.getTimestamp();
                    final TimeDuration age = TimeDuration.fromCurrent( timestamp );
                    if ( age.isLongerThan( maxIdleTime ) )
                    {
                        for ( final ChaiProvider chaiProvider : container.getProviderMap().values() )
                        {
                            LOGGER.trace( () -> "discarding idled connection id=" + chaiProvider.toString() + " from orphaned threadLocal, age="
                                    + age.asCompactString() + ", thread=" + container.getThreadName() );
                            statClearedThreadLocals.next();
                        }
                        container.getProviderMap().clear();
                    }
                }
            } );
        }
    }

    private void iterateThreadLocals( final Consumer<ThreadLocalContainer> consumer )
    {
        reentrantLock.lock();
        try
        {
            for ( final ThreadLocalContainer container : new HashSet<>( threadLocalContainers ) )
            {
                consumer.accept( container );
            }
        }
        finally
        {
            reentrantLock.unlock();
        }
    }
}
