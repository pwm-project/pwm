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

import lombok.Value;
import org.jetbrains.annotations.NotNull;
import password.pwm.AppAttribute;
import password.pwm.PwmDomain;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.value.data.UserPermission;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.bean.DisplayElement;
import password.pwm.ldap.permission.UserPermissionUtility;
import password.pwm.util.EventRateMeter;
import password.pwm.util.PwmScheduler;
import password.pwm.util.java.ConditionalTaskExecutor;
import password.pwm.util.java.FunctionalReentrantLock;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.PwmTimeUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.logging.PwmLogLevel;
import password.pwm.util.logging.PwmLogger;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ReportProcess implements AutoCloseable
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( ReportProcess.class );

    private static final FunctionalReentrantLock REPORT_ID_LOCK = new FunctionalReentrantLock();

    private final PwmDomain pwmDomain;
    private final ReportService reportService;

    private final ReportSettings reportSettings;

    private final ConditionalTaskExecutor debugOutputLogger;
    private final long reportId;
    private final ReportProcessRequest reportProcessRequest;

    private final AtomicLong recordCounter = new AtomicLong();
    private final AtomicBoolean inProgress = new AtomicBoolean();
    private final AtomicBoolean cancelFlag = new AtomicBoolean();
    private final EventRateMeter processRateMeter = new EventRateMeter( TimeDuration.MINUTE.asDuration() );
    private final List<String> recordErrorMessages = new ArrayList<>();

    private Instant startTime = Instant.now();
    private ReportSummaryCalculator summaryCalculator = ReportSummaryCalculator.empty();
    private ReportProcessResult result;

    ReportProcess(
            final PwmDomain pwmDomain,
            final ReportService reportService,
            final ReportProcessRequest reportProcessRequest,
            final ReportSettings reportSettings
    )
    {
        this.pwmDomain = Objects.requireNonNull( pwmDomain );
        this.reportService = Objects.requireNonNull( reportService );
        this.reportSettings = Objects.requireNonNull( reportSettings );
        this.reportProcessRequest = reportProcessRequest;

        this.reportId = nextProcessId();

        this.debugOutputLogger = ConditionalTaskExecutor.forPeriodicTask(
                this::logStatus,
                TimeDuration.MINUTE.asDuration() );
    }

    void log( final PwmLogLevel level, final Supplier<String> message, final TimeDuration timeDuration )
    {
        final Supplier<String> wrappedMsg = () -> "report #" + reportId + " " + message.get();
        LOGGER.log( level, reportProcessRequest.getSessionLabel(), wrappedMsg, null, timeDuration );
    }

    private void logStatus()
    {
        log( PwmLogLevel.TRACE,
                () -> "in progress: " + recordCounter.longValue()
                        + " records exported at " + processRateMeter.prettyEps( reportProcessRequest.getLocale() )
                        + " records/second, duration: "
                        + TimeDuration.fromCurrent( startTime ).asCompactString()
                        + " jobDetails: "
                        + JsonFactory.get().serialize( reportProcessRequest ),
                null );
    }

    boolean isCancelled()
    {
        return cancelFlag.get();
    }

    PwmDomain getPwmDomain()
    {
        return pwmDomain;
    }

    SessionLabel getSessionLabel()
    {
        return reportProcessRequest.getSessionLabel();
    }

    Optional<ReportProcessResult> getResult()
    {
        return Optional.ofNullable( result );
    }

    static ReportProcess createReportProcess(
            final PwmDomain pwmDomain,
            final ReportService reportService,
            final ReportProcessRequest reportProcessRequest,
            final ReportSettings reportSettings
    )
    {
        return new ReportProcess( pwmDomain, reportService, reportProcessRequest, reportSettings );
    }

    public void startReport(
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

        if ( cancelFlag.get() )
        {
            throw new PwmUnrecoverableException( PwmError.ERROR_INTERNAL, "report process #" + reportId + " cannot be started, report already cancelled" );
        }

        this.startTime = Instant.now();
        this.summaryCalculator = ReportSummaryCalculator.newSummaryData( reportSettings.getTrackDays() );
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
            throw new PwmUnrecoverableException( new ErrorInformation(  PwmError.ERROR_INTERNAL, e.getMessage() ) );
        }
        catch ( final IOException e )
        {
            log( PwmLogLevel.DEBUG, () -> "I/O error during report generation: " + e.getMessage(), null );
        }
        catch ( final Exception e )
        {
            log( PwmLogLevel.DEBUG, () -> "Job interrupted during report generation: " + e.getMessage(), null );
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
            throws PwmUnrecoverableException, PwmOperationalException, IOException, ExecutionException, InterruptedException
    {
        final ReportRecordWriter recordWriter = reportProcessRequest.getReportType() == ReportProcessRequest.ReportType.json
                ? new ReportJsonRecordWriter( zipOutputStream )
                : new ReportCsvRecordWriter( zipOutputStream, pwmDomain, reportProcessRequest.getLocale() );

        result = executeDomainReport( reportProcessRequest, zipOutputStream, recordWriter );

        checkCancel( zipOutputStream );
        ReportProcessUtil.outputSummary( pwmDomain, summaryCalculator, reportProcessRequest.getLocale(), zipOutputStream );

        checkCancel( zipOutputStream );
        ReportProcessUtil.outputResult( zipOutputStream, result );

        checkCancel( zipOutputStream );
        ReportProcessUtil.outputErrors( zipOutputStream, recordErrorMessages );

        log( PwmLogLevel.TRACE, () -> "completed report generation with " + recordCounter.longValue() + " records at "
                + processRateMeter.prettyEps( reportProcessRequest.getLocale() ), TimeDuration.fromCurrent( startTime ) );

    }

    private void checkCancel( final OutputStream outputStream )
            throws IOException
    {
        if ( cancelFlag.get() )
        {
            outputStream.close();
        }
    }

    private ReportProcessResult executeDomainReport(
            final ReportProcessRequest reportProcessRequest,
            final ZipOutputStream zipOutputStream,
            final ReportRecordWriter recordWriter
    )
            throws IOException, PwmUnrecoverableException, PwmOperationalException, InterruptedException
    {
        final Instant startTime = Instant.now();

        zipOutputStream.putNextEntry( new ZipEntry( recordWriter.getZipName() ) );
        recordWriter.outputHeader();

        final long jobTimeoutMs = reportSettings.getReportJobTimeout().asMillis();

        int recordCounter = 0;
        int errorCounter = 0;
        boolean recordLimitReached = false;

        final CompletionWrapper<UserReportRecord> completion = createAndSubmitRecordReaderTasks( reportProcessRequest );

        try
        {
            while ( recordCounter < completion.getItemCount() && !cancelFlag.get() && !recordLimitReached )
            {
                final Future<UserReportRecord> future = completion.getCompletionService().poll( jobTimeoutMs, TimeUnit.MILLISECONDS );
                recordCounter++;

                try
                {
                    final UserReportRecord nextRecord = future.get();
                    recordWriter.outputRecord( nextRecord );
                    perRecordOutputTasks( nextRecord, zipOutputStream );
                }
                catch ( final Exception e )
                {
                    errorCounter++;

                    final String msg = JavaHelper.readHostileExceptionMessage( e.getCause() );

                    log( PwmLogLevel.TRACE, () -> msg, null );

                    if ( recordErrorMessages.size() < reportSettings.getMaxErrorRecords() )
                    {
                        recordErrorMessages.add( msg );
                    }
                }

                if ( recordCounter >= reportProcessRequest.getMaximumRecords() )
                {
                    recordLimitReached = true;
                }
            }

            recordWriter.outputFooter();
            recordWriter.close();
        }
        finally
        {
            completion.getExecutorService().shutdownNow();
        }

        final Instant finishTime = Instant.now();
        final TimeDuration duration = TimeDuration.between( startTime, finishTime );
        return new ReportProcessResult(
                reportProcessRequest,
                recordCounter,
                errorCounter,
                startTime,
                finishTime,
                duration,
                recordLimitReached,
                pwmDomain.getPwmApplication().getInstanceID(),
                Long.toString( reportId ) );
    }

    private CompletionWrapper<UserReportRecord> createAndSubmitRecordReaderTasks(
            final ReportProcessRequest reportProcessRequest
    )
            throws PwmUnrecoverableException, PwmOperationalException
    {
        final Queue<UserIdentity> identityIterator = readUserListFromLdap( reportProcessRequest );

        final ExecutorService executorService = PwmScheduler.makeMultiThreadExecutor(
                reportSettings.getReportJobThreads(),
                pwmDomain.getPwmApplication(),
                getSessionLabel(),
                ReportProcess.class,
                "reportId-" + reportId );

        final CompletionService<UserReportRecord> completionService = new ExecutorCompletionService<>( executorService );

        int itemCount = 0;
        while ( !identityIterator.isEmpty() )
        {
            final UserIdentity nextIdentity = identityIterator.poll();
            final UserReportRecordReaderTask task = new UserReportRecordReaderTask( this, nextIdentity );
            completionService.submit( task );
            itemCount++;
        }

        return new CompletionWrapper<>( completionService, executorService, itemCount );
    }

    private void perRecordOutputTasks( final UserReportRecord userReportRecord, final ZipOutputStream zipOutputStream )
            throws IOException
    {
        checkCancel( zipOutputStream );
        summaryCalculator.update( userReportRecord );
        recordCounter.incrementAndGet();
        processRateMeter.markEvents( 1 );
        debugOutputLogger.conditionallyExecuteTask();
    }

    Queue<UserIdentity> readUserListFromLdap(
            final ReportProcessRequest reportProcessRequest
    )
            throws PwmUnrecoverableException, PwmOperationalException
    {
        final Instant loopStartTime = Instant.now();
        final int maxSearchSize = ( int ) JavaHelper.rangeCheck( 0, reportSettings.getMaxSearchSize(), reportProcessRequest.getMaximumRecords() );


        final PwmDomain pwmDomain = getPwmDomain();
        log( PwmLogLevel.TRACE, () -> "beginning ldap search process for domain '" + pwmDomain.getDomainID() + "'", null );
        final List<UserPermission> searchFilters = reportSettings.getSearchFilter().get( pwmDomain.getDomainID() );

        final List<UserIdentity> searchResults = UserPermissionUtility.discoverMatchingUsers(
                pwmDomain,
                searchFilters,
                reportProcessRequest.getSessionLabel(),
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

        reportService.closeReportProcess( this );
    }

    private long nextProcessId()
    {
        return REPORT_ID_LOCK.exec( () ->
        {
            final long lastId = pwmDomain.getPwmApplication().readAppAttribute( AppAttribute.REPORT_COUNTER, Long.class ).orElse( 0L );
            final long nextId = JavaHelper.nextPositiveLong( lastId );
            pwmDomain.getPwmApplication().writeAppAttribute( AppAttribute.REPORT_COUNTER, nextId );
            return nextId;
        } );
    }

    @Value
    private static class CompletionWrapper<E>
    {
        private final CompletionService<E> completionService;
        private final ExecutorService executorService;
        private final int itemCount;
    }
}
