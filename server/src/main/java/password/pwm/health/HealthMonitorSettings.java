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
