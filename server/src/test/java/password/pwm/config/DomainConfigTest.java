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
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import password.pwm.PwmApplication;
import password.pwm.bean.DomainID;
import password.pwm.bean.ProfileID;
import password.pwm.config.stored.StoredConfigKey;
import password.pwm.config.stored.StoredConfiguration;
import password.pwm.config.stored.StoredConfigurationFactory;
import password.pwm.config.stored.StoredConfigurationModifier;
import password.pwm.config.value.StringArrayValue;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.localdb.TestHelper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DomainConfigTest
{
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void testLdapProfileOrdering()
            throws PwmUnrecoverableException, IOException
    {
        final StoredConfiguration storedConfiguration = StoredConfigurationFactory.newConfig();
        final StoredConfigurationModifier modifier = StoredConfigurationModifier.newModifier( storedConfiguration );
        final StoredConfigKey key = StoredConfigKey.forSetting( PwmSetting.LDAP_PROFILE_LIST, null, DomainID.DOMAIN_ID_DEFAULT );
        modifier.writeSetting( key, StringArrayValue.create( List.of( "ldap1", "ldap2", "ldap3", "ldap4", "ldap5" ) ), null );

        final PwmApplication pwmApplication = TestHelper.makeTestPwmApplication( temporaryFolder.newFolder(), AppConfig.forStoredConfig( modifier.newStoredConfiguration() ) );
        final AppConfig appConfig = pwmApplication.getConfig();
        final DomainConfig domainConfig = appConfig.getDomainConfigs().get( DomainID.DOMAIN_ID_DEFAULT );

        final List<ProfileID> ldapProfileIDs = new ArrayList<>( domainConfig.getLdapProfiles().keySet() );

        Assert.assertEquals( ProfileID.create( "ldap1" ), ldapProfileIDs.get( 0 ) );
        Assert.assertEquals( ProfileID.create( "ldap2" ), ldapProfileIDs.get( 1 ) );
        Assert.assertEquals( ProfileID.create( "ldap3" ), ldapProfileIDs.get( 2 ) );
        Assert.assertEquals( ProfileID.create( "ldap4" ), ldapProfileIDs.get( 3 ) );
        Assert.assertEquals( ProfileID.create( "ldap5" ), ldapProfileIDs.get( 4 ) );
    }
}
