/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
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

package password.pwm.health;

import lombok.Value;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.error.PwmException;
import password.pwm.svc.PwmService;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

public class HealthMonitor implements PwmService
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( HealthMonitor.class );

    private static final List<HealthChecker> HEALTH_CHECKERS;

    static
    {
        final List<HealthChecker> records = new ArrayList<>();
        records.add( new LDAPHealthChecker() );
        records.add( new JavaChecker() );
        records.add( new ConfigurationChecker() );
        records.add( new LocalDBHealthChecker() );
        records.add( new CertificateChecker() );
        records.add( new ApplianceStatusChecker() );
        HEALTH_CHECKERS = records;
    }

    private ExecutorService executorService;
    private HealthMonitorSettings settings;

    private Map<HealthMonitorFlag, Serializable> healthProperties = new ConcurrentHashMap<>();

    private STATUS status = STATUS.NEW;
    private PwmApplication pwmApplication;
    private volatile HealthData healthData = emptyHealthData();

    enum HealthMonitorFlag
    {
        LdapVendorSameCheck,
        AdPasswordPolicyApiCheck,
    }

    public HealthMonitor( )
    {
    }

    public Instant getLastHealthCheckTime( )
    {
        if ( status != STATUS.OPEN )
        {
            return null;
        }
        final HealthData healthData = this.healthData;
        return healthData != null ? healthData.getTimeStamp() : Instant.ofEpochMilli( 0 );
    }

    public HealthStatus getMostSevereHealthStatus( )
    {
        if ( status != STATUS.OPEN )
        {
            return HealthStatus.GOOD;
        }
        return getMostSevereHealthStatus( getHealthRecords( ) );
    }

    public static HealthStatus getMostSevereHealthStatus( final Collection<HealthRecord> healthRecords )
    {
        HealthStatus returnStatus = HealthStatus.GOOD;
        if ( healthRecords != null )
        {
            for ( final HealthRecord record : healthRecords )
            {
                if ( record.getStatus().getSeverityLevel() > returnStatus.getSeverityLevel() )
                {
                    returnStatus = record.getStatus();
                }
            }
        }
        return returnStatus;
    }

    public STATUS status( )
    {
        return status;
    }

    public void init( final PwmApplication pwmApplication ) throws PwmException
    {
        status = STATUS.OPENING;
        this.pwmApplication = pwmApplication;
        settings = HealthMonitorSettings.fromConfiguration( pwmApplication.getConfig() );

        if ( !Boolean.parseBoolean( pwmApplication.getConfig().readAppProperty( AppProperty.HEALTHCHECK_ENABLED ) ) )
        {
            LOGGER.debug( () -> "health monitor will remain inactive due to AppProperty " + AppProperty.HEALTHCHECK_ENABLED.getKey() );
            status = STATUS.CLOSED;
            return;
        }

        executorService = JavaHelper.makeBackgroundExecutor( pwmApplication, this.getClass() );

        status = STATUS.OPEN;
    }

    public Set<HealthRecord> getHealthRecords( )
    {
        if ( status != STATUS.OPEN )
        {
            return Collections.emptySet();
        }

        if ( healthData.recordsAreOutdated() )
        {
            final Instant startTime = Instant.now();
            LOGGER.trace( () ->  "begin force immediate check" );
            final Future future = pwmApplication.scheduleFutureJob( new ImmediateJob(), executorService, TimeDuration.ZERO );
            JavaHelper.pause( settings.getMaximumForceCheckWait().asMillis(), 500, o -> future.isDone() );
            LOGGER.trace( () ->  "exit force immediate check, done=" + future.isDone() + ", " + TimeDuration.compactFromCurrent( startTime ) );
        }

        pwmApplication.scheduleFutureJob( new UpdateJob(), executorService, settings.getNominalCheckInterval() );

        {
            final HealthData localHealthData = this.healthData;
            if ( localHealthData.recordsAreOutdated() )
            {
                return Collections.singleton( HealthRecord.forMessage( HealthMessage.NoData ) );
            }

            return localHealthData.getHealthRecords();
        }
    }

    public void close( )
    {
        if ( executorService != null )
        {
            executorService.shutdown();
        }
        healthData = emptyHealthData();
        status = STATUS.CLOSED;
    }

    private HealthData emptyHealthData()
    {
        return new HealthData( Collections.emptySet(), Instant.ofEpochMilli( 0 ) );
    }

    public List<HealthRecord> healthCheck( )
    {
        return Collections.emptyList();
    }

    private AtomicInteger healthCheckCount = new AtomicInteger( 0 );

    private void doHealthChecks( )
    {
        final int counter = healthCheckCount.getAndIncrement();
        if ( status != STATUS.OPEN )
        {
            return;
        }

        final Instant startTime = Instant.now();
        LOGGER.trace( () -> "beginning health check execution (" + counter + ")" );
        final List<HealthRecord> tempResults = new ArrayList<>();
        for ( final HealthChecker loopChecker : HEALTH_CHECKERS )
        {
            try
            {
                final List<HealthRecord> loopResults = loopChecker.doHealthCheck( pwmApplication );
                if ( loopResults != null )
                {
                    tempResults.addAll( loopResults );
                }
            }
            catch ( Exception e )
            {
                if ( status == STATUS.OPEN )
                {
                    LOGGER.warn( "unexpected error during healthCheck: " + e.getMessage(), e );
                }
            }
        }
        for ( final PwmService service : pwmApplication.getPwmServices() )
        {
            try
            {
                final List<HealthRecord> loopResults = service.healthCheck();
                if ( loopResults != null )
                {
                    tempResults.addAll( loopResults );
                }
            }
            catch ( Exception e )
            {
                if ( status == STATUS.OPEN )
                {
                    LOGGER.warn( "unexpected error during healthCheck: " + e.getMessage(), e );
                }
            }
        }

        healthData = new HealthData( Collections.unmodifiableSet( new TreeSet<>( tempResults ) ), Instant.now() );
        LOGGER.trace( () -> "completed health check execution (" + counter + ") in " + TimeDuration.compactFromCurrent( startTime ) );
    }

    public ServiceInfoBean serviceInfo( )
    {
        return new ServiceInfoBean( Collections.emptyList() );
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
            try
            {
                final Instant startTime = Instant.now();
                doHealthChecks();
                LOGGER.trace( () -> "completed health check dredge " + TimeDuration.compactFromCurrent( startTime ) );
            }
            catch ( Throwable e )
            {
                LOGGER.error( "error during health check execution: " + e.getMessage(), e );
            }
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
