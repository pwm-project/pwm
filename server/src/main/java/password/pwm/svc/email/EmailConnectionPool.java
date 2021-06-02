/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2020 The PWM Project
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

package password.pwm.svc.email;

import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.AtomicLoopIntIncrementer;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import jakarta.mail.Transport;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class EmailConnectionPool
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( EmailConnectionPool.class );

    private final Set<EmailConnection> connections = Collections.newSetFromMap( new ConcurrentHashMap<>() );
    private final Lock lock = new ReentrantLock();

    private final EmailServiceSettings settings;
    private final List<EmailServer> servers;

    private final AtomicInteger activeConnectionCounter = new AtomicInteger();
    private final AtomicBoolean closed = new AtomicBoolean( false );
    private final AtomicLoopIntIncrementer serverIncrementer;

    public static EmailConnectionPool emptyConnectionPool()
    {
        return new EmailConnectionPool( Collections.emptyList(), EmailServiceSettings.builder().build() );
    }

    public EmailConnectionPool(
            final List<EmailServer> servers,
            final EmailServiceSettings settings )
    {
        this.servers = Collections.unmodifiableList( new ArrayList<>( servers ) );
        this.serverIncrementer = AtomicLoopIntIncrementer.builder().ceiling( servers.size() ).build();
        this.settings = settings;
    }

    public int idleConnectionCount()
    {
        return connections.size();
    }

    public int activeConnectionCount()
    {
        return activeConnectionCounter.get();
    }

    public List<EmailServer> getServers()
    {
        return servers;
    }

    public EmailConnection getConnection()
            throws PwmUnrecoverableException
    {
        if ( closed.get() )
        {
            throw new PwmUnrecoverableException( PwmError.ERROR_SERVICE_NOT_AVAILABLE, "email connection pool is closed" );
        }

        lock.lock();
        try
        {
            while  ( !connections.isEmpty() )
            {
                final EmailConnection emailConnection = connections.iterator().next();
                connections.remove( emailConnection );
                if ( connectionStillValid( emailConnection ) )
                {
                    activeConnectionCounter.incrementAndGet();
                    return emailConnection;
                }
                emailConnection.close();
            }
            final Instant startTime = Instant.now();
            final EmailConnection emailConnection = getSmtpTransport();
            LOGGER.trace( () -> "created new email connection " + emailConnection.getId()
                            + " to " + emailConnection.getEmailServer().getId(),
                    () -> TimeDuration.fromCurrent( startTime ) );
            activeConnectionCounter.incrementAndGet();
            return emailConnection;
        }
        finally
        {
            lock.unlock();
        }
    }

    public void returnEmailConnection( final EmailConnection emailConnection )
    {
        lock.lock();
        try
        {

            if ( connections.add( emailConnection ) )
            {
                activeConnectionCounter.decrementAndGet();
            }
            else
            {
                LOGGER.warn( () -> "connection " + emailConnection.getId() + "returned but was already in oool" );
            }
        }
        finally
        {
            lock.unlock();
        }
    }

    private boolean connectionStillValid( final EmailConnection emailConnection )
    {
        if ( emailConnection.getSentItems() >= settings.getConnectionSendItemLimit() )
        {
            LOGGER.trace( () -> "email connection " + emailConnection.getId() + " has sent " + emailConnection.getSentItems() + " and will be retired" );
            return false;
        }

        final TimeDuration connectionAge = TimeDuration.fromCurrent( emailConnection.getStartTime() );
        if ( connectionAge.isLongerThan( settings.getConnectionSendItemDuration() ) )
        {
            LOGGER.trace( () -> "email connection " + emailConnection.getId() + " has lived " + connectionAge.asCompactString() + " and will be retired" );
            return false;
        }

        if ( !emailConnection.getTransport().isConnected() )
        {
            LOGGER.trace( () -> "email connection " + emailConnection.getId() + " is no longer connected " + connectionAge.asCompactString() + " and will be retired" );
            return false;

        }

        return true;
    }

    private EmailConnection getSmtpTransport( )
            throws PwmUnrecoverableException
    {
        final int serverCount = servers.size();

        // the global server incrementer rotates the server list by 1 offset each attempt to get an smtp transport.
        int nextSlot = serverIncrementer.next();

        for ( int i = 0; i < serverCount; i++ )
        {
            if ( nextSlot >= serverCount )
            {
                nextSlot -= serverCount;
            }

            final EmailServer server = servers.get( nextSlot );
            try
            {
                final Transport transport = EmailServerUtil.makeSmtpTransport( server );
                server.getConnectionStats().increment( EmailServer.ServerStat.newConnections );
                return new EmailConnection( server, transport );
            }
            catch ( final Exception e )
            {
                final String exceptionMsg = JavaHelper.readHostileExceptionMessage( e );
                final String msg = "unable to connect to email server '" + server.toDebugString() + "', error: " + exceptionMsg;
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_SERVICE_UNREACHABLE, msg );
                server.getConnectionStats().increment( EmailServer.ServerStat.failedConnections );
                LOGGER.warn( errorInformation::toDebugStr );
            }

            nextSlot++;
        }

        throw PwmUnrecoverableException.newException( PwmError.ERROR_SERVICE_UNREACHABLE, "unable to reach any configured email server" );
    }


    public void close()
    {
        closed.set( true );
        lock.lock();
        try
        {
            for ( final EmailConnection emailConnection : connections )
            {
                emailConnection.close();
            }
            connections.clear();
        }
        finally
        {
            lock.unlock();
        }

    }
}
