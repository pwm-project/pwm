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
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.svc.AbstractPwmService;
import password.pwm.svc.PwmService;
import password.pwm.util.PwmScheduler;
import password.pwm.util.debug.DebugItemGenerator;
import password.pwm.util.java.AtomicLoopIntIncrementer;
import password.pwm.util.java.FileSystemUtility;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.StatisticAverageBundle;
import password.pwm.util.java.StatisticCounterBundle;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogLevel;
import password.pwm.util.logging.PwmLogger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.zip.ZipOutputStream;

public class HealthService extends AbstractPwmService implements PwmService
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( HealthService.class );

    private static final List<HealthSupplier> HEALTH_SUPPLIERS = List.of(
            new LDAPHealthChecker(),
            new JavaChecker(),
            new ConfigurationChecker(),
            new LocalDBHealthChecker(),
            new CertificateChecker() );


    private ExecutorService executorService;
    private ExecutorService supportZipWriterService;
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
            LOGGER.debug( () -> "health monitor will remain inactive due to AppProperty " + AppProperty.HEALTHCHECK_ENABLED.getKey() );
            return STATUS.CLOSED;
        }

        executorService = PwmScheduler.makeBackgroundExecutor( pwmApplication, this.getClass() );
        supportZipWriterService = PwmScheduler.makeBackgroundExecutor( pwmApplication, this.getClass() );
        scheduleNextZipOutput();

        if ( settings.getThreadDumpInterval().as( TimeDuration.Unit.SECONDS ) > 0 )
        {
            pwmApplication.getPwmScheduler().scheduleFixedRateJob( new ThreadDumpLogger(), executorService, TimeDuration.SECOND, settings.getThreadDumpInterval() );
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
            LOGGER.trace( () ->  "begin force immediate check" );
            final Future future = getPwmApplication().getPwmScheduler().scheduleJob( new ImmediateJob(), executorService, TimeDuration.ZERO );
            settings.getMaximumForceCheckWait().pause( future::isDone );
            final TimeDuration checkDuration = TimeDuration.fromCurrent( startTime );
            averageStats.update( AverageStatKey.checkProcessTime, checkDuration.asDuration() );
            counterStats.increment( CounterStatKey.checks );
            LOGGER.trace( () ->  "exit force immediate check, done=" + future.isDone(), () -> checkDuration );
        }

        getPwmApplication().getPwmScheduler().scheduleJob( new UpdateJob(), executorService, settings.getNominalCheckInterval() );

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
        if ( executorService != null )
        {
            executorService.shutdown();
        }
        if ( supportZipWriterService != null )
        {
            supportZipWriterService.shutdown();
        }
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
        LOGGER.trace( () -> "beginning health check execution #" + counter  );
        final List<HealthRecord> tempResults = new ArrayList<>();

        for ( final Supplier<List<HealthRecord>> loopSupplier : gatherSuppliers( getPwmApplication(), getSessionLabel() ) )
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
                    LOGGER.warn( () -> "unexpected error during healthCheck: " + e.getMessage(), e );
                }
            }
        }

        healthData = new HealthData( Collections.unmodifiableSet( new TreeSet<>( tempResults ) ), Instant.now() );
        LOGGER.trace( () -> "completed health check execution #" + counter, () -> TimeDuration.fromCurrent( startTime ) );
    }

    private static List<Supplier<List<HealthRecord>>> gatherSuppliers(
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
                    LOGGER.warn( () -> "unexpected error during healthCheck: " + e.getMessage(), e );
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
            try
            {
                final Instant startTime = Instant.now();
                doHealthChecks();
                LOGGER.trace( () -> "completed health check dredge", () -> TimeDuration.fromCurrent( startTime ) );
            }
            catch ( final Throwable e )
            {
                LOGGER.error( () -> "error during health check execution: " + e.getMessage(), e );
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

    private void scheduleNextZipOutput()
    {
        final int intervalSeconds = JavaHelper.silentParseInt( getPwmApplication().getConfig().readAppProperty( AppProperty.HEALTH_SUPPORT_BUNDLE_WRITE_INTERVAL_SECONDS ), 0 );
        if ( intervalSeconds > 0 )
        {
            final TimeDuration intervalDuration = TimeDuration.of( intervalSeconds, TimeDuration.Unit.SECONDS );
            getPwmApplication().getPwmScheduler().scheduleJob( new SupportZipFileWriter( getPwmApplication() ), supportZipWriterService, intervalDuration );
        }
    }

    private class SupportZipFileWriter implements Runnable
    {
        private final PwmApplication pwmApplication;

        SupportZipFileWriter( final PwmApplication pwmApplication )
        {
            this.pwmApplication = pwmApplication;
        }

        @Override
        public void run()
        {
            try
            {
                writeSupportZipToAppPath();
            }
            catch ( final Exception e )
            {
                LOGGER.debug( getSessionLabel(), () -> "error writing support zip to file system: " + e.getMessage() );
            }

            scheduleNextZipOutput();
        }

        private void writeSupportZipToAppPath()
                throws IOException, PwmUnrecoverableException
        {
            final File appPath = getPwmApplication().getPwmEnvironment().getApplicationPath();
            if ( !appPath.exists() )
            {
                return;
            }

            final int rotationCount = JavaHelper.silentParseInt( pwmApplication.getConfig().readAppProperty( AppProperty.HEALTH_SUPPORT_BUNDLE_FILE_WRITE_COUNT ), 10 );
            final DebugItemGenerator debugItemGenerator = new DebugItemGenerator( pwmApplication, getSessionLabel() );

            final File supportPath = new File( appPath.getPath() + File.separator + "support" );

            Files.createDirectories( supportPath.toPath() );

            final File supportFile = new File ( supportPath.getPath() + File.separator + debugItemGenerator.getFilename() );

            FileSystemUtility.rotateBackups( supportFile, rotationCount );

            final File newSupportFile = new File ( supportFile.getPath() + ".new" );
            Files.deleteIfExists( newSupportFile.toPath() );

            try ( ZipOutputStream zipOutputStream = new ZipOutputStream( new FileOutputStream( newSupportFile ) ) )
            {
                LOGGER.trace( getSessionLabel(), () -> "beginning periodic support bundle filesystem output" );
                debugItemGenerator.outputZipDebugFile( zipOutputStream );
            }

            Files.move( newSupportFile.toPath(), supportFile.toPath() );
        }
    }

    private static class ThreadDumpLogger implements Runnable
    {
        private static final PwmLogger LOGGER = PwmLogger.forClass( ThreadDumpLogger.class );
        private static final AtomicLoopIntIncrementer COUNTER = new AtomicLoopIntIncrementer();

        @Override
        public void run()
        {
            if ( !LOGGER.isEnabled( PwmLogLevel.TRACE ) )
            {
                return;
            }

            final int count = COUNTER.next();
            output( "---BEGIN OUTPUT THREAD DUMP #" + count + "---" );

            {
                final Map<String, String> debugValues = new LinkedHashMap<>();
                debugValues.put( "Memory Free", Long.toString( Runtime.getRuntime().freeMemory() ) );
                debugValues.put( "Memory Allocated", Long.toString( Runtime.getRuntime().totalMemory() ) );
                debugValues.put( "Memory Max", Long.toString( Runtime.getRuntime().maxMemory() ) );
                debugValues.put( "Thread Count", Integer.toString( Thread.activeCount() ) );
                output( "Thread Data #: " + StringUtil.mapToString( debugValues ) );
            }

            final ThreadInfo[] threads = ManagementFactory.getThreadMXBean().dumpAllThreads( true, true );
            for ( final ThreadInfo threadInfo : threads )
            {
                output( JavaHelper.threadInfoToString( threadInfo ) );
            }

            output( "---END OUTPUT THREAD DUMP #" + count + "---" );
        }

        private void output( final CharSequence output )
        {
            LOGGER.trace( () -> output );
        }
    }
}
