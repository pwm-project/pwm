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
public class PwNotifySettings implements Serializable
{
    private final List<Integer> notificationIntervals;
    private final TimeDuration maximumSkipWindow;
    private final TimeDuration zuluOffset;
    private final int maxLdapSearchSize;
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

        builder.zuluOffset( TimeDuration.of( configuration.readSettingAsLong( PwmSetting.PW_EXPY_NOTIFY_JOB_OFFSET ), TimeDuration.Unit.SECONDS ) );
        builder.batchCount( Integer.parseInt( configuration.readAppProperty( AppProperty.PWNOTIFY_BATCH_COUNT ) ) );
        builder.maxLdapSearchSize( Integer.parseInt( configuration.readAppProperty( AppProperty.PWNOTIFY_MAX_LDAP_SEARCH_SIZE ) ) );
        builder.batchTimeMultiplier( new BigDecimal( configuration.readAppProperty( AppProperty.PWNOTIFY_BATCH_DELAY_TIME_MULTIPLIER ) ) );
        builder.maximumSkipWindow( TimeDuration.of(
                Long.parseLong( configuration.readAppProperty( AppProperty.PWNOTIFY_MAX_SKIP_RERUN_WINDOW_SECONDS ) ), TimeDuration.Unit.SECONDS ) );
        return builder.build();
    }
}
