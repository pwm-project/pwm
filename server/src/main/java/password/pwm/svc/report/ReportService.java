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

package password.pwm.svc.report;

import password.pwm.AppAttribute;
import password.pwm.PwmApplication;
import password.pwm.PwmDomain;
import password.pwm.bean.DomainID;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.config.value.data.UserPermission;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthRecord;
import password.pwm.ldap.UserInfo;
import password.pwm.ldap.UserInfoFactory;
import password.pwm.ldap.permission.UserPermissionUtility;
import password.pwm.svc.AbstractPwmService;
import password.pwm.svc.PwmService;
import password.pwm.util.EventRateMeter;
import password.pwm.util.PwmScheduler;
import password.pwm.util.TransactionSizeCalculator;
import password.pwm.util.java.AverageTracker;
import password.pwm.util.java.BlockingThreadPool;
import password.pwm.util.java.ClosableIterator;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.MiscUtil;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBException;
import password.pwm.util.localdb.LocalDBStoredQueue;
import password.pwm.util.logging.PwmLogger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


public class ReportService extends AbstractPwmService implements PwmService
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( ReportService.class );

    private final AverageTracker avgTracker = new AverageTracker( 100 );

    private final AtomicBoolean cancelFlag = new AtomicBoolean( false );
    private ReportSummaryData summaryData = ReportSummaryData.newSummaryData( null );
    private ExecutorService executorService;

    private ReportRecordLocalDBStorageService userCacheService;
    private ReportSettings settings = ReportSettings.builder().build();

    private Queue<String> dnQueue;

    private final AtomicReference<ReportStatusInfo> reportStatus = new AtomicReference<>( ReportStatusInfo.builder().build() );
    private final EventRateMeter processRateMeter = new EventRateMeter( TimeDuration.of( 5, TimeDuration.Unit.MINUTES ) );


    public ReportService( )
    {
    }

    public enum ReportCommand
    {
        Start,
        Stop,
        Clear,
    }

    protected STATUS postAbstractInit( final PwmApplication pwmApplication, final DomainID domainID )
            throws PwmException
    {
        try
        {
            userCacheService = new ReportRecordLocalDBStorageService();
            userCacheService.init( this.getPwmApplication(), domainID );
        }
        catch ( final Exception e )
        {
            LOGGER.error( getSessionLabel(), () -> "unable to init cache service" );
            return STATUS.CLOSED;
        }

        settings = ReportSettings.readSettingsFromConfig( this.getPwmApplication().getConfig() );
        summaryData = ReportSummaryData.newSummaryData( settings.getTrackDays() );

        dnQueue = LocalDBStoredQueue.createLocalDBStoredQueue( pwmApplication, getPwmApplication().getLocalDB(), LocalDB.DB.REPORT_QUEUE );

        executorService = PwmScheduler.makeBackgroundExecutor( pwmApplication, this.getClass() );

        if ( !pwmApplication.getPwmEnvironment().isInternalRuntimeInstance() )
        {
            executorService.submit( new InitializationTask() );
        }

        return STATUS.OPEN;
    }

    @Override
    public void shutdownImpl( )
    {
        setStatus( STATUS.CLOSED );
        cancelFlag.set( true );

        JavaHelper.closeAndWaitExecutor( executorService, TimeDuration.SECONDS_10 );

        if ( userCacheService != null )
        {
            userCacheService.shutdown();
        }

        executorService = null;
    }

    private void writeReportStatus( )
    {
        if ( status() != STATUS.OPEN )
        {
            return;
        }

        try
        {
            getPwmApplication().writeAppAttribute( AppAttribute.REPORT_STATUS, reportStatus.get() );
        }
        catch ( final Exception e )
        {
            LOGGER.error( getSessionLabel(), () -> "error writing cached report dredge info into memory: " + e.getMessage() );
        }
    }


    @Override
    public List<HealthRecord> serviceHealthCheck( )
    {
        return Collections.emptyList();
    }

    @Override
    public ServiceInfoBean serviceInfo( )
    {
        return ServiceInfoBean.builder().storageMethod( DataStorageMethod.LDAP ).build();
    }

    public void executeCommand( final ReportCommand reportCommand )
    {
        switch ( reportCommand )
        {
            case Start:
            {
                final ReportStatusInfo localReportStatus = reportStatus.get();
                if ( localReportStatus.getCurrentProcess() != ReportStatusInfo.ReportEngineProcess.ReadData
                        && localReportStatus.getCurrentProcess() != ReportStatusInfo.ReportEngineProcess.SearchLDAP
                )
                {
                    executorService.execute( new ClearTask() );
                    executorService.execute( new ReadLDAPTask() );
                }
            }
            break;

            case Stop:
            {
                cancelFlag.set( true );
                clearWorkQueue();
            }
            break;

            case Clear:
            {
                cancelFlag.set( true );
                executorService.execute( new ClearTask() );
            }
            break;

            default:
                MiscUtil.unhandledSwitchStatement( reportCommand );
        }
    }

    public BigDecimal getEventRate( )
    {
        return processRateMeter.readEventRate();
    }

    public long getTotalRecords( )
    {
        return userCacheService.size();
    }

    private void clearWorkQueue( )
    {
        reportStatus.updateAndGet( reportStatusInfo -> reportStatusInfo.toBuilder()
                .count( 0 )
                .jobDuration( TimeDuration.ZERO )
                .build() );

        dnQueue.clear();
    }

    private void resetJobStatus( )
    {
        cancelFlag.set( false );
        processRateMeter.reset();

        reportStatus.updateAndGet( reportStatusInfo -> reportStatusInfo.toBuilder()
                .lastError( null )
                .errors( 0 )
                .finishDate( null )
                .reportComplete( false )
                .build() );

        writeReportStatus();
    }


    public ReportStatusInfo getReportStatusInfo( )
    {
        return reportStatus.get();
    }


    public ClosableIterator<UserReportRecord> iterator( )
    {
        return new ClosableIterator<>()
        {
            private final ReportRecordLocalDBStorageService.UserStatusCacheBeanIterator<UserIdentity> storageKeyIterator = userCacheService.iterator();

            @Override
            public boolean hasNext( )
            {
                return this.storageKeyIterator.hasNext();
            }

            @Override
            public UserReportRecord next( )
            {
                try
                {
                    while ( this.storageKeyIterator.hasNext() )
                    {
                        final UserIdentity key = this.storageKeyIterator.next();
                        final Optional<UserReportRecord> returnBean = userCacheService.readStorageKey( key );
                        if ( returnBean.isPresent() )
                        {
                            return returnBean.get();
                        }

                    }
                }
                catch ( final LocalDBException e )
                {
                    throw new IllegalStateException( "unexpected iterator traversal error while reading LocalDB: " + e.getMessage() );
                }
                return null;
            }

            @Override
            public void remove( )
            {

            }

            @Override
            public void close( )
            {
                storageKeyIterator.close();
            }
        };
    }


    public ReportSummaryData getSummaryData( )
    {
        return summaryData;
    }

    public int getWorkQueueSize( )
    {
        return dnQueue.size();
    }

    private class ReadLDAPTask implements Runnable
    {
        @Override
        public void run( )
        {
            reportStatus.updateAndGet( reportStatusInfo -> reportStatusInfo.toBuilder()
                    .currentProcess( ReportStatusInfo.ReportEngineProcess.SearchLDAP )
                    .build() );
            try
            {
                readUserListFromLdap();
                executorService.execute( new ProcessWorkQueueTask() );
            }
            catch ( final Exception e )
            {
                boolean errorProcessed = false;
                if ( e instanceof PwmException )
                {
                    if ( ( ( PwmException ) e ).getErrorInformation().getError() == PwmError.ERROR_DIRECTORY_UNAVAILABLE )
                    {
                        if ( executorService != null )
                        {
                            LOGGER.error( getSessionLabel(),
                                    () -> "directory unavailable error during background SearchLDAP, will retry; error: " + e.getMessage() );
                            getPwmApplication().getPwmScheduler().scheduleJob( new ReadLDAPTask(), executorService, TimeDuration.of( 10, TimeDuration.Unit.MINUTES ) );
                            errorProcessed = true;
                        }
                    }
                }

                if ( !errorProcessed )
                {
                    LOGGER.error( getSessionLabel(), () -> "error during background ReadData: " + e.getMessage(), e );
                }
            }
            finally
            {
                resetCurrentProcess();
            }
        }

        private void readUserListFromLdap( )
                throws PwmUnrecoverableException, PwmOperationalException
        {
            final Instant startTime = Instant.now();

            resetJobStatus();
            clearWorkQueue();

            for ( final PwmDomain pwmDomain : getPwmApplication().domains().values() )
            {
                final Instant loopStartTime = Instant.now();
                LOGGER.trace( getSessionLabel(), () -> "beginning ldap search process for domain '" + pwmDomain.getDomainID() + "'" );

                final List<UserPermission> searchFilters = settings.getSearchFilter().get( pwmDomain.getDomainID() );

                final Iterator<UserIdentity> searchResults = UserPermissionUtility.discoverMatchingUsers(
                        pwmDomain,
                        searchFilters,
                        getSessionLabel(),
                        settings.getMaxSearchSize(),
                        settings.getSearchTimeout() );

                LOGGER.trace(
                        getSessionLabel(),
                        () -> "completed ldap search process with for domain '" + pwmDomain.getDomainID() + "'",
                        () -> TimeDuration.fromCurrent( loopStartTime ) );

                writeUsersToLocalDBQueue( searchResults );
            }

            LOGGER.trace(
                    getSessionLabel(),
                    () -> "completed ldap search process with entries for " + getPwmApplication().domains().size() + " domains",
                    () -> TimeDuration.fromCurrent( startTime ) );

        }

        private void writeUsersToLocalDBQueue( final Iterator<UserIdentity> identityQueue )
        {
            final Instant startTime = Instant.now();
            LOGGER.trace( getSessionLabel(), () -> "transferring search results to work queue" );

            final TransactionSizeCalculator transactionCalculator = new TransactionSizeCalculator(
                    TransactionSizeCalculator.Settings.builder()
                            .durationGoal( TimeDuration.SECOND )
                            .minTransactions( 10 )
                            .maxTransactions( 100 * 1000 )
                            .build()
            );

            while ( !cancelFlag.get() && identityQueue.hasNext() )
            {
                final Instant loopStart = Instant.now();
                final List<String> bufferList = new ArrayList<>();
                final int loopCount = transactionCalculator.getTransactionSize();
                while ( !cancelFlag.get() && identityQueue.hasNext() && bufferList.size() < loopCount )
                {
                    bufferList.add( identityQueue.next().toDelimitedKey() );
                }
                dnQueue.addAll( bufferList );
                transactionCalculator.recordLastTransactionDuration( TimeDuration.fromCurrent( loopStart ) );
            }
            LOGGER.trace( getSessionLabel(),
                    () -> "completed transfer of ldap search results to work queue", () -> TimeDuration.fromCurrent( startTime ) );
        }
    }


    private class ProcessWorkQueueTask implements Runnable
    {
        private final Lock updateTimeLock = new ReentrantLock();

        @Override
        public void run( )
        {
            reportStatus.updateAndGet( reportStatusInfo -> reportStatusInfo.toBuilder()
                    .currentProcess( ReportStatusInfo.ReportEngineProcess.ReadData )
                    .build() );
            try
            {
                processWorkQueue();
                if ( status() == STATUS.OPEN )
                {
                    reportStatus.updateAndGet( reportStatusInfo -> reportStatusInfo.toBuilder()
                            .reportComplete( true )
                            .build() );
                    writeReportStatus();
                }
            }
            catch ( final PwmException e )
            {
                if ( e.getErrorInformation().getError() == PwmError.ERROR_DIRECTORY_UNAVAILABLE )
                {
                    if ( executorService != null )
                    {
                        LOGGER.error( getSessionLabel(), () -> "directory unavailable error during background ReadData, will retry; error: " + e.getMessage() );
                        getPwmApplication().getPwmScheduler().scheduleJob(
                                new ProcessWorkQueueTask(), executorService, TimeDuration.of( 10, TimeDuration.Unit.MINUTES ) );
                    }
                }
                else
                {
                    LOGGER.error( getSessionLabel(), () -> "error during background ReadData: " + e.getMessage(), e );
                }
            }
            finally
            {
                resetCurrentProcess();
            }
        }

        private void processWorkQueue( )
                throws PwmUnrecoverableException
        {
            LOGGER.debug( getSessionLabel(), () -> "beginning process to updating user cache records from ldap" );
            if ( status() != STATUS.OPEN )
            {
                return;
            }

            if ( dnQueue.isEmpty() )
            {
                return;
            }

            resetJobStatus();

            final int threadCount = settings.getReportJobIntensity() == ReportSettings.JobIntensity.HIGH
                    ? settings.getReportJobThreads()
                    : 1;

            final boolean pauseBetweenIterations = settings.getReportJobIntensity() == ReportSettings.JobIntensity.LOW;


            try
            {
                LOGGER.trace( getSessionLabel(), () -> "about to begin ldap processing with thread count of " + threadCount );
                final String threadName = PwmScheduler.makeThreadName( getPwmApplication(), this.getClass() );
                final BlockingThreadPool threadService = new BlockingThreadPool( threadCount, threadName );
                while ( status() == STATUS.OPEN && !dnQueue.isEmpty() && !cancelFlag.get() )
                {
                    final UserIdentity userIdentity = UserIdentity.fromDelimitedKey( getSessionLabel(), dnQueue.poll() );
                    if ( getPwmApplication().getConfig().isDevDebugMode() )
                    {
                        LOGGER.trace( getSessionLabel(), () -> "submit " + Instant.now().toString()
                                + " size=" + threadService.getQueue().size() );
                    }
                    threadService.blockingSubmit( ( ) ->
                    {
                        LOGGER.traceDevDebug( getSessionLabel(), () -> "start " + Instant.now().toString()
                                + " size=" + threadService.getQueue().size() );

                        processRecord( userIdentity, pauseBetweenIterations, threadCount );

                        LOGGER.traceDevDebug( getSessionLabel(), () -> "finish " + Instant.now().toString()
                                + " size=" + threadService.getQueue().size() );

                    } );
                }
                if ( getPwmApplication().getConfig().isDevDebugMode() )
                {
                    LOGGER.trace( getSessionLabel(), () -> "exit " + Instant.now().toString()
                            + " size=" + threadService.getQueue().size() );
                }

                JavaHelper.closeAndWaitExecutor( threadService, TimeDuration.SECONDS_10 );

                if ( cancelFlag.get() )
                {
                    final ErrorInformation errorInformation = new ErrorInformation(
                            PwmError.ERROR_SERVICE_NOT_AVAILABLE, "report cancelled by operator" );
                    reportStatus.updateAndGet( reportStatusInfo -> reportStatusInfo.toBuilder()
                            .lastError( errorInformation )
                            .build() );
                }
            }
            finally
            {
                reportStatus.updateAndGet( reportStatusInfo -> reportStatusInfo.toBuilder()
                        .finishDate( Instant.now() )
                        .build() );
                writeReportStatus();
            }
            LOGGER.debug( getSessionLabel(), () -> "update user cache process completed: " + JsonFactory.get().serialize( reportStatus ) );
        }


        private void updateCachedRecordFromLdap( final UserIdentity userIdentity )
                throws PwmUnrecoverableException, LocalDBException
        {
            if ( status() != STATUS.OPEN )
            {
                return;
            }

            final Instant startTime = Instant.now();

            final UserInfo userInfo = UserInfoFactory.newUserInfoUsingProxyForOfflineUser(
                    getPwmApplication(),
                    getSessionLabel(),
                    userIdentity
            );

            final Optional<UserReportRecord> newUserReportRecord = userCacheService.updateUserCache( userInfo );
            if ( newUserReportRecord.isPresent() )
            {
                userCacheService.store( newUserReportRecord.get() );
                summaryData.update( newUserReportRecord.get() );
                processRateMeter.markEvents( 1 );

                LOGGER.trace( getSessionLabel(), () -> "stored cache for " + userIdentity, () -> TimeDuration.fromCurrent( startTime ) );
            }
        }

        private void processRecord(
                final UserIdentity userIdentity,
                final boolean pauseBetweenIterations,
                final int threadCount
        )
        {
            try
            {
                final Instant startUpdateTime = Instant.now();
                updateCachedRecordFromLdap( userIdentity );
                reportStatus.updateAndGet( reportStatusInfo -> reportStatusInfo.toBuilder()
                        .count( reportStatusInfo.getCount() + 1 )
                        .build() );
                final TimeDuration totalUpdateTime = TimeDuration.fromCurrent( startUpdateTime );
                avgTracker.addSample( totalUpdateTime.asMillis() );

                updateTimeLock.lock();
                try
                {
                    final TimeDuration scaledTime = TimeDuration.of( totalUpdateTime.asMillis() / threadCount, TimeDuration.Unit.MILLISECONDS );
                    reportStatus.updateAndGet( reportStatusInfo -> reportStatusInfo.toBuilder()
                            .jobDuration( reportStatusInfo.getJobDuration().add( scaledTime ) )
                            .build() );
                }
                finally
                {
                    updateTimeLock.unlock();
                }

                if ( pauseBetweenIterations )
                {
                    TimeDuration.of( avgTracker.avgAsLong(), TimeDuration.Unit.MILLISECONDS ).pause();
                }
            }
            catch ( final PwmUnrecoverableException e )
            {
                LOGGER.debug( () -> "unexpected error reading report data: " + e.getMessage() );
            }
            catch ( final Exception e )
            {
                String errorMsg = "error while updating report cache for " + userIdentity.toString() + ", cause: ";
                errorMsg += e instanceof PwmException
                        ? ( ( PwmException ) e ).getErrorInformation().toDebugStr()
                        : e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_REPORTING_ERROR, errorMsg );
                if ( e instanceof PwmException )
                {
                    LOGGER.error( getSessionLabel(), errorInformation::toDebugStr );
                }
                else
                {
                    LOGGER.error( getSessionLabel(), errorInformation::toDebugStr, e );
                }
                reportStatus.updateAndGet( reportStatusInfo -> reportStatusInfo.toBuilder()
                        .lastError( errorInformation )
                        .errors( reportStatusInfo.getErrors() + 1 )
                        .build() );
            }
        }
    }

    private class DailyJobExecuteTask implements Runnable
    {
        @Override
        public void run( )
        {
            if ( status() != STATUS.OPEN )
            {
                return;
            }

            checkForOutdatedStoreData();

            if ( settings.isDailyJobEnabled() )
            {
                executorService.execute( new ClearTask() );
                executorService.execute( new ReadLDAPTask() );
            }
        }
    }

    private class InitializationTask implements Runnable
    {
        @Override
        public void run( )
        {
            try
            {
                final TimeDuration restTime = TimeDuration.MINUTE;
                LOGGER.trace( getSessionLabel(), () -> "resting for " + restTime.asCompactString() );
                restTime.pause();

                initTempData();
                LOGGER.debug( getSessionLabel(), () -> "report service initialized: " + JsonFactory.get().serialize( reportStatus.get() ) );
            }
            catch ( final PwmUnrecoverableException e )
            {
                LOGGER.error( getSessionLabel(), () -> "error during initialization: " + e.getMessage() );
                setStatus( STATUS.CLOSED );
                return;
            }

            final boolean reportingEnabled = getPwmApplication().getConfig().readSettingAsBoolean( PwmSetting.REPORTING_ENABLE_DAILY_JOB );
            if ( reportingEnabled )
            {
                final TimeDuration jobOffset = TimeDuration.of( settings.getJobOffsetSeconds(), TimeDuration.Unit.SECONDS );
                getPwmApplication().getPwmScheduler().scheduleDailyZuluZeroStartJob( new DailyJobExecuteTask(), executorService, jobOffset );
            }
        }

        private void initTempData( )
                throws PwmUnrecoverableException
        {
            try
            {
                getPwmApplication().readAppAttribute( AppAttribute.REPORT_STATUS, ReportStatusInfo.class )
                        .ifPresent( reportStatus::set );
            }
            catch ( final Exception e )
            {
                LOGGER.error( getSessionLabel(), () -> "error loading cached report status info into memory: " + e.getMessage() );
            }

            boolean clearFlag = false;
            if ( reportStatus.get() == null )
            {
                clearFlag = true;
                LOGGER.debug( getSessionLabel(), () -> "report service did not close cleanly, will clear data." );
            }
            else
            {
                final String settingsHash = settings.getSettingsHash();
                final String storedHash = reportStatus.get().getSettingsHash();
                if ( !Objects.equals( storedHash, settingsHash ) )
                {
                    LOGGER.error( getSessionLabel(), () -> "configuration has changed, will clear cached report data" );
                    clearFlag = true;
                }
            }

            if ( status() == STATUS.OPEN && clearFlag )
            {
                initReportStatus();
                executeCommand( ReportCommand.Clear );
            }

            startNextTask();
        }

        private void startNextTask()
        {
            if ( status() != STATUS.OPEN )
            {
                return;
            }

            checkForOutdatedStoreData();

            if ( !reportStatus.get().isReportComplete() && !dnQueue.isEmpty() )
            {
                LOGGER.trace( getSessionLabel(), () -> "resuming report data processing" );
                executorService.execute( new ProcessWorkQueueTask() );
            }
        }
    }

    private class ClearTask implements Runnable
    {
        @Override
        public void run( )
        {
            try
            {
                doClear();
            }
            catch ( final LocalDBException | PwmUnrecoverableException e )
            {
                LOGGER.error( getSessionLabel(), () -> "error during clear operation: " + e.getMessage() );
            }
            finally
            {
                resetCurrentProcess();
            }
        }

        private void doClear( ) throws LocalDBException, PwmUnrecoverableException
        {
            final Instant startTime = Instant.now();
            LOGGER.debug( getSessionLabel(), () -> "clearing cached report data" );
            clearWorkQueue();
            if ( userCacheService != null )
            {
                userCacheService.clear();
            }
            summaryData = ReportSummaryData.newSummaryData( settings.getTrackDays() );
            initReportStatus();
            LOGGER.debug( getSessionLabel(), () -> "finished clearing report " + TimeDuration.compactFromCurrent( startTime ) );
        }
    }

    private void initReportStatus()
            throws PwmUnrecoverableException
    {
        final String settingsHash = settings.getSettingsHash();
        reportStatus.set( ReportStatusInfo.builder()
                .settingsHash( settingsHash )
                .currentProcess( ReportStatusInfo.ReportEngineProcess.None )
                .build() );
        writeReportStatus();
    }

    private void resetCurrentProcess()
    {
        reportStatus.updateAndGet( reportStatusInfo -> reportStatusInfo.toBuilder()
                .currentProcess( ReportStatusInfo.ReportEngineProcess.None )
                .build() );
        writeReportStatus();
    }

    private void checkForOutdatedStoreData()
    {
        final Instant lastFinishDate = reportStatus.get().getFinishDate();
        if ( lastFinishDate != null && TimeDuration.fromCurrent( lastFinishDate ).isLongerThan( settings.getMaxCacheAge() ) )
        {
            executorService.execute( new ClearTask() );
        }
    }
}
