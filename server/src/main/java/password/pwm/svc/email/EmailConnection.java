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

import password.pwm.util.java.AtomicLoopIntIncrementer;
import password.pwm.util.logging.PwmLogger;

import jakarta.mail.MessagingException;
import jakarta.mail.Transport;
import java.time.Instant;

class EmailConnection
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( EmailConnection.class );

    private final EmailServer emailServer;
    private final Transport transport;
    private final AtomicLoopIntIncrementer sentItems = new AtomicLoopIntIncrementer( );
    private final Instant startTime = Instant.now();
    private final String id;

    private static final AtomicLoopIntIncrementer ID_COUNTER = new AtomicLoopIntIncrementer();

    EmailConnection( final EmailServer emailServer, final Transport transport )
    {
        this.emailServer = emailServer;
        this.transport = transport;
        this.id = String.valueOf( ID_COUNTER.next() );
    }

    public EmailServer getEmailServer()
    {
        return emailServer;
    }

    public int getSentItems()
    {
        return sentItems.get();
    }

    public Transport getTransport()
    {
        return transport;
    }

    public void incrementSentItems()
    {
        sentItems.next();
    }

    public Instant getStartTime()
    {
        return startTime;
    }

    public String getId()
    {
        return id;
    }

    public void close()
    {
        if ( getTransport() != null )
        {
            try
            {
                 getTransport().close();
            }
            catch ( final MessagingException e )
            {
               LOGGER.debug( () -> "error closing connection: " + e.getMessage() );
            }
        }
    }
}
