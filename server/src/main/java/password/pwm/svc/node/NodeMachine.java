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
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.PwmScheduler;
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
import java.util.concurrent.ExecutorService;

class NodeMachine
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( NodeMachine.class );

    private final PwmApplication pwmApplication;
    private final ExecutorService executorService;
    private final NodeDataServiceProvider clusterDataServiceProvider;

    private ErrorInformation lastError;

    private final Map<String, StoredNodeData> knownNodes = new ConcurrentHashMap<>();

    private final NodeServiceSettings settings;
    private final NodeServiceStatistics nodeServiceStatistics = new NodeServiceStatistics();

    NodeMachine(
            final PwmApplication pwmApplication,
            final NodeDataServiceProvider clusterDataServiceProvider,
            final NodeServiceSettings nodeServiceSettings
    )
    {
        this.pwmApplication = pwmApplication;
        this.clusterDataServiceProvider = clusterDataServiceProvider;
        this.settings = nodeServiceSettings;

        this.executorService = PwmScheduler.makeBackgroundExecutor( pwmApplication, NodeMachine.class );

        pwmApplication.getPwmScheduler().scheduleFixedRateJob( new HeartbeatProcess(), executorService, settings.getHeartbeatInterval(), settings.getHeartbeatInterval() );
    }

    public void close( )
    {
        JavaHelper.closeAndWaitExecutor( executorService, TimeDuration.SECOND );
    }


    public List<NodeInfo> nodes( ) throws PwmUnrecoverableException
    {
        final Map<String, NodeInfo> returnObj = new TreeMap<>();
        final String configHash = pwmApplication.getConfig().configurationHash( pwmApplication.getSecureService() );
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
                nodeServiceStatistics.getClusterWrites().incrementAndGet();
            }
            catch ( final PwmException e )
            {
                final String errorMsg = "error writing node service heartbeat: " + e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_NODE_SERVICE_ERROR, errorMsg );
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
                nodeServiceStatistics.getClusterReads().incrementAndGet();
            }
            catch ( final PwmException e )
            {
                final String errorMsg = "error reading node statuses: " + e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_NODE_SERVICE_ERROR, errorMsg );
                lastError = errorInformation;
                LOGGER.error( lastError );
            }
        }

        void purgeOutdatedNodes( )
        {
            try
            {
                final int purges = clusterDataServiceProvider.purgeOutdatedNodes( settings.getNodePurgeInterval() );
                nodeServiceStatistics.getNodePurges().addAndGet( purges );
            }
            catch ( final PwmException e )
            {
                final String errorMsg = "error purging outdated node reference: " + e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_NODE_SERVICE_ERROR, errorMsg );
                lastError = errorInformation;
                LOGGER.error( lastError );
            }
        }
    }

    public NodeServiceStatistics getNodeServiceStatistics( )
    {
        return nodeServiceStatistics;
    }
}
