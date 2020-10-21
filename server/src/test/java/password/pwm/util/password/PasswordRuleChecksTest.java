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
import org.junit.Test;
import password.pwm.config.profile.PwmPasswordPolicy;
import password.pwm.config.profile.PwmPasswordRule;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class PasswordRuleChecksTest
{
    @Test
    public void minimumLengthTest()
            throws Exception
    {
        final Map<String, String> policyMap = new HashMap<>();
        policyMap.put( PwmPasswordRule.MinimumLength.getKey(), "7" );

        // violations
        Assert.assertThat( doCheck( policyMap, "123" ), hasItems( PwmError.PASSWORD_TOO_SHORT ) );
        Assert.assertThat( doCheck( policyMap, "1234" ), hasItems( PwmError.PASSWORD_TOO_SHORT ) );
        Assert.assertThat( doCheck( policyMap, "12345" ), hasItems( PwmError.PASSWORD_TOO_SHORT ) );
        Assert.assertThat( doCheck( policyMap, "123456" ),  hasItems( PwmError.PASSWORD_TOO_SHORT ) );

        // not violations
        Assert.assertThat( doCheck( policyMap, "1234567" ), not( hasItems( PwmError.PASSWORD_TOO_SHORT ) ) );
        Assert.assertThat( doCheck( policyMap, "12345678" ), not( hasItems( PwmError.PASSWORD_TOO_SHORT ) ) );
    }

    @Test
    public void maximumLengthTest()
            throws Exception
    {
        final Map<String, String> policyMap = new HashMap<>();
        policyMap.put( PwmPasswordRule.MaximumLength.getKey(), "7" );

        // violations
        Assert.assertThat( doCheck( policyMap, "12345678" ), hasItems( PwmError.PASSWORD_TOO_LONG ) );
        Assert.assertThat( doCheck( policyMap, "123456789" ), hasItems( PwmError.PASSWORD_TOO_LONG ) );

        // not violations
        Assert.assertThat( doCheck( policyMap, "123" ), not( hasItems( PwmError.PASSWORD_TOO_LONG ) ) );
        Assert.assertThat( doCheck( policyMap, "1234" ), not( hasItems( PwmError.PASSWORD_TOO_LONG ) ) );
        Assert.assertThat( doCheck( policyMap, "12345" ), not( hasItems( PwmError.PASSWORD_TOO_LONG ) ) );
        Assert.assertThat( doCheck( policyMap, "123456" ),  not( hasItems( PwmError.PASSWORD_TOO_LONG ) ) );
        Assert.assertThat( doCheck( policyMap, "1234567" ),  not( hasItems( PwmError.PASSWORD_TOO_LONG ) ) );
    }

    @Test
    public void minimumUpperCaseTest()
            throws Exception
    {
        final Map<String, String> policyMap = new HashMap<>();
        policyMap.put( PwmPasswordRule.MinimumUpperCase.getKey(), "3" );

        // violations
        Assert.assertThat( doCheck( policyMap, "A" ), hasItems( PwmError.PASSWORD_NOT_ENOUGH_UPPER ) );
        Assert.assertThat( doCheck( policyMap, "AB" ), hasItems( PwmError.PASSWORD_NOT_ENOUGH_UPPER ) );
        Assert.assertThat( doCheck( policyMap, "ABc" ),  hasItems( PwmError.PASSWORD_NOT_ENOUGH_UPPER ) );

        // not violations
        Assert.assertThat( doCheck( policyMap, "ABC" ), not( hasItems( PwmError.PASSWORD_NOT_ENOUGH_UPPER ) ) );
        Assert.assertThat( doCheck( policyMap, "ABCD" ), not( hasItems( PwmError.PASSWORD_NOT_ENOUGH_UPPER ) ) );
        Assert.assertThat( doCheck( policyMap, "ABCDe" ), not( hasItems( PwmError.PASSWORD_NOT_ENOUGH_UPPER ) ) );
        Assert.assertThat( doCheck( policyMap, "123456ABC" ), not( hasItems( PwmError.PASSWORD_NOT_ENOUGH_UPPER ) ) );
    }

    @Test
    public void maximumUpperCaseTest()
            throws Exception
    {
        final Map<String, String> policyMap = new HashMap<>();
        policyMap.put( PwmPasswordRule.MaximumUpperCase.getKey(), "3" );

        // violations
        Assert.assertThat( doCheck( policyMap, "ABCD" ), hasItems( PwmError.PASSWORD_TOO_MANY_UPPER ) );
        Assert.assertThat( doCheck( policyMap, "ABCDE" ), hasItems( PwmError.PASSWORD_TOO_MANY_UPPER ) );

        // not violations
        Assert.assertThat( doCheck( policyMap, "A" ), not( hasItems( PwmError.PASSWORD_TOO_MANY_UPPER ) ) );
        Assert.assertThat( doCheck( policyMap, "AB" ), not( hasItems( PwmError.PASSWORD_TOO_MANY_UPPER ) ) );
        Assert.assertThat( doCheck( policyMap, "ABC" ), not( hasItems( PwmError.PASSWORD_TOO_MANY_UPPER ) ) );
        Assert.assertThat( doCheck( policyMap, "ABCd" ),  not( hasItems( PwmError.PASSWORD_TOO_MANY_UPPER ) ) );
    }

    @Test
    public void minimumLowerCaseTest()
            throws Exception
    {
        final Map<String, String> policyMap = new HashMap<>();
        policyMap.put( PwmPasswordRule.MinimumLowerCase.getKey(), "3" );

        // violations
        Assert.assertThat( doCheck( policyMap, "a" ), hasItems( PwmError.PASSWORD_NOT_ENOUGH_LOWER ) );
        Assert.assertThat( doCheck( policyMap, "ab" ), hasItems( PwmError.PASSWORD_NOT_ENOUGH_LOWER ) );
        Assert.assertThat( doCheck( policyMap, "abC" ),  hasItems( PwmError.PASSWORD_NOT_ENOUGH_LOWER ) );

        // not violations
        Assert.assertThat( doCheck( policyMap, "abc" ), not( hasItems( PwmError.PASSWORD_NOT_ENOUGH_LOWER ) ) );
        Assert.assertThat( doCheck( policyMap, "abcd" ), not( hasItems( PwmError.PASSWORD_NOT_ENOUGH_LOWER ) ) );
        Assert.assertThat( doCheck( policyMap, "abcdE" ), not( hasItems( PwmError.PASSWORD_NOT_ENOUGH_LOWER ) ) );
        Assert.assertThat( doCheck( policyMap, "123456abc" ), not( hasItems( PwmError.PASSWORD_NOT_ENOUGH_LOWER ) ) );
    }

    @Test
    public void maximumLowerCaseTest()
            throws Exception
    {
        final Map<String, String> policyMap = new HashMap<>();
        policyMap.put( PwmPasswordRule.MaximumLowerCase.getKey(), "3" );

        // violations
        Assert.assertThat( doCheck( policyMap, "abcd" ), hasItems( PwmError.PASSWORD_TOO_MANY_LOWER ) );
        Assert.assertThat( doCheck( policyMap, "abcde" ), hasItems( PwmError.PASSWORD_TOO_MANY_LOWER ) );

        // not violations
        Assert.assertThat( doCheck( policyMap, "a" ), not( hasItems( PwmError.PASSWORD_TOO_MANY_LOWER ) ) );
        Assert.assertThat( doCheck( policyMap, "ab" ), not( hasItems( PwmError.PASSWORD_TOO_MANY_LOWER ) ) );
        Assert.assertThat( doCheck( policyMap, "abc" ), not( hasItems( PwmError.PASSWORD_TOO_MANY_LOWER ) ) );
        Assert.assertThat( doCheck( policyMap, "abcD" ),  not( hasItems( PwmError.PASSWORD_TOO_MANY_LOWER ) ) );
    }

    @Test
    public void minimumSpecialTest()
            throws Exception
    {
        final Map<String, String> policyMap = new HashMap<>();
        policyMap.put( PwmPasswordRule.AllowSpecial.getKey(), "true" );
        policyMap.put( PwmPasswordRule.MinimumSpecial.getKey(), "3" );

        // violations
        Assert.assertThat( doCheck( policyMap, "!" ), hasItems( PwmError.PASSWORD_NOT_ENOUGH_SPECIAL ) );
        Assert.assertThat( doCheck( policyMap, "!!" ), hasItems( PwmError.PASSWORD_NOT_ENOUGH_SPECIAL ) );
        Assert.assertThat( doCheck( policyMap, "!!A" ),  hasItems( PwmError.PASSWORD_NOT_ENOUGH_SPECIAL ) );

        // not violations
        Assert.assertThat( doCheck( policyMap, "!!!" ), not( hasItems( PwmError.PASSWORD_NOT_ENOUGH_SPECIAL ) ) );
        Assert.assertThat( doCheck( policyMap, "!!!A" ), not( hasItems( PwmError.PASSWORD_NOT_ENOUGH_SPECIAL ) ) );
    }

    @Test
    public void maximumSpecialTest()
            throws Exception
    {
        final Map<String, String> policyMap = new HashMap<>();
        policyMap.put( PwmPasswordRule.AllowSpecial.getKey(), "true" );
        policyMap.put( PwmPasswordRule.MaximumSpecial.getKey(), "3" );

        // violations
        Assert.assertThat( doCheck( policyMap, "!!!!" ), hasItems( PwmError.PASSWORD_TOO_MANY_SPECIAL ) );
        Assert.assertThat( doCheck( policyMap, "!!!!!" ), hasItems( PwmError.PASSWORD_TOO_MANY_SPECIAL ) );

        // not violations
        Assert.assertThat( doCheck( policyMap, "!!!" ), not( hasItems( PwmError.PASSWORD_TOO_MANY_SPECIAL ) ) );
        Assert.assertThat( doCheck( policyMap, "!!!A" ),  not( hasItems( PwmError.PASSWORD_TOO_MANY_SPECIAL ) ) );
    }

    @Test
    public void minimumAlphaTest()
            throws Exception
    {
        final Map<String, String> policyMap = new HashMap<>();
        policyMap.put( PwmPasswordRule.MinimumAlpha.getKey(), "3" );

        // violations
        Assert.assertThat( doCheck( policyMap, "a" ), hasItems( PwmError.PASSWORD_NOT_ENOUGH_ALPHA ) );
        Assert.assertThat( doCheck( policyMap, "ab" ), hasItems( PwmError.PASSWORD_NOT_ENOUGH_ALPHA ) );
        Assert.assertThat( doCheck( policyMap, "ab1" ),  hasItems( PwmError.PASSWORD_NOT_ENOUGH_ALPHA ) );

        // not violations
        Assert.assertThat( doCheck( policyMap, "abc" ), not( hasItems( PwmError.PASSWORD_NOT_ENOUGH_ALPHA ) ) );
        Assert.assertThat( doCheck( policyMap, "abcd" ), not( hasItems( PwmError.PASSWORD_NOT_ENOUGH_ALPHA ) ) );
    }

    @Test
    public void maximumAlphaTest()
            throws Exception
    {
        final Map<String, String> policyMap = new HashMap<>();
        policyMap.put( PwmPasswordRule.MaximumAlpha.getKey(), "3" );

        // violations
        Assert.assertThat( doCheck( policyMap, "abcd" ), hasItems( PwmError.PASSWORD_TOO_MANY_ALPHA ) );
        Assert.assertThat( doCheck( policyMap, "abcd1" ), hasItems( PwmError.PASSWORD_TOO_MANY_ALPHA ) );

        // not violations
        Assert.assertThat( doCheck( policyMap, "abc" ), not( hasItems( PwmError.PASSWORD_TOO_MANY_ALPHA ) ) );
        Assert.assertThat( doCheck( policyMap, "abc1" ),  not( hasItems( PwmError.PASSWORD_TOO_MANY_ALPHA ) ) );
    }

    @Test
    public void minimumNonAlphaTest()
            throws Exception
    {
        final Map<String, String> policyMap = new HashMap<>();
        policyMap.put( PwmPasswordRule.MinimumNonAlpha.getKey(), "3" );

        // violations
        Assert.assertThat( doCheck( policyMap, "!!" ), hasItems( PwmError.PASSWORD_NOT_ENOUGH_NONALPHA ) );
        Assert.assertThat( doCheck( policyMap, "44" ), hasItems( PwmError.PASSWORD_NOT_ENOUGH_NONALPHA ) );
        Assert.assertThat( doCheck( policyMap, "5" ),  hasItems( PwmError.PASSWORD_NOT_ENOUGH_NONALPHA ) );

        // not violations
        Assert.assertThat( doCheck( policyMap, "!!!" ), not( hasItems( PwmError.PASSWORD_NOT_ENOUGH_NONALPHA ) ) );
        Assert.assertThat( doCheck( policyMap, ",,," ), not( hasItems( PwmError.PASSWORD_NOT_ENOUGH_NONALPHA ) ) );
    }

    @Test
    public void maximumNonAlphaTest()
            throws Exception
    {
        final Map<String, String> policyMap = new HashMap<>();
        policyMap.put( PwmPasswordRule.MaximumNonAlpha.getKey(), "3" );

        // violations
        Assert.assertThat( doCheck( policyMap, "1234" ), hasItems( PwmError.PASSWORD_TOO_MANY_NONALPHA ) );
        Assert.assertThat( doCheck( policyMap, "----" ), hasItems( PwmError.PASSWORD_TOO_MANY_NONALPHA ) );

        // not violations
        Assert.assertThat( doCheck( policyMap, "123" ), not( hasItems( PwmError.PASSWORD_TOO_MANY_NONALPHA ) ) );
        Assert.assertThat( doCheck( policyMap, "---" ),  not( hasItems( PwmError.PASSWORD_TOO_MANY_NONALPHA ) ) );
    }

    @Test
    public void minimumNumericTest()
            throws Exception
    {
        final Map<String, String> policyMap = new HashMap<>();
        policyMap.put( PwmPasswordRule.AllowNumeric.getKey(), "true" );
        policyMap.put( PwmPasswordRule.MinimumNumeric.getKey(), "3" );

        // violations
        Assert.assertThat( doCheck( policyMap, "1" ), hasItems( PwmError.PASSWORD_NOT_ENOUGH_NUM ) );
        Assert.assertThat( doCheck( policyMap, "12" ), hasItems( PwmError.PASSWORD_NOT_ENOUGH_NUM ) );
        Assert.assertThat( doCheck( policyMap, "12a" ),  hasItems( PwmError.PASSWORD_NOT_ENOUGH_NUM ) );

        // not violations
        Assert.assertThat( doCheck( policyMap, "123" ), not( hasItems( PwmError.PASSWORD_NOT_ENOUGH_NUM ) ) );
        Assert.assertThat( doCheck( policyMap, "1234" ), not( hasItems( PwmError.PASSWORD_NOT_ENOUGH_NUM ) ) );
    }

    @Test
    public void maximumNumericTest()
            throws Exception
    {
        final Map<String, String> policyMap = new HashMap<>();
        policyMap.put( PwmPasswordRule.AllowNumeric.getKey(), "true" );
        policyMap.put( PwmPasswordRule.MaximumNumeric.getKey(), "3" );

        // violations
        Assert.assertThat( doCheck( policyMap, "1234" ), hasItems( PwmError.PASSWORD_TOO_MANY_NUMERIC ) );
        Assert.assertThat( doCheck( policyMap, "12345" ), hasItems( PwmError.PASSWORD_TOO_MANY_NUMERIC ) );

        // not violations
        Assert.assertThat( doCheck( policyMap, "12" ), not( hasItems( PwmError.PASSWORD_TOO_MANY_NUMERIC ) ) );
        Assert.assertThat( doCheck( policyMap, "123" ),  not( hasItems( PwmError.PASSWORD_TOO_MANY_NUMERIC ) ) );
    }

    @Test
    public void minimumUniqueTest()
            throws Exception
    {
        final Map<String, String> policyMap = new HashMap<>();
        policyMap.put( PwmPasswordRule.MinimumUnique.getKey(), "4" );

        // violations
        Assert.assertThat( doCheck( policyMap, "aaa" ), hasItems( PwmError.PASSWORD_NOT_ENOUGH_UNIQUE ) );
        Assert.assertThat( doCheck( policyMap, "aaa2" ), hasItems( PwmError.PASSWORD_NOT_ENOUGH_UNIQUE ) );
        Assert.assertThat( doCheck( policyMap, "aaa23" ), hasItems( PwmError.PASSWORD_NOT_ENOUGH_UNIQUE ) );

        // not violations
        Assert.assertThat( doCheck( policyMap, "aaa234" ), not( hasItems( PwmError.PASSWORD_NOT_ENOUGH_UNIQUE ) ) );
        Assert.assertThat( doCheck( policyMap, "aaa2345" ), not( hasItems( PwmError.PASSWORD_NOT_ENOUGH_UNIQUE ) ) );
    }

    @Test
    public void maximumSequentialRepeatTest()
            throws Exception
    {
        final Map<String, String> policyMap = new HashMap<>();
        policyMap.put( PwmPasswordRule.MaximumSequentialRepeat.getKey(), "4" );

        // violations
        Assert.assertThat( doCheck( policyMap, "aaaaa" ), hasItems( PwmError.PASSWORD_TOO_MANY_REPEAT ) );
        Assert.assertThat( doCheck( policyMap, "aaaaaa" ), hasItems( PwmError.PASSWORD_TOO_MANY_REPEAT ) );

        // not violations
        Assert.assertThat( doCheck( policyMap, "aaa" ), not( hasItems( PwmError.PASSWORD_TOO_MANY_REPEAT ) ) );
        Assert.assertThat( doCheck( policyMap, "aaaa" ), not( hasItems( PwmError.PASSWORD_TOO_MANY_REPEAT ) ) );
        Assert.assertThat( doCheck( policyMap, "aaa23" ), not( hasItems( PwmError.PASSWORD_TOO_MANY_REPEAT ) ) );
    }

    @Test
    public void maximumRepeatTest()
            throws Exception
    {
        final Map<String, String> policyMap = new HashMap<>();
        policyMap.put( PwmPasswordRule.MaximumRepeat.getKey(), "4" );

        // violations
        Assert.assertThat( doCheck( policyMap, "aa2aaa" ), hasItems( PwmError.PASSWORD_TOO_MANY_REPEAT ) );
        Assert.assertThat( doCheck( policyMap, "aa2aaaa" ), hasItems( PwmError.PASSWORD_TOO_MANY_REPEAT ) );

        // not violations
        Assert.assertThat( doCheck( policyMap, "aa2a" ), not( hasItems( PwmError.PASSWORD_TOO_MANY_REPEAT ) ) );
        Assert.assertThat( doCheck( policyMap, "aa2aa" ), not( hasItems( PwmError.PASSWORD_TOO_MANY_REPEAT ) ) );
        Assert.assertThat( doCheck( policyMap, "aa2a23" ), not( hasItems( PwmError.PASSWORD_TOO_MANY_REPEAT ) ) );
    }

    @Test
    public void allowNumericTest()
            throws Exception
    {
        {
            final Map<String, String> policyMap = new HashMap<>();
            policyMap.put( PwmPasswordRule.AllowNumeric.getKey(), "true" );

            // not violations
            Assert.assertThat( doCheck( policyMap, "aaa" ), not( hasItems( PwmError.PASSWORD_TOO_MANY_NUMERIC ) ) );
            Assert.assertThat( doCheck( policyMap, "aaa2" ), not( hasItems( PwmError.PASSWORD_TOO_MANY_NUMERIC ) ) );
        }
        {
            final Map<String, String> policyMap = new HashMap<>();
            policyMap.put( PwmPasswordRule.AllowNumeric.getKey(), "false" );

            // violations
            Assert.assertThat( doCheck( policyMap, "aaa2" ), hasItems( PwmError.PASSWORD_TOO_MANY_NUMERIC ) );

            // not violations
            Assert.assertThat( doCheck( policyMap, "aaa" ), not( hasItems( PwmError.PASSWORD_TOO_MANY_NUMERIC ) ) );
        }
    }

    @Test
    public void allowSpecialTest()
            throws Exception
    {
        {
            final Map<String, String> policyMap = new HashMap<>();
            policyMap.put( PwmPasswordRule.AllowSpecial.getKey(), "true" );

            // not violations
            Assert.assertThat( doCheck( policyMap, "aaa" ), not( hasItems( PwmError.PASSWORD_TOO_MANY_SPECIAL ) ) );
            Assert.assertThat( doCheck( policyMap, "aaa^" ), not( hasItems( PwmError.PASSWORD_TOO_MANY_SPECIAL ) ) );
            Assert.assertThat( doCheck( policyMap, "123" ), not( hasItems( PwmError.PASSWORD_TOO_MANY_SPECIAL ) ) );
        }
        {
            final Map<String, String> policyMap = new HashMap<>();
            policyMap.put( PwmPasswordRule.AllowSpecial.getKey(), "false" );

            // violations
            Assert.assertThat( doCheck( policyMap, "aaa^" ), hasItems( PwmError.PASSWORD_TOO_MANY_SPECIAL ) );
            Assert.assertThat( doCheck( policyMap, "^" ), hasItems( PwmError.PASSWORD_TOO_MANY_SPECIAL ) );

            // not violations
            Assert.assertThat( doCheck( policyMap, "aaa" ), not( hasItems( PwmError.PASSWORD_TOO_MANY_SPECIAL ) ) );
            Assert.assertThat( doCheck( policyMap, "123" ), not( hasItems( PwmError.PASSWORD_TOO_MANY_SPECIAL ) ) );
        }
    }

    @Test
    public void allowNonAlpha()
            throws Exception
    {
        {
            final Map<String, String> policyMap = new HashMap<>();
            policyMap.put( PwmPasswordRule.AllowNonAlpha.getKey(), "true" );

            // violations
            Assert.assertThat( doCheck( policyMap, "aaa" ), not( hasItems( PwmError.PASSWORD_TOO_MANY_NONALPHA ) ) );

            // not violations
            Assert.assertThat( doCheck( policyMap, "^" ), not( hasItems( PwmError.PASSWORD_TOO_MANY_NONALPHA ) ) );
            Assert.assertThat( doCheck( policyMap, "aaa^" ), not( hasItems( PwmError.PASSWORD_TOO_MANY_NONALPHA ) ) );
            Assert.assertThat( doCheck( policyMap, "123" ), not( hasItems( PwmError.PASSWORD_TOO_MANY_NONALPHA ) ) );
        }
        {
            final Map<String, String> policyMap = new HashMap<>();
            policyMap.put( PwmPasswordRule.AllowNonAlpha.getKey(), "false" );

            // violations
            Assert.assertThat( doCheck( policyMap, "^" ), hasItems( PwmError.PASSWORD_TOO_MANY_NONALPHA ) );
            Assert.assertThat( doCheck( policyMap, "aaa^" ), hasItems( PwmError.PASSWORD_TOO_MANY_NONALPHA ) );
            Assert.assertThat( doCheck( policyMap, "123" ), hasItems( PwmError.PASSWORD_TOO_MANY_NONALPHA ) );

            // not violations
            Assert.assertThat( doCheck( policyMap, "aaa" ), not( hasItems( PwmError.PASSWORD_TOO_MANY_NONALPHA ) ) );
        }
    }

    private Set<PwmError> doCheck(
            final Map<String, String> policy,
            final String password
    )
            throws PwmUnrecoverableException
    {
        final Map<String, String> policyMap = new HashMap<>( PwmPasswordPolicy.defaultPolicy().getPolicyMap() );
        policyMap.putAll( policy );
        final PwmPasswordPolicy pwmPasswordPolicy = PwmPasswordPolicy.createPwmPasswordPolicy( policyMap );
        final List<ErrorInformation> errorResults = PasswordRuleChecks.extendedPolicyRuleChecker( null, pwmPasswordPolicy, password, null, null );
        return errorResults.stream().map( ErrorInformation::getError ).collect( Collectors.toSet() );
    }

    private static <T> org.hamcrest.Matcher<T> not( final org.hamcrest.Matcher<T> matcher )
    {
        return org.hamcrest.CoreMatchers.not( matcher );
    }

    private static <T> org.hamcrest.Matcher<java.lang.Iterable<T>> hasItems( final T... items )
    {
        return org.hamcrest.core.IsCollectionContaining.hasItems( items );
    }
}
