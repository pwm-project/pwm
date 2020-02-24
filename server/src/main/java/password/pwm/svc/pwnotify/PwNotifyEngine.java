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

import com.novell.ldapchai.ChaiUser;
import password.pwm.PwmApplication;
import password.pwm.bean.EmailItemBean;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.config.value.data.UserPermission;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.ldap.LdapOperationsHelper;
import password.pwm.ldap.UserInfo;
import password.pwm.ldap.UserInfoFactory;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsManager;
import password.pwm.util.PwmScheduler;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.java.ConditionalTaskExecutor;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;

import java.io.IOException;
import java.io.Writer;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class PwNotifyEngine
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( PwNotifyEngine.class );

    private static final SessionLabel SESSION_LABEL = SessionLabel.PW_EXP_NOTICE_LABEL;

    private static final int MAX_LOG_SIZE = 1024 * 1024 * 1024;

    private final PwNotifySettings settings;
    private final PwmApplication pwmApplication;
    private final Writer debugWriter;
    private final StringBuffer internalLog = new StringBuffer(  );
    private final List<UserPermission> permissionList;
    private final PwNotifyStorageService storageService;
    private final Supplier<Boolean> cancelFlag;

    private final ConditionalTaskExecutor debugOutputTask = new ConditionalTaskExecutor(
            this::periodicDebugOutput,
            new ConditionalTaskExecutor.TimeDurationPredicate( 1, TimeDuration.Unit.MINUTES )
    );

    private final AtomicInteger examinedCount = new AtomicInteger( 0 );
    private final AtomicInteger noticeCount = new AtomicInteger( 0 );
    private Instant startTime;

    private volatile boolean running;

    PwNotifyEngine(
            final PwmApplication pwmApplication,
            final PwNotifyStorageService storageService,
            final Supplier<Boolean> cancelFlag,
            final Writer debugWriter
    )
    {
        this.pwmApplication = pwmApplication;
        this.cancelFlag = cancelFlag;
        this.storageService = storageService;
        this.settings = PwNotifySettings.fromConfiguration( pwmApplication.getConfig() );
        this.debugWriter = debugWriter;
        this.permissionList = pwmApplication.getConfig().readSettingAsUserPermission( PwmSetting.PW_EXPY_NOTIFY_PERMISSION );
    }

    public boolean isRunning()
    {
        return running;
    }

    public String getDebugLog()
    {
        return internalLog.toString();
    }

    private boolean checkIfRunningOnMaster( )
    {
        if ( !pwmApplication.getPwmEnvironment().isInternalRuntimeInstance() )
        {
            if ( pwmApplication.getClusterService() != null && pwmApplication.getClusterService().isMaster() )
            {
                return true;
            }
        }

        return false;
    }

    boolean canRunOnThisServer()
    {
        return checkIfRunningOnMaster();
    }

    void executeJob( )
            throws PwmOperationalException, PwmUnrecoverableException
    {
        startTime = Instant.now();
        examinedCount.set( 0 );
        noticeCount.set( 0 );
        try
        {
            internalLog.delete( 0, internalLog.length() );
            running = true;

            if ( !canRunOnThisServer() || cancelFlag.get() )
            {
                return;
            }

            if ( JavaHelper.isEmpty( permissionList ) )
            {
                log( "no users are included in permission list setting "
                        + PwmSetting.PW_EXPY_NOTIFY_PERMISSION.toMenuLocationDebug( null, null )
                        + ", exiting."
                );
                return;
            }

            log( "starting job, beginning ldap search" );
            final Iterator<UserIdentity> workQueue = LdapOperationsHelper.readUsersFromLdapForPermissions(
                    pwmApplication,
                    SESSION_LABEL,
                    permissionList,
                    settings.getMaxLdapSearchSize()
            );

            log( "ldap search complete, examining users..." );

            final ThreadPoolExecutor threadPoolExecutor = createExecutor( pwmApplication );
            while ( workQueue.hasNext() )
            {
                if ( !checkIfRunningOnMaster() || cancelFlag.get() )
                {
                    final String msg = "job interrupted, server is no longer the cluster master.";
                    log( msg );
                    throw PwmUnrecoverableException.newException( PwmError.ERROR_SERVICE_NOT_AVAILABLE, msg );
                }

                threadPoolExecutor.submit( new ProcessJob( workQueue.next() ) );
            }

            JavaHelper.closeAndWaitExecutor( threadPoolExecutor, TimeDuration.DAY );

            log( "job complete, " + examinedCount + " users evaluated in " + TimeDuration.fromCurrent( startTime ).asCompactString()
                    + ", sent " + noticeCount + " notices."
            );
        }
        catch ( final PwmUnrecoverableException | PwmOperationalException e )
        {
            log( "error while executing job: " + e.getMessage() );
            throw e;
        }
        finally
        {
            running = false;
        }
    }

    private void periodicDebugOutput()
    {
        final String msg = "job in progress, " + examinedCount + " users evaluated in "
                + TimeDuration.fromCurrent( startTime ).asCompactString()
                + ", sent " + noticeCount + " notices.";
        log( msg );
    }

    private class ProcessJob implements Runnable
    {
        final UserIdentity userIdentity;

        ProcessJob( final UserIdentity userIdentity )
        {
            this.userIdentity = userIdentity;
        }

        @Override
        public void run()
        {
            try
            {
                processUserIdentity( userIdentity );
                debugOutputTask.conditionallyExecuteTask();
            }
            catch ( final Exception e )
            {
                LOGGER.trace( () -> "unexpected error processing user '" + userIdentity.toDisplayString() + "', error: " + e.getMessage() );
            }
        }
    }

    private void processUserIdentity(
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException
    {
        if ( !canRunOnThisServer() || cancelFlag.get() )
        {
            return;
        }

        examinedCount.incrementAndGet();
        final ChaiUser theUser = pwmApplication.getProxiedChaiUser( userIdentity );
        final Instant passwordExpirationTime = LdapOperationsHelper.readPasswordExpirationTime( theUser );

        if ( passwordExpirationTime == null )
        {
            LOGGER.trace( SESSION_LABEL, () -> "skipping user '" + userIdentity.toDisplayString() + "', has no password expiration" );
            return;
        }

        if ( passwordExpirationTime.isBefore( Instant.now() ) )
        {
            LOGGER.trace( SESSION_LABEL, () -> "skipping user '" + userIdentity.toDisplayString() + "', password expiration is in the past" );
            return;
        }

        final int nextDayInterval = figureNextDayInterval( passwordExpirationTime );
        if ( nextDayInterval < 1 )
        {
            LOGGER.trace( SESSION_LABEL, () -> "skipping user '" + userIdentity.toDisplayString() + "', password expiration time is not within an interval" );
            return;
        }

        if ( checkIfNoticeAlreadySent( userIdentity, passwordExpirationTime, nextDayInterval ) )
        {
            log( "notice for interval " + nextDayInterval + " already sent for " + userIdentity.toDisplayString() );
            return;
        }

        log( "sending notice to " + userIdentity.toDisplayString() + " for interval " + nextDayInterval );
        storageService.writeStoredUserState( userIdentity, SESSION_LABEL, new PwNotifyUserStatus( passwordExpirationTime, Instant.now(), nextDayInterval ) );
        sendNoticeEmail( userIdentity );
    }

    private int figureNextDayInterval(
            final Instant passwordExpirationTime
    )
    {
        final long maxSecondsAfterExpiration = TimeDuration.DAY.as( TimeDuration.Unit.SECONDS );
        int nextDayInterval = -1;
        for ( final int configuredDayInterval : settings.getNotificationIntervals() )
        {
            final Instant futureConfiguredDayInterval = Instant.now().plus( configuredDayInterval, ChronoUnit.DAYS );
            final long secondsUntilConfiguredInterval = Duration.between( Instant.now(), futureConfiguredDayInterval ).abs().getSeconds();
            final long secondsUntilPasswordExpiration = Duration.between( Instant.now(), passwordExpirationTime ).abs().getSeconds();
            if ( secondsUntilPasswordExpiration < secondsUntilConfiguredInterval )
            {
                final long secondsBetweenIntervalAndExpiration = Duration.between( futureConfiguredDayInterval, passwordExpirationTime ).abs().getSeconds();
                if ( secondsBetweenIntervalAndExpiration < maxSecondsAfterExpiration )
                {
                    nextDayInterval = configuredDayInterval;
                }
            }
        }

        return nextDayInterval;
    }

    private boolean checkIfNoticeAlreadySent(
            final UserIdentity userIdentity,
            final Instant passwordExpirationTime,
            final int interval
    )
            throws PwmUnrecoverableException
    {
        final Optional<PwNotifyUserStatus> optionalStoredState = storageService.readStoredUserState( userIdentity, SESSION_LABEL );

        if ( !optionalStoredState.isPresent() )
        {
            return false;
        }

        final PwNotifyUserStatus storedState = optionalStoredState.get();
        if ( storedState.getExpireTime() == null || !storedState.getExpireTime().equals( passwordExpirationTime ) )
        {
            return false;
        }

        if ( storedState.getInterval() == 0 || storedState.getInterval() != interval )
        {
            return false;
        }

        return true;
    }

    private void sendNoticeEmail( final UserIdentity userIdentity )
            throws PwmUnrecoverableException
    {
        final UserInfo userInfoBean = UserInfoFactory.newUserInfoUsingProxyForOfflineUser(
                pwmApplication,
                SESSION_LABEL,
                userIdentity
        );
        final Locale ldapLocale = LocaleHelper.parseLocaleString( userInfoBean.getLanguage() );
        final MacroMachine macroMachine = MacroMachine.forUser( pwmApplication, ldapLocale, SESSION_LABEL, userIdentity );
        final EmailItemBean emailItemBean = pwmApplication.getConfig().readSettingAsEmail(
                PwmSetting.EMAIL_PW_EXPIRATION_NOTICE,
                ldapLocale
        );

        noticeCount.incrementAndGet();
        StatisticsManager.incrementStat( pwmApplication, Statistic.PWNOTIFY_EMAILS_SENT );
        pwmApplication.getEmailQueue().submitEmail( emailItemBean, userInfoBean, macroMachine );
    }

    private void log( final String output )
    {
        final String msg = JavaHelper.toIsoDate( Instant.now() )
                + " "
                + output
                + "\n";

        if ( debugWriter != null )
        {
            try
            {
                debugWriter.append( msg );
                debugWriter.flush();
            }
            catch ( final IOException e )
            {
                LOGGER.warn( SessionLabel.PWNOTIFY_SESSION_LABEL, () -> "unexpected IO error writing to debugWriter: " + e.getMessage() );
            }
        }

        internalLog.append( msg );
        while ( internalLog.length() > MAX_LOG_SIZE )
        {
            final int nextLf = internalLog.indexOf( "\n" );
            if ( nextLf > 0 )
            {
                internalLog.delete( 0, nextLf );
            }
            else
            {
                internalLog.delete( 0, Math.max( 1024, internalLog.length() ) );
            }
        }

        LOGGER.trace( SessionLabel.PWNOTIFY_SESSION_LABEL, () -> output );
    }

    private ThreadPoolExecutor createExecutor( final PwmApplication pwmApplication )
    {
        final ThreadFactory threadFactory = PwmScheduler.makePwmThreadFactory( PwmScheduler.makeThreadName( pwmApplication, this.getClass() ), true );
        final ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
                1,
                10,
                1,
                TimeUnit.MINUTES,
                new LinkedBlockingDeque<>(),
                threadFactory
        );
        threadPoolExecutor.allowCoreThreadTimeOut( true );
        return threadPoolExecutor;
    }
}
