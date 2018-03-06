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
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthRecord;
import password.pwm.svc.PwmService;
import password.pwm.util.db.DatabaseException;
import password.pwm.util.db.DatabaseTable;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.time.Instant;
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
            return new StoredJobState( null, null, null, null );
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
            LOGGER.trace( "will remain closed, pw notify feature is not enabled" );
            return;
        }

        engine = new PwNotifyEngine( pwmApplication, null );

        executorService = Executors.newSingleThreadScheduledExecutor(
                JavaHelper.makePwmThreadFactory(
                        JavaHelper.makeThreadName( pwmApplication, this.getClass() ) + "-",
                        true
                ) );

        {
            final long jobOffsetSeconds = pwmApplication.getConfig().readSettingAsLong( PwmSetting.PW_EXPY_NOTIFY_JOB_OFFSET );
            final Instant nextZuluZeroTime = JavaHelper.nextZuluZeroTime();
            final long secondsUntilNextDredge = jobOffsetSeconds + TimeDuration.fromCurrent( nextZuluZeroTime ).getTotalSeconds();
            executorService.scheduleAtFixedRate( new DailyJobRunning(), secondsUntilNextDredge, TimeDuration.DAY.getTotalSeconds(), TimeUnit.SECONDS );
            LOGGER.debug( "scheduled daily execution, next task will be at " + nextZuluZeroTime.toString() );
        }
    }

    @Override
    public void close( )
    {
        JavaHelper.closeAndWaitExecutor( executorService, new TimeDuration( 5, TimeUnit.SECONDS ) );
    }

    @Override
    public List<HealthRecord> healthCheck( )
    {
        return null;
    }

    @Override
    public ServiceInfoBean serviceInfo( )
    {
        return null;
    }

    public void runJob( )
    {
        executorService.schedule( new DailyJobRunning(), 1, TimeUnit.SECONDS );
    }

    class DailyJobRunning implements Runnable
    {
        @Override
        public void run( )
        {
            final Instant start = Instant.now();
            try
            {
                writeStoredJobState( new StoredJobState() );
                engine.executeJob();
                final Instant finish = Instant.now();
                final StoredJobState storedJobState = new StoredJobState( start, finish, pwmApplication.getInstanceID(), null );
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
                final StoredJobState storedJobState = new StoredJobState( start, finish, instanceID, errorInformation );
                try
                {
                    writeStoredJobState( storedJobState );
                }
                catch ( Exception e2 )
                {
                    //no hope
                }
                LOGGER.debug( "error executing scheduled job: " + e.getMessage() );
            }
        }
    }


}
