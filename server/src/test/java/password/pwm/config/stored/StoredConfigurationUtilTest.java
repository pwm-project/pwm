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
import password.pwm.bean.DomainID;
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingScope;
import password.pwm.config.PwmSettingSyntax;
import password.pwm.config.value.StringValue;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.CollectionUtil;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class StoredConfigurationUtilTest
{

    @Test
    public void testChangedValues() throws PwmUnrecoverableException
    {
        final StoredConfiguration storedConfiguration = StoredConfigurationFactory.newConfig();
        final StoredConfigurationModifier modifier = StoredConfigurationModifier.newModifier( storedConfiguration );

        final StoredConfigKey key = StoredConfigKey.forSetting( PwmSetting.NOTES, null, DomainID.DOMAIN_ID_DEFAULT );

        modifier.writeSetting( key, new StringValue( "notes test" ), null );

        final StoredConfiguration newConfig = modifier.newStoredConfiguration();

        final Set<StoredConfigKey> modifiedKeys = StoredConfigurationUtil.changedValues( storedConfiguration, newConfig );
        Assert.assertEquals( 1, modifiedKeys.size() );
        Assert.assertEquals( modifiedKeys.iterator().next(), key );


        final StoredConfigurationModifier modifier2 = StoredConfigurationModifier.newModifier( newConfig );
        modifier2.resetSetting( key, null );
        final StoredConfiguration resetConfig = modifier2.newStoredConfiguration();
        final Set<StoredConfigKey> resetKeys = StoredConfigurationUtil.changedValues( newConfig, resetConfig );
        Assert.assertEquals( 1, resetKeys.size() );
        Assert.assertEquals( resetKeys.iterator().next(), key );

    }

    @Test
    public void testCopyDomain()
            throws Exception
    {
        StoredConfiguration storedConfiguration = StoredConfigurationFactory.newConfig();
        final List<StoredConfigKey> stringSettingKeys = PwmSetting.sortedValues().stream()
                .filter( setting -> setting.getSyntax() == PwmSettingSyntax.STRING )
                .filter( setting -> setting.getCategory().getScope() == PwmSettingScope.DOMAIN )
                .map( setting -> StoredConfigKey.forSetting( setting, null, DomainID.create( "default" ) ) )
                .collect( Collectors.toList() );
        {
            final StoredConfigurationModifier modifier = StoredConfigurationModifier.newModifier( storedConfiguration );
            for ( final StoredConfigKey key : stringSettingKeys )
            {
                modifier.writeSetting( key, new StringValue( "test" ), null );
            }
            storedConfiguration = modifier.newStoredConfiguration();
        }

        storedConfiguration = StoredConfigurationUtil.copyDomainID( storedConfiguration, "default", "target", null );

        final List<StoredConfigKey> destKeys = CollectionUtil.iteratorToStream( storedConfiguration.keys() )
                .filter( key -> key.isRecordType( StoredConfigKey.RecordType.SETTING ) )
                .filter( key -> key.getSyntax() == PwmSettingSyntax.STRING )
                .filter( key -> key.toPwmSetting().getCategory().getScope() == PwmSettingScope.DOMAIN )
                .filter( key -> key.getDomainID().equals( DomainID.create( "target" ) ) )
                .collect( Collectors.toList() );

        Assert.assertEquals( stringSettingKeys.size(), destKeys.size() );
    }
}
