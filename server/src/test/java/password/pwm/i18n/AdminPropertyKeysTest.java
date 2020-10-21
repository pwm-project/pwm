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
