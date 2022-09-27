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

package password.pwm.health;

import lombok.Value;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.bean.DomainID;
import password.pwm.bean.SessionLabel;
import password.pwm.error.PwmException;
import password.pwm.svc.AbstractPwmService;
import password.pwm.svc.PwmService;
import password.pwm.util.java.StatisticAverageBundle;
import password.pwm.util.java.StatisticCounterBundle;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogManager;
import password.pwm.util.logging.PwmLogger;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class HealthService extends AbstractPwmService implements PwmService
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( HealthService.class );

    private static final List<HealthSupplier> HEALTH_SUPPLIERS = List.of(
            new LDAPHealthChecker(),
            new JavaChecker(),
            new ConfigurationChecker(),
            new LocalDBHealthChecker(),
            new CertificateChecker() );


    private HealthMonitorSettings settings;

    private final Map<HealthMonitorFlag, Serializable> healthProperties = new ConcurrentHashMap<>();
    private final AtomicInteger healthCheckCount = new AtomicInteger( 0 );

    private final StatisticCounterBundle<CounterStatKey> counterStats = new StatisticCounterBundle<>( CounterStatKey.class );
    private final StatisticAverageBundle<AverageStatKey> averageStats = new StatisticAverageBundle<>( AverageStatKey.class );

    private volatile HealthData healthData = emptyHealthData();

    enum CounterStatKey
    {
        checks,
    }

    enum AverageStatKey
    {
        checkProcessTime,
    }

    enum HealthMonitorFlag
    {
        LdapVendorSameCheck,
        AdPasswordPolicyApiCheck,
    }

    public HealthService( )
    {
    }

    @Override
    public STATUS postAbstractInit( final PwmApplication pwmApplication, final DomainID domainID )
            throws PwmException
    {
        this.healthData = emptyHealthData();
        settings = HealthMonitorSettings.fromConfiguration( pwmApplication.getConfig() );

        if ( !settings.isHealthCheckEnabled() )
        {
            LOGGER.debug( getSessionLabel(), () -> "health monitor will remain inactive due to AppProperty " + AppProperty.HEALTHCHECK_ENABLED.getKey() );
            return STATUS.CLOSED;
        }

        return STATUS.OPEN;
    }

    public Instant getLastHealthCheckTime( )
    {
        if ( status() != STATUS.OPEN )
        {
            return null;
        }

        return healthData != null ? healthData.getTimeStamp() : Instant.ofEpochMilli( 0 );
    }

    public HealthStatus getMostSevereHealthStatus( )
    {
        if ( status() != STATUS.OPEN )
        {
            return HealthStatus.GOOD;
        }
        return HealthUtils.getMostSevereHealthStatus( getHealthRecords( ) );
    }


    public Set<HealthRecord> getHealthRecords( )
    {
        if ( status() != STATUS.OPEN )
        {
            return Collections.emptySet();
        }

        if ( healthData.recordsAreOutdated() )
        {
            final Instant startTime = Instant.now();
            LOGGER.trace( getSessionLabel(), () -> "begin force immediate check" );
            final Future<?> future = scheduleJob( new ImmediateJob(), TimeDuration.ZERO );
            settings.getMaximumForceCheckWait().pause( future::isDone );
            final TimeDuration checkDuration = TimeDuration.fromCurrent( startTime );
            averageStats.update( AverageStatKey.checkProcessTime, checkDuration.asDuration() );
            counterStats.increment( CounterStatKey.checks );
            LOGGER.trace( getSessionLabel(), () -> "exit force immediate check, done=" + future.isDone(), checkDuration );
        }

        scheduleJob( new UpdateJob(), settings.getNominalCheckInterval() );

        {
            final HealthData localHealthData = this.healthData;
            if ( localHealthData.recordsAreOutdated() )
            {
                return Collections.singleton( HealthRecord.forMessage( DomainID.systemId(), HealthMessage.NoData ) );
            }

            return localHealthData.getHealthRecords();
        }
    }

    @Override
    public void shutdownImpl( )
    {
        healthData = emptyHealthData();
        setStatus( STATUS.CLOSED );
    }

    private HealthData emptyHealthData()
    {
        return new HealthData( Collections.emptySet(), Instant.ofEpochMilli( 0 ) );
    }

    @Override
    public List<HealthRecord> serviceHealthCheck( )
    {
        return Collections.emptyList();
    }


    private void doHealthChecks( )
    {
        final int counter = healthCheckCount.getAndIncrement();
        if ( status() != STATUS.OPEN )
        {
            return;
        }

        final Instant startTime = Instant.now();
        LOGGER.trace( getSessionLabel(), () -> "beginning health check execution #" + counter  );
        final List<HealthRecord> tempResults = new ArrayList<>();

        for ( final Supplier<List<HealthRecord>> loopSupplier : gatherSuppliers( getPwmApplication(), SessionLabel.HEALTH_LABEL ) )
        {
            try
            {
                final List<HealthRecord> loopResults = loopSupplier.get();
                if ( loopResults != null )
                {
                    tempResults.addAll( loopResults );
                }
            }
            catch ( final Exception e )
            {
                if ( status() == STATUS.OPEN )
                {
                    LOGGER.warn( getSessionLabel(), () -> "unexpected error during healthCheck: " + e.getMessage(), e );
                }
            }
        }

        healthData = new HealthData( Collections.unmodifiableSet( new TreeSet<>( tempResults ) ), Instant.now() );
        LOGGER.trace( getSessionLabel(), () -> "completed health check execution #" + counter, TimeDuration.fromCurrent( startTime ) );
    }

    private List<Supplier<List<HealthRecord>>> gatherSuppliers(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel
    )
    {
        final List<Supplier<List<HealthRecord>>> suppliers = new ArrayList<>();

        for ( final Map.Entry<DomainID, List<PwmService>> domainIDListEntry : pwmApplication.getAppAndDomainPwmServices().entrySet() )
        {
            for ( final PwmService service : domainIDListEntry.getValue() )
            {
                try
                {
                    final List<HealthRecord> loopResults = service.healthCheck();
                    if ( loopResults != null )
                    {
                        final Supplier<List<HealthRecord>> wrappedSupplier = () -> loopResults;
                        suppliers.add( wrappedSupplier );
                    }
                }
                catch ( final Exception e )
                {
                    LOGGER.warn( getSessionLabel(), () -> "unexpected error during healthCheck: " + e.getMessage(), e );
                }
            }
        }

        for ( final HealthSupplier supplier : HEALTH_SUPPLIERS )
        {
            suppliers.addAll( supplier.jobs( new HealthSupplier.HealthSupplierRequest( pwmApplication, sessionLabel ) ) );
        }

        return Collections.unmodifiableList( suppliers );
    }

    @Override
    public ServiceInfoBean serviceInfo( )
    {
        final Map<String, String> debugData = new HashMap<>();
        debugData.putAll( averageStats.debugStats() );
        debugData.putAll( counterStats.debugStats() );
        return ServiceInfoBean.builder()
                .debugProperties( Collections.unmodifiableMap( debugData ) )
                .build();
    }

    Map<HealthMonitorFlag, Serializable> getHealthProperties( )
    {
        return healthProperties;
    }

    private class UpdateJob implements Runnable
    {
        @Override
        public void run( )
        {
            if ( healthData.recordsAreStale() )
            {
                new ImmediateJob().run();
            }
        }
    }

    private class ImmediateJob implements Runnable
    {
        @Override
        public void run( )
        {
            PwmLogManager.executeWithThreadSessionData( getSessionLabel(), () ->
                    {
                        try
                        {
                            doHealthChecks();
                        }
                        catch ( final Throwable e )
                        {
                            LOGGER.error( getSessionLabel(), () -> "error during health check execution: " + e.getMessage(), e );
                        }
                    } );
        }
    }

    @Value
    private class HealthData
    {
        private Set<HealthRecord> healthRecords;
        private Instant timeStamp;

        private boolean recordsAreStale()
        {
            return TimeDuration.fromCurrent( this.getTimeStamp() ).isLongerThan( settings.getNominalCheckInterval() );
        }

        private boolean recordsAreOutdated()
        {
            return TimeDuration.fromCurrent( this.getTimeStamp() ).isLongerThan( settings.getMaximumRecordAge() );
        }
    }
}
