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

import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.ClosableIterator;
import password.pwm.util.java.StatisticCounterBundle;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.localdb.LocalDBException;

import java.util.Optional;

interface IntruderRecordStore
{
    StatisticCounterBundle<DebugKeys> getStats();

    Optional<IntruderRecord> read( String key ) throws PwmUnrecoverableException;

    void write( String key, IntruderRecord record ) throws PwmOperationalException, PwmUnrecoverableException;

    ClosableIterator<IntruderRecord> iterator( ) throws PwmUnrecoverableException;

    void cleanup( TimeDuration maxRecordAge ) throws LocalDBException;

    enum DebugKeys
    {
        reads,
        writes,
        cleanupCycles,
        cleanupExamines,
        cleanupRemoves,
    }
}
