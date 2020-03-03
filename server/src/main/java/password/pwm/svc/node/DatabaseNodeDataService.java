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
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.svc.PwmService;
import password.pwm.util.db.DatabaseAccessor;
import password.pwm.util.db.DatabaseException;
import password.pwm.util.db.DatabaseTable;
import password.pwm.util.java.ClosableIterator;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.util.LinkedHashMap;
import java.util.Map;

class DatabaseNodeDataService implements NodeDataServiceProvider
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( DatabaseNodeDataService.class );

    private static final DatabaseTable TABLE = DatabaseTable.CLUSTER_STATE;
    private static final String KEY_PREFIX_NODE = "node-";

    private final PwmApplication pwmApplication;

    DatabaseNodeDataService( final PwmApplication pwmApplication ) throws PwmUnrecoverableException
    {
        this.pwmApplication = pwmApplication;

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
        final String instanceID = storedNodeData.getInstanceID();
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
                    final String rawValueInDb = databaseAccessor.get( TABLE, dbKey );
                    final StoredNodeData nodeDataInDb = JsonUtil.deserialize( rawValueInDb, StoredNodeData.class );
                    returnList.put( nodeDataInDb.getInstanceID(), nodeDataInDb );
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
            final String value = JsonUtil.serialize( storedNodeData );
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
                final TimeDuration recordAge = TimeDuration.fromCurrent( storedNodeData.getTimestamp() );
                final String instanceID = storedNodeData.getInstanceID();


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
}
