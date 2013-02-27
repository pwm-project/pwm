/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2012 The PWM Project
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

package password.pwm.event;

import com.google.gson.Gson;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.PwmApplication;
import password.pwm.PwmService;
import password.pwm.PwmSession;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthRecord;
import password.pwm.util.PwmLogger;
import password.pwm.util.TimeDuration;
import password.pwm.util.pwmdb.PwmDB;
import password.pwm.util.pwmdb.PwmDBStoredQueue;

import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

public class AuditManager implements PwmService {
    private static final PwmLogger LOGGER = PwmLogger.getLogger(AuditManager.class);

    private STATUS status = STATUS.NEW;
    private PwmApplication pwmApplication;
    private PwmDBStoredQueue auditDB;

    private TimeDuration maxRecordAge = new TimeDuration(TimeDuration.DAY.getTotalMilliseconds() * 30);
    private Date oldestRecord = null;

    public AuditManager() {
    }

    @Override
    public STATUS status() {
        return status;
    }

    @Override
    public void init(PwmApplication pwmApplication) throws PwmException {
        this.status = STATUS.OPENING;
        this.pwmApplication = pwmApplication;
        this.maxRecordAge = new TimeDuration(pwmApplication.getConfig().readSettingAsLong(PwmSetting.EVENTS_AUDIT_MAX_AGE) * 1000);
        if (pwmApplication.getPwmDB() != null) {
            this.auditDB = PwmDBStoredQueue.createPwmDBStoredQueue(pwmApplication.getPwmDB(), PwmDB.DB.AUDIT_EVENTS);
            this.status = STATUS.OPEN;
        } else {
            this.status = STATUS.CLOSED;
        }
    }

    @Override
    public void close() {
        this.status = STATUS.CLOSED;
    }

    @Override
    public List<HealthRecord> healthCheck() {
        return null;
    }

    public Iterator<AuditRecord> readLocalDB() {
        if (status != STATUS.OPEN) {
            return Collections.emptyIterator();
        }
        return new IteratorWrapper<AuditRecord>(auditDB.iterator());
    }

    public List<AuditRecord> readUserAuditRecords(final PwmSession pwmSession)
            throws PwmUnrecoverableException
    {
        return readUserAuditRecords(pwmSession.getUserInfoBean());
    }

    public List<AuditRecord> readUserAuditRecords(final UserInfoBean userInfoBean)
            throws PwmUnrecoverableException
    {
        try {
            return UserLdapHistory.readUserHistory(pwmApplication, userInfoBean);
        } catch (ChaiUnavailableException e) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_DIRECTORY_UNAVAILABLE,e.getMessage()));
        }
    }

    public void submitAuditRecord(final AuditEvent auditEvent, final UserInfoBean userInfoBean)
            throws PwmUnrecoverableException
    {
        final AuditRecord auditRecord = new AuditRecord(auditEvent, userInfoBean);
        submitAuditRecord(auditRecord);
    }

    public int localSize() {
        if (status != STATUS.OPEN || auditDB == null) {
            return 0;
        }

        return auditDB.size();
    }

    public void submitAuditRecord(final AuditRecord auditRecord)
            throws PwmUnrecoverableException
    {
        final String gsonRecord = new Gson().toJson(auditRecord);

        // add to audit db
        if (status == STATUS.OPEN && auditDB != null) {
            auditDB.addLast(gsonRecord);
            trimDB();
        }

        // add to user ldap record
        try {
            UserLdapHistory.updateUserHistory(pwmApplication, auditRecord);
        } catch (ChaiUnavailableException e) {
            LOGGER.error("error updating ldap user history: " + e.getMessage());
        }

        // add to debug log
        LOGGER.info("audit event: " + gsonRecord);
    }

    private void trimDB() {
        if (oldestRecord != null && TimeDuration.fromCurrent(oldestRecord).isShorterThan(maxRecordAge)) {
            return;
        }

        if (auditDB.isEmpty()) {
            return;
        }

        int workActions = 0;
        while (workActions < 3) {
            final String stringFirstRecord = auditDB.getFirst();
            final AuditRecord firstRecord = new Gson().fromJson(stringFirstRecord,AuditRecord.class);
            oldestRecord = firstRecord.getTimestamp();
            if (TimeDuration.fromCurrent(oldestRecord).isShorterThan(maxRecordAge)) {
                auditDB.removeFirst();
                workActions++;
            } else {
                return;
            }
        }
    }

    private static class IteratorWrapper<AuditRecord> implements Iterator<AuditRecord> {
        private Iterator<String> innerIter;

        private IteratorWrapper(Iterator<String> innerIter) {
            this.innerIter = innerIter;
        }

        @Override
        public boolean hasNext() {
            return innerIter.hasNext();
        }

        @Override
        public AuditRecord next() {
            final String value = innerIter.next();
            return (AuditRecord)new Gson().fromJson(value,password.pwm.event.AuditRecord.class);
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
}
