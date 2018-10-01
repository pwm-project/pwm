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

package password.pwm.health;

import lombok.Builder;
import lombok.Value;
import password.pwm.AppProperty;
import password.pwm.config.Configuration;
import password.pwm.util.java.TimeDuration;

import java.io.Serializable;

@Value
@Builder
class HealthMonitorSettings implements Serializable
{
    private TimeDuration nominalCheckInterval;
    private TimeDuration minimumCheckInterval;
    private TimeDuration maximumRecordAge;
    private TimeDuration maximumForceCheckWait;

    static HealthMonitorSettings fromConfiguration( final Configuration config )
    {
        return HealthMonitorSettings.builder()
                .nominalCheckInterval( TimeDuration.of( Long.parseLong( config.readAppProperty( AppProperty.HEALTHCHECK_NOMINAL_CHECK_INTERVAL ) ), TimeDuration.Unit.SECONDS ) )
                .minimumCheckInterval( TimeDuration.of( Long.parseLong( config.readAppProperty( AppProperty.HEALTHCHECK_MIN_CHECK_INTERVAL ) ), TimeDuration.Unit.SECONDS ) )
                .maximumRecordAge( TimeDuration.of( Long.parseLong( config.readAppProperty( AppProperty.HEALTHCHECK_MAX_RECORD_AGE ) ), TimeDuration.Unit.SECONDS ) )
                .maximumForceCheckWait( TimeDuration.of( Long.parseLong( config.readAppProperty( AppProperty.HEALTHCHECK_MAX_FORCE_WAIT ) ), TimeDuration.Unit.SECONDS ) )
                .build();
    }
}
