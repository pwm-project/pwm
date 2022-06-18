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

package password.pwm.config;

import org.junit.Assert;
import org.junit.Test;
import password.pwm.PwmConstants;

import java.util.EnumSet;

public class PwmSettingCategoryTest
{
    @Test
    public void testLabels()
    {
        for ( final PwmSettingCategory category : PwmSettingCategory.values() )
        {
            category.getLabel( PwmConstants.DEFAULT_LOCALE );
        }
    }

    @Test
    public void testDescriptions()
    {
        for ( final PwmSettingCategory category : PwmSettingCategory.values() )
        {
            category.getDescription( PwmConstants.DEFAULT_LOCALE );
        }
    }

    @Test
    public void testProfileSetting()
    {
        for ( final PwmSettingCategory category : PwmSettingCategory.values() )
        {
            if ( category.hasProfiles() )
            {
                category.getProfileSetting();
            }
        }
    }

    @Test
    public void testProfileSettingSyntax()
    {
        for ( final PwmSettingCategory category : PwmSettingCategory.values() )
        {
            if ( category.hasProfiles() )
            {
                final PwmSetting pwmSetting = category.getProfileSetting().orElseThrow( IllegalStateException::new );
                Assert.assertEquals( PwmSettingSyntax.PROFILE, pwmSetting.getSyntax() );
            }
        }
    }

    @Test
    public void testProfileCategoryHasSettingsOrChildren()
    {
        for ( final PwmSettingCategory category : PwmSettingCategory.values() )
        {
            if ( category.hasProfiles() )
            {
                final boolean hasChildren = !category.getChildren().isEmpty();
                final boolean hasSettings = !category.getSettings().isEmpty();
                Assert.assertTrue( hasChildren || hasSettings );
                Assert.assertFalse( category.getKey() + " has both child categories and settings", hasChildren && hasSettings );
            }
        }
    }

    @Test
    public void testScope()
    {
        for ( final PwmSettingCategory category : PwmSettingCategory.values() )
        {
                final PwmSettingScope scope = category.getScope();
                Assert.assertNotNull( scope );
        }
    }

    @Test
    public void testAllCategoryMethods()
    {
        for ( final PwmSettingCategory pwmSettingCategory : EnumSet.allOf( PwmSettingCategory.class ) )
        {
            pwmSettingCategory.getLabel( PwmConstants.DEFAULT_LOCALE );
            pwmSettingCategory.getDescription( PwmConstants.DEFAULT_LOCALE );
            pwmSettingCategory.isHidden();
            pwmSettingCategory.getLevel();
            pwmSettingCategory.toMenuLocationDebug( null, PwmConstants.DEFAULT_LOCALE );
            pwmSettingCategory.getScope();
            pwmSettingCategory.getChildren();
            pwmSettingCategory.getLevel();
            pwmSettingCategory.getSettings();
        }
    }
}
