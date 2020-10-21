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

package password.pwm.svc.event;

import password.pwm.util.secure.PwmRandom;

import java.io.Serializable;
import java.time.Instant;

public abstract class AuditRecord implements Serializable
{
    protected AuditEvent.Type type;
    protected AuditEvent eventCode;
    protected String guid;
    protected Instant timestamp = Instant.now();
    protected String message;
    protected String narrative;
    protected final String xdasTaxonomy;
    protected final String xdasOutcome;


    protected AuditRecord(
            final Instant timestamp,
            final AuditEvent eventCode,
            final String message
    )
    {
        this.type = eventCode.getType();
        this.eventCode = eventCode;
        this.message = message;

        this.timestamp = timestamp;
        this.guid = PwmRandom.getInstance().randomUUID().toString();
        this.xdasOutcome = eventCode.getXdasOutcome();
        this.xdasTaxonomy = eventCode.getXdasTaxonomy();
    }


    protected AuditRecord( final AuditEvent eventCode, final String message )
    {
        this( Instant.now(), eventCode, message );
    }

    public AuditEvent.Type getType( )
    {
        return type;
    }

    public AuditEvent getEventCode( )
    {
        return eventCode;
    }

    public Instant getTimestamp( )
    {
        return timestamp;
    }

    public String getMessage( )
    {
        return message;
    }

    public String getGuid( )
    {
        return guid;
    }

    public String getNarrative( )
    {
        return narrative;
    }

    public String getXdasTaxonomy( )
    {
        return xdasTaxonomy;
    }

    public String getXdasOutcome( )
    {
        return xdasOutcome;
    }
}
