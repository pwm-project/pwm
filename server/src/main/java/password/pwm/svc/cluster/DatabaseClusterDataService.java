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
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.db.DatabaseAccessor;
import password.pwm.util.db.DatabaseException;
import password.pwm.util.db.DatabaseTable;
import password.pwm.util.java.ClosableIterator;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.util.LinkedHashMap;
import java.util.Map;

public class DatabaseClusterDataService implements ClusterDataServiceProvider
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( DatabaseClusterDataService.class );

    private static final DatabaseTable TABLE = DatabaseTable.CLUSTER_STATE;
    private static final String KEY_PREFIX_NODE = "node-";

    private final PwmApplication pwmApplication;

    public DatabaseClusterDataService( final PwmApplication pwmApplication )
    {
        this.pwmApplication = pwmApplication;
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
        try ( ClosableIterator<String> tableIterator = databaseAccessor.iterator( TABLE ) )
        {
            while ( tableIterator.hasNext() )
            {
                final String dbKey = tableIterator.next();
                if ( dbKey.startsWith( KEY_PREFIX_NODE ) )
                {
                    final String rawValueInDb = databaseAccessor.get( TABLE, dbKey );
                    final StoredNodeData nodeDataInDb = JsonUtil.deserialize( rawValueInDb, StoredNodeData.class );
                    returnList.put( nodeDataInDb.getInstanceID(), nodeDataInDb );
                }
            }
        }
        catch ( DatabaseException e )
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
        catch ( DatabaseException e )
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
        catch ( DatabaseException e )
        {
            throw new PwmUnrecoverableException( PwmError.ERROR_DB_UNAVAILABLE, "unexpected database error writing cluster node status: " + e.getMessage() );
        }

        return nodesPurged;
    }
}
