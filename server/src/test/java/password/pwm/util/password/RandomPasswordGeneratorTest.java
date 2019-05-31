/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
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
