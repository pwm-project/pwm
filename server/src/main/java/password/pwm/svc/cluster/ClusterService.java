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

import password.pwm.PwmApplication;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthMessage;
import password.pwm.health.HealthRecord;
import password.pwm.svc.PwmService;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.logging.PwmLogger;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClusterService implements PwmService
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( ClusterService.class );

    private PwmApplication pwmApplication;
    private STATUS status = STATUS.NEW;
    private ClusterMachine clusterMachine;
    private DataStorageMethod dataStore;
    private ErrorInformation startupError;


    @Override
    public STATUS status( )
    {
        return status;
    }

    @Override
    public void init( final PwmApplication pwmApplication ) throws PwmException
    {
        status = STATUS.OPENING;
        this.pwmApplication = pwmApplication;


        try
        {
            final ClusterSettings clusterSettings;
            final ClusterDataServiceProvider clusterDataServiceProvider;
            dataStore = figureDataStorageMethod( pwmApplication );

            if ( dataStore != null )
            {
                switch ( dataStore )
                {
                    case DB:
                    {
                        LOGGER.trace( () -> "starting database-backed cluster provider" );
                        clusterSettings = ClusterSettings.fromConfigForDB( pwmApplication.getConfig() );
                        clusterDataServiceProvider = new DatabaseClusterDataService( pwmApplication );
                    }
                    break;

                    case LDAP:
                    {
                        LOGGER.trace( () -> "starting ldap-backed cluster provider" );
                        clusterSettings = ClusterSettings.fromConfigForLDAP( pwmApplication.getConfig() );
                        clusterDataServiceProvider = new LDAPClusterDataService( pwmApplication );
                    }
                    break;

                    default:
                        LOGGER.debug( "no suitable storage method configured " );
                        JavaHelper.unhandledSwitchStatement( dataStore );
                        return;

                }

                clusterMachine = new ClusterMachine( pwmApplication, clusterDataServiceProvider, clusterSettings );
                status = STATUS.OPEN;
                return;
            }
        }
        catch ( Exception e )
        {
            LOGGER.error( "error starting up cluster service: " + e.getMessage() );
        }

        status = STATUS.CLOSED;
    }

    @Override
    public void close( )
    {
        if ( clusterMachine != null )
        {
            clusterMachine.close();
            clusterMachine = null;
        }
        status = STATUS.CLOSED;
    }

    @Override
    public List<HealthRecord> healthCheck( )
    {
        if ( clusterMachine != null )
        {
            final ErrorInformation errorInformation = clusterMachine.getLastError();
            if ( errorInformation != null )
            {
                final HealthRecord healthRecord = HealthRecord.forMessage( HealthMessage.Cluster_Error, errorInformation.getDetailedErrorMsg() );
                return Collections.singletonList( healthRecord );
            }
        }

        if ( startupError != null )
        {
            final HealthRecord healthRecord = HealthRecord.forMessage( HealthMessage.Cluster_Error, startupError.getDetailedErrorMsg() );
            return Collections.singletonList( healthRecord );
        }

        return null;
    }

    @Override
    public ServiceInfoBean serviceInfo( )
    {
        final Map<String, String> props = new HashMap<>();

        if ( clusterMachine != null )
        {
            props.putAll( JsonUtil.deserializeStringMap( JsonUtil.serialize( clusterMachine.getClusterStatistics() ) ) );
        }
        return new ServiceInfoBean( Collections.singleton( dataStore ), props );
    }

    public boolean isMaster( )
    {
        if ( status == STATUS.OPEN && clusterMachine != null )
        {
            return clusterMachine.isMaster();
        }

        return false;
    }

    public List<NodeInfo> nodes( ) throws PwmUnrecoverableException
    {
        if ( status == STATUS.OPEN && clusterMachine != null )
        {
            return clusterMachine.nodes();
        }
        return Collections.emptyList();
    }

    private DataStorageMethod figureDataStorageMethod( final PwmApplication pwmApplication )
            throws PwmUnrecoverableException
    {
        final DataStorageMethod method = pwmApplication.getConfig().readSettingAsEnum( PwmSetting.CLUSTER_STORAGE_MODE, DataStorageMethod.class );
        if ( method == DataStorageMethod.LDAP )
        {
            final UserIdentity userIdentity = pwmApplication.getConfig().getDefaultLdapProfile().getTestUser( pwmApplication );
            if ( userIdentity == null )
            {
                final String msg = "LDAP storage type selected, but LDAP test user not defined.";
                LOGGER.debug( msg );
                startupError = new ErrorInformation( PwmError.ERROR_CLUSTER_SERVICE_ERROR, msg );
                return null;
            }
        }

        if ( method == DataStorageMethod.DB )
        {
            if ( !pwmApplication.getConfig().hasDbConfigured() )
            {
                final String msg = "DB storage type selected, but remote DB is not configured.";
                LOGGER.debug( msg );
                startupError = new ErrorInformation( PwmError.ERROR_CLUSTER_SERVICE_ERROR, msg );
                return null;
            }
        }

        return method;
    }
}
