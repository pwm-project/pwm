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

import password.pwm.PwmConstants;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.StatisticCounterBundle;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

class NodeMachine
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( NodeMachine.class );

    private final NodeService nodeService;
    private final NodeDataServiceProvider clusterDataServiceProvider;

    private ErrorInformation lastError;

    private final Map<String, StoredNodeData> knownNodes = new ConcurrentHashMap<>();

    private final NodeServiceSettings settings;

    private final StatisticCounterBundle<Stats> nodeServiceStatistics = new StatisticCounterBundle<>( Stats.class );

    private enum Stats
    {
        clusterWrites,
        clusterReads,
        nodePurges
    }

    NodeMachine(
            final NodeService nodeService,
            final NodeDataServiceProvider clusterDataServiceProvider,
            final NodeServiceSettings nodeServiceSettings
    )
    {
        this.nodeService = nodeService;
        this.clusterDataServiceProvider = clusterDataServiceProvider;
        this.settings = nodeServiceSettings;
    }

    public void close( )
    {
    }


    public List<NodeInfo> nodes( ) throws PwmUnrecoverableException
    {
        final Map<String, NodeInfo> returnObj = new TreeMap<>();
        final String configHash = nodeService.getPwmApp().getConfig().getValueHash();
        for ( final StoredNodeData storedNodeData : knownNodes.values() )
        {
            final boolean configMatch = configHash.equals( storedNodeData.configHash() );
            final boolean timedOut = isTimedOut( storedNodeData );

            final NodeState nodeState = isMaster( storedNodeData )
                    ? NodeState.master
                    : timedOut
                            ? NodeState.offline
                            : NodeState.online;

            final Instant startupTime = nodeState == NodeState.offline
                    ? null
                    : storedNodeData.startupTimestamp();


            final NodeInfo nodeInfo = new NodeInfo(
                    storedNodeData.instanceID(),
                    storedNodeData.timestamp(),
                    startupTime,
                    nodeState,
                    configMatch
            );
            returnObj.put( nodeInfo.instanceID(), nodeInfo );
        }

        return List.copyOf( returnObj.values() );
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
                if ( nodeData.startupTimestamp().isBefore( eldestRecord ) )
                {
                    eldestRecord = nodeData.startupTimestamp();
                    masterID = nodeData.instanceID();
                }
            }
        }
        return masterID;
    }

    public boolean isMaster( )
    {
        final String myID = nodeService.getPwmApp().getInstanceID();
        final String masterID = masterInstanceId();
        return myID.equals( masterID );
    }

    private boolean isMaster( final StoredNodeData storedNodeData )
    {
        final String masterID = masterInstanceId();
        return storedNodeData.instanceID().equals( masterID );
    }

    private boolean isTimedOut( final StoredNodeData storedNodeData )
    {
        final TimeDuration age = TimeDuration.fromCurrent( storedNodeData.timestamp() );
        return age.isLongerThan( settings.nodeTimeout() );
    }

    public ErrorInformation getLastError( )
    {
        return lastError;
    }

    protected HeartbeatProcess getHeartbeatProcess()
    {
        return new HeartbeatProcess();
    }

    private class HeartbeatProcess implements Runnable
    {
        @Override
        public void run( )
        {
            try
            {
                writeNodeStatus();
                readNodeStatuses();
                purgeOutdatedNodes();
                lastError = null;
            }
            catch ( final PwmUnrecoverableException e )
            {
                lastError = e.getErrorInformation();
                LOGGER.error( nodeService.getSessionLabel(), e.getErrorInformation() );
            }
        }

        void writeNodeStatus( )
                throws PwmUnrecoverableException
        {
            try
            {
                final StoredNodeData storedNodeData = StoredNodeData.makeNew( nodeService.getPwmApp() );
                clusterDataServiceProvider.writeNodeStatus( storedNodeData );
                nodeServiceStatistics.increment( Stats.clusterWrites );
            }
            catch ( final PwmException e )
            {
                final String errorMsg = "error writing node service heartbeat: " + e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_NODE_SERVICE_ERROR, errorMsg );
                throw new PwmUnrecoverableException( errorInformation );
            }
        }

        void readNodeStatuses( )
                throws PwmUnrecoverableException
        {
            try
            {
                final Map<String, StoredNodeData> readNodeData = clusterDataServiceProvider.readStoredData();
                knownNodes.putAll( readNodeData );
                nodeServiceStatistics.increment( Stats.clusterReads );
            }
            catch ( final PwmException e )
            {
                final String errorMsg = "error reading node statuses: " + e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_NODE_SERVICE_ERROR, errorMsg );
                throw new PwmUnrecoverableException( errorInformation );
            }
        }

        void purgeOutdatedNodes( )
                throws PwmUnrecoverableException
        {
            try
            {
                final int purges = clusterDataServiceProvider.purgeOutdatedNodes( settings.nodePurgeInterval() );
                nodeServiceStatistics.increment( Stats.nodePurges, purges );
            }
            catch ( final PwmException e )
            {
                final String errorMsg = "error purging outdated node reference: " + e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_NODE_SERVICE_ERROR, errorMsg );
                throw new PwmUnrecoverableException( errorInformation );
            }
        }
    }

    public Map<String, String> getNodeServiceStatistics( )
    {
        return nodeServiceStatistics.debugStats( PwmConstants.DEFAULT_LOCALE );
    }
}
