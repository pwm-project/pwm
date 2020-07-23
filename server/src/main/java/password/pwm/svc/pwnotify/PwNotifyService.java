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

package password.pwm.svc.pwnotify;

import password.pwm.PwmApplication;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthMessage;
import password.pwm.health.HealthRecord;
import password.pwm.svc.AbstractPwmService;
import password.pwm.svc.PwmService;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsManager;
import password.pwm.util.PwmScheduler;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

public class PwNotifyService extends AbstractPwmService implements PwmService
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( PwNotifyService.class );

    private ExecutorService executorService;
    private PwmApplication pwmApplication;
    private PwNotifyEngine engine;
    private PwNotifySettings settings;
    private Instant nextExecutionTime;
    private PwNotifyStorageService storageService;

    private DataStorageMethod storageMethod;

    public PwNotifyStoredJobState getJobState() throws PwmUnrecoverableException
    {
        if ( status() != STATUS.OPEN )
        {
            if ( getStartupError() != null )
            {
                return PwNotifyStoredJobState.builder().lastError( getStartupError() ).build();
            }

            return PwNotifyStoredJobState.builder().build();
        }

        return storageService.readStoredJobState();
    }

    public boolean isRunning()
    {
        return engine != null && engine.isRunning();
    }

    public String debugLog()
    {
        if ( engine != null && !StringUtil.isEmpty( engine.getDebugLog() ) )
        {
            return engine.getDebugLog();
        }

        if ( getStartupError(  ) != null )
        {
            return getStartupError().toDebugStr();
        }

        return "";
    }

    @Override
    public void init( final PwmApplication pwmApplication ) throws PwmException
    {
        this.pwmApplication = pwmApplication;

        if ( !pwmApplication.getConfig().readSettingAsBoolean( PwmSetting.PW_EXPY_NOTIFY_ENABLE ) )
        {
            LOGGER.trace( SessionLabel.PWNOTIFY_SESSION_LABEL, () -> "will remain closed, pw notify feature is not enabled" );
            setStatus( STATUS.CLOSED );
            return;
        }

        try
        {
            if ( pwmApplication.getClusterService() == null || pwmApplication.getClusterService().status() != STATUS.OPEN )
            {
                throw PwmUnrecoverableException.newException( PwmError.ERROR_PWNOTIFY_SERVICE_ERROR, "will remain closed, node service is not running" );
            }

            settings = PwNotifySettings.fromConfiguration( pwmApplication.getConfig() );
            storageMethod = pwmApplication.getConfig().readSettingAsEnum( PwmSetting.PW_EXPY_NOTIFY_STORAGE_MODE, DataStorageMethod.class );

            switch ( storageMethod )
            {
                case LDAP:
                {
                    storageService = new PwNotifyLdapStorageService( pwmApplication, settings );
                }
                break;

                case DB:
                {
                    storageService = new PwNotifyDbStorageService( pwmApplication );
                }
                break;

                default:
                    JavaHelper.unhandledSwitchStatement( storageMethod );
            }

            executorService = PwmScheduler.makeBackgroundExecutor( pwmApplication, this.getClass() );

            engine = new PwNotifyEngine( pwmApplication, storageService, () -> status() == STATUS.CLOSED, null );

            pwmApplication.getPwmScheduler().scheduleFixedRateJob( new PwNotifyJob(), executorService, TimeDuration.MINUTE, TimeDuration.MINUTE );

            setStatus( STATUS.OPEN );
        }
        catch ( final PwmUnrecoverableException e )
        {
            setStatus( STATUS.CLOSED );
            LOGGER.trace( SessionLabel.PWNOTIFY_SESSION_LABEL, () -> "will remain closed, pw notify feature is not enabled due to error: " + e.getMessage() );
            setStartupError( e.getErrorInformation() );
        }
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
            LOGGER.debug( SessionLabel.PWNOTIFY_SESSION_LABEL, () -> "scheduled next job execution at " + nextExecutionTime.toString() );
        }
        catch ( final Exception e )
        {
            LOGGER.error( SessionLabel.PWNOTIFY_SESSION_LABEL, () -> "error calculating next job execution time: " + e.getMessage() );
        }
    }

    private Instant figureNextJobExecutionTime()
            throws PwmUnrecoverableException
    {
        final PwNotifyStoredJobState pwNotifyStoredJobState = storageService.readStoredJobState();
        if ( pwNotifyStoredJobState != null )
        {
            // never run, or last job not successful.
            if ( pwNotifyStoredJobState.getLastCompletion() == null || pwNotifyStoredJobState.getLastError() != null )
            {
                return Instant.now().plus( 1, ChronoUnit.MINUTES );
            }

            // more than 24hr ago.
            final long maxSeconds = settings.getMaximumSkipWindow().as( TimeDuration.Unit.SECONDS );
            if ( Duration.between( Instant.now(), pwNotifyStoredJobState.getLastCompletion() ).abs().getSeconds() > maxSeconds )
            {
                return Instant.now();
            }
        }

        final Instant nextZuluZeroTime = PwmScheduler.nextZuluZeroTime();
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
        setStatus( STATUS.CLOSED );
        JavaHelper.closeAndWaitExecutor( executorService, TimeDuration.of( 5, TimeDuration.Unit.SECONDS ) );
    }

    @Override
    protected List<HealthRecord> serviceHealthCheck( )
    {
        if ( status() != STATUS.OPEN )
        {
            return Collections.emptyList();
        }

        final List<HealthRecord> returnRecords = new ArrayList<>( );

        try
        {
            final PwNotifyStoredJobState pwNotifyStoredJobState = storageService.readStoredJobState();
            if ( pwNotifyStoredJobState != null )
            {
                final ErrorInformation errorInformation = pwNotifyStoredJobState.getLastError();
                if ( errorInformation != null )
                {
                    returnRecords.add( HealthRecord.forMessage( HealthMessage.PwNotify_Failure, errorInformation.toDebugStr() ) );
                }
            }
        }
        catch ( final PwmUnrecoverableException e  )
        {
            LOGGER.error( SessionLabel.PWNOTIFY_SESSION_LABEL, () -> "error while generating health information: " + e.getMessage() );
        }

        return returnRecords;
    }

    @Override
    public ServiceInfoBean serviceInfo( )
    {
        return new ServiceInfoBean( Collections.singleton( storageMethod ), Collections.emptyMap() );
    }

    public void executeJob( )
    {
        if ( status() != STATUS.OPEN )
        {
            LOGGER.trace( SessionLabel.PWNOTIFY_SESSION_LABEL, () -> "ignoring job request start, service is not open" );
            return;
        }

        if ( !isRunning() )
        {
            nextExecutionTime = Instant.now();
            pwmApplication.getPwmScheduler().scheduleJob( new PwNotifyJob(), executorService, TimeDuration.ZERO );
        }
    }

    public boolean canRunOnThisServer()
    {
        if ( status() == STATUS.OPEN )
        {
            return engine.canRunOnThisServer();
        }

        return false;
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
                catch ( final Exception e )
                {
                    LOGGER.error( SessionLabel.PWNOTIFY_SESSION_LABEL, () -> "unexpected error running job: " + e.getMessage() );
                }
            }
        }

        private void doJob( )
        {
            setStartupError( null );
            final Instant start = Instant.now();
            try
            {
                storageService.writeStoredJobState( new PwNotifyStoredJobState( Instant.now(), null, pwmApplication.getInstanceID(), null, false ) );
                StatisticsManager.incrementStat( pwmApplication, Statistic.PWNOTIFY_JOBS );
                engine.executeJob();

                final Instant finish = Instant.now();
                final PwNotifyStoredJobState pwNotifyStoredJobState = new PwNotifyStoredJobState( start, finish, pwmApplication.getInstanceID(), null, true );
                storageService.writeStoredJobState( pwNotifyStoredJobState );
            }
            catch ( final Exception e )
            {
                final ErrorInformation errorInformation;
                if ( e instanceof PwmException )
                {
                    errorInformation = ( ( PwmException ) e ).getErrorInformation();
                }
                else
                {
                    errorInformation = new ErrorInformation( PwmError.ERROR_INTERNAL, "error " + e.getMessage() );
                }

                final Instant finish = Instant.now();
                final String instanceID = pwmApplication.getInstanceID();
                final PwNotifyStoredJobState pwNotifyStoredJobState = new PwNotifyStoredJobState( start, finish, instanceID, errorInformation, false );

                try
                {
                    storageService.writeStoredJobState( pwNotifyStoredJobState );
                }
                catch ( final Exception e2 )
                {
                    //no hope
                }
                StatisticsManager.incrementStat( pwmApplication, Statistic.PWNOTIFY_JOB_ERRORS );
                LOGGER.debug( SessionLabel.PWNOTIFY_SESSION_LABEL, errorInformation );
                setStartupError( errorInformation );
            }
        }
    }

    public Optional<PwNotifyUserStatus> readUserNotificationState(
            final UserIdentity userIdentity,
            final SessionLabel sessionLabel
    )
            throws PwmUnrecoverableException
    {
        if ( status() == STATUS.OPEN )
        {
            return storageService.readStoredUserState( userIdentity, sessionLabel );
        }

        throw PwmUnrecoverableException.newException( PwmError.ERROR_SERVICE_NOT_AVAILABLE, "pwnotify service is not open" );
    }
}
