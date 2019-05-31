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

package password.pwm.i18n;

import org.junit.Assert;
import org.junit.Test;
import password.pwm.PwmConstants;
import password.pwm.svc.stats.AvgStatistic;
import password.pwm.svc.stats.EpsStatistic;
import password.pwm.svc.stats.Statistic;

import java.util.Collections;
import java.util.HashSet;
import java.util.ResourceBundle;
import java.util.Set;

public class AdminPropertyKeysTest
{

    @Test
    public void testStatisticsLabelKeys()
    {
        final ResourceBundle resourceBundle = ResourceBundle.getBundle( password.pwm.i18n.Admin.class.getName(), PwmConstants.DEFAULT_LOCALE );

        final Set<String> expectedKeys = new HashSet<>();

        for ( final Statistic statistic : Statistic.values() )
        {
            final String[] keys = new String[] {
                    password.pwm.i18n.Admin.STATISTICS_DESCRIPTION_PREFIX + statistic.getKey(),
                    password.pwm.i18n.Admin.STATISTICS_LABEL_PREFIX + statistic.getKey(),
            };
            Collections.addAll( expectedKeys, keys );
        }
        for ( final AvgStatistic statistic : AvgStatistic.values() )
        {
            final String[] keys = new String[] {
                    password.pwm.i18n.Admin.STATISTICS_DESCRIPTION_PREFIX + statistic.getKey(),
                    password.pwm.i18n.Admin.STATISTICS_LABEL_PREFIX + statistic.getKey(),
            };
            Collections.addAll( expectedKeys, keys );
        }

        for ( final String key : expectedKeys )
        {
            Assert.assertTrue(
                    "Admin.properties missing record for " + key,
                    resourceBundle.containsKey( key ) );
        }

        final Set<String> extraKeys = new HashSet<>( resourceBundle.keySet() );
        extraKeys.removeAll( expectedKeys );

        for ( final String key : extraKeys )
        {
            if ( key.startsWith( password.pwm.i18n.Admin.STATISTICS_DESCRIPTION_PREFIX )
                    || key.startsWith( password.pwm.i18n.Admin.STATISTICS_LABEL_PREFIX ) )
            {

                Assert.fail( "unexpected key in Admin.properties file: " + key );
            }
        }
    }


    @Test
    public void testDpsStatisticsLabelKeys()
    {
        final ResourceBundle resourceBundle = ResourceBundle.getBundle( password.pwm.i18n.Admin.class.getName(), PwmConstants.DEFAULT_LOCALE );

        final Set<String> expectedKeys = new HashSet<>();

        for ( final EpsStatistic statistic : EpsStatistic.values() )
        {
            final String key = Admin.EPS_STATISTICS_LABEL_PREFIX + statistic.name();
            expectedKeys.add( key );
            Assert.assertTrue(
                    "Admin.properties missing record for " + key,
                    resourceBundle.containsKey( key ) );
        }

        final Set<String> extraKeys = new HashSet<>( resourceBundle.keySet() );
        extraKeys.removeAll( expectedKeys );

        for ( final String key : extraKeys )
        {
            if ( key.startsWith( password.pwm.i18n.Admin.EPS_STATISTICS_LABEL_PREFIX ) )
            {
                Assert.fail( "unexpected key in Admin.properties file: " + key );
            }
        }
    }
}
