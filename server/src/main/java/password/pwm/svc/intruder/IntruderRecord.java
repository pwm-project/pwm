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

package password.pwm.svc.intruder;

import lombok.Data;
import password.pwm.bean.DomainID;
import password.pwm.util.java.StringUtil;

import java.time.Instant;

@Data
public class IntruderRecord
{
    private IntruderRecordType type;
    private DomainID domainID;
    private String subject;
    private Instant timeStamp = Instant.now();
    private int attemptCount = 0;
    private boolean alerted = false;

    public IntruderRecord( final DomainID domainID, final IntruderRecordType type, final String subject )
    {
        if ( type == null )
        {
            throw new IllegalArgumentException( "type must have a value" );
        }
        if ( StringUtil.isEmpty( subject ) )
        {
            throw new IllegalArgumentException( "subject must have a value" );
        }

        this.type = type;
        this.domainID = domainID;
        this.subject = subject;
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
}
