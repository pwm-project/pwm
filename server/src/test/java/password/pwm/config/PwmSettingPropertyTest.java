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

package password.pwm.config;

import org.junit.Assert;
import org.junit.Test;
import password.pwm.PwmConstants;

import java.util.HashSet;
import java.util.ResourceBundle;
import java.util.Set;

public class PwmSettingPropertyTest
{

    @Test
    public void testForMissingSettings()
    {
        final ResourceBundle resourceBundle = ResourceBundle.getBundle( password.pwm.i18n.PwmSetting.class.getName(), PwmConstants.DEFAULT_LOCALE );

        final Set<String> expectedKeys = new HashSet<>();

        for ( final PwmSetting pwmSetting : PwmSetting.values() )
        {
            final String[] keys = new String[] {
                    password.pwm.i18n.PwmSetting.SETTING_DESCRIPTION_PREFIX + pwmSetting.getKey(),
                    password.pwm.i18n.PwmSetting.SETTING_LABEL_PREFIX + pwmSetting.getKey(),
            };
            for ( final String key : keys )
            {
                expectedKeys.add( key );
                Assert.assertTrue(
                        "PwmSettings.properties missing record for " + key,
                        resourceBundle.containsKey( key ) );
            }
        }

        for ( final PwmSettingCategory category : PwmSettingCategory.values() )
        {
            final String[] keys = new String[] {
                    password.pwm.i18n.PwmSetting.CATEGORY_DESCRIPTION_PREFIX + category.getKey(),
                    password.pwm.i18n.PwmSetting.CATEGORY_LABEL_PREFIX + category.getKey(),
            };
            for ( final String key : keys )
            {
                expectedKeys.add( key );
                Assert.assertTrue(
                        "PwmSettings.properties missing record for " + key,
                        resourceBundle.containsKey( key ) );
            }
        }

        final Set<String> extraKeys = new HashSet<>( resourceBundle.keySet() );
        extraKeys.removeAll( expectedKeys );

        if ( !extraKeys.isEmpty() )
        {
            Assert.fail( "unexpected key in PwmSetting.properties file: " + extraKeys.iterator().next() );
        }
    }
}
