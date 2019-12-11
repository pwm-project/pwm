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

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import password.pwm.PwmConstants;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.ADPolicyComplexity;
import password.pwm.config.option.RecoveryMinLifetimeOption;
import password.pwm.config.option.WebServiceUsage;
import password.pwm.config.profile.ForgottenPasswordProfile;
import password.pwm.config.profile.PeopleSearchProfile;
import password.pwm.config.profile.PwmPasswordPolicy;
import password.pwm.config.value.data.UserPermission;
import password.pwm.util.logging.PwmLogger;

import java.io.InputStream;
import java.util.List;
import java.util.Set;

public class ConfigurationCleanerTest
{
    private static Configuration configuration;

    @BeforeClass
    public static void setUp() throws Exception
    {
        //PwmLogger.disableAllLogging();

        try ( InputStream xmlFile = ConfigurationCleanerTest.class.getResourceAsStream( "ConfigurationCleanerTest.xml" ); )
        {
            final StoredConfiguration storedConfiguration = StoredConfigurationFactory.fromXml( xmlFile );
            configuration = new Configuration( storedConfiguration );
        }
    }

    @AfterClass
    public static void tearDown()
    {
        configuration = null;
    }

    @Test
    public void testCleaningConfigFileLoaded()
    {
        final String notesText = configuration.readSettingAsString( PwmSetting.NOTES );
        Assert.assertEquals( "deprecated-test-configuration-file", notesText );
    }

    @Test
    public void testProfiledSettings()
    {
        final PeopleSearchProfile peopleSearchProfile = configuration.getPeopleSearchProfiles().get( PwmConstants.PROFILE_ID_DEFAULT );
        final List<UserPermission> userPermissionList = peopleSearchProfile.readSettingAsUserPermission( PwmSetting.PEOPLE_SEARCH_PHOTO_QUERY_FILTER );
        final UserPermission userPermission = userPermissionList.iterator().next();
        Assert.assertEquals( "(|(cn=*smith*)(cn=*blake*)(givenName=*Margo*))", userPermission.getLdapQuery() );
    }

    @Test
    public void testDeprecatedPublicHealthStatsWebService()
    {        PwmLogger.disableAllLogging();

        {
            final Set<WebServiceUsage> usages = configuration.readSettingAsOptionList( PwmSetting.WEBSERVICES_PUBLIC_ENABLE, WebServiceUsage.class );
            Assert.assertEquals( usages.size(), 2 );
            Assert.assertTrue( usages.contains( WebServiceUsage.Statistics ) );
            Assert.assertTrue( usages.contains( WebServiceUsage.Health ) );
        }
    }

    @Test
    public void testDeprecatedMinLifetimeSetting()
    {
        for ( final ForgottenPasswordProfile profile : configuration.getForgottenPasswordProfiles().values() )
        {
            final RecoveryMinLifetimeOption minLifetimeOption = profile.readSettingAsEnum(
                    PwmSetting.RECOVERY_MINIMUM_PASSWORD_LIFETIME_OPTIONS,
                    RecoveryMinLifetimeOption.class
            );
            Assert.assertEquals( RecoveryMinLifetimeOption.NONE, minLifetimeOption );
        }
    }

    @Test
    public void testDeprecatedAdComplexitySettings()
    {
        for ( final String profile : configuration.getPasswordProfileIDs() )
        {
            final PwmPasswordPolicy pwmPasswordPolicy = configuration.getPasswordPolicy( profile, PwmConstants.DEFAULT_LOCALE );
            final ADPolicyComplexity adPolicyComplexity = pwmPasswordPolicy.getRuleHelper().getADComplexityLevel();

            Assert.assertEquals( ADPolicyComplexity.AD2003, adPolicyComplexity );
        }
    }
}
