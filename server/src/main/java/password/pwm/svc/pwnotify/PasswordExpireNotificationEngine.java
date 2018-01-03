/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
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
import lombok.AllArgsConstructor;
import lombok.Getter;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.EmailItemBean;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.ldap.LdapOperationsHelper;
import password.pwm.ldap.UserInfo;
import password.pwm.ldap.UserInfoFactory;
import password.pwm.util.db.DatabaseException;
import password.pwm.util.db.DatabaseTable;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public class PasswordExpireNotificationEngine
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( PasswordExpireNotificationEngine.class );

    private static final SessionLabel SESSION_LABEL = SessionLabel.PW_EXP_NOTICE_LABEL;

    private final Settings settings;
    private final PwmApplication pwmApplication;


    public PasswordExpireNotificationEngine( final PwmApplication pwmApplication )
    {
        this.pwmApplication = pwmApplication;
        this.settings = Settings.fromConfiguration( pwmApplication.getConfig() );
    }

    public void executeJob( )
            throws ChaiUnavailableException, ChaiOperationException, PwmOperationalException, PwmUnrecoverableException
    {
        final Iterator<UserIdentity> workQueue = LdapOperationsHelper.readAllUsersFromLdap(
                pwmApplication,
                null,
                null,
                1_000_000
        );

        while ( workQueue.hasNext() )
        {
            final UserIdentity userIdentity = workQueue.next();
            processUserIdentity( userIdentity );
        }
    }

    private void processUserIdentity(
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException
    {
        final ChaiUser theUser = pwmApplication.getProxiedChaiUser( userIdentity );
        final Instant passwordExpirationTime = LdapOperationsHelper.readPasswordExpirationTime( theUser );
        if ( passwordExpirationTime == null || passwordExpirationTime.isBefore( Instant.now() ) )
        {
            return;
        }

        final Instant previousNotice;
        {
            final DbStorage dbStorage = new DbStorage( pwmApplication );
            final NotificationState storedState = dbStorage.readStoredState( userIdentity, SESSION_LABEL );
            if ( storedState == null || storedState.getExpireTime() == null || !storedState.getExpireTime().equals( passwordExpirationTime ) )
            {
                previousNotice = null;
            }
            else
            {
                previousNotice = storedState.getLastNotice();
            }
        }
        final int currentDayInterval = daysUntilInstant( passwordExpirationTime );
        final int previousDays = previousNotice == null
                ? Integer.MAX_VALUE
                : daysUntilInstant( previousNotice );

        int nextDayInterval = -1;
        for ( final int configuredDayInterval : settings.getDayIntervals() )
        {
            if ( currentDayInterval <= configuredDayInterval )
            {
                if ( configuredDayInterval != previousDays )
                {
                    nextDayInterval = configuredDayInterval;
                }
            }
        }

        if ( nextDayInterval < 1 )
        {
            return;
        }

        System.out.println( userIdentity + " next=" + nextDayInterval );
        {
            final DbStorage dbStorage = new DbStorage( pwmApplication );
            dbStorage.writeStoredState( userIdentity, SESSION_LABEL, new NotificationState( passwordExpirationTime, Instant.now() ) );
        }

        sendNoticeEmail( userIdentity );
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

    static int daysUntilInstant( final Instant instant )
    {
        final TimeDuration timeDuration = TimeDuration.fromCurrent( instant );
        return ( int ) timeDuration.getTotalDays();

    }

    @Getter
    static class Settings implements Serializable
    {
        private List<Integer> dayIntervals = Collections.unmodifiableList( new ArrayList<>( Arrays.asList( 8, 5, 3 ) ) );

        static Settings fromConfiguration( final Configuration configuration )
        {
            final Settings settings = new Settings();

            final List<Integer> tempList = new ArrayList<>( Arrays.asList( 8, 5, 3 ) );
            Collections.sort( tempList );
            Collections.reverse( tempList );
            settings.dayIntervals = Collections.unmodifiableList( tempList );

            return settings;
        }
    }

    @Getter
    @AllArgsConstructor
    static class NotificationState implements Serializable
    {
        private Instant expireTime;
        private Instant lastNotice;
    }

    interface PwExpireStorageEngine
    {

        NotificationState readStoredState(
                UserIdentity userIdentity,
                SessionLabel sessionLabel
        )
                throws PwmUnrecoverableException;

        void writeStoredState( UserIdentity userIdentity, SessionLabel sessionLabel, NotificationState notificationState ) throws PwmUnrecoverableException;

    }

    static class DbStorage implements PwExpireStorageEngine
    {
        private static final DatabaseTable TABLE = DatabaseTable.PW_NOTIFY;
        private final PwmApplication pwmApplication;

        DbStorage( final PwmApplication pwmApplication )
        {
            this.pwmApplication = pwmApplication;
        }

        @Override
        public NotificationState readStoredState(
                final UserIdentity userIdentity,
                final SessionLabel sessionLabel
        )
                throws PwmUnrecoverableException
        {
            final String guid;
            try
            {
                guid = LdapOperationsHelper.readLdapGuidValue( pwmApplication, sessionLabel, userIdentity, true );
            }
            catch ( ChaiUnavailableException e )
            {
                throw new PwmUnrecoverableException( PwmUnrecoverableException.fromChaiException( e ).getErrorInformation() );
            }
            if ( StringUtil.isEmpty( guid ) )
            {
                throw new PwmUnrecoverableException( PwmError.ERROR_MISSING_GUID );
            }

            final String rawDbValue;
            try
            {
                rawDbValue = pwmApplication.getDatabaseAccessor().get( TABLE, guid );
            }
            catch ( DatabaseException e )
            {
                throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_DB_UNAVAILABLE, e.getMessage() ) );
            }

            return JsonUtil.deserialize( rawDbValue, NotificationState.class );
        }

        public void writeStoredState(
                final UserIdentity userIdentity,
                final SessionLabel sessionLabel,
                final NotificationState notificationState
        )
                throws PwmUnrecoverableException
        {
            final String guid;
            try
            {
                guid = LdapOperationsHelper.readLdapGuidValue( pwmApplication, sessionLabel, userIdentity, true );
            }
            catch ( ChaiUnavailableException e )
            {
                throw new PwmUnrecoverableException( PwmUnrecoverableException.fromChaiException( e ).getErrorInformation() );
            }
            if ( StringUtil.isEmpty( guid ) )
            {
                throw new PwmUnrecoverableException( PwmError.ERROR_MISSING_GUID );
            }

            final String rawDbValue = JsonUtil.serialize( notificationState );
            try
            {
                pwmApplication.getDatabaseAccessor().put( TABLE, guid, rawDbValue );
            }
            catch ( DatabaseException e )
            {
                throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_DB_UNAVAILABLE, e.getMessage() ) );
            }
        }
    }
}
