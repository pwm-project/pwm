/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2021 The PWM Project
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

package password.pwm.svc.userhistory;

import password.pwm.PwmDomain;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.ldap.LdapOperationsHelper;
import password.pwm.svc.db.DatabaseException;
import password.pwm.svc.db.DatabaseService;
import password.pwm.svc.db.DatabaseTable;
import password.pwm.svc.event.AuditEventType;
import password.pwm.svc.event.AuditRecordData;
import password.pwm.svc.event.HelpdeskAuditRecord;
import password.pwm.svc.event.UserAuditRecord;
import password.pwm.user.UserInfo;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.logging.PwmLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

class DatabaseUserHistory implements UserHistoryStore
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( DatabaseUserHistory.class );

    private static final DatabaseTable TABLE = DatabaseTable.USER_AUDIT;

    final PwmDomain pwmDomain;
    final DatabaseService databaseService;

    DatabaseUserHistory( final PwmDomain pwmDomain )
    {
        this.pwmDomain = pwmDomain;
        this.databaseService = pwmDomain.getPwmApplication().getDatabaseService();
    }

    @Override
    public void updateUserHistory( final SessionLabel sessionLabel, final UserAuditRecord auditRecord ) throws PwmUnrecoverableException
    {
        // user info
        final UserIdentity userIdentity;
        if ( auditRecord instanceof HelpdeskAuditRecord && auditRecord.type() == AuditEventType.HELPDESK )
        {
            final HelpdeskAuditRecord helpdeskAuditRecord = ( HelpdeskAuditRecord ) auditRecord;
            userIdentity = UserIdentity.create( helpdeskAuditRecord.targetDN(), helpdeskAuditRecord.targetLdapProfile(), auditRecord.domain() );
        }
        else
        {
            userIdentity = UserIdentity.create( auditRecord.perpetratorDN(), auditRecord.perpetratorLdapProfile(), auditRecord.domain() );
        }

        final String guid = LdapOperationsHelper.readLdapGuidValue( pwmDomain, sessionLabel, userIdentity )
                .orElseThrow( () -> PwmUnrecoverableException.newException( PwmError.ERROR_MISSING_GUID ) );

        try
        {
            final StoredHistory storedHistory;
            storedHistory = readStoredHistory( guid );
            final List<AuditRecordData> mutableRecordList = new ArrayList<>( storedHistory.records() );
            mutableRecordList.add( ( AuditRecordData ) auditRecord );
            writeStoredHistory( guid, new StoredHistory( mutableRecordList ) );
        }
        catch ( final DatabaseException e )
        {
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_DB_UNAVAILABLE, e.getMessage() ) );
        }
    }

    @Override
    public List<UserAuditRecord> readUserHistory( final SessionLabel sessionLabel, final UserInfo userInfo ) throws PwmUnrecoverableException
    {
        final String userGuid = userInfo.getUserGuid();
        try
        {
            return readStoredHistory( userGuid ).records().stream()
                    .map( auditRecordData -> ( UserAuditRecord ) auditRecordData )
                    .collect( Collectors.toList() );
        }
        catch ( final DatabaseException e )
        {
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_DB_UNAVAILABLE, e.getMessage() ) );
        }
    }

    private StoredHistory readStoredHistory( final String guid ) throws DatabaseException, PwmUnrecoverableException
    {
        final Optional<String> str = this.databaseService.getAccessor().get( TABLE, guid );
        if ( str.isEmpty() )
        {
            return new StoredHistory( List.of() );
        }
        return JsonFactory.get().deserialize( str.get(), StoredHistory.class );
    }

    private void writeStoredHistory( final String guid, final StoredHistory storedHistory ) throws DatabaseException, PwmUnrecoverableException
    {
        if ( storedHistory == null )
        {
            return;
        }
        final String str = JsonFactory.get().serialize( storedHistory );
        databaseService.getAccessor().put( TABLE, guid, str );
    }

    record StoredHistory(
            List<AuditRecordData> records
    )
    {
        StoredHistory
        {
            records = CollectionUtil.stripNulls( records );
        }
    }
}
