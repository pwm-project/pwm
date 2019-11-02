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

package password.pwm.config.stored;

import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class ComparingChangeLog implements ConfigChangeLog
{
    public static final PwmLogger LOGGER = PwmLogger.forClass( ComparingChangeLog.class );

    private final StoredConfiguration originalConfiguration;
    private final StoredConfiguration modifiedConfiguration;

    public ComparingChangeLog(
            final StoredConfiguration originalConfiguration,
            final StoredConfiguration modifiedConfiguration
    )
    {
        this.originalConfiguration = originalConfiguration;
        this.modifiedConfiguration = modifiedConfiguration;
    }

    @Override
    public Set<StoredConfigItemKey> changedValues ()
    {
        final Instant startTime = Instant.now();

        final Set<StoredConfigItemKey> interestedReferences = new HashSet<>();
        interestedReferences.addAll( StoredConfigurationUtil.modifiedItems( originalConfiguration ) );
        interestedReferences.addAll( StoredConfigurationUtil.modifiedItems( modifiedConfiguration ) );

        final Set<StoredConfigItemKey> deltaReferences = interestedReferences.parallelStream()
                .filter( reference ->
                        {
                            final String hash = StoredConfigurationUtil.valueForReference( originalConfiguration, reference ).valueHash();
                            final String hash2 = StoredConfigurationUtil.valueForReference( modifiedConfiguration, reference ).valueHash();
                            return !Objects.equals( hash, hash2 );
                        }
                ).collect( Collectors.toSet() );

        LOGGER.trace( () -> "generated changeLog items via compare in " + TimeDuration.compactFromCurrent( startTime ) );

        return Collections.unmodifiableSet( deltaReferences );
    }

    @Override
    public boolean isModified()
            throws PwmUnrecoverableException
    {
        return !changedValues().isEmpty();
    }

}
