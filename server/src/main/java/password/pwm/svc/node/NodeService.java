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

package password.pwm.svc.node;

import password.pwm.PwmApplication;
import password.pwm.PwmDomain;
import password.pwm.bean.DomainID;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthMessage;
import password.pwm.health.HealthRecord;
import password.pwm.svc.AbstractPwmService;
import password.pwm.svc.PwmService;
import password.pwm.util.java.MiscUtil;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.logging.PwmLogger;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class NodeService extends AbstractPwmService implements PwmService
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( NodeService.class );

    private NodeMachine nodeMachine;
    private DataStorageMethod dataStore;

    @Override
    public STATUS postAbstractInit( final PwmApplication pwmApplication, final DomainID domainID )
            throws PwmException
    {
        final boolean serviceEnabled = pwmApplication.getConfig().readSettingAsBoolean( PwmSetting.NODE_SERVICE_ENABLED );
        if ( !serviceEnabled )
        {
            return STATUS.CLOSED;
        }

        try
        {
            final NodeServiceSettings nodeServiceSettings;
            final NodeDataServiceProvider clusterDataServiceProvider;
            dataStore = pwmApplication.getConfig().readSettingAsEnum( PwmSetting.NODE_SERVICE_STORAGE_MODE, DataStorageMethod.class );

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
                        clusterDataServiceProvider = new LDAPNodeDataService( this, pwmApplication.getAdminDomain() );
                    }
                    break;

                    default:
                        LOGGER.debug( () -> "no suitable storage method configured " );
                        MiscUtil.unhandledSwitchStatement( dataStore );
                        return STATUS.CLOSED;

                }

                nodeMachine = new NodeMachine( pwmApplication, clusterDataServiceProvider, nodeServiceSettings );
                scheduleFixedRateJob( nodeMachine.getHeartbeatProcess(), nodeServiceSettings.getHeartbeatInterval(), nodeServiceSettings.getHeartbeatInterval() );
            }
        }
        catch ( final PwmUnrecoverableException e )
        {
            setStartupError( e.getErrorInformation() );
            LOGGER.error( () -> "error starting up node service: " + e.getMessage() );
            return STATUS.CLOSED;
        }
        catch ( final Exception e )
        {
            setStartupError( new ErrorInformation( PwmError.ERROR_NODE_SERVICE_ERROR, "error starting up node service: " + e.getMessage() ) );
            LOGGER.error( getStartupError() );
            return STATUS.CLOSED;
        }

        return STATUS.OPEN;
    }

    @Override
    public void shutdownImpl( )
    {
        if ( nodeMachine != null )
        {
            nodeMachine.close();
            nodeMachine = null;
        }
        setStatus( STATUS.CLOSED );
    }

    @Override
    public List<HealthRecord> serviceHealthCheck( )
    {
        if ( nodeMachine != null )
        {
            final ErrorInformation errorInformation = nodeMachine.getLastError();
            if ( errorInformation != null )
            {
                return Collections.singletonList( HealthRecord.forMessage(
                        DomainID.systemId(),
                        HealthMessage.Cluster_Error,
                        errorInformation.getDetailedErrorMsg() ) );
            }
        }

        if ( getStartupError() != null )
        {
            return Collections.singletonList( HealthRecord.forMessage(
                    DomainID.systemId(),
                    HealthMessage.Cluster_Error,
                    getStartupError().getDetailedErrorMsg() ) );
        }

        return Collections.emptyList();
    }

    @Override
    public ServiceInfoBean serviceInfo( )
    {
        final Map<String, String> props = new HashMap<>();

        if ( nodeMachine != null )
        {
            props.putAll( JsonFactory.get().deserializeStringMap( JsonFactory.get().serialize( nodeMachine.getNodeServiceStatistics() ) ) );
        }
        return ServiceInfoBean.builder()
                .storageMethod( dataStore )
                .debugProperties( props )
                .build();
    }

    public boolean isMaster( )
    {
        if ( status() == STATUS.OPEN && nodeMachine != null )
        {
            return nodeMachine.isMaster();
        }

        return false;
    }

    public List<NodeInfo> nodes( ) throws PwmUnrecoverableException
    {
        if ( status() == STATUS.OPEN && nodeMachine != null )
        {
            return nodeMachine.nodes();
        }
        return Collections.emptyList();
    }

    private void figureDataStorageMethod( final PwmDomain pwmDomain )
            throws PwmUnrecoverableException
    {
        {
            final Optional<UserIdentity> userIdentity = pwmDomain.getConfig().getDefaultLdapProfile().getTestUser( getSessionLabel(), pwmDomain );
            if ( userIdentity.isEmpty() )
            {
                final String msg = "LDAP storage type selected, but LDAP test user not defined.";
                LOGGER.debug( () -> msg );
                setStartupError( new ErrorInformation( PwmError.ERROR_NODE_SERVICE_ERROR, msg ) );
            }
        }

        {
            if ( !pwmDomain.getConfig().getAppConfig().hasDbConfigured() )
            {
                final String msg = "DB storage type selected, but remote DB is not configured.";
                LOGGER.debug( () -> msg );
                setStartupError( new ErrorInformation( PwmError.ERROR_NODE_SERVICE_ERROR, msg ) );
            }
        }
    }
}
