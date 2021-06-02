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

package password.pwm.config.stored;

import org.junit.Assert;
import org.junit.Test;
import password.pwm.config.PwmSetting;
import password.pwm.i18n.Config;
import password.pwm.i18n.Display;
import password.pwm.i18n.PwmLocaleBundle;
import password.pwm.util.java.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
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

    @Test
    public void testKeyUniqueness()
    {
        final Set<StoredConfigItemKey> set = new HashSet<>();
        set.add( StoredConfigItemKey.fromSetting( PwmSetting.PWM_SITE_URL, null ) );
        set.add( StoredConfigItemKey.fromSetting( PwmSetting.SECURITY_ENABLE_FORM_NONCE, null ) );
        set.add( StoredConfigItemKey.fromSetting( PwmSetting.SECURITY_ENABLE_FORM_NONCE, null ) );
        set.add( StoredConfigItemKey.fromSetting( PwmSetting.CHALLENGE_ENABLE, null ) );
        Assert.assertEquals( 3, set.size() );
    }


    @Test
    public void testKeySorting()
    {
        final List<String> profiles = new ArrayList<>();
        for ( int i = 0; i < 20; i++ )
        {
            profiles.add( "profile" + StringUtil.padLeft( String.valueOf( i ), 2, '0' ) );
        }

        final List<StoredConfigItemKey> list = new ArrayList<>();
        for ( final PwmSetting pwmSetting : PwmSetting.values() )
        {
            if ( pwmSetting.getCategory().hasProfiles() )
            {
                for ( final String profileID : profiles )
                {
                    list.add( StoredConfigItemKey.fromSetting( pwmSetting, profileID ) );
                }
            }
            else
            {
                list.add( StoredConfigItemKey.fromSetting( pwmSetting, null ) );
            }
        }

        Collections.shuffle( list );
        list.sort( StoredConfigItemKey.comparator() );
        //System.out.println( list.size() );
        //list.forEach( System.out::println );
    }
}
