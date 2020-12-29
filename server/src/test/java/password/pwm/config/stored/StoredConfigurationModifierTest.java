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
import password.pwm.PwmConstants;
import password.pwm.bean.DomainID;
import password.pwm.config.PwmSetting;
import password.pwm.config.value.NumericValue;
import password.pwm.config.value.StringValue;
import password.pwm.config.value.ValueTypeConverter;
import password.pwm.error.PwmUnrecoverableException;

import java.util.List;

public class StoredConfigurationModifierTest
{

    @Test
    public void testWriteSetting() throws PwmUnrecoverableException
    {
        final DomainID domainID = PwmConstants.DOMAIN_ID_DEFAULT;
        final StoredConfigKey key = StoredConfigKey.forSetting( PwmSetting.NOTES, null, domainID );


        final StoredConfiguration storedConfiguration = StoredConfigurationFactory.newConfig();
        final StoredConfigurationModifier modifier = StoredConfigurationModifier.newModifier( storedConfiguration );

        modifier.writeSetting( key, new StringValue( "notes test" ), null );

        final StoredConfiguration newConfig = modifier.newStoredConfiguration();

        final String notesText = ValueTypeConverter.valueToString( newConfig.readStoredValue( key ).orElseThrow() );
        Assert.assertEquals( notesText, "notes test" );
    }

    @Test
    public void testCopyProfileID() throws PwmUnrecoverableException
    {
        final DomainID domainID = PwmConstants.DOMAIN_ID_DEFAULT;
        final StoredConfiguration storedConfiguration = StoredConfigurationFactory.newConfig();
        final StoredConfigurationModifier modifier = StoredConfigurationModifier.newModifier( storedConfiguration );

        final StoredConfigKey key = StoredConfigKey.forSetting( PwmSetting.HELPDESK_RESULT_LIMIT, "default", domainID );

        modifier.writeSetting( key, new NumericValue( 19 ), null );
        final StoredConfiguration preCopyConfig = modifier.newStoredConfiguration();

        final StoredConfiguration postCopyConfig = StoredConfigurationUtil.copyProfileID(
                preCopyConfig,
                domainID,
                PwmSetting.HELPDESK_RESULT_LIMIT.getCategory(),
                "default",
                "newProfile",
                null );

        final List<String> profileNames = StoredConfigurationUtil.profilesForSetting( PwmSetting.HELPDESK_RESULT_LIMIT, postCopyConfig );
        Assert.assertEquals( 2, profileNames.size() );
        Assert.assertTrue( profileNames.contains( "default" ) );
        Assert.assertTrue( profileNames.contains( "newProfile" ) );

        final long copiedResultLimit = ValueTypeConverter.valueToLong( postCopyConfig.readStoredValue( key ).orElseThrow() );
        Assert.assertEquals( 19, copiedResultLimit );
    }
}
