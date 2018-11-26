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
import com.novell.ldapchai.util.ConfigObjectRecord;
import password.pwm.PwmApplication;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.profile.LdapProfile;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;

class PwNotifyLdapStorageService implements PwNotifyStorageService
{
    private final PwmApplication pwmApplication;
    private final PwNotifySettings settings;

    private enum CoreType
    {
        User( "0007" ),
        ProxyUser( "0005" ),;

        private final String recordID;

        CoreType( final String recordID )
        {
            this.recordID = recordID;
        }

        public String getRecordID()
        {
            return recordID;
        }
    }

    PwNotifyLdapStorageService( final PwmApplication pwmApplication, final PwNotifySettings settings )
    {
        this.pwmApplication = pwmApplication;
        this.settings = settings;
    }

    @Override
    public StoredNotificationState readStoredUserState(
            final UserIdentity userIdentity,
            final SessionLabel sessionLabel
    )
            throws PwmUnrecoverableException
    {
        final ConfigObjectRecord configObjectRecord = getUserCOR( userIdentity, CoreType.User );
        final String payload = configObjectRecord.getPayload();
        if ( StringUtil.isEmpty( payload ) )
        {
            return JsonUtil.deserialize( payload, StoredNotificationState.class );
        }
        return null;
    }

    public void writeStoredUserState(
            final UserIdentity userIdentity,
            final SessionLabel sessionLabel,
            final StoredNotificationState storedNotificationState
    )
            throws PwmUnrecoverableException
    {
        final ConfigObjectRecord configObjectRecord = getUserCOR( userIdentity, CoreType.User );
        final String payload = JsonUtil.serialize( storedNotificationState );
        try
        {
            configObjectRecord.updatePayload( payload );
        }
        catch ( ChaiOperationException e )
        {
            final String msg = "error writing user pwNotifyStatus attribute '" + settings.getLdapUserAttribute() + ", error: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_LDAP_DATA_ERROR, msg );
            throw new PwmUnrecoverableException( errorInformation );
        }
        catch ( ChaiUnavailableException e )
        {
            throw PwmUnrecoverableException.fromChaiException( e );
        }
    }

    @Override
    public StoredJobState readStoredJobState()
            throws PwmUnrecoverableException
    {
        final LdapProfile ldapProfile = pwmApplication.getConfig().getDefaultLdapProfile();
        final UserIdentity proxyUser = ldapProfile.getProxyUser( pwmApplication );
        final ConfigObjectRecord configObjectRecord = getUserCOR( proxyUser, CoreType.ProxyUser );
        final String payload = configObjectRecord.getPayload();

        if ( StringUtil.isEmpty( payload ) )
        {
            return new StoredJobState( null, null, null, null, false );
        }
        return JsonUtil.deserialize( payload, StoredJobState.class );
    }

    @Override
    public void writeStoredJobState( final StoredJobState storedJobState )
            throws PwmUnrecoverableException
    {
        final LdapProfile ldapProfile = pwmApplication.getConfig().getDefaultLdapProfile();
        final UserIdentity proxyUser = ldapProfile.getProxyUser( pwmApplication );
        final ConfigObjectRecord configObjectRecord = getUserCOR( proxyUser, CoreType.ProxyUser );
        final String payload = JsonUtil.serialize( storedJobState );

        try
        {
            configObjectRecord.updatePayload( payload );
        }
        catch ( ChaiOperationException e )
        {
            final String msg = "error writing user pwNotifyStatus attribute on proxy user '" + settings.getLdapUserAttribute() + ", error: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_LDAP_DATA_ERROR, msg );
            throw new PwmUnrecoverableException( errorInformation );
        }
        catch ( ChaiUnavailableException e )
        {
            throw PwmUnrecoverableException.fromChaiException( e );
        }
    }

    private ConfigObjectRecord getUserCOR( final UserIdentity userIdentity, final CoreType coreType )
            throws PwmUnrecoverableException
    {
        final String userAttr = settings.getLdapUserAttribute();
        final ChaiUser chaiUser = pwmApplication.getProxiedChaiUser( userIdentity );
        return ConfigObjectRecord.createNew( chaiUser, userAttr, coreType.getRecordID(), null, null );
    }
}
