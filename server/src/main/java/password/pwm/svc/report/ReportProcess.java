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

import org.apache.commons.csv.CSVPrinter;
import org.jetbrains.annotations.NotNull;
import password.pwm.AppAttribute;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.PwmDomain;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.AppConfig;
import password.pwm.config.value.data.UserPermission;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmInternalException;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.bean.DisplayElement;
import password.pwm.ldap.UserInfoFactory;
import password.pwm.ldap.permission.UserPermissionUtility;
import password.pwm.user.UserInfo;
import password.pwm.util.EventRateMeter;
import password.pwm.util.java.ConditionalTaskExecutor;
import password.pwm.util.java.FunctionalReentrantLock;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.PwmTimeUtil;
import password.pwm.util.java.PwmUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.json.JsonProvider;
import password.pwm.util.logging.PwmLogLevel;
import password.pwm.util.logging.PwmLogger;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ReportProcess implements AutoCloseable
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( ReportProcess.class );

    private static final FunctionalReentrantLock REPORT_ID_LOCK = new FunctionalReentrantLock();

    private final PwmApplication pwmApplication;
    private final Semaphore reportServiceSemaphore;
    private final ReportSettings reportSettings;
    private final Locale locale;
    private final SessionLabel sessionLabel;

    private final ConditionalTaskExecutor debugOutputLogger;
    private final long reportId;
    private final AtomicLong recordCounter = new AtomicLong();
    private final AtomicBoolean inProgress = new AtomicBoolean();
    private final AtomicBoolean cancelFlag = new AtomicBoolean();
    private final EventRateMeter processRateMeter = new EventRateMeter( TimeDuration.MINUTE.asDuration() );


    private Instant startTime = Instant.now();
    private ReportSummaryCalculator summaryData = ReportSummaryCalculator.newSummaryData( Collections.singletonList( 1 ) );

    ReportProcess(
            @NotNull final PwmApplication pwmApplication,
            @NotNull final Semaphore reportServiceSemaphore,
            @NotNull final ReportSettings reportSettings,
            final Locale locale,
            @NotNull final SessionLabel sessionLabel
    )
    {
        this.pwmApplication = Objects.requireNonNull( pwmApplication );
        this.reportServiceSemaphore = Objects.requireNonNull( reportServiceSemaphore );
        this.reportSettings = Objects.requireNonNull( reportSettings );
        this.locale = Objects.requireNonNullElse( locale, PwmConstants.DEFAULT_LOCALE );
        this.sessionLabel = sessionLabel;

        this.reportId = nextProcessId();

        this.debugOutputLogger = ConditionalTaskExecutor.forPeriodicTask(
                () -> log( PwmLogLevel.TRACE, () -> " in progress: " + recordCounter.longValue() + " records exported at " + processRateMeter.prettyEps( locale ),
                        TimeDuration.fromCurrent( startTime ) ),
                TimeDuration.MINUTE.asDuration() );
    }

    private void log( final PwmLogLevel level, final Supplier<String> message, final TimeDuration timeDuration )
    {
        final Supplier<String> wrappedMsg = () -> "report #" + reportId + " " + message.get();
        LOGGER.log( level, sessionLabel, wrappedMsg, null, timeDuration );
    }

    static ReportProcess createReportProcess(
            @NotNull final PwmApplication pwmApplication,
            @NotNull final Semaphore reportServiceSemaphore,
            @NotNull final ReportSettings reportSettings,
            final Locale locale,
            @NotNull final SessionLabel sessionLabel
    )
    {
        return new ReportProcess( pwmApplication, reportServiceSemaphore, reportSettings, locale, sessionLabel );
    }

    public static void outputSummaryToCsv(
            final AppConfig config,
            final ReportSummaryCalculator reportSummaryData,
            final OutputStream outputStream,
            final Locale locale
    )
            throws IOException
    {

        final List<ReportSummaryCalculator.PresentationRow> outputList = reportSummaryData.asPresentableCollection( config, locale );
        final CSVPrinter csvPrinter = PwmUtil.makeCsvPrinter( outputStream );

        for ( final ReportSummaryCalculator.PresentationRow presentationRow : outputList )
        {
            csvPrinter.printRecord( presentationRow.toStringList() );
        }

        csvPrinter.flush();
    }

    public void startReport(
            @NotNull final ReportProcessRequest reportProcessRequest,
            @NotNull final OutputStream outputStream
    )
            throws PwmUnrecoverableException
    {
        Objects.requireNonNull( reportProcessRequest );
        Objects.requireNonNull( outputStream );

        if ( inProgress.get() )
        {
            throw new PwmUnrecoverableException( PwmError.ERROR_INTERNAL, "report process #" + reportId + " cannot be started, report already in progress" );
        }

        if ( !reportServiceSemaphore.tryAcquire() )
        {
            throw new PwmUnrecoverableException( PwmError.ERROR_INTERNAL, "report process #" + reportId + " cannot be started, maximum concurrent reports already in progress." );
        }

        this.startTime = Instant.now();
        this.summaryData = ReportSummaryCalculator.newSummaryData( reportSettings.getTrackDays() );
        this.recordCounter.set( 0 );
        this.processRateMeter.reset();
        this.inProgress.set( true );
        this.cancelFlag.set( false );

        log( PwmLogLevel.TRACE, () -> "beginning report generation, request parameters: "
                + JsonFactory.get().serialize( reportProcessRequest, ReportProcessRequest.class ), null );

        try ( ZipOutputStream zipOutputStream = new ZipOutputStream( outputStream ) )
        {
            liveReportDownloadZipImpl( reportProcessRequest, zipOutputStream );
        }
        catch ( final PwmException e )
        {
            log( PwmLogLevel.DEBUG, () -> "error during report generation: " + e.getMessage(), null );
            cancelFlag.set( true );
            throw new PwmUnrecoverableException( new ErrorInformation(  PwmError.ERROR_INTERNAL, e.getMessage() ) );
        }
        catch ( final IOException e )
        {
            log( PwmLogLevel.DEBUG, () -> "I/O error during report generation: " + e.getMessage(), null );
            cancelFlag.set( true );
        }
        finally
        {
            close();
        }
    }

    private void liveReportDownloadZipImpl(
            final ReportProcessRequest reportProcessRequest,
            final ZipOutputStream zipOutputStream
    )
            throws PwmUnrecoverableException, PwmOperationalException, IOException
    {
        final ReportRecordWriter recordWriter = reportProcessRequest.getReportType() == ReportProcessRequest.ReportType.json
                ? new ReportJsonRecordWriter( zipOutputStream )
                : new ReportCsvRecordWriter( zipOutputStream, pwmApplication, locale );

        final boolean recordLimitReached = processReport( reportProcessRequest, zipOutputStream, recordWriter );

        checkCancel( zipOutputStream );
        outputSummary( zipOutputStream );

        checkCancel( zipOutputStream );
        outputResult( reportProcessRequest, zipOutputStream, recordLimitReached );

        log( PwmLogLevel.TRACE, () -> "completed report generation with " + recordCounter.longValue() + " records at "
                + processRateMeter.prettyEps( locale ), TimeDuration.fromCurrent( startTime ) );

    }

    private void checkCancel( final OutputStream outputStream )
            throws IOException
    {
        if ( cancelFlag.get() )
        {
            outputStream.close();
        }
    }

    private void outputResult(
            final ReportProcessRequest request,
            final ZipOutputStream zipOutputStream,
            final boolean recordLimitReached
    )
            throws IOException
    {
        final ReportProcessResult result = new ReportProcessResult(
                request,
                this.recordCounter.get(),
                startTime,
                Instant.now(),
                TimeDuration.fromCurrent( startTime ),
                recordLimitReached );

        final String jsonData = JsonFactory.get().serialize( result, ReportProcessResult.class, JsonProvider.Flag.PrettyPrint );

        zipOutputStream.putNextEntry( new ZipEntry( "result.json" ) );
        zipOutputStream.write( jsonData.getBytes( PwmConstants.DEFAULT_CHARSET ) );
        zipOutputStream.closeEntry();
    }

    private void outputSummary(
            final ZipOutputStream zipOutputStream
    )
            throws IOException
    {
        {
            zipOutputStream.putNextEntry( new ZipEntry( "summary.json" ) );
            outputJsonSummaryToZip( summaryData, zipOutputStream );
            zipOutputStream.closeEntry();
        }
        {
            zipOutputStream.putNextEntry( new ZipEntry( "summary.csv" ) );
            outputSummaryToCsv( pwmApplication.getConfig(), summaryData, zipOutputStream, locale );
            zipOutputStream.closeEntry();
        }
    }

    private boolean processReport(
            final ReportProcessRequest reportProcessRequest,
            final ZipOutputStream zipOutputStream,
            final ReportRecordWriter recordWriter
    )
            throws IOException, PwmUnrecoverableException, PwmOperationalException
    {
        zipOutputStream.putNextEntry( new ZipEntry( recordWriter.getZipName() ) );

        recordWriter.outputHeader();

        int processCounter = 0;
        boolean recordLimitReached = false;

        for (
                final Iterator<PwmDomain> domainIterator = applicableDomains( reportProcessRequest ).iterator();
                domainIterator.hasNext() && !cancelFlag.get() && !recordLimitReached;
        )
        {
            final PwmDomain pwmDomain = domainIterator.next();

            for (
                    final Iterator<UserReportRecord> reportRecordQueue = executeUserRecordReadJobs( reportProcessRequest, pwmDomain );
                    reportRecordQueue.hasNext() && !cancelFlag.get() && !recordLimitReached;
            )
            {
                processCounter++;

                if ( processCounter >= reportProcessRequest.getMaximumRecords() )
                {
                    recordLimitReached = true;
                }

                final UserReportRecord userReportRecord = reportRecordQueue.next();
                final boolean lastRecord = recordLimitReached || ( !reportRecordQueue.hasNext() && !domainIterator.hasNext() );
                recordWriter.outputRecord( userReportRecord, lastRecord );
                perRecordOutputTasks( userReportRecord, zipOutputStream );
            }
        }

        recordWriter.outputFooter();
        recordWriter.close();

        zipOutputStream.closeEntry();
        return recordLimitReached;
    }

    private Iterator<UserReportRecord> executeUserRecordReadJobs(
            final ReportProcessRequest reportProcessRequest,
            final PwmDomain pwmDomain
    )
            throws PwmUnrecoverableException, PwmOperationalException
    {
        final Queue<UserIdentity> identityIterator = readUserListFromLdap( reportProcessRequest, pwmDomain );
        final ExecutorService executor = pwmApplication.getReportService().getExecutor();
        final Queue<Future<UserReportRecord>> returnQueue = new ArrayDeque<>();
        while ( !identityIterator.isEmpty() )
        {
            returnQueue.add( executor.submit( new UserReportRecordReaderTask( identityIterator.poll() ) ) );
        }
        return new FutureUserReportRecordIterator( returnQueue );
    }

    private Collection<PwmDomain> applicableDomains( final ReportProcessRequest reportProcessRequest )
    {
        if ( reportProcessRequest.getDomainID() != null )
        {
            return Collections.singleton( pwmApplication.domains().get( reportProcessRequest.getDomainID() ) );
        }

        return pwmApplication.domains().values();
    }

    private void perRecordOutputTasks( final UserReportRecord userReportRecord, final ZipOutputStream zipOutputStream )
            throws IOException
    {
        checkCancel( zipOutputStream );
        summaryData.update( userReportRecord );
        recordCounter.incrementAndGet();
        processRateMeter.markEvents( 1 );
        debugOutputLogger.conditionallyExecuteTask();

        log( PwmLogLevel.TRACE, () -> "completed output of user " + UserIdentity.create(
                        userReportRecord.getUserDN(),
                        userReportRecord.getLdapProfile(),
                        userReportRecord.getDomainID() ).toDisplayString(),
                TimeDuration.fromCurrent( startTime ) );
    }

    private static void outputJsonSummaryToZip( final ReportSummaryCalculator reportSummary, final OutputStream outputStream )
    {
        try
        {
            final ReportSummaryData data = ReportSummaryData.fromCalculator( reportSummary );
            final String json = JsonFactory.get().serialize( data, ReportSummaryData.class, JsonProvider.Flag.PrettyPrint );
            outputStream.write( json.getBytes( PwmConstants.DEFAULT_CHARSET ) );
        }
        catch ( final IOException e )
        {
            throw new PwmInternalException( e.getMessage(), e );
        }
    }

    private UserReportRecord readUserReportRecord(
            final UserIdentity userIdentity
    )
    {
        try
        {
            final UserInfo userInfo = UserInfoFactory.newUserInfoUsingProxyForOfflineUser(
                    pwmApplication,
                    sessionLabel,
                    userIdentity );

            return UserReportRecord.fromUserInfo( userInfo );
        }
        catch ( final PwmUnrecoverableException e )
        {
            throw PwmInternalException.fromPwmException( e );
        }
    }

    Queue<UserIdentity> readUserListFromLdap(
            final ReportProcessRequest reportProcessRequest,
            final PwmDomain pwmDomain
    )
            throws PwmUnrecoverableException, PwmOperationalException
    {
        final Instant loopStartTime = Instant.now();
        final int maxSearchSize = ( int ) JavaHelper.rangeCheck( 0, reportSettings.getMaxSearchSize(), reportProcessRequest.getMaximumRecords() );
        log( PwmLogLevel.TRACE, () -> "beginning ldap search process for domain '" + pwmDomain.getDomainID() + "'", null );

        final List<UserPermission> searchFilters = reportSettings.getSearchFilter().get( pwmDomain.getDomainID() );

        final List<UserIdentity> searchResults = UserPermissionUtility.discoverMatchingUsers(
                pwmDomain,
                searchFilters,
                sessionLabel,
                maxSearchSize,
                reportSettings.getSearchTimeout() );

        log( PwmLogLevel.TRACE,
                () -> "completed ldap search process with for domain '" + pwmDomain.getDomainID() + "'",
                TimeDuration.fromCurrent( loopStartTime ) );

        processRateMeter.reset();
        return new ArrayDeque<>( searchResults );
    }

    public ReportProcessStatus getStatus( final Locale locale )
    {
        final List<DisplayElement> list = new ArrayList<>();
        if ( inProgress.get() )
        {
            list.add( new DisplayElement( "status", DisplayElement.Type.string,
                    "Status",
                    "In Progress" ) );
            list.add( new DisplayElement( "recordCount", DisplayElement.Type.number,
                    "Record Count",
                    String.valueOf( recordCounter.longValue() ) ) );
            list.add( new DisplayElement( "duration", DisplayElement.Type.string,
                    "Duration",
                    PwmTimeUtil.asLongString( TimeDuration.fromCurrent( startTime ), locale ) ) );
            if ( recordCounter.get() > 0 )
            {
                final String rate = processRateMeter.rawEps()
                        .multiply( new BigDecimal( "60" ) )
                        .setScale( 2, RoundingMode.UP ).toString();
                list.add( new DisplayElement( "eventRate", DisplayElement.Type.number,
                        "Users / Minute",
                        String.valueOf( rate ) ) );
            }
        }
        else
        {
            list.add( new DisplayElement( "status", DisplayElement.Type.string, "Status", "Idle" ) );
        }

        return new ReportProcessStatus( List.copyOf( list ), inProgress.get() );
    }

    public void close()
    {
        this.inProgress.set( false );
        if ( !cancelFlag.get() )
        {
            log( PwmLogLevel.TRACE, () -> "cancelling report process", null );
            cancelFlag.set( true );
        }
        reportServiceSemaphore.release();
    }

    public class UserReportRecordReaderTask implements Callable<UserReportRecord>
    {
        private final UserIdentity userIdentity;

        public UserReportRecordReaderTask( final UserIdentity userIdentity )
        {
            this.userIdentity = userIdentity;
        }

        @Override
        public UserReportRecord call()
        {
            if ( cancelFlag.get() )
            {
                throw new RuntimeException( "report process job cancelled" );
            }
            return readUserReportRecord( userIdentity );
        }
    }

    class FutureUserReportRecordIterator implements Iterator<UserReportRecord>
    {
        private final Queue<Future<UserReportRecord>> reportRecordQueue;

        FutureUserReportRecordIterator( final Queue<Future<UserReportRecord>> reportRecordQueue )
        {
            this.reportRecordQueue = reportRecordQueue;
        }

        @Override
        public boolean hasNext()
        {
            return !reportRecordQueue.isEmpty();
        }

        @Override
        public UserReportRecord next()
        {
            try
            {
                final Future<UserReportRecord> future = reportRecordQueue.poll();
                if ( future == null )
                {
                    throw new NoSuchMethodException();
                }
                return future.get();
            }
            catch ( final InterruptedException | ExecutionException | NoSuchMethodException e )
            {
                log( PwmLogLevel.TRACE, () -> "user report record job failure: " + e.getMessage(), null );
                throw new RuntimeException( e );
            }
        }
    }

    private long nextProcessId()
    {
        return REPORT_ID_LOCK.exec( () ->
        {
            final long lastId = pwmApplication.readAppAttribute( AppAttribute.REPORT_COUNTER, Long.class ).orElse( 0L );
            final long nextId = JavaHelper.nextPositiveLong( lastId );
            pwmApplication.writeAppAttribute( AppAttribute.REPORT_COUNTER, nextId );
            return nextId;
        } );
    }

}
