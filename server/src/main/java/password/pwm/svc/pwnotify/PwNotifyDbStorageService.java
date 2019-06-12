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
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.ldap.LdapOperationsHelper;
import password.pwm.util.db.DatabaseException;
import password.pwm.util.db.DatabaseTable;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;

import java.util.Optional;

class PwNotifyDbStorageService implements PwNotifyStorageService
{
    private static final String DB_STATE_STRING = "PwNotifyJobState";

    private static final DatabaseTable TABLE = DatabaseTable.PW_NOTIFY;
    private final PwmApplication pwmApplication;

    PwNotifyDbStorageService( final PwmApplication pwmApplication ) throws PwmUnrecoverableException
    {
        this.pwmApplication = pwmApplication;

        if ( !pwmApplication.getConfig().hasDbConfigured() )
        {
            final String msg = "DB storage type selected, but remote DB is not configured.";
            throw PwmUnrecoverableException.newException( PwmError.ERROR_NODE_SERVICE_ERROR, msg );
        }
    }

    @Override
    public Optional<PwNotifyUserStatus> readStoredUserState(
            final UserIdentity userIdentity,
            final SessionLabel sessionLabel
    )
            throws PwmUnrecoverableException
    {
        final String guid = LdapOperationsHelper.readLdapGuidValue( pwmApplication, sessionLabel, userIdentity, true );

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

        if ( !StringUtil.isEmpty( rawDbValue ) )
        {
            return Optional.ofNullable( JsonUtil.deserialize( rawDbValue, PwNotifyUserStatus.class ) );
        }

        return Optional.empty();
    }

    public void writeStoredUserState(
            final UserIdentity userIdentity,
            final SessionLabel sessionLabel,
            final PwNotifyUserStatus pwNotifyUserStatus
    )
            throws PwmUnrecoverableException
    {
        final String guid = LdapOperationsHelper.readLdapGuidValue( pwmApplication, sessionLabel, userIdentity, true );

        if ( StringUtil.isEmpty( guid ) )
        {
            throw new PwmUnrecoverableException( PwmError.ERROR_MISSING_GUID );
        }

        final String rawDbValue = JsonUtil.serialize( pwNotifyUserStatus );
        try
        {
            pwmApplication.getDatabaseAccessor().put( TABLE, guid, rawDbValue );
        }
        catch ( DatabaseException e )
        {
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_DB_UNAVAILABLE, e.getMessage() ) );
        }
    }

    @Override
    public PwNotifyStoredJobState readStoredJobState()
            throws PwmUnrecoverableException
    {
        try
        {
            final String strValue = pwmApplication.getDatabaseService().getAccessor().get( DatabaseTable.PW_NOTIFY, DB_STATE_STRING );
            if ( StringUtil.isEmpty( strValue ) )
            {
                return new PwNotifyStoredJobState( null, null, null, null, false );
            }
            return JsonUtil.deserialize( strValue, PwNotifyStoredJobState.class );
        }
        catch ( DatabaseException e )
        {
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_DB_UNAVAILABLE, e.getMessage() ) );
        }
    }

    @Override
    public void writeStoredJobState( final PwNotifyStoredJobState pwNotifyStoredJobState )
            throws PwmUnrecoverableException
    {
        try
        {
            final String strValue = JsonUtil.serialize( pwNotifyStoredJobState );
            pwmApplication.getDatabaseService().getAccessor().put( DatabaseTable.PW_NOTIFY, DB_STATE_STRING, strValue );
        }
        catch ( DatabaseException e )
        {
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_DB_UNAVAILABLE, e.getMessage() ) );
        }
    }

}
