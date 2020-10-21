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

package password.pwm.config;

import org.junit.Assert;
import org.junit.Test;
import password.pwm.PwmConstants;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
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

    @Test
    public void testMinMaxValueRanges()
    {
        for ( final PwmSetting pwmSetting : PwmSetting.values() )
        {
            final long minValue = Long.parseLong( pwmSetting.getProperties().getOrDefault( PwmSettingProperty.Minimum, "0" ) );
            final long maxValue = Long.parseLong( pwmSetting.getProperties().getOrDefault( PwmSettingProperty.Maximum, "0" ) );
            if ( maxValue != 0 )
            {
                Assert.assertTrue( "setting: " + pwmSetting.getKey(), maxValue > minValue );
            }
        }
    }

    @Test
    public void testNumericProperties()
    {
        final Set<PwmSettingProperty> numericProperties = EnumSet.of(
                PwmSettingProperty.Maximum,
                PwmSettingProperty.Minimum,
                PwmSettingProperty.Maximum_Values,
                PwmSettingProperty.Minimum_Values
        );

        for ( final PwmSetting pwmSetting : PwmSetting.values() )
        {
            final Map<PwmSettingProperty, String> properties = pwmSetting.getProperties();

            for ( final PwmSettingProperty pwmSettingProperty : numericProperties )
            {
                if ( properties.containsKey( pwmSettingProperty ) )
                {
                    try
                    {
                        Long.parseLong( properties.get( pwmSettingProperty ) );
                    }
                    catch ( final NumberFormatException e )
                    {
                        throw new NumberFormatException(
                                "setting " + pwmSetting + " value for property " + pwmSettingProperty
                                + " parse error: " + e.getMessage()
                        );
                    }
                }
            }
        }
    }
}
