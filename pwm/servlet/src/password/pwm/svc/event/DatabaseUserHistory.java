/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2015 The PWM Project
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

package password.pwm.svc.event;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.PwmApplication;
import password.pwm.bean.UserIdentity;
import password.pwm.bean.UserInfoBean;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.ldap.LdapOperationsHelper;
import password.pwm.util.JsonUtil;
import password.pwm.util.db.DatabaseAccessorImpl;
import password.pwm.util.db.DatabaseException;
import password.pwm.util.db.DatabaseTable;
import password.pwm.util.logging.PwmLogger;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

class DatabaseUserHistory implements UserHistoryStore {
    private static final PwmLogger LOGGER = PwmLogger.forClass(DatabaseUserHistory.class);

    private static final DatabaseTable TABLE = DatabaseTable.USER_AUDIT;

    final PwmApplication pwmApplication;
    final DatabaseAccessorImpl databaseAccessor;

    DatabaseUserHistory(final PwmApplication pwmApplication) {
        this.pwmApplication = pwmApplication;
        this.databaseAccessor = pwmApplication.getDatabaseAccessor();
    }

    @Override
    public void updateUserHistory(UserAuditRecord auditRecord) throws PwmUnrecoverableException {
        // user info
        final UserIdentity userIdentity;
        if (auditRecord instanceof HelpdeskAuditRecord && auditRecord.getType() == AuditEvent.Type.HELPDESK) {
            final HelpdeskAuditRecord helpdeskAuditRecord = (HelpdeskAuditRecord)auditRecord;
            userIdentity = new UserIdentity(helpdeskAuditRecord.getTargetDN(),helpdeskAuditRecord.getTargetLdapProfile());
        } else {
            userIdentity = new UserIdentity(auditRecord.getPerpetratorDN(),auditRecord.getPerpetratorLdapProfile());
        }

        final String guid;
        try {
            guid = LdapOperationsHelper.readLdapGuidValue(pwmApplication, null, userIdentity, false);
        } catch (ChaiUnavailableException e) {
            LOGGER.error("unable to read guid for user '" + userIdentity + "', cannot update user history, error: " + e.getMessage());
            return;
        }

        try {
            final StoredHistory storedHistory;
            storedHistory = readStoredHistory(guid);
            storedHistory.getRecords().add(auditRecord);
            writeStoredHistory(guid,storedHistory);
        } catch (DatabaseException e) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_DB_UNAVAILABLE,e.getMessage()));
        }
    }

    @Override
    public List<UserAuditRecord> readUserHistory(UserInfoBean userInfoBean) throws PwmUnrecoverableException {
        final String userGuid = userInfoBean.getUserGuid();
        try {
            return readStoredHistory(userGuid).getRecords();
        } catch (DatabaseException e) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_DB_UNAVAILABLE,e.getMessage()));
        }
    }

    private StoredHistory readStoredHistory(final String guid) throws DatabaseException {
        final String str = this.databaseAccessor.get(TABLE, guid);
        if (str == null || str.length() < 1) {
            return new StoredHistory();
        }
        return JsonUtil.deserialize(str,StoredHistory.class);
    }

    private void writeStoredHistory(final String guid, final StoredHistory storedHistory) throws DatabaseException {
        if (storedHistory == null) {
            return;
        }
        final String str = JsonUtil.serialize(storedHistory);
        databaseAccessor.put(TABLE,guid,str);
    }

    static class StoredHistory implements Serializable {
        private List<UserAuditRecord> records = new ArrayList<>();

        List<UserAuditRecord> getRecords() {
            return records;
        }

        void setRecords(List<UserAuditRecord> records) {
            this.records = records;
        }
    }
}
