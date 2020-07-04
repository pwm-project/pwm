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
import password.pwm.config.value.NumericValue;
import password.pwm.config.value.StringValue;
import password.pwm.error.PwmUnrecoverableException;

import java.util.List;

public class StoredConfigurationModifierTest
{

    @Test
    public void testWriteSetting() throws PwmUnrecoverableException
    {
        final StoredConfiguration storedConfiguration = StoredConfigurationFactory.newConfig();
        final StoredConfigurationModifier modifier = StoredConfigurationModifier.newModifier( storedConfiguration );

        modifier.writeSetting( PwmSetting.NOTES, null, new StringValue( "notes test" ), null );

        final StoredConfiguration newConfig = modifier.newStoredConfiguration();

        final String notesText = ( ( String ) newConfig.readSetting( PwmSetting.NOTES, null ).toNativeObject() );
        Assert.assertEquals( notesText, "notes test" );
    }

    @Test
    public void testCopyProfileID() throws PwmUnrecoverableException
    {
        final StoredConfiguration storedConfiguration = StoredConfigurationFactory.newConfig();
        final StoredConfigurationModifier modifier = StoredConfigurationModifier.newModifier( storedConfiguration );

        modifier.writeSetting( PwmSetting.HELPDESK_RESULT_LIMIT, "default", new NumericValue( 19 ), null );
        modifier.copyProfileID( PwmSetting.HELPDESK_RESULT_LIMIT.getCategory(), "default", "newProfile", null );

        final StoredConfiguration newConfig = modifier.newStoredConfiguration();

        final List<String> profileNames = newConfig.profilesForSetting( PwmSetting.HELPDESK_RESULT_LIMIT );
        Assert.assertEquals( profileNames.size(), 2 );
        Assert.assertTrue( profileNames.contains( "default" ) );
        Assert.assertTrue( profileNames.contains( "newProfile" ) );

        final long copiedResultLimit = ( ( long ) newConfig.readSetting( PwmSetting.HELPDESK_RESULT_LIMIT, "default" ).toNativeObject() );
        Assert.assertEquals( copiedResultLimit, 19 );
    }
}
