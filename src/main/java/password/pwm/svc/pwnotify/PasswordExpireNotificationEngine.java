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
import lombok.Getter;
import password.pwm.PwmApplication;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.ldap.LdapOperationsHelper;
import password.pwm.util.db.DatabaseException;
import password.pwm.util.db.DatabaseTable;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;

import java.io.Serializable;
import java.time.Instant;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class PasswordExpireNotificationEngine {

    private final Settings settings;
    private final PwmApplication pwmApplication;

    public PasswordExpireNotificationEngine(final PwmApplication pwmApplication)
    {
        this.pwmApplication = pwmApplication;
        this.settings = new Settings();
    }

    public void executeJob()
            throws ChaiUnavailableException, ChaiOperationException, PwmOperationalException, PwmUnrecoverableException
    {
        final Queue<UserIdentity> workQueue;
        {
            final List<UserIdentity> users = LdapOperationsHelper.readAllUsersFromLdap(
                    pwmApplication,
                    null,
                    null,
                    1_000_000
            );
            workQueue = new LinkedList<>(users);
        }

        while (!workQueue.isEmpty()) {
            final UserIdentity userIdentity = workQueue.poll();
            final StoredState storedState = new DbStorage(pwmApplication).getStoredState(userIdentity, null);
        }
    }

    static void processUserIdentity(
            final PwmApplication pwmApplication,
            final UserIdentity userIdentity
            )
            throws PwmUnrecoverableException
    {
        final ChaiUser theUser = pwmApplication.getProxiedChaiUser(userIdentity);
        final Instant passwordExpirationTime = LdapOperationsHelper.readPasswordExpirationTime(theUser);
        if (passwordExpirationTime == null || passwordExpirationTime.isBefore(Instant.now())) {
            return;
        }
    }

    @Getter
    static class Settings implements Serializable {
        private List<Integer> dayIntervals;
    }

    @Getter
    static class StoredState implements Serializable {
        private Instant lastSendTimestamp;
    }

    interface PwExpireStorageEngine {

        StoredState getStoredState(
                UserIdentity userIdentity,
                SessionLabel sessionLabel
        )
                throws PwmUnrecoverableException;
    }

    static class DbStorage implements PwExpireStorageEngine {
        private final PwmApplication pwmApplication;

        DbStorage(final PwmApplication pwmApplication)
        {
            this.pwmApplication = pwmApplication;
        }

        @Override
        public StoredState getStoredState(
                final UserIdentity userIdentity,
                final SessionLabel sessionLabel
        )
                throws PwmUnrecoverableException
        {
            final String guid;
            try {
                guid = LdapOperationsHelper.readLdapGuidValue(pwmApplication, sessionLabel, userIdentity, true);
            } catch (ChaiUnavailableException e) {
                throw new PwmUnrecoverableException(PwmUnrecoverableException.fromChaiException(e).getErrorInformation());
            }
            if (StringUtil.isEmpty(guid)) {
                throw new PwmUnrecoverableException(PwmError.ERROR_MISSING_GUID);
            }

            final String rawDbValue;
            try {
                rawDbValue = pwmApplication.getDatabaseAccessor().get(DatabaseTable.PW_NOTIFY, guid);
            } catch (DatabaseException e) {
                throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_DB_UNAVAILABLE,e.getMessage()));
            }

            return JsonUtil.deserialize(rawDbValue, StoredState.class);
        }
    }
}
