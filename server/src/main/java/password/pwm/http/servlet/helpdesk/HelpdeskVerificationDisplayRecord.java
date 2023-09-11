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

package password.pwm.http.servlet.helpdesk;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;

record HelpdeskVerificationDisplayRecord(
        Instant timestamp,
        String profile,
        String username,
        String method
)
        implements Comparable<HelpdeskVerificationDisplayRecord>

{
    private static final Comparator<HelpdeskVerificationDisplayRecord> COMPARATOR = Comparator.comparing(
                    HelpdeskVerificationDisplayRecord::username )
            .thenComparing( HelpdeskVerificationDisplayRecord::profile )
            .thenComparing( HelpdeskVerificationDisplayRecord::method )
            .thenComparing( HelpdeskVerificationDisplayRecord::timestamp );

    HelpdeskVerificationDisplayRecord(
            final Instant timestamp,
            final String profile,
            final String username,
            final String method
    )
    {
        this.timestamp = Objects.requireNonNull( timestamp );
        this.profile = Objects.requireNonNull( profile );
        this.username = Objects.requireNonNull( username );
        this.method = Objects.requireNonNull( method );
    }

    @Override
    public int compareTo( @NotNull final HelpdeskVerificationDisplayRecord o )
    {
        return COMPARATOR.compare( this, o );
    }
}
