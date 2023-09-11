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

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.config.AppConfig;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.svc.PwmService;
import password.pwm.svc.db.DatabaseAccessor;
import password.pwm.svc.db.DatabaseException;
import password.pwm.svc.db.DatabaseTable;
import password.pwm.util.java.ClosableIterator;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.logging.PwmLogger;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

class DatabaseNodeDataService implements NodeDataServiceProvider
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( DatabaseNodeDataService.class );

    private static final DatabaseTable TABLE = DatabaseTable.CLUSTER_STATE;
    private static final String KEY_PREFIX_NODE = "node-";

    private final PwmApplication pwmApplication;

    DatabaseNodeDataService( final PwmApplication pwmApplication ) throws PwmUnrecoverableException
    {
        this.pwmApplication = Objects.requireNonNull( pwmApplication );

        if ( pwmApplication.getDatabaseService().status() != PwmService.STATUS.OPEN )
        {
            throw new PwmUnrecoverableException( PwmError.ERROR_NODE_SERVICE_ERROR, "database service is not available" );
        }
    }

    private DatabaseAccessor getDatabaseAccessor()
            throws PwmUnrecoverableException
    {
        return pwmApplication.getDatabaseService().getAccessor();
    }

    private String localKeyForStoredNode( final StoredNodeData storedNodeData )
            throws PwmUnrecoverableException
    {
        final String instanceID = storedNodeData.instanceID();
        final String hash = pwmApplication.getSecureService().hash( instanceID );
        final String truncatedHash = hash.length() > 64
                ? hash.substring( 0, 64 )
                : hash;

        return KEY_PREFIX_NODE + truncatedHash;
    }


    @Override
    public Map<String, StoredNodeData> readStoredData( )
            throws PwmUnrecoverableException
    {
        final Map<String, StoredNodeData> returnList = new LinkedHashMap<>();
        final DatabaseAccessor databaseAccessor = getDatabaseAccessor();
        try ( ClosableIterator<Map.Entry<String, String>> tableIterator = databaseAccessor.iterator( TABLE ) )
        {
            while ( tableIterator.hasNext() )
            {
                final String dbKey = tableIterator.next().getKey();
                if ( dbKey.startsWith( KEY_PREFIX_NODE ) )
                {
                    final Optional<String> rawValueInDb = databaseAccessor.get( TABLE, dbKey );
                    rawValueInDb.ifPresent( s ->
                    {
                        final StoredNodeData nodeDataInDb = JsonFactory.get().deserialize( s, StoredNodeData.class );
                        returnList.put( nodeDataInDb.instanceID(), nodeDataInDb );
                    } );
                }
            }
        }
        catch ( final DatabaseException e )
        {
            throw new PwmUnrecoverableException( PwmError.ERROR_DB_UNAVAILABLE, "unexpected database error reading cluster node status: " + e.getMessage() );
        }
        return returnList;
    }

    @Override
    public void writeNodeStatus( final StoredNodeData storedNodeData ) throws PwmUnrecoverableException
    {
        try
        {
            final DatabaseAccessor databaseAccessor = getDatabaseAccessor();
            final String key = localKeyForStoredNode( storedNodeData );
            final String value = JsonFactory.get().serialize( storedNodeData );
            databaseAccessor.put( TABLE, key, value );
        }
        catch ( final DatabaseException e )
        {
            throw new PwmUnrecoverableException( PwmError.ERROR_DB_UNAVAILABLE, "unexpected database error writing cluster node status: " + e.getMessage() );
        }
    }

    @Override
    public int purgeOutdatedNodes( final TimeDuration maxNodeAge )
            throws PwmUnrecoverableException
    {
        int nodesPurged = 0;

        try
        {
            final Map<String, StoredNodeData> nodeDatas = readStoredData();
            final DatabaseAccessor databaseAccessor = getDatabaseAccessor();
            for ( final StoredNodeData storedNodeData : nodeDatas.values() )
            {
                final TimeDuration recordAge = TimeDuration.fromCurrent( storedNodeData.timestamp() );
                final String instanceID = storedNodeData.instanceID();


                if ( recordAge.isLongerThan( maxNodeAge ) )
                {
                    // purge outdated records
                    LOGGER.debug( () -> "purging outdated node reference to instanceID '" + instanceID + "'" );

                    databaseAccessor.remove( TABLE, localKeyForStoredNode( storedNodeData ) );
                    nodesPurged++;
                }
            }
        }
        catch ( final DatabaseException e )
        {
            throw new PwmUnrecoverableException( PwmError.ERROR_DB_UNAVAILABLE, "unexpected database error writing cluster node status: " + e.getMessage() );
        }

        return nodesPurged;
    }


    public NodeServiceSettings settings( final AppConfig appConfig )
    {
        return new NodeServiceSettings(
                TimeDuration.of( Integer.parseInt( appConfig.readAppProperty( AppProperty.CLUSTER_DB_HEARTBEAT_SECONDS ) ), TimeDuration.Unit.SECONDS ),
                TimeDuration.of( Integer.parseInt( appConfig.readAppProperty( AppProperty.CLUSTER_DB_NODE_TIMEOUT_SECONDS ) ), TimeDuration.Unit.SECONDS ),
                TimeDuration.of( Integer.parseInt( appConfig.readAppProperty( AppProperty.CLUSTER_DB_NODE_PURGE_SECONDS ) ), TimeDuration.Unit.SECONDS )
        );
    }

}
