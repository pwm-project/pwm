/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2021 The PWM Project
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
import password.pwm.bean.DomainID;
import password.pwm.bean.ProfileID;
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

public class StoredConfigKeyTest
{
    @Test
    public void testKeyEquality()
    {
        final DomainID domainID = DomainID.systemId();

        {
            final StoredConfigKey key1 = StoredConfigKey.forSetting( PwmSetting.PWM_SITE_URL, null, domainID );
            final StoredConfigKey key2 = StoredConfigKey.forSetting( PwmSetting.PWM_SITE_URL, null, domainID );
            Assert.assertEquals( key1, key2 );
        }
        {
            final StoredConfigKey key1 = StoredConfigKey.forLocaleBundle( PwmLocaleBundle.DISPLAY, Display.Title_Application.getKey(), domainID );
            final StoredConfigKey key2 = StoredConfigKey.forLocaleBundle( PwmLocaleBundle.DISPLAY, Display.Title_Application.getKey(), domainID );
            Assert.assertEquals( key1, key2 );
        }
        {
            final StoredConfigKey key1 = StoredConfigKey.forLocaleBundle( PwmLocaleBundle.DISPLAY, Display.Title_Application.getKey(), domainID );
            final StoredConfigKey key2 = StoredConfigKey.forLocaleBundle( PwmLocaleBundle.DISPLAY, Display.Title_Admin.getKey(), domainID );
            Assert.assertNotEquals( key1, key2 );
        }
        {
            final Set<StoredConfigKey> set = new HashSet<>();
            set.add( StoredConfigKey.forLocaleBundle( PwmLocaleBundle.DISPLAY, Display.Title_Application.getKey(), domainID ) );
            set.add( StoredConfigKey.forLocaleBundle( PwmLocaleBundle.DISPLAY, Display.Title_Application.getKey(), domainID ) );
            Assert.assertEquals( 1, set.size() );
            set.add( StoredConfigKey.forLocaleBundle( PwmLocaleBundle.CONFIG, Config.Display_AboutTemplates.getKey(), domainID ) );
            Assert.assertEquals( 2, set.size() );
        }
    }

    @Test
    public void testKeyUniqueness()
    {
        final Set<StoredConfigKey> set = new HashSet<>();
        set.add( StoredConfigKey.forSetting( PwmSetting.PWM_SITE_URL, null, DomainID.systemId() ) );
        set.add( StoredConfigKey.forSetting( PwmSetting.SECURITY_ENABLE_FORM_NONCE, null, DomainID.systemId() ) );
        set.add( StoredConfigKey.forSetting( PwmSetting.SECURITY_ENABLE_FORM_NONCE, null, DomainID.systemId() ) );
        set.add( StoredConfigKey.forSetting( PwmSetting.SETUP_RESPONSE_ENABLE, null, DomainID.systemId() ) );
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

        final List<StoredConfigKey> list = new ArrayList<>();
        for ( final PwmSetting pwmSetting : PwmSetting.values() )
        {
            if ( pwmSetting.getCategory().hasProfiles() )
            {
                for ( final String profileID : profiles )
                {
                    list.add( StoredConfigKey.forSetting( pwmSetting, ProfileID.create( profileID ), DomainID.systemId() ) );
                }
            }
            else
            {
                list.add( StoredConfigKey.forSetting( pwmSetting, null, DomainID.systemId() ) );
            }
        }

        Collections.shuffle( list );
        list.sort( StoredConfigKey.comparator() );
        Collections.sort( profiles );
        Assert.assertEquals(
                StoredConfigKey.forSetting( PwmSetting.NOTES, null, DomainID.systemId() ),
                list.stream().findFirst().orElseThrow() );
    }
}
