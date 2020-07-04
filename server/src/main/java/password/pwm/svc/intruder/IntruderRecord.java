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

package password.pwm.svc.intruder;

import java.io.Serializable;
import java.time.Instant;

public class IntruderRecord implements Serializable
{
    private RecordType type;
    private String subject;
    private Instant timeStamp = Instant.now();
    private int attemptCount = 0;
    private boolean alerted = false;

    IntruderRecord( )
    {
    }

    public IntruderRecord( final RecordType type, final String subject )
    {
        if ( type == null )
        {
            throw new IllegalArgumentException( "type must have a value" );
        }
        if ( subject == null || subject.length() < 1 )
        {
            throw new IllegalArgumentException( "subject must have a value" );
        }
        this.type = type;
        this.subject = subject;
    }

    public RecordType getType( )
    {
        return type;
    }

    public String getSubject( )
    {
        return subject;
    }

    public Instant getTimeStamp( )
    {
        return timeStamp;
    }

    public int getAttemptCount( )
    {
        return attemptCount;
    }

    void incrementAttemptCount( )
    {
        timeStamp = Instant.now();
        attemptCount++;
    }

    void clearAttemptCount( )
    {
        alerted = false;
        attemptCount = 0;
    }

    public boolean isAlerted( )
    {
        return alerted;
    }

    void setAlerted( )
    {
        this.alerted = true;
    }
}
