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

package password.pwm.util.password;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import password.pwm.PwmApplication;
import password.pwm.config.profile.PwmPasswordPolicy;
import password.pwm.config.profile.PwmPasswordRule;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.PasswordData;
import password.pwm.util.localdb.TestHelper;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class RandomPasswordGeneratorTest
{
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();


    @Test
    public void generateRandomPasswordsTest()
            throws PwmUnrecoverableException, IOException
    {
        final PwmApplication pwmApplication = TestHelper.makeTestPwmApplication( temporaryFolder.newFolder() );
        final Map<String, String> policyMap = new HashMap<>( PwmPasswordPolicy.defaultPolicy().getPolicyMap() );
        policyMap.put( PwmPasswordRule.AllowNumeric.getKey(), "true" );
        final PwmPasswordPolicy pwmPasswordPolicy = PwmPasswordPolicy.createPwmPasswordPolicy( policyMap );

        final int loopCount = 1_000;
        final Set<String> seenValues = new HashSet<>();

        for ( int i = 0; i < loopCount; i++ )
        {
            final PasswordData passwordData = RandomPasswordGenerator.createRandomPassword(
                    null,
                    pwmPasswordPolicy,
                    pwmApplication );

            final String passwordString = passwordData.getStringValue();
            if ( seenValues.contains( passwordString ) )
            {
                Assert.fail( "repeated random generated password" );
            }
            seenValues.add( passwordString );
        }
    }
}
