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

import lombok.Builder;
import lombok.Value;
import password.pwm.PwmApplication;
import password.pwm.bean.DomainID;

import java.io.Serializable;
import java.time.Instant;

@Value
@Builder
public class PublicIntruderRecord implements Serializable
{
    private final IntruderRecordType type;
    private final DomainID domainID;
    private final String subject;
    private final Instant timeStamp;
    private final int attemptCount;
    private final boolean alerted;
    private final LockStatus status;

    enum LockStatus
    {
        watching,
        locked,
    }

    static PublicIntruderRecord fromIntruderRecord( final PwmApplication pwmApplication, final IntruderRecord intruderRecord )
    {
        return PublicIntruderRecord.builder()
                .type( intruderRecord.getType() )
                .subject( intruderRecord.getSubject() )
                .timeStamp( intruderRecord.getTimeStamp() )
                .attemptCount( intruderRecord.getAttemptCount() )
                .status( IntruderSystemService.lockStatus( pwmApplication, intruderRecord ) )
                .domainID( intruderRecord.getDomainID() )
                .alerted( intruderRecord.isAlerted() )
                .build();
    }
}
