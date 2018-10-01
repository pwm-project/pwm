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

package password.pwm.svc.pwnotify;

import password.pwm.PwmApplication;
import password.pwm.bean.SessionLabel;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthMessage;
import password.pwm.health.HealthRecord;
import password.pwm.svc.PwmService;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsManager;
import password.pwm.util.db.DatabaseException;
import password.pwm.util.db.DatabaseTable;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PwNotifyService implements PwmService
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( PwNotifyService.class );

    private ScheduledExecutorService executorService;
    private PwmApplication pwmApplication;
    private STATUS status = STATUS.NEW;
    private PwNotifyEngine engine;
    private PwNotifySettings settings;
    private Instant nextExecutionTime;

    @Override
    public STATUS status( )
    {
        return status;
    }

    private static final String DB_STATE_STRING = "PwNotifyJobState";

    private StoredJobState readStoredJobState()
            throws PwmUnrecoverableException, DatabaseException
    {
        final String strValue = pwmApplication.getDatabaseService().getAccessor().get( DatabaseTable.PW_NOTIFY, DB_STATE_STRING );
        if ( StringUtil.isEmpty( strValue ) )
        {
            return new StoredJobState( null, null, null, null, false );
        }
        return JsonUtil.deserialize( strValue, StoredJobState.class );
    }

    public StoredJobState getJobState() throws DatabaseException, PwmUnrecoverableException
    {
        return readStoredJobState();
    }

    public boolean isRunning()
    {
        return engine != null && engine.isRunning();
    }

    public String debugLog()
    {
        if ( engine != null )
        {
            return engine.getDebugLog();
        }
        return "";
    }

    private void writeStoredJobState( final StoredJobState storedJobState )
            throws PwmUnrecoverableException, DatabaseException
    {
        final String strValue = JsonUtil.serialize( storedJobState );
        pwmApplication.getDatabaseService().getAccessor().put( DatabaseTable.PW_NOTIFY, DB_STATE_STRING, strValue );
    }

    @Override
    public void init( final PwmApplication pwmApplication ) throws PwmException
    {
        this.pwmApplication = pwmApplication;

        if ( !pwmApplication.getConfig().readSettingAsBoolean( PwmSetting.PW_EXPY_NOTIFY_ENABLE ) )
        {
            status = STATUS.CLOSED;
            LOGGER.trace( SessionLabel.PWNOTIFY_SESSION_LABEL, "will remain closed, pw notify feature is not enabled" );
            return;
        }

        settings = PwNotifySettings.fromConfiguration( pwmApplication.getConfig() );
        engine = new PwNotifyEngine( pwmApplication, null );

        executorService = Executors.newSingleThreadScheduledExecutor(
                JavaHelper.makePwmThreadFactory(
                        JavaHelper.makeThreadName( pwmApplication, this.getClass() ) + "-",
                        true
                ) );

        executorService.scheduleWithFixedDelay( new PwNotifyJob(), 1, 1, TimeUnit.MINUTES );

        status = STATUS.OPEN;
    }

    public Instant getNextExecutionTime( )
    {
        return nextExecutionTime;
    }

    private void scheduleNextJobExecution()
    {
        try
        {
            nextExecutionTime = figureNextJobExecutionTime();
            LOGGER.debug( SessionLabel.PWNOTIFY_SESSION_LABEL, "scheduled next job execution at " + nextExecutionTime.toString() );
        }
        catch ( Exception e )
        {
            LOGGER.error( SessionLabel.PWNOTIFY_SESSION_LABEL, "error calculating next job execution time: " + e.getMessage() );
        }
    }

    private Instant figureNextJobExecutionTime() throws DatabaseException, PwmUnrecoverableException
    {
        final StoredJobState storedJobState = readStoredJobState();
        if ( storedJobState != null )
        {
            // never run, or last job not successful.
            if ( storedJobState.getLastCompletion() == null || storedJobState.getLastError() != null )
            {
                return Instant.now().plus( 1, ChronoUnit.MINUTES );
            }

            // more than 24hr ago.
            if ( Duration.between( Instant.now(), storedJobState.getLastCompletion() ).abs().getSeconds() > settings.getMaximumSkipWindow().as( TimeDuration.Unit.SECONDS ) )
            {
                return Instant.now();
            }
        }

        final Instant nextZuluZeroTime = JavaHelper.nextZuluZeroTime();
        final Instant adjustedNextZuluZeroTime = nextZuluZeroTime.plus( settings.getZuluOffset().as( TimeDuration.Unit.SECONDS ), ChronoUnit.SECONDS );
        final Instant previousAdjustedZuluZeroTime = adjustedNextZuluZeroTime.minus( 1, ChronoUnit.DAYS );

        if ( previousAdjustedZuluZeroTime.isAfter( Instant.now() ) )
        {
            return previousAdjustedZuluZeroTime;
        }
        return adjustedNextZuluZeroTime;
    }

    @Override
    public void close( )
    {
        status = STATUS.CLOSED;
        JavaHelper.closeAndWaitExecutor( executorService, TimeDuration.of( 5, TimeDuration.Unit.SECONDS ) );
    }

    @Override
    public List<HealthRecord> healthCheck( )
    {
        if ( status != STATUS.OPEN )
        {
            return Collections.emptyList();
        }

        try
        {
            final StoredJobState storedJobState = readStoredJobState();
            if ( storedJobState != null )
            {
                final ErrorInformation errorInformation = storedJobState.getLastError();
                if ( errorInformation != null )
                {
                    return Collections.singletonList( HealthRecord.forMessage( HealthMessage.PwNotify_Failure, errorInformation.toDebugStr() ) );
                }
            }
        }
        catch ( DatabaseException | PwmUnrecoverableException e  )
        {
            LOGGER.error( SessionLabel.PWNOTIFY_SESSION_LABEL, "error while generating health information: " + e.getMessage() );
        }
        return null;
    }

    @Override
    public ServiceInfoBean serviceInfo( )
    {
        return null;
    }

    public void executeJob( )
    {
        if ( status != STATUS.OPEN )
        {
            LOGGER.trace( SessionLabel.PWNOTIFY_SESSION_LABEL, "ignoring job request start, service is not open" );
            return;
        }

        if ( !isRunning() )
        {
            nextExecutionTime = Instant.now();
            executorService.schedule( new PwNotifyJob(), 1, TimeUnit.SECONDS );
        }
    }

    public boolean canRunOnThisServer()
    {
        return engine.canRunOnThisServer();
    }

    class PwNotifyJob implements Runnable
    {
        @Override
        public void run( )
        {
            if ( !canRunOnThisServer() )
            {
                nextExecutionTime = null;
                return;
            }

            if ( nextExecutionTime == null )
            {
                scheduleNextJobExecution();
            }

            if ( nextExecutionTime != null && nextExecutionTime.isBefore( Instant.now() ) )
            {
                try
                {
                    doJob();
                    scheduleNextJobExecution();
                }
                catch ( Exception e )
                {
                    LOGGER.error( SessionLabel.PWNOTIFY_SESSION_LABEL, "unexpected error running job: " + e.getMessage() );
                }
            }
        }

        private void doJob( )
        {
            final Instant start = Instant.now();
            try
            {
                writeStoredJobState( new StoredJobState( Instant.now(), null, pwmApplication.getInstanceID(), null, false ) );
                StatisticsManager.incrementStat( pwmApplication, Statistic.PWNOTIFY_JOBS );
                engine.executeJob();

                final Instant finish = Instant.now();
                final StoredJobState storedJobState = new StoredJobState( start, finish, pwmApplication.getInstanceID(), null, true );
                writeStoredJobState( storedJobState );
            }
            catch ( Exception e )
            {
                final ErrorInformation errorInformation;
                if ( e instanceof PwmException )
                {
                    errorInformation = ( ( PwmException ) e ).getErrorInformation();
                }
                else
                {
                    errorInformation = new ErrorInformation( PwmError.ERROR_UNKNOWN, "error " + e.getMessage() );
                }

                final Instant finish = Instant.now();
                final String instanceID = pwmApplication.getInstanceID();
                final StoredJobState storedJobState = new StoredJobState( start, finish, instanceID, errorInformation, false );

                try
                {
                    writeStoredJobState( storedJobState );
                }
                catch ( Exception e2 )
                {
                    //no hope
                }
                StatisticsManager.incrementStat( pwmApplication, Statistic.PWNOTIFY_JOB_ERRORS );
                LOGGER.debug( SessionLabel.PWNOTIFY_SESSION_LABEL, "error executing scheduled job: " + e.getMessage() );
            }
        }
    }
}
