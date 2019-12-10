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

package password.pwm.svc.node;

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiException;
import lombok.Value;
import password.pwm.PwmApplication;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.config.profile.LdapProfile;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

class LDAPNodeDataService implements NodeDataServiceProvider
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( LDAPNodeDataService.class );

    private final PwmApplication pwmApplication;
    private static final String VALUE_PREFIX = "0006#.#.#";

    LDAPNodeDataService( final PwmApplication pwmApplication ) throws PwmUnrecoverableException
    {
        this.pwmApplication = pwmApplication;

        final LdapProfile ldapProfile = pwmApplication.getConfig().getDefaultLdapProfile();
        final String testUser = ldapProfile.readSettingAsString( PwmSetting.LDAP_TEST_USER_DN );

        if ( StringUtil.isEmpty( testUser ) )
        {
            final String msg = "ldap node service requires that setting "
                    + PwmSetting.LDAP_TEST_USER_DN.toMenuLocationDebug( ldapProfile.getIdentifier(), null )
                    + " is configured";
            throw PwmUnrecoverableException.newException( PwmError.ERROR_NODE_SERVICE_ERROR, msg );
        }
    }

    @Override
    public Map<String, StoredNodeData> readStoredData( ) throws PwmUnrecoverableException
    {
        final Map<String, StoredNodeData> returnData = new LinkedHashMap<>(  );

        final LDAPHelper ldapHelper = LDAPHelper.createLDAPHelper( pwmApplication );

        try
        {
            final Set<String> values = ldapHelper.getChaiUser().readMultiStringAttribute( ldapHelper.getAttr() );
            for ( final String value : values )
            {
                if ( value.startsWith( VALUE_PREFIX ) )
                {
                    final String rawValue = value.substring( VALUE_PREFIX.length() );
                    final StoredNodeData storedNodeData = JsonUtil.deserialize( rawValue, StoredNodeData.class );
                    returnData.put( storedNodeData.getInstanceID(),  storedNodeData );
                }
            }
        }
        catch ( final ChaiException e )
        {
            throw new PwmUnrecoverableException( PwmError.ERROR_LDAP_DATA_ERROR, "error reading node service data "
                    + ldapHelper.debugInfo() + ", error: " + e.getMessage() );
        }

        return returnData;
    }

    @Override
    public void writeNodeStatus( final StoredNodeData storedNodeData ) throws PwmUnrecoverableException
    {
        final Map<String, StoredNodeData> currentServerData = readStoredData();
        final StoredNodeData removeNode = currentServerData.get( storedNodeData.getInstanceID() );

        final LDAPHelper ldapHelper = LDAPHelper.createLDAPHelper( pwmApplication );

        final String newRawValue = VALUE_PREFIX + JsonUtil.serialize( storedNodeData );

        try
        {
            if ( removeNode != null )
            {
                final String oldRawValue = VALUE_PREFIX + JsonUtil.serialize( removeNode );
                ldapHelper.getChaiUser().replaceAttribute( ldapHelper.getAttr(), oldRawValue, newRawValue );
            }
            else
            {
                ldapHelper.getChaiUser().addAttribute( ldapHelper.getAttr(), newRawValue );
            }
        }
        catch ( final ChaiException e )
        {
            throw new PwmUnrecoverableException( PwmError.ERROR_LDAP_DATA_ERROR, "error writing node service data "
                    + ldapHelper.debugInfo() + ", error: " + e.getMessage() );
        }

    }

    @Override
    public int purgeOutdatedNodes( final TimeDuration maxNodeAge ) throws PwmUnrecoverableException
    {
        final LDAPHelper ldapHelper = LDAPHelper.createLDAPHelper( pwmApplication );

        int nodesPurged = 0;

        final Map<String, StoredNodeData> nodeDatas = readStoredData();

        for ( final StoredNodeData storedNodeData : nodeDatas.values() )
        {
            final TimeDuration recordAge = TimeDuration.fromCurrent( storedNodeData.getTimestamp() );
            final String instanceID = storedNodeData.getInstanceID();

            if ( recordAge.isLongerThan( maxNodeAge ) )
            {
                // purge outdated records
                LOGGER.debug( () -> "purging outdated node reference to instanceID '" + instanceID + "'" );

                try
                {
                    final String oldRawValue = VALUE_PREFIX + JsonUtil.serialize( storedNodeData );
                    ldapHelper.getChaiUser().deleteAttribute( ldapHelper.getAttr(), oldRawValue );
                    nodesPurged++;
                }
                catch ( final ChaiException e )
                {
                    throw new PwmUnrecoverableException( PwmError.ERROR_LDAP_DATA_ERROR, "error purging node service data "
                          + ldapHelper.debugInfo() + ", error: " + e.getMessage() );
                }
            }
        }

        return nodesPurged;

    }

    @Value
    private static class LDAPHelper
    {
        private final PwmApplication pwmApplication;
        private final UserIdentity userIdentity;
        private final ChaiUser chaiUser;
        private final String attr;

        private LDAPHelper( final PwmApplication pwmApplication )
                throws PwmUnrecoverableException
        {
            this.pwmApplication = pwmApplication;

            userIdentity = pwmApplication.getConfig().getDefaultLdapProfile().getTestUser( pwmApplication );

            if ( userIdentity == null )
            {
                final String ldapProfileID = pwmApplication.getConfig().getDefaultLdapProfile().getIdentifier();
                final String errorMsg = "a test user is not configured for ldap profile '" + ldapProfileID + "'";
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.CONFIG_FORMAT_ERROR, errorMsg );
                throw new PwmUnrecoverableException( errorInformation );
            }

            chaiUser = pwmApplication.getProxiedChaiUser( userIdentity );

            attr = userIdentity.getLdapProfile( pwmApplication.getConfig() ).readSettingAsString( PwmSetting.LDAP_ATTRIBUTE_PWNOTIFY );
        }

        static LDAPHelper createLDAPHelper( final PwmApplication pwmApplication ) throws PwmUnrecoverableException
        {
            return new LDAPHelper( pwmApplication );
        }

        String debugInfo()
        {
            return "user '" + this.userIdentity.toDisplayString() + "' attribute '" + attr  + "'";
        }
    }
}
