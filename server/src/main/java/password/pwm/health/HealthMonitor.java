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
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class HealthMonitor implements PwmService
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( HealthMonitor.class );

    private PwmApplication pwmApplication;

    private final Set<HealthRecord> healthRecords = new TreeSet<>();

    private static final List<HealthChecker> HEALTH_CHECKERS;

    static
    {
        final List<HealthChecker> records = new ArrayList<>();
        records.add( new LDAPStatusChecker() );
        records.add( new JavaChecker() );
        records.add( new ConfigurationChecker() );
        records.add( new LocalDBHealthChecker() );
        records.add( new CertificateChecker() );
        records.add( new ApplianceStatusChecker() );
        HEALTH_CHECKERS = records;
    }

    private ScheduledExecutorService executorService;
    private HealthMonitorSettings settings;

    private volatile Instant lastHealthCheckTime = Instant.ofEpochMilli( 0 );
    private volatile Instant lastRequestedUpdateTime = Instant.ofEpochMilli( 0 );

    private Map<HealthMonitorFlag, Serializable> healthProperties = new HashMap<>();

    private STATUS status = STATUS.NEW;

    enum HealthMonitorFlag
    {
        LdapVendorSameCheck,
        AdPasswordPolicyApiCheck,
    }

    public enum CheckTimeliness
    {
        /* Execute update immediately and wait for results */
        Immediate,

        /* Take current data unless its ancient */
        CurrentButNotAncient,

        /* Take current data even if its ancient and never block */
        NeverBlock,
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
        return lastHealthCheckTime;
    }

    public HealthStatus getMostSevereHealthStatus( final CheckTimeliness timeliness )
    {
        if ( status != STATUS.OPEN )
        {
            return HealthStatus.GOOD;
        }
        return getMostSevereHealthStatus( getHealthRecords( timeliness ) );
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
            LOGGER.debug( "health monitor will remain inactive due to AppProperty " + AppProperty.HEALTHCHECK_ENABLED.getKey() );
            status = STATUS.CLOSED;
            return;
        }


        executorService = Executors.newSingleThreadScheduledExecutor(
                JavaHelper.makePwmThreadFactory(
                        JavaHelper.makeThreadName( pwmApplication, this.getClass() ) + "-",
                        true
                ) );


        executorService.scheduleAtFixedRate( new ScheduledUpdater(), 0, settings.getNominalCheckInterval().getTotalMilliseconds(), TimeUnit.MILLISECONDS );

        status = STATUS.OPEN;
    }

    public Set<HealthRecord> getHealthRecords( final CheckTimeliness timeliness )
    {
        if ( status != STATUS.OPEN )
        {
            return Collections.emptySet();
        }

        lastRequestedUpdateTime = Instant.now();

        {
            final boolean recordsAreStale = TimeDuration.fromCurrent( lastHealthCheckTime ).isLongerThan( settings.getMaximumRecordAge() );
            if ( timeliness == CheckTimeliness.Immediate || ( timeliness == CheckTimeliness.CurrentButNotAncient && recordsAreStale ) )
            {
                final ScheduledFuture updateTask = executorService.schedule( new ImmediateUpdater(), 0, TimeUnit.NANOSECONDS );
                final Date beginWaitTime = new Date();
                while ( !updateTask.isDone() && TimeDuration.fromCurrent( beginWaitTime ).isShorterThan( settings.getMaximumForceCheckWait() ) )
                {
                    JavaHelper.pause( 500 );
                }
            }
        }

        final boolean recordsAreStale = TimeDuration.fromCurrent( lastHealthCheckTime ).isLongerThan( settings.getMaximumRecordAge() );
        if ( recordsAreStale )
        {
            return Collections.singleton( HealthRecord.forMessage( HealthMessage.NoData ) );
        }

        return Collections.unmodifiableSet( healthRecords );
    }

    public void close( )
    {
        if ( executorService != null )
        {
            executorService.shutdown();
        }
        healthRecords.clear();
        status = STATUS.CLOSED;
    }

    public List<HealthRecord> healthCheck( )
    {
        return Collections.emptyList();
    }

    private void doHealthChecks( )
    {
        if ( status != STATUS.OPEN )
        {
            return;
        }

        final TimeDuration timeSinceLastUpdate = TimeDuration.fromCurrent( lastHealthCheckTime );
        if ( timeSinceLastUpdate.isShorterThan( settings.getMinimumCheckInterval().getTotalMilliseconds(), TimeUnit.MILLISECONDS ) )
        {
            return;
        }

        final Instant startTime = Instant.now();
        LOGGER.trace( "beginning background health check process" );
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
        healthRecords.clear();
        healthRecords.addAll( tempResults );
        lastHealthCheckTime = Instant.now();
        LOGGER.trace( "health check process completed (" + TimeDuration.fromCurrent( startTime ).asCompactString() + ")" );
    }

    public ServiceInfoBean serviceInfo( )
    {
        return new ServiceInfoBean( Collections.emptyList() );
    }

    public Map<HealthMonitorFlag, Serializable> getHealthProperties( )
    {
        return healthProperties;
    }

    private class ScheduledUpdater implements Runnable
    {
        @Override
        public void run( )
        {
            final TimeDuration timeSinceLastRequest = TimeDuration.fromCurrent( lastRequestedUpdateTime );
            if ( timeSinceLastRequest.isShorterThan( settings.getNominalCheckInterval().getTotalMilliseconds() + 1000, TimeUnit.MILLISECONDS ) )
            {
                try
                {
                    doHealthChecks();
                }
                catch ( Throwable e )
                {
                    LOGGER.error( "error during health check execution: " + e.getMessage(), e );

                }
            }
        }
    }

    private class ImmediateUpdater implements Runnable
    {
        @Override
        public void run( )
        {
            final TimeDuration timeSinceLastUpdate = TimeDuration.fromCurrent( lastHealthCheckTime );
            if ( timeSinceLastUpdate.isLongerThan( settings.getMinimumCheckInterval().getTotalMilliseconds(), TimeUnit.MILLISECONDS ) )
            {
                try
                {
                    doHealthChecks();
                }
                catch ( Throwable e )
                {
                    LOGGER.error( "error during health check execution: " + e.getMessage(), e );
                }
            }
        }
    }

}
