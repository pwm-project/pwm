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

import lombok.Value;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.util.java.TimeDuration;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Value
public class PwNotifySettings implements Serializable
{
    private final List<Integer> notificationIntervals;
    private final TimeDuration maximumSkipWindow;
    private final int maxLdapSearchSize;

    static PwNotifySettings fromConfiguration( final Configuration configuration )
    {
        final List<Integer> timeDurations = new ArrayList<>(  );
        {
            final List<String> stringValues = configuration.readSettingAsStringArray( PwmSetting.PW_EXPY_NOTIFY_INTERVAL );
            for ( final String value : stringValues )
            {
                timeDurations.add( Integer.parseInt( value ) );
            }
            Collections.sort( timeDurations );
        }
        final TimeDuration maxSkipWindow = new TimeDuration( 24, TimeUnit.HOURS );
        final int maxLdapSearchSize = 1_000_000;

        return new PwNotifySettings( Collections.unmodifiableList( timeDurations ), maxSkipWindow, maxLdapSearchSize );
    }
}
