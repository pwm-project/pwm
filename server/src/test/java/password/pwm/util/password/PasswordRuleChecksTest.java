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
