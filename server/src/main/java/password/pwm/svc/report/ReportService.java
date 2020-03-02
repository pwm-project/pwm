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

package password.pwm.svc.report;

import password.pwm.AppAttribute;
import password.pwm.PwmApplication;
import password.pwm.PwmApplicationMode;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthRecord;
import password.pwm.ldap.LdapOperationsHelper;
import password.pwm.ldap.UserInfo;
import password.pwm.ldap.UserInfoFactory;
import password.pwm.svc.PwmService;
import password.pwm.util.EventRateMeter;
import password.pwm.util.PwmScheduler;
import password.pwm.util.TransactionSizeCalculator;
import password.pwm.util.java.AverageTracker;
import password.pwm.util.java.BlockingThreadPool;
import password.pwm.util.java.ClosableIterator;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
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
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


public class ReportService implements PwmService
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( ReportService.class );

    private final AverageTracker avgTracker = new AverageTracker( 100 );

    private PwmApplication pwmApplication;
    private STATUS status = STATUS.NEW;
    private volatile boolean cancelFlag = false;
    private ReportSummaryData summaryData = ReportSummaryData.newSummaryData( null );
    private ExecutorService executorService;

    private UserCacheService userCacheService;
    private ReportSettings settings = ReportSettings.builder().build();

    private Queue<String> dnQueue;

    private final AtomicReference<ReportStatusInfo> reportStatus = new AtomicReference<>( ReportStatusInfo.builder().build() );
    private final EventRateMeter processRateMeter = new EventRateMeter( TimeDuration.MINUTE );


    public ReportService( )
    {
    }

    public STATUS status( )
    {
        return status;
    }

    public enum ReportCommand
    {
        Start,
        Stop,
        Clear,
    }


    @Override
    public void init( final PwmApplication pwmApplication )
            throws PwmException
    {
        status = STATUS.OPENING;
        this.pwmApplication = pwmApplication;

        if ( pwmApplication.getApplicationMode() == PwmApplicationMode.READ_ONLY )
        {
            LOGGER.debug( SessionLabel.REPORTING_SESSION_LABEL, () -> "application mode is read-only, will remain closed" );
            status = STATUS.CLOSED;
            return;
        }

        if ( pwmApplication.getLocalDB() == null || LocalDB.Status.OPEN != pwmApplication.getLocalDB().status() )
        {
            LOGGER.debug( SessionLabel.REPORTING_SESSION_LABEL, () -> "LocalDB is not open, will remain closed" );
            status = STATUS.CLOSED;
            return;
        }

        try
        {
            userCacheService = new UserCacheService();
            userCacheService.init( pwmApplication );
        }
        catch ( final Exception e )
        {
            LOGGER.error( SessionLabel.REPORTING_SESSION_LABEL, () -> "unable to init cache service" );
            status = STATUS.CLOSED;
            return;
        }

        settings = ReportSettings.readSettingsFromConfig( pwmApplication.getConfig() );
        summaryData = ReportSummaryData.newSummaryData( settings.getTrackDays() );

        dnQueue = LocalDBStoredQueue.createLocalDBStoredQueue( pwmApplication, pwmApplication.getLocalDB(), LocalDB.DB.REPORT_QUEUE );

        executorService = PwmScheduler.makeBackgroundExecutor( pwmApplication, this.getClass() );

        executorService.submit( new InitializationTask() );

        status = STATUS.OPEN;
    }

    @Override
    public void close( )
    {
        status = STATUS.CLOSED;
        cancelFlag = true;

        JavaHelper.closeAndWaitExecutor( executorService, TimeDuration.SECONDS_10 );

        if ( userCacheService != null )
        {
            userCacheService.close();
        }

        executorService = null;
        writeReportStatus();
    }

    private void writeReportStatus( )
    {
        try
        {
            pwmApplication.writeAppAttribute( AppAttribute.REPORT_STATUS, reportStatus.get() );
        }
        catch ( final Exception e )
        {
            LOGGER.error( SessionLabel.REPORTING_SESSION_LABEL, () -> "error writing cached report dredge info into memory: " + e.getMessage() );
        }
    }


    @Override
    public List<HealthRecord> healthCheck( )
    {
        return null;
    }

    @Override
    public ServiceInfoBean serviceInfo( )
    {
        return new ServiceInfoBean( Collections.singletonList( DataStorageMethod.LDAP ) );
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
                cancelFlag = true;
                clearWorkQueue();
            }
            break;

            case Clear:
            {
                cancelFlag = true;
                executorService.execute( new ClearTask() );
            }
            break;

            default:
                JavaHelper.unhandledSwitchStatement( reportCommand );
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
        cancelFlag = false;
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


    public ClosableIterator<UserCacheRecord> iterator( )
    {
        return new ClosableIterator<UserCacheRecord>()
        {
            private UserCacheService.UserStatusCacheBeanIterator<UserCacheService.StorageKey> storageKeyIterator = userCacheService.iterator();

            @Override
            public boolean hasNext( )
            {
                return this.storageKeyIterator.hasNext();
            }

            @Override
            public UserCacheRecord next( )
            {
                try
                {
                    while ( this.storageKeyIterator.hasNext() )
                    {
                        final UserCacheService.StorageKey key = this.storageKeyIterator.next();
                        final UserCacheRecord returnBean = userCacheService.readStorageKey( key );
                        if ( returnBean != null )
                        {
                            return returnBean;
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
                            LOGGER.error( SessionLabel.REPORTING_SESSION_LABEL,
                                    () -> "directory unavailable error during background SearchLDAP, will retry; error: " + e.getMessage() );
                            pwmApplication.getPwmScheduler().scheduleJob( new ReadLDAPTask(), executorService, TimeDuration.of( 10, TimeDuration.Unit.MINUTES ) );
                            errorProcessed = true;
                        }
                    }
                }

                if ( !errorProcessed )
                {
                    LOGGER.error( SessionLabel.REPORTING_SESSION_LABEL, () -> "error during background ReadData: " + e.getMessage() );
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
            LOGGER.trace( SessionLabel.REPORTING_SESSION_LABEL, () -> "beginning ldap search process" );

            resetJobStatus();
            clearWorkQueue();

            final Iterator<UserIdentity> memQueue = LdapOperationsHelper.readUsersFromLdapForPermissions(
                    pwmApplication,
                    SessionLabel.REPORTING_SESSION_LABEL,
                    settings.getSearchFilter(),
                    settings.getMaxSearchSize()
            );

            LOGGER.trace( SessionLabel.REPORTING_SESSION_LABEL, () -> "completed ldap search process (" + TimeDuration.compactFromCurrent( startTime ) + ")" );

            writeUsersToLocalDBQueue( memQueue );
        }

        private void writeUsersToLocalDBQueue( final Iterator<UserIdentity> identityQueue )
        {
            final Instant startTime = Instant.now();
            LOGGER.trace( SessionLabel.REPORTING_SESSION_LABEL, () -> "transferring search results to work queue" );

            final TransactionSizeCalculator transactionCalculator = new TransactionSizeCalculator(
                    TransactionSizeCalculator.Settings.builder()
                            .durationGoal( TimeDuration.SECOND )
                            .minTransactions( 10 )
                            .maxTransactions( 100 * 1000 )
                            .build()
            );

            while ( !cancelFlag && identityQueue.hasNext() )
            {
                final Instant loopStart = Instant.now();
                final List<String> bufferList = new ArrayList<>();
                final int loopCount = transactionCalculator.getTransactionSize();
                while ( !cancelFlag && identityQueue.hasNext() && bufferList.size() < loopCount )
                {
                    bufferList.add( identityQueue.next().toDelimitedKey() );
                }
                dnQueue.addAll( bufferList );
                transactionCalculator.recordLastTransactionDuration( TimeDuration.fromCurrent( loopStart ) );
            }
            LOGGER.trace( SessionLabel.REPORTING_SESSION_LABEL,
                    () -> "completed transfer of ldap search results to work queue in " + TimeDuration.compactFromCurrent( startTime ) );
        }
    }

    private class ProcessWorkQueueTask implements Runnable
    {
        @Override
        public void run( )
        {
            reportStatus.updateAndGet( reportStatusInfo -> reportStatusInfo.toBuilder()
                    .currentProcess( ReportStatusInfo.ReportEngineProcess.ReadData )
                    .build() );
            try
            {
                processWorkQueue();
                if ( status == STATUS.OPEN )
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
                        LOGGER.error( SessionLabel.REPORTING_SESSION_LABEL, () -> "directory unavailable error during background ReadData, will retry; error: " + e.getMessage() );
                        pwmApplication.getPwmScheduler().scheduleJob( new ProcessWorkQueueTask(), executorService, TimeDuration.of( 10, TimeDuration.Unit.MINUTES ) );
                    }
                }
                else
                {
                    LOGGER.error( SessionLabel.REPORTING_SESSION_LABEL, () -> "error during background ReadData: " + e.getMessage() );
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
            LOGGER.debug( SessionLabel.REPORTING_SESSION_LABEL, () -> "beginning process to updating user cache records from ldap" );
            if ( status != STATUS.OPEN )
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

            final Lock updateTimeLock = new ReentrantLock();

            try
            {
                LOGGER.trace( SessionLabel.REPORTING_SESSION_LABEL, () -> "about to begin ldap processing with thread count of " + threadCount );
                final BlockingThreadPool threadService = new BlockingThreadPool( threadCount, "reporting-thread" );
                while ( status == STATUS.OPEN && !dnQueue.isEmpty() && !cancelFlag )
                {
                    final UserIdentity userIdentity = UserIdentity.fromDelimitedKey( dnQueue.poll() );
                    if ( pwmApplication.getConfig().isDevDebugMode() )
                    {
                        LOGGER.trace( SessionLabel.REPORTING_SESSION_LABEL, () -> "submit " + Instant.now().toString()
                                + " size=" + threadService.getQueue().size() );
                    }
                    threadService.blockingSubmit( ( ) ->
                    {
                        if ( pwmApplication.getConfig().isDevDebugMode() )
                        {
                            LOGGER.trace( SessionLabel.REPORTING_SESSION_LABEL, () -> "start " + Instant.now().toString()
                                    + " size=" + threadService.getQueue().size() );
                        }
                        try
                        {
                            final Instant startUpdateTime = Instant.now();
                            updateCachedRecordFromLdap( userIdentity );
                            reportStatus.updateAndGet( reportStatusInfo -> reportStatusInfo.toBuilder()
                                    .count( reportStatusInfo.getCount() + 1 )
                                    .build() );
                            final TimeDuration totalUpdateTime = TimeDuration.fromCurrent( startUpdateTime );
                            avgTracker.addSample( totalUpdateTime.asMillis() );

                            try
                            {
                                updateTimeLock.lock();
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
                                LOGGER.error( SessionLabel.REPORTING_SESSION_LABEL, () -> errorInformation.toDebugStr() );
                            }
                            else
                            {
                                LOGGER.error( SessionLabel.REPORTING_SESSION_LABEL, () -> errorInformation.toDebugStr(), e );
                            }
                            reportStatus.updateAndGet( reportStatusInfo -> reportStatusInfo.toBuilder()
                                    .lastError( errorInformation )
                                    .errors( reportStatusInfo.getErrors() + 1 )
                                    .build() );
                        }
                        if ( pwmApplication.getConfig().isDevDebugMode() )
                        {
                            LOGGER.trace( SessionLabel.REPORTING_SESSION_LABEL, () -> "finish " + Instant.now().toString()
                                    + " size=" + threadService.getQueue().size() );
                        }
                    } );
                }
                if ( pwmApplication.getConfig().isDevDebugMode() )
                {
                    LOGGER.trace( SessionLabel.REPORTING_SESSION_LABEL, () -> "exit " + Instant.now().toString()
                            + " size=" + threadService.getQueue().size() );
                }

                JavaHelper.closeAndWaitExecutor( threadService, TimeDuration.SECONDS_10 );

                if ( cancelFlag )
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
            LOGGER.debug( SessionLabel.REPORTING_SESSION_LABEL, () -> "update user cache process completed: " + JsonUtil.serialize( reportStatus ) );
        }


        private void updateCachedRecordFromLdap( final UserIdentity userIdentity )
                throws PwmUnrecoverableException, LocalDBException
        {
            if ( status != STATUS.OPEN )
            {
                return;
            }

            final UserInfo userInfo = UserInfoFactory.newUserInfoUsingProxyForOfflineUser(
                    pwmApplication,
                    SessionLabel.REPORTING_SESSION_LABEL,
                    userIdentity
            );
            final UserCacheRecord newUserCacheRecord = userCacheService.updateUserCache( userInfo );

            userCacheService.store( newUserCacheRecord );
            summaryData.update( newUserCacheRecord );
            processRateMeter.markEvents( 1 );

            LOGGER.trace( SessionLabel.REPORTING_SESSION_LABEL, () -> "stored cache for " + userIdentity );
        }
    }

    private class DailyJobExecuteTask implements Runnable
    {
        @Override
        public void run( )
        {
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
                initTempData();
                LOGGER.debug( SessionLabel.REPORTING_SESSION_LABEL, () -> "report service initialized: " + JsonUtil.serialize( reportStatus.get() ) );
            }
            catch ( final PwmUnrecoverableException e )
            {
                LOGGER.error( SessionLabel.REPORTING_SESSION_LABEL, () -> "error during initialization: " + e.getMessage() );
                status = STATUS.CLOSED;
                return;
            }

            final boolean reportingEnabled = pwmApplication.getConfig().readSettingAsBoolean( PwmSetting.REPORTING_ENABLE_DAILY_JOB );
            if ( reportingEnabled )
            {
                final TimeDuration jobOffset = TimeDuration.of( settings.getJobOffsetSeconds(), TimeDuration.Unit.SECONDS );
                pwmApplication.getPwmScheduler().scheduleDailyZuluZeroStartJob( new DailyJobExecuteTask(), executorService, jobOffset );
            }
        }

        private void initTempData( )
                throws PwmUnrecoverableException
        {
            try
            {
                final ReportStatusInfo localReportStatus = pwmApplication.readAppAttribute( AppAttribute.REPORT_STATUS, ReportStatusInfo.class );
                reportStatus.set( localReportStatus );
            }
            catch ( final Exception e )
            {
                LOGGER.error( SessionLabel.REPORTING_SESSION_LABEL, () -> "error loading cached report status info into memory: " + e.getMessage() );
            }

            boolean clearFlag = false;
            if ( reportStatus.get() == null )
            {
                clearFlag = true;
                LOGGER.debug( SessionLabel.REPORTING_SESSION_LABEL, () -> "report service did not close cleanly, will clear data." );
            }
            else
            {
                if ( !Objects.equals( reportStatus.get().getSettingsHash(), settings.getSettingsHash() ) )
                {
                    LOGGER.error( SessionLabel.REPORTING_SESSION_LABEL, () -> "configuration has changed, will clear cached report data" );
                    clearFlag = true;
                }
            }

            if ( clearFlag )
            {
                initReportStatus();
                executeCommand( ReportCommand.Clear );
            }

            startNextTask();
        }

        private void startNextTask()
        {
            checkForOutdatedStoreData();

            if ( !reportStatus.get().isReportComplete() && !dnQueue.isEmpty() )
            {
                LOGGER.trace( SessionLabel.REPORTING_SESSION_LABEL, () -> "resuming report data processing" );
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
                LOGGER.error( SessionLabel.REPORTING_SESSION_LABEL, () -> "error during clear operation: " + e.getMessage() );
            }
            finally
            {
                resetCurrentProcess();
            }
        }

        private void doClear( ) throws LocalDBException, PwmUnrecoverableException
        {
            final Instant startTime = Instant.now();
            LOGGER.debug( SessionLabel.REPORTING_SESSION_LABEL, () -> "clearing cached report data" );
            clearWorkQueue();
            if ( userCacheService != null )
            {
                userCacheService.clear();
            }
            summaryData = ReportSummaryData.newSummaryData( settings.getTrackDays() );
            initReportStatus();
            LOGGER.debug( SessionLabel.REPORTING_SESSION_LABEL, () -> "finished clearing report " + TimeDuration.compactFromCurrent( startTime ) );
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
