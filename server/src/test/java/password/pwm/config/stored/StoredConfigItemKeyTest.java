/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2019 The PWM Project
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

package password.pwm.config.stored;

import org.junit.Assert;
import org.junit.Test;
import password.pwm.config.PwmSetting;
import password.pwm.i18n.Config;
import password.pwm.i18n.Display;
import password.pwm.i18n.PwmLocaleBundle;

import java.util.HashSet;
import java.util.Set;

public class StoredConfigItemKeyTest
{
    @Test
    public void testKeyEquality()
    {
        {
            final StoredConfigItemKey key1 = StoredConfigItemKey.fromSetting( PwmSetting.PWM_SITE_URL, null );
            final StoredConfigItemKey key2 = StoredConfigItemKey.fromSetting( PwmSetting.PWM_SITE_URL, null );
            Assert.assertEquals( key1, key2 );
        }
        {
            final StoredConfigItemKey key1 = StoredConfigItemKey.fromLocaleBundle( PwmLocaleBundle.DISPLAY, Display.Title_Application.getKey() );
            final StoredConfigItemKey key2 = StoredConfigItemKey.fromLocaleBundle( PwmLocaleBundle.DISPLAY, Display.Title_Application.getKey() );
            Assert.assertEquals( key1, key2 );
        }
        {
            final StoredConfigItemKey key1 = StoredConfigItemKey.fromLocaleBundle( PwmLocaleBundle.DISPLAY, Display.Title_Application.getKey() );
            final StoredConfigItemKey key2 = StoredConfigItemKey.fromLocaleBundle( PwmLocaleBundle.DISPLAY, Display.Title_Admin.getKey() );
            Assert.assertNotEquals( key1, key2 );
        }
        {
            final Set<StoredConfigItemKey> set = new HashSet<>();
            set.add( StoredConfigItemKey.fromLocaleBundle( PwmLocaleBundle.DISPLAY, Display.Title_Application.getKey() ) );
            set.add( StoredConfigItemKey.fromLocaleBundle( PwmLocaleBundle.DISPLAY, Display.Title_Application.getKey() ) );
            Assert.assertEquals( 1, set.size() );
            set.add( StoredConfigItemKey.fromLocaleBundle( PwmLocaleBundle.CONFIG, Config.Display_AboutTemplates.getKey() ) );
            Assert.assertEquals( 2, set.size() );

        }
    }
}
