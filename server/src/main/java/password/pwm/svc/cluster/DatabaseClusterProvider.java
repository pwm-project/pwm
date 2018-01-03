/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
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
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.db.DatabaseService;
import password.pwm.util.db.DatabaseTable;
import password.pwm.util.java.ClosableIterator;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

class DatabaseClusterProvider implements ClusterProvider
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( DatabaseClusterProvider.class );

    private static final DatabaseTable TABLE = DatabaseTable.CLUSTER_STATE;


    private static final String KEY_PREFIX_NODE = "node-";

    private final PwmApplication pwmApplication;
    private final DatabaseService databaseService;
    private final ScheduledExecutorService executorService;

    private ErrorInformation lastError;

    private final Map<String, DatabaseStoredNodeData> nodeDatas = new ConcurrentHashMap<>();

    private final DatabaseClusterSettings settings;

    DatabaseClusterProvider( final PwmApplication pwmApplication ) throws PwmUnrecoverableException
    {
        this.pwmApplication = pwmApplication;
        this.settings = DatabaseClusterSettings.fromConfig( pwmApplication.getConfig() );

        if ( !settings.isEnable() )
        {
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.CONFIG_FORMAT_ERROR, "database clustering is not enabled via app property" ) );
        }

        this.databaseService = pwmApplication.getDatabaseService();
        this.executorService = JavaHelper.makeSingleThreadExecutorService( pwmApplication, DatabaseClusterProvider.class );

        final long intervalSeconds = settings.getHeartbeatInterval().getTotalSeconds();

        this.executorService.scheduleAtFixedRate(
                new HeartbeatProcess(),
                1,
                intervalSeconds,
                TimeUnit.SECONDS
        );
    }

    @Override
    public void close( )
    {
        JavaHelper.closeAndWaitExecutor( executorService, new TimeDuration( 1, TimeUnit.SECONDS ) );
    }


    @Override
    public List<NodeInfo> nodes( ) throws PwmUnrecoverableException
    {
        final Map<String, NodeInfo> returnObj = new TreeMap<>();
        final String configHash = pwmApplication.getConfig().configurationHash();
        for ( final DatabaseStoredNodeData storedNodeData : nodeDatas.values() )
        {
            final boolean configMatch = configHash.equals( storedNodeData.getConfigHash() );
            final boolean timedOut = isTimedOut( storedNodeData );

            final NodeInfo.NodeState nodeState = isMaster( storedNodeData )
                    ? NodeInfo.NodeState.master
                    : timedOut
                    ? NodeInfo.NodeState.offline
                    : NodeInfo.NodeState.online;

            final Instant startupTime = nodeState == NodeInfo.NodeState.offline
                    ? null
                    : storedNodeData.getStartupTimestamp();


            final NodeInfo nodeInfo = new NodeInfo(
                    storedNodeData.getInstanceID(),
                    storedNodeData.getTimestamp(),
                    startupTime,
                    nodeState,
                    configMatch
            );
            returnObj.put( nodeInfo.getInstanceID(), nodeInfo );
        }

        return Collections.unmodifiableList( new ArrayList<>( returnObj.values() ) );
    }


    private String masterInstanceId( )
    {
        final List<DatabaseStoredNodeData> copiedDatas = new ArrayList<>( nodeDatas.values() );
        if ( copiedDatas.isEmpty() )
        {
            return null;
        }

        String masterID = null;
        Instant eldestRecord = Instant.now();

        for ( final DatabaseStoredNodeData nodeData : copiedDatas )
        {
            if ( !isTimedOut( nodeData ) )
            {
                if ( nodeData.getStartupTimestamp().isBefore( eldestRecord ) )
                {
                    eldestRecord = nodeData.getStartupTimestamp();
                    masterID = nodeData.getInstanceID();
                }
            }
        }
        return masterID;
    }

    @Override
    public boolean isMaster( )
    {
        final String myID = pwmApplication.getInstanceID();
        final String masterID = masterInstanceId();
        return myID.equals( masterID );
    }

    private boolean isMaster( final DatabaseStoredNodeData databaseStoredNodeData )
    {
        final String masterID = masterInstanceId();
        return databaseStoredNodeData.getInstanceID().equals( masterID );
    }

    private String dbKeyForStoredNode( final DatabaseStoredNodeData storedNodeData ) throws PwmUnrecoverableException
    {
        final String instanceID = storedNodeData.getInstanceID();
        final String hash = pwmApplication.getSecureService().hash( instanceID );
        final String truncatedHash = hash.length() > 64
                ? hash.substring( 0, 64 )
                : hash;

        return KEY_PREFIX_NODE + truncatedHash;
    }

    private boolean isTimedOut( final DatabaseStoredNodeData storedNodeData )
    {
        final TimeDuration age = TimeDuration.fromCurrent( storedNodeData.getTimestamp() );
        return age.isLongerThan( settings.getNodeTimeout() );
    }

    private class HeartbeatProcess implements Runnable
    {
        public void run( )
        {
            writeNodeStatus();
            readNodeStatuses();
            purgeOutdatedNodes();
        }

        void writeNodeStatus( )
        {
            try
            {
                final DatabaseStoredNodeData storedNodeData = DatabaseStoredNodeData.makeNew( pwmApplication );
                final String key = dbKeyForStoredNode( storedNodeData );
                final String value = JsonUtil.serialize( storedNodeData );
                databaseService.getAccessor().put( TABLE, key, value );
            }
            catch ( PwmException e )
            {
                final String errorMsg = "error writing database cluster heartbeat: " + e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_DB_UNAVAILABLE, errorMsg );
                lastError = errorInformation;
                LOGGER.error( lastError );
            }
        }

        void readNodeStatuses( )
        {
            try ( ClosableIterator<String> tableIterator = databaseService.getAccessor().iterator( TABLE ) )
            {
                while ( tableIterator.hasNext() )
                {
                    final String dbKey = tableIterator.next();
                    if ( dbKey.startsWith( KEY_PREFIX_NODE ) )
                    {
                        final String rawValueInDb = databaseService.getAccessor().get( TABLE, dbKey );
                        final DatabaseStoredNodeData nodeDataInDb = JsonUtil.deserialize( rawValueInDb, DatabaseStoredNodeData.class );
                        nodeDatas.put( nodeDataInDb.getInstanceID(), nodeDataInDb );
                    }
                }
            }
            catch ( PwmException e )
            {
                final String errorMsg = "error reading database node statuses: " + e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_DB_UNAVAILABLE, errorMsg );
                lastError = errorInformation;
                LOGGER.error( lastError );
            }
        }

        void purgeOutdatedNodes( )
        {
            for ( final DatabaseStoredNodeData storedNodeData : nodeDatas.values() )
            {
                final TimeDuration recordAge = TimeDuration.fromCurrent( storedNodeData.getTimestamp() );
                final String instanceID = storedNodeData.getInstanceID();

                if ( recordAge.isLongerThan( settings.getNodePurgeInterval() ) )
                {
                    // purge outdated records
                    LOGGER.debug( "purging outdated node reference to instanceID '" + instanceID + "'" );

                    try
                    {
                        databaseService.getAccessor().remove( TABLE, dbKeyForStoredNode( storedNodeData ) );
                    }
                    catch ( PwmException e )
                    {
                        final String errorMsg = "error purging outdated node reference: " + e.getMessage();
                        final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_DB_UNAVAILABLE, errorMsg );
                        lastError = errorInformation;
                        LOGGER.error( lastError );
                    }
                    nodeDatas.remove( instanceID );
                }
            }
        }
    }
}
