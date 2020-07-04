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

package password.pwm.svc.report;

import lombok.Builder;
import lombok.Value;
import password.pwm.error.ErrorInformation;
import password.pwm.util.java.TimeDuration;

import java.io.Serializable;
import java.time.Instant;

@Value
@Builder( toBuilder = true )
public class ReportStatusInfo implements Serializable
{
    @Builder.Default
    private TimeDuration jobDuration = TimeDuration.ZERO;

    private Instant startDate;
    private Instant finishDate;
    private boolean reportComplete;
    private int count;
    private int errors;
    private ErrorInformation lastError;
    private String settingsHash;

    @Builder.Default
    private ReportEngineProcess currentProcess = ReportEngineProcess.None;

    public enum ReportEngineProcess
    {
        RollOver( "Initializing" ),
        ReadData( "Process LDAP Records" ),
        None( "Idle" ),
        SearchLDAP( "Searching LDAP" ),
        Clear( "Clearing Records" ),;

        private final String label;

        ReportEngineProcess( final String label )
        {
            this.label = label;
        }

        public String getLabel( )
        {
            return label;
        }
    }
}
