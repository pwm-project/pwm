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

import lombok.Value;
import password.pwm.PwmApplication;
import password.pwm.error.PwmException;
import password.pwm.error.PwmOperationalException;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBException;

import java.time.Instant;
import java.util.Iterator;

public interface AuditVault
{

    void init( PwmApplication pwmApplication, LocalDB localDB, Settings settings ) throws LocalDBException, PwmException;

    void close( );

    int size( );

    Instant oldestRecord( );

    Iterator<AuditRecord> readVault( );

    String sizeToDebugString( );

    void add( AuditRecord record ) throws PwmOperationalException;

    @Value
    class Settings
    {
        private final long maxRecordCount;
        private final TimeDuration maxRecordAge;
    }
}
