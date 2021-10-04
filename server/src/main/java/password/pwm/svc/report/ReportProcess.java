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
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.PwmDomain;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.SettingReader;
import password.pwm.config.value.data.UserPermission;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmInternalException;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.bean.DisplayElement;
import password.pwm.ldap.UserInfo;
import password.pwm.ldap.UserInfoFactory;
import password.pwm.ldap.permission.UserPermissionUtility;
import password.pwm.util.EventRateMeter;
import password.pwm.util.java.ConditionalTaskExecutor;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ReportProcess implements AutoCloseable
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( ReportProcess.class );

    private static final AtomicLong REPORT_COUNTER = new AtomicLong();

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
    private final EventRateMeter processRateMeter = new EventRateMeter( TimeDuration.of( 5, TimeDuration.Unit.MINUTES ) );


    private Instant startTime = Instant.now();
    private ReportSummaryData summaryData = ReportSummaryData.newSummaryData( Collections.singletonList( 1 ) );

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

        this.reportId = REPORT_COUNTER.incrementAndGet();

        this.debugOutputLogger = ConditionalTaskExecutor.forPeriodicTask(
                () -> LOGGER.trace( sessionLabel, () -> "live report #" + reportId + " in progress: " + recordCounter.longValue() + " records exported",
                        () -> TimeDuration.fromCurrent( startTime ) ),
                TimeDuration.MINUTE );

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
            throw new PwmUnrecoverableException( PwmError.ERROR_INTERNAL, "report process cannot be started, report already in progress" );
        }

        if ( !reportServiceSemaphore.tryAcquire() )
        {
            throw new PwmUnrecoverableException( PwmError.ERROR_INTERNAL, "report process cannot be started, maximum concurrent reports already in progress." );
        }

        this.startTime = Instant.now();
        this.summaryData = ReportSummaryData.newSummaryData( reportSettings.getTrackDays() );
        this.inProgress.set( true );
        this.cancelFlag.set( false );

        LOGGER.trace( sessionLabel, () -> "beginning live report #" + reportId + " generation, request parameters: " + JsonUtil.serialize( reportProcessRequest ) );

        try ( ZipOutputStream zipOutputStream = new ZipOutputStream( outputStream ) )
        {
            liveReportDownloadZipImpl( reportProcessRequest, zipOutputStream );
        }
        catch ( final IOException | PwmOperationalException e )
        {
            LOGGER.debug( sessionLabel, () -> "error during live report #" + reportId + " generation: " + e.getMessage() );
            throw new PwmUnrecoverableException( new ErrorInformation(  PwmError.ERROR_INTERNAL, e.getMessage() ) );
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
        final AtomicLong recordCounter = new AtomicLong();

        if ( reportProcessRequest.getReportType() == ReportProcessRequest.ReportType.json )
        {
            outputUsersToJson( reportProcessRequest, zipOutputStream );
        }
        else
        {
            outputUsersToCsv( reportProcessRequest, zipOutputStream );
        }

        checkCancel( zipOutputStream );
        outputSummary( zipOutputStream );

        checkCancel( zipOutputStream );
        outputResult( zipOutputStream );

        LOGGER.trace( sessionLabel, () -> "completed liveReport generation with " + recordCounter.longValue() + " records",
                () -> TimeDuration.fromCurrent( startTime ) );

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
            final ZipOutputStream zipOutputStream
    )
            throws IOException
    {
        zipOutputStream.putNextEntry( new ZipEntry( "result.json" ) );
        zipOutputStream.write( JsonUtil.serialize( generateResult() ).getBytes( StandardCharsets.UTF_8 ) );
        zipOutputStream.closeEntry();
    }

    private ReportProcessResult generateResult()
    {
        return new ReportProcessResult( recordCounter.incrementAndGet(), startTime, Instant.now() );
    }

    private void outputSummary(
            final ZipOutputStream zipOutputStream
    )
            throws IOException
    {
        {
            zipOutputStream.putNextEntry( new ZipEntry( "summary.json" ) );
            outputSummaryToZip( summaryData, zipOutputStream );
            zipOutputStream.closeEntry();
        }
        {
            zipOutputStream.putNextEntry( new ZipEntry( "summary.csv" ) );
            ReportCsvUtility.outputSummaryToCsv( pwmApplication.getConfig(), summaryData, zipOutputStream, locale );
            zipOutputStream.closeEntry();
        }
    }

    private void outputUsersToJson(
            final ReportProcessRequest reportProcessRequest,
            final ZipOutputStream zipOutputStream
    )
            throws IOException, PwmUnrecoverableException, PwmOperationalException
    {
        zipOutputStream.putNextEntry( new ZipEntry( "report.json" ) );

        for ( final PwmDomain pwmDomain : applicableDomains( reportProcessRequest ) )
        {
            final Queue<UserIdentity> identityIterator = new LinkedList<>( ReportProcess.readUserListFromLdap( reportProcessRequest, pwmDomain, sessionLabel, reportSettings ) );
            while ( identityIterator.peek() != null )
            {
                final UserReportRecord userReportRecord = readUserReportRecord( identityIterator.poll() );
                zipOutputStream.write( JsonUtil.serialize( userReportRecord ).getBytes( StandardCharsets.UTF_8 ) );
                perRecordOutputTasks( userReportRecord, zipOutputStream );

            }
        }

        zipOutputStream.closeEntry();
    }

    private Collection<PwmDomain> applicableDomains( final ReportProcessRequest reportProcessRequest )
    {
        if ( reportProcessRequest.getDomainID() != null )
        {
            return Collections.singleton( pwmApplication.domains().get( reportProcessRequest.getDomainID() ) );
        }

        return pwmApplication.domains().values();
    }

    private void outputUsersToCsv(
            final ReportProcessRequest reportProcessRequest,
            final ZipOutputStream zipOutputStream
    )
            throws IOException, PwmUnrecoverableException, PwmOperationalException
    {
        zipOutputStream.putNextEntry( new ZipEntry( "report.csv" ) );

        final CSVPrinter csvPrinter = JavaHelper.makeCsvPrinter( zipOutputStream );
        ReportCsvUtility.outputHeaderRow( locale, csvPrinter, pwmApplication.getConfig() );

        for ( final PwmDomain pwmDomain : applicableDomains( reportProcessRequest ) )
        {
            final Queue<UserIdentity> identityIterator = new LinkedList<>( ReportProcess.readUserListFromLdap( reportProcessRequest, pwmDomain, sessionLabel, reportSettings ) );
            while ( identityIterator.peek() != null )
            {
                final UserReportRecord userReportRecord = outputIdentityToCsvRow( identityIterator.poll(), csvPrinter );
                perRecordOutputTasks( userReportRecord, zipOutputStream );
            }
        }

        zipOutputStream.closeEntry();
    }

    private void perRecordOutputTasks( final UserReportRecord userReportRecord, final ZipOutputStream zipOutputStream )
            throws IOException
    {
        checkCancel( zipOutputStream );
        summaryData.update( userReportRecord );
        recordCounter.incrementAndGet();
        processRateMeter.markEvents( 1 );
        debugOutputLogger.conditionallyExecuteTask();
    }

    private static void outputSummaryToZip( final ReportSummaryData reportSummaryData, final OutputStream outputStream )
    {
        try
        {
            final String json = JsonUtil.serialize( reportSummaryData );
            outputStream.write( json.getBytes( StandardCharsets.UTF_8 ) );
        }
        catch ( final IOException e )
        {
            throw new PwmInternalException( e.getMessage(), e );
        }

    }

    private UserReportRecord outputIdentityToCsvRow(
            final UserIdentity userIdentity,
            final CSVPrinter csvPrinter
    )
    {
        try
        {
            final UserReportRecord userReportRecord = readUserReportRecord( userIdentity );
            final SettingReader settingReader = pwmApplication.getConfig();
            ReportCsvUtility.outputRecordRow( settingReader, locale, userReportRecord, csvPrinter );
            summaryData.update( userReportRecord );
            return userReportRecord;
        }
        catch ( final IOException e )
        {
            throw new PwmInternalException( e.getMessage(), e );
        }
    }

    private  UserReportRecord readUserReportRecord(
            final UserIdentity userIdentity
    )
    {
        try
        {
            final UserInfo userInfo = UserInfoFactory.newUserInfoUsingProxyForOfflineUser(
                    pwmApplication,
                    sessionLabel,
                    userIdentity
            );



            return UserReportRecord.fromUserInfo( userInfo );
        }
        catch ( final PwmUnrecoverableException e )
        {
            throw PwmInternalException.fromPwmException( e );
        }
    }

    static List<UserIdentity> readUserListFromLdap(
            final ReportProcessRequest reportProcessRequest,
            final PwmDomain pwmDomain,
            final SessionLabel sessionLabel,
            final ReportSettings settings
    )
            throws PwmUnrecoverableException, PwmOperationalException
    {
        final Instant loopStartTime = Instant.now();
        final int maxSearchSize = ( int ) JavaHelper.rangeCheck( 0, settings.getMaxSearchSize(), reportProcessRequest.getMaximumRecords() );
        LOGGER.trace( sessionLabel, () -> "beginning ldap search process for domain '" + pwmDomain.getDomainID() + "'" );

        final List<UserPermission> searchFilters = settings.getSearchFilter().get( pwmDomain.getDomainID() );

        final List<UserIdentity> searchResults = UserPermissionUtility.discoverMatchingUsers(
                pwmDomain,
                searchFilters,
                sessionLabel,
                maxSearchSize,
                settings.getSearchTimeout() );

        LOGGER.trace(
                sessionLabel,
                () -> "completed ldap search process with for domain '" + pwmDomain.getDomainID() + "'",
                () -> TimeDuration.fromCurrent( loopStartTime ) );

        return searchResults;
    }

    public ReportProcessStatus getStatus( final Locale locale )
    {
        final List<DisplayElement> list = new ArrayList<>();
        if ( inProgress.get() )
        {
            list.add( new DisplayElement( "status", DisplayElement.Type.string, "Status", "In Progress" ) );
            list.add( new DisplayElement( "recordCount", DisplayElement.Type.number, "Record Count", String.valueOf( recordCounter.longValue() ) ) );
            list.add( new DisplayElement( "duration", DisplayElement.Type.string, "Duration", TimeDuration.fromCurrent( startTime ).asLongString( locale ) ) );
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
            LOGGER.trace( sessionLabel, () -> "cancelling report process" );
            cancelFlag.set( true );
        }
        reportServiceSemaphore.release();
    }
}
