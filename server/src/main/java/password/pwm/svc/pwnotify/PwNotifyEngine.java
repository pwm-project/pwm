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

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.EmailItemBean;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.ldap.LdapOperationsHelper;
import password.pwm.ldap.UserInfo;
import password.pwm.ldap.UserInfoFactory;
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
import java.util.Locale;

public class PwNotifyEngine
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( PwNotifyEngine.class );

    private static final SessionLabel SESSION_LABEL = SessionLabel.PW_EXP_NOTICE_LABEL;


    private final PwNotifySettings settings;
    private final PwmApplication pwmApplication;
    private final Writer debugWriter;
    private final StringBuffer internalLog = new StringBuffer(  );

    private volatile boolean running;


    public PwNotifyEngine(
            final PwmApplication pwmApplication,
            final Writer debugWriter
    )
    {
        this.pwmApplication = pwmApplication;
        this.settings = PwNotifySettings.fromConfiguration( pwmApplication.getConfig() );
        this.debugWriter = debugWriter;
    }

    public boolean isRunning()
    {
        return running;
    }

    public String getDebugLog()
    {
        return internalLog.toString();
    }

    private void checkIfRunningOnMaster( final String msg ) throws PwmUnrecoverableException
    {
        if ( !pwmApplication.getPwmEnvironment().isInternalRuntimeInstance() )
        {
            if ( pwmApplication.getClusterService() != null && !pwmApplication.getClusterService().isMaster() )
            {
                throw PwmUnrecoverableException.newException( PwmError.ERROR_SERVICE_NOT_AVAILABLE, msg );
            }
        }
    }

    public void executeJob( )
            throws ChaiUnavailableException, ChaiOperationException, PwmOperationalException, PwmUnrecoverableException
    {
        try
        {
            checkIfRunningOnMaster( "job can run only on a server that is currently the cluster master" );
            running = true;

            final Instant startTime = Instant.now();
            internalLog.delete( 0, internalLog.length() );
            log( "starting job, beginning ldap search" );
            final Iterator<UserIdentity> workQueue = LdapOperationsHelper.readAllUsersFromLdap(
                    pwmApplication,
                    null,
                    null,
                    settings.getMaxLdapSearchSize()
            );
            log( "ldap search complete, examining users..." );
            int examinedCount = 0;
            int noticeCount = 0;
            while ( workQueue.hasNext() )
            {
                checkIfRunningOnMaster( "job interrupted, server is no longer the cluster master." );
                examinedCount++;
                final UserIdentity userIdentity = workQueue.next();
                if ( processUserIdentity( userIdentity ) )
                {
                    noticeCount++;
                }
            }
            log( "job complete, " + examinedCount + " users evaluated in " + TimeDuration.fromCurrent( startTime ).asCompactString() );
        }
        finally
        {
            running = false;
        }
    }

    private boolean processUserIdentity(
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException
    {
        final ChaiUser theUser = pwmApplication.getProxiedChaiUser( userIdentity );
        final Instant passwordExpirationTime = LdapOperationsHelper.readPasswordExpirationTime( theUser );

        if ( passwordExpirationTime == null || passwordExpirationTime.isBefore( Instant.now() ) )
        {
            return false;
        }

        final int nextDayInterval = figureNextDayInterval( passwordExpirationTime );
        if ( nextDayInterval < 1 )
        {
            return false;
        }

        if ( checkIfNoticeAlreadySent( userIdentity, passwordExpirationTime, nextDayInterval ) )
        {
            log( "notice for interval " + nextDayInterval + " already sent for " + userIdentity.toDisplayString() );
            return false;
        }

        log( "sending notice to " + userIdentity.toDisplayString() + " for interval " + nextDayInterval );
        {
            final PwNotifyDbStorageService dbStorage = new PwNotifyDbStorageService( pwmApplication );
            dbStorage.writeStoredState( userIdentity, SESSION_LABEL, new StoredNotificationState( passwordExpirationTime, Instant.now(), nextDayInterval ) );
        }

        sendNoticeEmail( userIdentity );
        return true;
    }

    private int figureNextDayInterval(
            final Instant passwordExpirationTime
    )
    {
        final long maxSecondsAfterExpiration = TimeDuration.DAY.getTotalSeconds();
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
        final PwNotifyDbStorageService dbStorage = new PwNotifyDbStorageService( pwmApplication );
        final StoredNotificationState storedState = dbStorage.readStoredState( userIdentity, SESSION_LABEL );

        if ( storedState == null )
        {
            return false;
        }

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

    void sendNoticeEmail( final UserIdentity userIdentity )
            throws PwmUnrecoverableException
    {
        final Locale userLocale = PwmConstants.DEFAULT_LOCALE;
        final EmailItemBean emailItemBean = pwmApplication.getConfig().readSettingAsEmail(
                PwmSetting.EMAIL_PW_EXPIRATION_NOTICE,
                userLocale
        );
        final MacroMachine macroMachine = MacroMachine.forUser( pwmApplication, userLocale, SESSION_LABEL, userIdentity );
        final UserInfo userInfoBean = UserInfoFactory.newUserInfoUsingProxy(
                pwmApplication,
                SESSION_LABEL,
                userIdentity, userLocale
        );

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
            catch ( IOException e )
            {
                LOGGER.warn( "unexpected IO error writing to debugWriter: " + e.getMessage() );
            }
        }

        internalLog.append( msg );
        while ( internalLog.length() > 1024 * 1024 * 1024 )
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

        LOGGER.trace( output );
    }
}
