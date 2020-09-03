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

package password.pwm.svc.pwnotify;

import lombok.Builder;
import lombok.Value;
import password.pwm.AppProperty;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.util.java.TimeDuration;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Value
@Builder
class PwNotifySettings implements Serializable
{
    private final List<Integer> notificationIntervals;
    private final TimeDuration maximumSkipWindow;
    private final TimeDuration zuluOffset;
    private final int maxLdapSearchSize;
    private final TimeDuration searchTimeout;
    private final int batchCount;
    private final BigDecimal batchTimeMultiplier;

    static PwNotifySettings fromConfiguration( final Configuration configuration )
    {
        final PwNotifySettingsBuilder builder = PwNotifySettings.builder();
        {
            final List<Integer> timeDurations = new ArrayList<>(  );
            final List<String> stringValues = configuration.readSettingAsStringArray( PwmSetting.PW_EXPY_NOTIFY_INTERVAL );
            for ( final String value : stringValues )
            {
                timeDurations.add( Integer.parseInt( value ) );
            }
            Collections.sort( timeDurations );
            builder.notificationIntervals( Collections.unmodifiableList( timeDurations ) );
        }

        builder.searchTimeout( TimeDuration.of( Long.parseLong( configuration.readAppProperty( AppProperty.REPORTING_LDAP_SEARCH_TIMEOUT_MS ) ), TimeDuration.Unit.MILLISECONDS ) );
        builder.zuluOffset( TimeDuration.of( configuration.readSettingAsLong( PwmSetting.PW_EXPY_NOTIFY_JOB_OFFSET ), TimeDuration.Unit.SECONDS ) );
        builder.batchCount( Integer.parseInt( configuration.readAppProperty( AppProperty.PWNOTIFY_BATCH_COUNT ) ) );
        builder.maxLdapSearchSize( Integer.parseInt( configuration.readAppProperty( AppProperty.PWNOTIFY_MAX_LDAP_SEARCH_SIZE ) ) );
        builder.batchTimeMultiplier( new BigDecimal( configuration.readAppProperty( AppProperty.PWNOTIFY_BATCH_DELAY_TIME_MULTIPLIER ) ) );
        builder.maximumSkipWindow( TimeDuration.of(
                Long.parseLong( configuration.readAppProperty( AppProperty.PWNOTIFY_MAX_SKIP_RERUN_WINDOW_SECONDS ) ), TimeDuration.Unit.SECONDS ) );

        return builder.build();
    }
}
