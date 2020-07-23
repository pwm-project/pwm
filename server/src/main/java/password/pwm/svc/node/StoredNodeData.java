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

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import password.pwm.PwmApplication;
import password.pwm.error.PwmUnrecoverableException;

import java.io.Serializable;
import java.time.Instant;

@Getter
@AllArgsConstructor( access = AccessLevel.PRIVATE )
class StoredNodeData implements Serializable
{
    private Instant timestamp;
    private Instant startupTimestamp;
    private String instanceID;
    private String guid;
    private String configHash;

    static StoredNodeData makeNew( final PwmApplication pwmApplication )
            throws PwmUnrecoverableException
    {
        return new StoredNodeData(
                Instant.now(),
                pwmApplication.getStartupTime(),
                pwmApplication.getInstanceID(),
                pwmApplication.getRuntimeNonce(),
                pwmApplication.getConfig().configurationHash( pwmApplication.getSecureService() )
        );
    }
}
