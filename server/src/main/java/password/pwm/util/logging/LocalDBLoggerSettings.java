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

package password.pwm.util.logging;

import lombok.Builder;
import lombok.Data;
import password.pwm.AppProperty;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.util.java.TimeDuration;

import java.io.Serializable;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

@Data
@Builder( toBuilder = true )
public class LocalDBLoggerSettings implements Serializable
{
    @Builder.Default
    static final int MINIMUM_MAXIMUM_EVENTS = 100;

    @Builder.Default
    static final TimeDuration MINIMUM_MAX_AGE = TimeDuration.HOUR;

    @Builder.Default
    private int maxEvents = 1000 * 1000;

    @Builder.Default
    private TimeDuration maxAge = TimeDuration.of( 7, TimeDuration.Unit.DAYS );

    @Builder.Default
    private Set<Flag> flags = Collections.emptySet();

    @Builder.Default
    private int maxBufferSize = 1000;

    @Builder.Default
    private TimeDuration maxBufferWaitTime = TimeDuration.of( 1, TimeDuration.Unit.MINUTES );

    @Builder.Default
    private int maxTrimSize = 501;


    public enum Flag
    {
        DevDebug,
    }

    LocalDBLoggerSettings applyValueChecks()
    {
        return toBuilder()
                .maxEvents( maxEvents < 1 ? 0 : Math.max( MINIMUM_MAXIMUM_EVENTS, maxEvents ) )
                .maxAge( maxAge == null || maxAge.isShorterThan( MINIMUM_MAX_AGE ) ? MINIMUM_MAX_AGE : maxAge )
                .build();
    }

    public static LocalDBLoggerSettings fromConfiguration( final Configuration configuration )
    {
        final Set<Flag> flags = EnumSet.noneOf( Flag.class );
        if ( configuration.isDevDebugMode() )
        {
            flags.add( Flag.DevDebug );
        }
        final int maxEvents = ( int ) configuration.readSettingAsLong( PwmSetting.EVENTS_PWMDB_MAX_EVENTS );
        final long maxAgeMS = 1000 * configuration.readSettingAsLong( PwmSetting.EVENTS_PWMDB_MAX_AGE );
        final TimeDuration maxAge = TimeDuration.of( maxAgeMS, TimeDuration.Unit.MILLISECONDS );
        final int maxBufferSize = Integer.parseInt( configuration.readAppProperty( AppProperty.LOCALDB_LOGWRITER_BUFFER_SIZE ) );
        final TimeDuration maxBufferWaitTime = TimeDuration.of(
                Long.parseLong( configuration.readAppProperty( AppProperty.LOCALDB_LOGWRITER_MAX_BUFFER_WAIT_MS ) ),
                TimeDuration.Unit.MILLISECONDS
        );
        final int maxTrimSize = Integer.parseInt( configuration.readAppProperty( AppProperty.LOCALDB_LOGWRITER_MAX_TRIM_SIZE ) );

        return LocalDBLoggerSettings.builder()
                .maxEvents( maxEvents )
                .maxAge( maxAge )
                .flags( flags )
                .maxBufferSize( maxBufferSize )
                .maxBufferWaitTime( maxBufferWaitTime )
                .maxTrimSize( maxTrimSize )
                .build().applyValueChecks();
    }
}
