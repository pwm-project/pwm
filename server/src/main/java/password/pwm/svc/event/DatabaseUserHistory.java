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

package password.pwm.svc.event;

import password.pwm.PwmApplication;
import password.pwm.bean.UserIdentity;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.ldap.LdapOperationsHelper;
import password.pwm.ldap.UserInfo;
import password.pwm.util.db.DatabaseException;
import password.pwm.util.db.DatabaseService;
import password.pwm.util.db.DatabaseTable;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.logging.PwmLogger;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

class DatabaseUserHistory implements UserHistoryStore
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( DatabaseUserHistory.class );

    private static final DatabaseTable TABLE = DatabaseTable.USER_AUDIT;

    final PwmApplication pwmApplication;
    final DatabaseService databaseService;

    DatabaseUserHistory( final PwmApplication pwmApplication )
    {
        this.pwmApplication = pwmApplication;
        this.databaseService = pwmApplication.getDatabaseService();
    }

    @Override
    public void updateUserHistory( final UserAuditRecord auditRecord ) throws PwmUnrecoverableException
    {
        // user info
        final UserIdentity userIdentity;
        if ( auditRecord instanceof HelpdeskAuditRecord && auditRecord.getType() == AuditEvent.Type.HELPDESK )
        {
            final HelpdeskAuditRecord helpdeskAuditRecord = ( HelpdeskAuditRecord ) auditRecord;
            userIdentity = new UserIdentity( helpdeskAuditRecord.getTargetDN(), helpdeskAuditRecord.getTargetLdapProfile() );
        }
        else
        {
            userIdentity = new UserIdentity( auditRecord.getPerpetratorDN(), auditRecord.getPerpetratorLdapProfile() );
        }

        final String guid = LdapOperationsHelper.readLdapGuidValue( pwmApplication, null, userIdentity, false );

        try
        {
            final StoredHistory storedHistory;
            storedHistory = readStoredHistory( guid );
            storedHistory.getRecords().add( auditRecord );
            writeStoredHistory( guid, storedHistory );
        }
        catch ( final DatabaseException e )
        {
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_DB_UNAVAILABLE, e.getMessage() ) );
        }
    }

    @Override
    public List<UserAuditRecord> readUserHistory( final UserInfo userInfo ) throws PwmUnrecoverableException
    {
        final String userGuid = userInfo.getUserGuid();
        try
        {
            return readStoredHistory( userGuid ).getRecords();
        }
        catch ( final DatabaseException e )
        {
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_DB_UNAVAILABLE, e.getMessage() ) );
        }
    }

    private StoredHistory readStoredHistory( final String guid ) throws DatabaseException, PwmUnrecoverableException
    {
        final String str = this.databaseService.getAccessor().get( TABLE, guid );
        if ( str == null || str.length() < 1 )
        {
            return new StoredHistory();
        }
        return JsonUtil.deserialize( str, StoredHistory.class );
    }

    private void writeStoredHistory( final String guid, final StoredHistory storedHistory ) throws DatabaseException, PwmUnrecoverableException
    {
        if ( storedHistory == null )
        {
            return;
        }
        final String str = JsonUtil.serialize( storedHistory );
        databaseService.getAccessor().put( TABLE, guid, str );
    }

    static class StoredHistory implements Serializable
    {
        private List<UserAuditRecord> records = new ArrayList<>();

        List<UserAuditRecord> getRecords( )
        {
            return records;
        }

        void setRecords( final List<UserAuditRecord> records )
        {
            this.records = records;
        }
    }
}
