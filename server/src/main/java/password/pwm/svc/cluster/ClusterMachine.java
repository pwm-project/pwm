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
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.JavaHelper;
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

class ClusterMachine
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( ClusterMachine.class );

    private final PwmApplication pwmApplication;
    private final ScheduledExecutorService executorService;
    private final ClusterDataServiceProvider clusterDataServiceProvider;

    private ErrorInformation lastError;

    private final Map<String, StoredNodeData> knownNodes = new ConcurrentHashMap<>();

    private final ClusterSettings settings;
    private final ClusterStatistics clusterStatistics = new ClusterStatistics();

    ClusterMachine(
            final PwmApplication pwmApplication,
            final ClusterDataServiceProvider clusterDataServiceProvider,
            final ClusterSettings clusterSettings
    )
    {
        this.pwmApplication = pwmApplication;
        this.clusterDataServiceProvider = clusterDataServiceProvider;
        this.settings = clusterSettings;

        this.executorService = JavaHelper.makeSingleThreadExecutorService( pwmApplication, ClusterMachine.class );

        final long intervalSeconds = settings.getHeartbeatInterval().getTotalSeconds();

        this.executorService.scheduleAtFixedRate(
                new HeartbeatProcess(),
                intervalSeconds,
                intervalSeconds,
                TimeUnit.SECONDS
        );
    }

    public void close( )
    {
        JavaHelper.closeAndWaitExecutor( executorService, new TimeDuration( 1, TimeUnit.SECONDS ) );
    }


    public List<NodeInfo> nodes( ) throws PwmUnrecoverableException
    {
        final Map<String, NodeInfo> returnObj = new TreeMap<>();
        final String configHash = pwmApplication.getConfig().configurationHash();
        for ( final StoredNodeData storedNodeData : knownNodes.values() )
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
        final List<StoredNodeData> copiedDatas = new ArrayList<>( knownNodes.values() );
        if ( copiedDatas.isEmpty() )
        {
            return null;
        }

        String masterID = null;
        Instant eldestRecord = Instant.now();

        for ( final StoredNodeData nodeData : copiedDatas )
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

    public boolean isMaster( )
    {
        final String myID = pwmApplication.getInstanceID();
        final String masterID = masterInstanceId();
        return myID.equals( masterID );
    }

    private boolean isMaster( final StoredNodeData storedNodeData )
    {
        final String masterID = masterInstanceId();
        return storedNodeData.getInstanceID().equals( masterID );
    }

    private boolean isTimedOut( final StoredNodeData storedNodeData )
    {
        final TimeDuration age = TimeDuration.fromCurrent( storedNodeData.getTimestamp() );
        return age.isLongerThan( settings.getNodeTimeout() );
    }

    public ErrorInformation getLastError( )
    {
        return lastError;
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
                final StoredNodeData storedNodeData = StoredNodeData.makeNew( pwmApplication );
                clusterDataServiceProvider.writeNodeStatus( storedNodeData );
                clusterStatistics.getClusterWrites().incrementAndGet();
            }
            catch ( PwmException e )
            {
                final String errorMsg = "error writing database cluster heartbeat: " + e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_CLUSTER_SERVICE_ERROR, errorMsg );
                lastError = errorInformation;
                LOGGER.error( lastError );
            }
        }

        void readNodeStatuses( )
        {
            try
            {
                final Map<String, StoredNodeData> readNodeData = clusterDataServiceProvider.readStoredData();
                knownNodes.putAll( readNodeData );
                clusterStatistics.getClusterReads().incrementAndGet();
            }
            catch ( PwmException e )
            {
                final String errorMsg = "error reading node statuses: " + e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_CLUSTER_SERVICE_ERROR, errorMsg );
                lastError = errorInformation;
                LOGGER.error( lastError );
            }
        }

        void purgeOutdatedNodes( )
        {
            try
            {
                final int purges = clusterDataServiceProvider.purgeOutdatedNodes( settings.getNodePurgeInterval() );
                clusterStatistics.getNodePurges().addAndGet( purges );
            }
            catch ( PwmException e )
            {
                final String errorMsg = "error purging outdated node reference: " + e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_CLUSTER_SERVICE_ERROR, errorMsg );
                lastError = errorInformation;
                LOGGER.error( lastError );
            }
        }
    }

    public ClusterStatistics getClusterStatistics( )
    {
        return clusterStatistics;
    }
}
