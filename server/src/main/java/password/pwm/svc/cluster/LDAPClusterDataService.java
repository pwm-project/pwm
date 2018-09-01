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

package password.pwm.svc.cluster;

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiException;
import lombok.Value;
import password.pwm.PwmApplication;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class LDAPClusterDataService implements ClusterDataServiceProvider
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( LDAPClusterDataService.class );

    private final PwmApplication pwmApplication;
    private static final String VALUE_PREFIX = "0006#.#.#";

    public LDAPClusterDataService( final PwmApplication pwmApplication )
    {
        this.pwmApplication = pwmApplication;
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
        catch ( ChaiException e )
        {
            throw new PwmUnrecoverableException( PwmError.ERROR_LDAP_DATA_ERROR, "error reading cluster data: " + e.getMessage() );
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
        catch ( ChaiException e )
        {
            throw new PwmUnrecoverableException( PwmError.ERROR_LDAP_DATA_ERROR, "error writing cluster data: " + e.getMessage() );
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
                LOGGER.debug( "purging outdated node reference to instanceID '" + instanceID + "'" );

                try
                {
                    final String oldRawValue = VALUE_PREFIX + JsonUtil.serialize( storedNodeData );
                    ldapHelper.getChaiUser().deleteAttribute( ldapHelper.getAttr(), oldRawValue );
                    nodesPurged++;
                }
                catch ( ChaiException e )
                {
                    throw new PwmUnrecoverableException( PwmError.ERROR_LDAP_DATA_ERROR, "error purging cluster data: " + e.getMessage() );
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
            attr = userIdentity.getLdapProfile( pwmApplication.getConfig() ).readSettingAsString( PwmSetting.CHALLENGE_USER_ATTRIBUTE );

        }

        static LDAPHelper createLDAPHelper( final PwmApplication pwmApplication ) throws PwmUnrecoverableException
        {
            return new LDAPHelper( pwmApplication );
        }
    }
}
