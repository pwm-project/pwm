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

package password.pwm.health;

import lombok.Value;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.bean.SessionLabel;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.servlet.configmanager.DebugItemGenerator;
import password.pwm.svc.PwmService;
import password.pwm.util.PwmScheduler;
import password.pwm.util.java.AtomicLoopIntIncrementer;
import password.pwm.util.java.FileSystemUtility;
import password.pwm.util.java.JavaHelper;
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
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipOutputStream;

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
    private ExecutorService supportZipWriterService;
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

        executorService = PwmScheduler.makeBackgroundExecutor( pwmApplication, this.getClass() );
        supportZipWriterService = PwmScheduler.makeBackgroundExecutor( pwmApplication, this.getClass() );
        scheduleNextZipOutput();

        {
            final int threadDumpIntervalSeconds = JavaHelper.silentParseInt( pwmApplication.getConfig().readAppProperty(
                    AppProperty.LOGGING_EXTRA_PERIODIC_THREAD_DUMP_INTERVAL ), 0 );
            if ( threadDumpIntervalSeconds > 0 )
            {
                final TimeDuration interval =  TimeDuration.of( threadDumpIntervalSeconds, TimeDuration.Unit.SECONDS );
                pwmApplication.getPwmScheduler().scheduleFixedRateJob( new ThreadDumpLogger(), executorService, TimeDuration.SECOND, interval );
            }
        }

        status = STATUS.OPEN;
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
            final Future future = pwmApplication.getPwmScheduler().scheduleJob( new ImmediateJob(), executorService, TimeDuration.ZERO );
            settings.getMaximumForceCheckWait().pause( future::isDone );
            LOGGER.trace( () ->  "exit force immediate check, done=" + future.isDone() + ", " + TimeDuration.compactFromCurrent( startTime ) );
        }

        pwmApplication.getPwmScheduler().scheduleJob( new UpdateJob(), executorService, settings.getNominalCheckInterval() );

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
        if ( supportZipWriterService != null )
        {
            supportZipWriterService.shutdown();
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
            catch ( final Exception e )
            {
                if ( status == STATUS.OPEN )
                {
                    LOGGER.warn( () -> "unexpected error during healthCheck: " + e.getMessage(), e );
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
            catch ( final Exception e )
            {
                if ( status == STATUS.OPEN )
                {
                    LOGGER.warn( () -> "unexpected error during healthCheck: " + e.getMessage(), e );
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
        final int intervalSeconds = JavaHelper.silentParseInt( pwmApplication.getConfig().readAppProperty( AppProperty.HEALTH_SUPPORT_BUNDLE_WRITE_INTERVAL_SECONDS ), 0 );
        if ( intervalSeconds > 0 )
        {
            final TimeDuration intervalDuration = TimeDuration.of( intervalSeconds, TimeDuration.Unit.SECONDS );
            pwmApplication.getPwmScheduler().scheduleJob( new SupportZipFileWriter( pwmApplication ), supportZipWriterService, intervalDuration );
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
                LOGGER.debug( SessionLabel.HEALTH_SESSION_LABEL, () -> "error writing support zip to file system: " + e.getMessage() );
            }

            scheduleNextZipOutput();
        }

        private void writeSupportZipToAppPath()
                throws IOException, PwmUnrecoverableException
        {
            final File appPath = pwmApplication.getPwmEnvironment().getApplicationPath();
            if ( !appPath.exists() )
            {
                return;
            }

            final int rotationCount = JavaHelper.silentParseInt( pwmApplication.getConfig().readAppProperty( AppProperty.HEALTH_SUPPORT_BUNDLE_FILE_WRITE_COUNT ), 10 );
            final DebugItemGenerator debugItemGenerator = new DebugItemGenerator( pwmApplication, SessionLabel.HEALTH_SESSION_LABEL );

            final File supportPath = new File( appPath.getPath() + File.separator + "support" );

            FileSystemUtility.mkdirs( supportPath );

            final File supportFile = new File ( supportPath.getPath() + File.separator + debugItemGenerator.getFilename() );

            FileSystemUtility.rotateBackups( supportFile, rotationCount );

            final File newSupportFile = new File ( supportFile.getPath() + ".new" );
            Files.deleteIfExists( newSupportFile.toPath() );

            try ( ZipOutputStream zipOutputStream = new ZipOutputStream( new FileOutputStream( newSupportFile ) ) )
            {
                LOGGER.trace( SessionLabel.HEALTH_SESSION_LABEL, () -> "beginning periodic support bundle filesystem output" );
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
