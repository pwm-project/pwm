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

public class NodeService implements PwmService
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( NodeService.class );

    private PwmApplication pwmApplication;
    private STATUS status = STATUS.NEW;
    private NodeMachine nodeMachine;
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

        final boolean serviceEnabled = pwmApplication.getConfig().readSettingAsBoolean( PwmSetting.CLUSTER_ENABLED );
        if ( !serviceEnabled )
        {
            status = STATUS.CLOSED;
            return;
        }

        try
        {
            final NodeServiceSettings nodeServiceSettings;
            final NodeDataServiceProvider clusterDataServiceProvider;
            dataStore = pwmApplication.getConfig().readSettingAsEnum( PwmSetting.CLUSTER_STORAGE_MODE, DataStorageMethod.class );

            if ( dataStore != null )
            {
                switch ( dataStore )
                {
                    case DB:
                    {
                        LOGGER.trace( () -> "starting database-backed node service provider" );
                        nodeServiceSettings = NodeServiceSettings.fromConfigForDB( pwmApplication.getConfig() );
                        clusterDataServiceProvider = new DatabaseNodeDataService( pwmApplication );
                    }
                    break;

                    case LDAP:
                    {
                        LOGGER.trace( () -> "starting ldap-backed node service provider" );
                        nodeServiceSettings = NodeServiceSettings.fromConfigForLDAP( pwmApplication.getConfig() );
                        clusterDataServiceProvider = new LDAPNodeDataService( pwmApplication );
                    }
                    break;

                    default:
                        LOGGER.debug( () -> "no suitable storage method configured " );
                        JavaHelper.unhandledSwitchStatement( dataStore );
                        return;

                }

                nodeMachine = new NodeMachine( pwmApplication, clusterDataServiceProvider, nodeServiceSettings );
                status = STATUS.OPEN;
                return;
            }
        }
        catch ( final PwmUnrecoverableException e )
        {
            startupError = e.getErrorInformation();
            LOGGER.error( () -> "error starting up node service: " + e.getMessage() );
        }
        catch ( final Exception e )
        {
            startupError = new ErrorInformation( PwmError.ERROR_NODE_SERVICE_ERROR, "error starting up node service: " + e.getMessage() );
            LOGGER.error( startupError );
        }

        status = STATUS.CLOSED;
    }

    @Override
    public void close( )
    {
        if ( nodeMachine != null )
        {
            nodeMachine.close();
            nodeMachine = null;
        }
        status = STATUS.CLOSED;
    }

    @Override
    public List<HealthRecord> healthCheck( )
    {
        if ( nodeMachine != null )
        {
            final ErrorInformation errorInformation = nodeMachine.getLastError();
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

        if ( nodeMachine != null )
        {
            props.putAll( JsonUtil.deserializeStringMap( JsonUtil.serialize( nodeMachine.getNodeServiceStatistics() ) ) );
        }
        return new ServiceInfoBean( Collections.singleton( dataStore ), props );
    }

    public boolean isMaster( )
    {
        if ( status == STATUS.OPEN && nodeMachine != null )
        {
            return nodeMachine.isMaster();
        }

        return false;
    }

    public List<NodeInfo> nodes( ) throws PwmUnrecoverableException
    {
        if ( status == STATUS.OPEN && nodeMachine != null )
        {
            return nodeMachine.nodes();
        }
        return Collections.emptyList();
    }

    private void figureDataStorageMethod( final PwmApplication pwmApplication )
            throws PwmUnrecoverableException
    {
        {
            final UserIdentity userIdentity = pwmApplication.getConfig().getDefaultLdapProfile().getTestUser( pwmApplication );
            if ( userIdentity == null )
            {
                final String msg = "LDAP storage type selected, but LDAP test user not defined.";
                LOGGER.debug( () -> msg );
                startupError = new ErrorInformation( PwmError.ERROR_NODE_SERVICE_ERROR, msg );
            }
        }

        {
            if ( !pwmApplication.getConfig().hasDbConfigured() )
            {
                final String msg = "DB storage type selected, but remote DB is not configured.";
                LOGGER.debug( () -> msg );
                startupError = new ErrorInformation( PwmError.ERROR_NODE_SERVICE_ERROR, msg );
            }
        }
    }
}
