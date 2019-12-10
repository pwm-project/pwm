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
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.util.ConfigObjectRecord;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.config.profile.LdapProfile;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

class PwNotifyLdapStorageService implements PwNotifyStorageService
{
    private static final String COR_GUID = ".";

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
            throws PwmUnrecoverableException
    {
        this.pwmApplication = pwmApplication;
        this.settings = settings;

        final LdapProfile defaultLdapProfile = pwmApplication.getConfig().getDefaultLdapProfile();
        final String testUserDN = defaultLdapProfile.readSettingAsString( PwmSetting.LDAP_TEST_USER_DN );
        if ( StringUtil.isEmpty( testUserDN ) )
        {
            final String msg = "LDAP storage type selected, but LDAP test user ("
                    + PwmSetting.LDAP_TEST_USER_DN.toMenuLocationDebug( defaultLdapProfile.getIdentifier(), PwmConstants.DEFAULT_LOCALE )
                    + ") not defined.";
            throw new PwmUnrecoverableException( PwmError.ERROR_PWNOTIFY_SERVICE_ERROR, msg );
        }

        for ( final LdapProfile ldapProfile : pwmApplication.getConfig().getLdapProfiles().values() )
        {
            if ( StringUtil.isEmpty( ldapProfile.readSettingAsString( PwmSetting.LDAP_ATTRIBUTE_PWNOTIFY ) ) )
            {
                final String msg = "LDAP storage type selected, but setting '"
                        + PwmSetting.LDAP_ATTRIBUTE_PWNOTIFY.toMenuLocationDebug( ldapProfile.getIdentifier(), PwmConstants.DEFAULT_LOCALE )
                        + " is not configured ";
                throw new PwmUnrecoverableException( PwmError.ERROR_PWNOTIFY_SERVICE_ERROR, msg );
            }
        }

    }

    @Override
    public Optional<PwNotifyUserStatus> readStoredUserState(
            final UserIdentity userIdentity,
            final SessionLabel sessionLabel
    )
            throws PwmUnrecoverableException
    {
        final ConfigObjectRecord configObjectRecord = getUserCOR( userIdentity, CoreType.User );
        final String payload = configObjectRecord.getPayload();
        if ( !StringUtil.isEmpty( payload ) )
        {
            return Optional.ofNullable( JsonUtil.deserialize( payload, PwNotifyUserStatus.class ) );
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
        final ConfigObjectRecord configObjectRecord = getUserCOR( userIdentity, CoreType.User );
        final String payload = JsonUtil.serialize( pwNotifyUserStatus );
        try
        {
            configObjectRecord.updatePayload( payload );
        }
        catch ( final ChaiOperationException e )
        {
            final String msg = "error writing user pwNotifyStatus attribute '" + getLdapUserAttribute( userIdentity ) + ", error: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_LDAP_DATA_ERROR, msg );
            throw new PwmUnrecoverableException( errorInformation );
        }
        catch ( final ChaiUnavailableException e )
        {
            throw PwmUnrecoverableException.fromChaiException( e );
        }
    }

    @Override
    public PwNotifyStoredJobState readStoredJobState()
            throws PwmUnrecoverableException
    {
        final UserIdentity proxyUser = pwmApplication.getConfig().getDefaultLdapProfile().getTestUser( pwmApplication );
        final ConfigObjectRecord configObjectRecord = getUserCOR( proxyUser, CoreType.ProxyUser );
        final String payload = configObjectRecord.getPayload();

        if ( StringUtil.isEmpty( payload ) )
        {
            return new PwNotifyStoredJobState( null, null, null, null, false );
        }
        return JsonUtil.deserialize( payload, PwNotifyStoredJobState.class );
    }

    @Override
    public void writeStoredJobState( final PwNotifyStoredJobState pwNotifyStoredJobState )
            throws PwmUnrecoverableException
    {
        final UserIdentity proxyUser = pwmApplication.getConfig().getDefaultLdapProfile().getTestUser( pwmApplication );
        final ConfigObjectRecord configObjectRecord = getUserCOR( proxyUser, CoreType.ProxyUser );
        final String payload = JsonUtil.serialize( pwNotifyStoredJobState );

        try
        {
            configObjectRecord.updatePayload( payload );
        }
        catch ( final ChaiOperationException e )
        {
            final String msg = "error writing user pwNotifyStatus attribute on proxy user '" + getLdapUserAttribute( proxyUser ) + ", error: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_LDAP_DATA_ERROR, msg );
            throw new PwmUnrecoverableException( errorInformation );
        }
        catch ( final ChaiUnavailableException e )
        {
            throw PwmUnrecoverableException.fromChaiException( e );
        }
    }

    private ConfigObjectRecord getUserCOR( final UserIdentity userIdentity, final CoreType coreType )
            throws PwmUnrecoverableException
    {
        final String userAttr = getLdapUserAttribute( userIdentity );
        final ChaiUser chaiUser = pwmApplication.getProxiedChaiUser( userIdentity );
        try
        {
            final List<ConfigObjectRecord> list = ConfigObjectRecord.readRecordFromLDAP(
                    chaiUser,
                    userAttr,
                    coreType.getRecordID(),
                    Collections.singleton( COR_GUID ),
                    Collections.singleton( COR_GUID ) );
            if ( list.isEmpty() )
            {
                return ConfigObjectRecord.createNew( chaiUser, userAttr, coreType.getRecordID(), COR_GUID, COR_GUID );
            }
            else
            {
                return list.iterator().next();
            }
        }
        catch ( final ChaiUnavailableException | ChaiOperationException e )
        {
            throw PwmUnrecoverableException.fromChaiException( e );
        }
    }

    private String getLdapUserAttribute( final UserIdentity userIdentity )
    {
        return  userIdentity.getLdapProfile( pwmApplication.getConfig() ).readSettingAsString( PwmSetting.LDAP_ATTRIBUTE_PWNOTIFY );
    }
}
