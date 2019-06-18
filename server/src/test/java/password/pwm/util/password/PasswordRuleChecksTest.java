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

package password.pwm.util.password;

import org.junit.Assert;
import org.junit.Test;
import password.pwm.config.profile.PwmPasswordPolicy;
import password.pwm.config.profile.PwmPasswordRule;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PasswordRuleChecksTest
{
    @Test
    public void minimumLengthTest()
            throws Exception
    {
        final Map<String, String> policyMap = new HashMap<>();
        policyMap.put( PwmPasswordRule.MinimumLength.getKey(), "7" );
        policyMap.put( PwmPasswordRule.AllowNumeric.getKey(), "true" );

        {
            final List<ErrorInformation> expectedErrors = new ArrayList<>();
            expectedErrors.add( new ErrorInformation( PwmError.PASSWORD_TOO_SHORT, null ) );

            Assert.assertTrue( doCompareTest( policyMap, "123", expectedErrors ) );
            Assert.assertTrue( doCompareTest( policyMap, "1234", expectedErrors ) );
            Assert.assertTrue( doCompareTest( policyMap, "12345", expectedErrors ) );
            Assert.assertTrue( doCompareTest( policyMap, "123456", expectedErrors ) );

            Assert.assertFalse( doCompareTest( policyMap, "1234567", expectedErrors ) );
            Assert.assertFalse( doCompareTest( policyMap, "12345678", expectedErrors ) );
        }
    }

    @Test
    public void maximumLengthTest()
            throws Exception
    {
        final Map<String, String> policyMap = new HashMap<>();
        policyMap.put( PwmPasswordRule.MaximumLength.getKey(), "7" );
        policyMap.put( PwmPasswordRule.AllowNumeric.getKey(), "true" );

        {
            final List<ErrorInformation> expectedErrors = new ArrayList<>();
            expectedErrors.add( new ErrorInformation( PwmError.PASSWORD_TOO_LONG, null ) );

            Assert.assertFalse( doCompareTest( policyMap, "123", expectedErrors ) );
            Assert.assertFalse( doCompareTest( policyMap, "1234", expectedErrors ) );
            Assert.assertFalse( doCompareTest( policyMap, "12345", expectedErrors ) );
            Assert.assertFalse( doCompareTest( policyMap, "123456", expectedErrors ) );
            Assert.assertFalse( doCompareTest( policyMap, "1234567", expectedErrors ) );

            Assert.assertTrue( doCompareTest( policyMap, "12345678", expectedErrors ) );
            Assert.assertTrue( doCompareTest( policyMap, "123456789", expectedErrors ) );
        }
    }

    @Test
    public void minimumUniqueTest()
            throws Exception
    {
        final Map<String, String> policyMap = new HashMap<>();
        policyMap.put( PwmPasswordRule.MinimumUnique.getKey(), "4" );
        policyMap.put( PwmPasswordRule.AllowNumeric.getKey(), "true" );

        {
            final List<ErrorInformation> expectedErrors = new ArrayList<>();
            expectedErrors.add( new ErrorInformation( PwmError.PASSWORD_NOT_ENOUGH_UNIQUE, null ) );

            Assert.assertTrue( doCompareTest( policyMap, "aaa", expectedErrors ) );
            Assert.assertTrue( doCompareTest( policyMap, "aaa2", expectedErrors ) );
            Assert.assertTrue( doCompareTest( policyMap, "aaa23", expectedErrors ) );

            Assert.assertFalse( doCompareTest( policyMap, "aaa234", expectedErrors ) );
            Assert.assertFalse( doCompareTest( policyMap, "aaa2345", expectedErrors ) );
        }
    }

    @Test
    public void allowNumericTest()
            throws Exception
    {
        {
            final Map<String, String> policyMap = new HashMap<>();
            policyMap.put( PwmPasswordRule.AllowNumeric.getKey(), "true" );

            {
                final List<ErrorInformation> expectedErrors = new ArrayList<>();
                expectedErrors.add( new ErrorInformation( PwmError.PASSWORD_TOO_MANY_NUMERIC, null ) );

                Assert.assertFalse( doCompareTest( policyMap, "aaa", expectedErrors ) );
                Assert.assertFalse( doCompareTest( policyMap, "aaa2", expectedErrors ) );
            }
        }
        {
            final Map<String, String> policyMap = new HashMap<>();
            policyMap.put( PwmPasswordRule.AllowNumeric.getKey(), "false" );

            {
                final List<ErrorInformation> expectedErrors = new ArrayList<>();
                expectedErrors.add( new ErrorInformation( PwmError.PASSWORD_TOO_MANY_NUMERIC, null ) );

                Assert.assertFalse( doCompareTest( policyMap, "aaa", expectedErrors ) );
                Assert.assertTrue( doCompareTest( policyMap, "aaa2", expectedErrors ) );
            }
        }
    }

    @Test
    public void allowSpecialTest()
            throws Exception
    {
        {
            final Map<String, String> policyMap = new HashMap<>();
            policyMap.put( PwmPasswordRule.AllowSpecial.getKey(), "true" );

            {
                final List<ErrorInformation> expectedErrors = new ArrayList<>();
                expectedErrors.add( new ErrorInformation( PwmError.PASSWORD_TOO_MANY_SPECIAL, null ) );

                Assert.assertFalse( doCompareTest( policyMap, "aaa", expectedErrors ) );
                Assert.assertFalse( doCompareTest( policyMap, "aaa^", expectedErrors ) );
            }
        }
        {
            final Map<String, String> policyMap = new HashMap<>();
            policyMap.put( PwmPasswordRule.AllowNumeric.getKey(), "false" );

            {
                final List<ErrorInformation> expectedErrors = new ArrayList<>();
                expectedErrors.add( new ErrorInformation( PwmError.PASSWORD_TOO_MANY_SPECIAL, null ) );

                Assert.assertFalse( doCompareTest( policyMap, "aaa", expectedErrors ) );
                Assert.assertTrue( doCompareTest( policyMap, "aaa^", expectedErrors ) );
            }
        }
    }

    private static List<ErrorInformation> doTest( final Map<String, String> policy, final String password )
            throws PwmUnrecoverableException
    {
        final Map<String, String> policyMap = new HashMap<>( PwmPasswordPolicy.defaultPolicy().getPolicyMap() );
        policyMap.putAll( policy );
        final PwmPasswordPolicy pwmPasswordPolicy = PwmPasswordPolicy.createPwmPasswordPolicy( policyMap );
        return PasswordRuleChecks.extendedPolicyRuleChecker( null, pwmPasswordPolicy, password, null, null );
    }

    private static boolean doCompareTest(
            final Map<String, String> policyMap,
            final String password,
            final List<ErrorInformation> expectedErrors
    )
            throws PwmUnrecoverableException
    {
        return ErrorInformation.listsContainSameErrors(
                doTest( policyMap, password ),
                expectedErrors );
    }

}
