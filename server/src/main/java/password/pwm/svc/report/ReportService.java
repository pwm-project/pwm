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

import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
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
import java.math.BigInteger;
import java.math.MathContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ReportService implements PwmService
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( ReportService.class );

    private final AvgTracker avgTracker = new AvgTracker( 100 );

    private PwmApplication pwmApplication;
    private STATUS status = STATUS.NEW;
    private boolean cancelFlag = false;
    private ReportStatusInfo reportStatus = ReportStatusInfo.builder().build();
    private ReportSummaryData summaryData = ReportSummaryData.newSummaryData( null );
    private ExecutorService executorService;

    private UserCacheService userCacheService;
    private ReportSettings settings = ReportSettings.builder().build();

    private Queue<String> dnQueue;

    private final EventRateMeter eventRateMeter = new EventRateMeter( TimeDuration.MINUTE );


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
        catch ( Exception e )
        {
            LOGGER.error( SessionLabel.REPORTING_SESSION_LABEL, "unable to init cache service" );
            status = STATUS.CLOSED;
            return;
        }

        settings = ReportSettings.readSettingsFromConfig( pwmApplication.getConfig() );
        summaryData = ReportSummaryData.newSummaryData( settings.getTrackDays() );

        dnQueue = LocalDBStoredQueue.createLocalDBStoredQueue( pwmApplication, pwmApplication.getLocalDB(), LocalDB.DB.REPORT_QUEUE );

        executorService = PwmScheduler.makeBackgroundExecutor( pwmApplication, this.getClass() );

        LOGGER.debug( () -> "report service started" );

        executorService.submit( new InitializationTask() );

        status = STATUS.OPEN;
    }

    @Override
    public void close( )
    {
        cancelFlag = true;

        JavaHelper.closeAndWaitExecutor( executorService, TimeDuration.SECONDS_10 );

        if ( userCacheService != null )
        {
            userCacheService.close();
        }

        status = STATUS.CLOSED;
        executorService = null;
        saveTempData();
    }

    private void saveTempData( )
    {
        try
        {
            pwmApplication.writeAppAttribute( PwmApplication.AppAttribute.REPORT_STATUS, reportStatus );
        }
        catch ( Exception e )
        {
            LOGGER.error( SessionLabel.REPORTING_SESSION_LABEL, "error writing cached report dredge info into memory: " + e.getMessage() );
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
                if ( reportStatus.getCurrentProcess() != ReportStatusInfo.ReportEngineProcess.ReadData
                        && reportStatus.getCurrentProcess() != ReportStatusInfo.ReportEngineProcess.SearchLDAP
                )
                {
                    executorService.execute( new ClearTask() );
                    executorService.execute( new ReadLDAPTask() );
                    LOGGER.trace( SessionLabel.REPORTING_SESSION_LABEL, () -> "submitted new ldap dredge task to executorService" );
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

    PwmApplication getPwmApplication( )
    {
        return pwmApplication;
    }

    public BigDecimal getEventRate( )
    {
        return eventRateMeter.readEventRate();
    }

    public long getTotalRecords( )
    {
        return userCacheService.size();
    }

    private void clearWorkQueue( )
    {
        reportStatus.setCount( 0 );
        reportStatus.setJobDuration( TimeDuration.ZERO );
        dnQueue.clear();
    }

    private void resetJobStatus( )
    {
        cancelFlag = false;
        eventRateMeter.reset();

        reportStatus.setLastError( null );
        reportStatus.setErrors( 0 );

        reportStatus.setFinishDate( null );

        pwmApplication.writeAppAttribute( PwmApplication.AppAttribute.REPORT_STATUS, null );
    }


    public ReportStatusInfo getReportStatusInfo( )
    {
        return reportStatus;
    }


    public interface RecordIterator<K> extends ClosableIterator<UserCacheRecord>
    {
    }

    public RecordIterator<UserCacheRecord> iterator( )
    {
        return new RecordIterator<UserCacheRecord>()
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
                catch ( LocalDBException e )
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

    public static class AvgTracker
    {
        private final int maxSamples;
        private final Queue<BigInteger> samples = new LinkedList<>();

        public AvgTracker( final int maxSamples )
        {
            this.maxSamples = maxSamples;
        }

        public void addSample( final long input )
        {
            samples.add( new BigInteger( Long.toString( input ) ) );
            while ( samples.size() > maxSamples )
            {
                samples.remove();
            }
        }

        public BigDecimal avg( )
        {
            if ( samples.isEmpty() )
            {
                throw new IllegalStateException( "unable to compute avg without samples" );
            }

            BigInteger total = BigInteger.ZERO;
            for ( final BigInteger sample : samples )
            {
                total = total.add( sample );
            }
            final BigDecimal maxAsBD = new BigDecimal( Integer.toString( maxSamples ) );
            return new BigDecimal( total ).divide( maxAsBD, MathContext.DECIMAL32 );
        }

        public long avgAsLong( )
        {
            return avg().longValue();
        }
    }

    private class ReadLDAPTask implements Runnable
    {
        @Override
        public void run( )
        {
            reportStatus.setCurrentProcess( ReportStatusInfo.ReportEngineProcess.SearchLDAP );
            try
            {
                readUserListFromLdap();
                executorService.execute( new ProcessWorkQueueTask() );
            }
            catch ( Exception e )
            {
                boolean errorProcessed = false;
                if ( e instanceof PwmException )
                {
                    if ( ( ( PwmException ) e ).getErrorInformation().getError() == PwmError.ERROR_DIRECTORY_UNAVAILABLE )
                    {
                        if ( executorService != null )
                        {
                            LOGGER.error( SessionLabel.REPORTING_SESSION_LABEL, "directory unavailable error during background SearchLDAP, will retry; error: " + e.getMessage() );
                            pwmApplication.getPwmScheduler().scheduleJob( new ReadLDAPTask(), executorService, TimeDuration.of( 10, TimeDuration.Unit.MINUTES ) );
                            errorProcessed = true;
                        }
                    }
                }

                if ( !errorProcessed )
                {
                    LOGGER.error( SessionLabel.REPORTING_SESSION_LABEL, "error during background ReadData: " + e.getMessage() );
                }
            }
            finally
            {
                reportStatus.setCurrentProcess( ReportStatusInfo.ReportEngineProcess.None );
            }
        }

        private void readUserListFromLdap( )
                throws ChaiUnavailableException, ChaiOperationException, PwmUnrecoverableException, PwmOperationalException
        {
            final Instant startTime = Instant.now();
            LOGGER.trace( () -> "beginning ldap search process" );

            resetJobStatus();
            clearWorkQueue();

            final Iterator<UserIdentity> memQueue = LdapOperationsHelper.readUsersFromLdapForPermissions(
                    pwmApplication,
                    SessionLabel.REPORTING_SESSION_LABEL,
                    settings.getSearchFilter(),
                    settings.getMaxSearchSize()
            );


            LOGGER.trace( () -> "completed ldap search process, transferring search results to work queue" );

            final TransactionSizeCalculator transactionCalculator = new TransactionSizeCalculator(
                    TransactionSizeCalculator.Settings.builder()
                            .durationGoal( TimeDuration.SECOND )
                            .minTransactions( 10 )
                            .maxTransactions( 100 * 1000 )
                            .build()
            );

            while ( status == STATUS.OPEN && !cancelFlag && memQueue.hasNext() )
            {
                final Instant loopStart = Instant.now();
                final List<String> bufferList = new ArrayList<>();
                final int loopCount = transactionCalculator.getTransactionSize();
                for ( int i = 0; i < loopCount && memQueue.hasNext(); i++ )
                {
                    bufferList.add( memQueue.next().toDelimitedKey() );
                }
                dnQueue.addAll( bufferList );
                transactionCalculator.recordLastTransactionDuration( TimeDuration.fromCurrent( loopStart ) );
            }
            LOGGER.trace( () -> "completed transfer of ldap search results to work queue in " + TimeDuration.fromCurrent( startTime ).asCompactString() );
        }
    }

    private class ProcessWorkQueueTask implements Runnable
    {
        @Override
        public void run( )
        {
            reportStatus.setCurrentProcess( ReportStatusInfo.ReportEngineProcess.ReadData );
            try
            {
                processWorkQueue();
            }
            catch ( Exception e )
            {
                if ( e instanceof PwmException )
                {
                    if ( ( ( PwmException ) e ).getErrorInformation().getError() == PwmError.ERROR_DIRECTORY_UNAVAILABLE )
                    {
                        if ( executorService != null )
                        {
                            LOGGER.error( SessionLabel.REPORTING_SESSION_LABEL, "directory unavailable error during background ReadData, will retry; error: " + e.getMessage() );
                            pwmApplication.getPwmScheduler().scheduleJob( new ProcessWorkQueueTask(), executorService, TimeDuration.of( 10, TimeDuration.Unit.MINUTES ) );
                        }
                    }
                    else
                    {
                        LOGGER.error( SessionLabel.REPORTING_SESSION_LABEL, "error during background ReadData: " + e.getMessage() );
                    }
                }
            }
            finally
            {
                reportStatus.setCurrentProcess( ReportStatusInfo.ReportEngineProcess.None );
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
                            reportStatus.setCount( reportStatus.getCount() + 1 );
                            eventRateMeter.markEvents( 1 );
                            final TimeDuration totalUpdateTime = TimeDuration.fromCurrent( startUpdateTime );
                            avgTracker.addSample( totalUpdateTime.asMillis() );

                            try
                            {
                                updateTimeLock.lock();
                                final TimeDuration scaledTime = TimeDuration.of( totalUpdateTime.asMillis() / threadCount, TimeDuration.Unit.MILLISECONDS );
                                reportStatus.setJobDuration( reportStatus.getJobDuration().add( scaledTime ) );
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
                        catch ( Exception e )
                        {
                            String errorMsg = "error while updating report cache for " + userIdentity.toString() + ", cause: ";
                            errorMsg += e instanceof PwmException ? ( ( PwmException ) e ).getErrorInformation().toDebugStr() : e.getMessage();
                            final ErrorInformation errorInformation;
                            errorInformation = new ErrorInformation( PwmError.ERROR_REPORTING_ERROR, errorMsg );
                            LOGGER.error( SessionLabel.REPORTING_SESSION_LABEL, errorInformation.toDebugStr(), e );
                            reportStatus.setLastError( errorInformation );
                            reportStatus.setErrors( reportStatus.getErrors() + 1 );
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
                    reportStatus.setLastError( new ErrorInformation( PwmError.ERROR_SERVICE_NOT_AVAILABLE, "report cancelled by operator" ) );
                }
            }
            finally
            {
                reportStatus.setFinishDate( Instant.now() );
                saveTempData();
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

        private void checkForOutdatedStoreData()
        {
            final Instant lastFinishDate = reportStatus.getFinishDate();
            if ( lastFinishDate != null && TimeDuration.fromCurrent( lastFinishDate ).isLongerThan( settings.getMaxCacheAge() ) )
            {
                executorService.execute( new ClearTask() );
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
            }
            catch ( LocalDBException | PwmUnrecoverableException e )
            {
                LOGGER.error( SessionLabel.REPORTING_SESSION_LABEL, "error during initialization: " + e.getMessage() );
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
                throws LocalDBException, PwmUnrecoverableException
        {
            try
            {
                reportStatus = pwmApplication.readAppAttribute( PwmApplication.AppAttribute.REPORT_STATUS, ReportStatusInfo.class );
            }
            catch ( Exception e )
            {
                LOGGER.error( SessionLabel.REPORTING_SESSION_LABEL, "error loading cached report status info into memory: " + e.getMessage() );
            }

            boolean clearFlag = false;
            if ( reportStatus == null )
            {
                clearFlag = true;
                LOGGER.debug( SessionLabel.REPORTING_SESSION_LABEL, () -> "report service did not close cleanly, will clear data." );
            }
            else
            {
                final String currentSettingCache = settings.getSettingsHash();
                if ( reportStatus.getSettingsHash() != null && !reportStatus.getSettingsHash().equals( currentSettingCache ) )
                {
                    LOGGER.error( SessionLabel.REPORTING_SESSION_LABEL, "configuration has changed, will clear cached report data" );
                    clearFlag = true;
                }
            }

            if ( clearFlag )
            {
                reportStatus = ReportStatusInfo.builder().settingsHash( settings.getSettingsHash() ).build();
                executeCommand( ReportCommand.Clear );
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
            catch ( LocalDBException | PwmUnrecoverableException e )
            {
                LOGGER.error( SessionLabel.REPORTING_SESSION_LABEL, "error during clear operation: " + e.getMessage() );
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
            reportStatus = ReportStatusInfo.builder().settingsHash( settings.getSettingsHash() ).build();
            LOGGER.debug( SessionLabel.REPORTING_SESSION_LABEL, () -> "finished clearing report " + TimeDuration.compactFromCurrent( startTime ) );
        }
    }
}
