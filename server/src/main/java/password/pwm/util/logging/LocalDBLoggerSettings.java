/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
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
import java.util.HashSet;
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
        final Set<Flag> flags = new HashSet<>();
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
