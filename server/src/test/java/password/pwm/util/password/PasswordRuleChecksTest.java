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

package password.pwm.util.password;

import org.hamcrest.CoreMatchers;
import org.hamcrest.Matcher;
import org.hamcrest.MatcherAssert;
import org.hamcrest.core.IsCollectionContaining;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import password.pwm.PwmApplication;
import password.pwm.PwmDomain;
import password.pwm.bean.DomainID;
import password.pwm.bean.SessionLabel;
import password.pwm.config.profile.PwmPasswordPolicy;
import password.pwm.config.profile.PwmPasswordRule;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.FileSystemUtility;
import password.pwm.util.localdb.TestHelper;

import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class PasswordRuleChecksTest
{
    @TempDir
    public Path temporaryFolder;

    private PwmApplication pwmApplication;

    @BeforeEach
    void beforeAll() throws Exception
    {
        final File localDbTestFolder = FileSystemUtility.createDirectory( temporaryFolder, "test-stored-queue-test" );
        this.pwmApplication = TestHelper.makeTestPwmApplication( localDbTestFolder );
    }


        @Test
    public void minimumLengthTest()
            throws Exception
    {
        final Map<String, String> policyMap = new HashMap<>();
        policyMap.put( PwmPasswordRule.MinimumLength.getKey(), "7" );

        // violations
        MatcherAssert.assertThat( doCheck( policyMap, "123" ), hasItems( PwmError.PASSWORD_TOO_SHORT ) );
        MatcherAssert.assertThat( doCheck( policyMap, "1234" ), hasItems( PwmError.PASSWORD_TOO_SHORT ) );
        MatcherAssert.assertThat( doCheck( policyMap, "12345" ), hasItems( PwmError.PASSWORD_TOO_SHORT ) );
        MatcherAssert.assertThat( doCheck( policyMap, "123456" ),  hasItems( PwmError.PASSWORD_TOO_SHORT ) );

        // not violations
        MatcherAssert.assertThat( doCheck( policyMap, "1234567" ), not( hasItems( PwmError.PASSWORD_TOO_SHORT ) ) );
        MatcherAssert.assertThat( doCheck( policyMap, "12345678" ), not( hasItems( PwmError.PASSWORD_TOO_SHORT ) ) );
    }

    @Test
    public void maximumLengthTest()
            throws Exception
    {
        final Map<String, String> policyMap = new HashMap<>();
        policyMap.put( PwmPasswordRule.MaximumLength.getKey(), "7" );

        // violations
        MatcherAssert.assertThat( doCheck( policyMap, "12345678" ), hasItems( PwmError.PASSWORD_TOO_LONG ) );
        MatcherAssert.assertThat( doCheck( policyMap, "123456789" ), hasItems( PwmError.PASSWORD_TOO_LONG ) );

        // not violations
        MatcherAssert.assertThat( doCheck( policyMap, "123" ), not( hasItems( PwmError.PASSWORD_TOO_LONG ) ) );
        MatcherAssert.assertThat( doCheck( policyMap, "1234" ), not( hasItems( PwmError.PASSWORD_TOO_LONG ) ) );
        MatcherAssert.assertThat( doCheck( policyMap, "12345" ), not( hasItems( PwmError.PASSWORD_TOO_LONG ) ) );
        MatcherAssert.assertThat( doCheck( policyMap, "123456" ),  not( hasItems( PwmError.PASSWORD_TOO_LONG ) ) );
        MatcherAssert.assertThat( doCheck( policyMap, "1234567" ),  not( hasItems( PwmError.PASSWORD_TOO_LONG ) ) );
    }

    @Test
    public void minimumUpperCaseTest()
            throws Exception
    {
        final Map<String, String> policyMap = new HashMap<>();
        policyMap.put( PwmPasswordRule.MinimumUpperCase.getKey(), "3" );

        // violations
        MatcherAssert.assertThat( doCheck( policyMap, "A" ), hasItems( PwmError.PASSWORD_NOT_ENOUGH_UPPER ) );
        MatcherAssert.assertThat( doCheck( policyMap, "AB" ), hasItems( PwmError.PASSWORD_NOT_ENOUGH_UPPER ) );
        MatcherAssert.assertThat( doCheck( policyMap, "ABc" ),  hasItems( PwmError.PASSWORD_NOT_ENOUGH_UPPER ) );

        // not violations
        MatcherAssert.assertThat( doCheck( policyMap, "ABC" ), not( hasItems( PwmError.PASSWORD_NOT_ENOUGH_UPPER ) ) );
        MatcherAssert.assertThat( doCheck( policyMap, "ABCD" ), not( hasItems( PwmError.PASSWORD_NOT_ENOUGH_UPPER ) ) );
        MatcherAssert.assertThat( doCheck( policyMap, "ABCDe" ), not( hasItems( PwmError.PASSWORD_NOT_ENOUGH_UPPER ) ) );
        MatcherAssert.assertThat( doCheck( policyMap, "123456ABC" ), not( hasItems( PwmError.PASSWORD_NOT_ENOUGH_UPPER ) ) );
    }

    @Test
    public void maximumUpperCaseTest()
            throws Exception
    {
        final Map<String, String> policyMap = new HashMap<>();
        policyMap.put( PwmPasswordRule.MaximumUpperCase.getKey(), "3" );

        // violations
        MatcherAssert.assertThat( doCheck( policyMap, "ABCD" ), hasItems( PwmError.PASSWORD_TOO_MANY_UPPER ) );
        MatcherAssert.assertThat( doCheck( policyMap, "ABCDE" ), hasItems( PwmError.PASSWORD_TOO_MANY_UPPER ) );

        // not violations
        MatcherAssert.assertThat( doCheck( policyMap, "A" ), not( hasItems( PwmError.PASSWORD_TOO_MANY_UPPER ) ) );
        MatcherAssert.assertThat( doCheck( policyMap, "AB" ), not( hasItems( PwmError.PASSWORD_TOO_MANY_UPPER ) ) );
        MatcherAssert.assertThat( doCheck( policyMap, "ABC" ), not( hasItems( PwmError.PASSWORD_TOO_MANY_UPPER ) ) );
        MatcherAssert.assertThat( doCheck( policyMap, "ABCd" ),  not( hasItems( PwmError.PASSWORD_TOO_MANY_UPPER ) ) );
    }

    @Test
    public void minimumLowerCaseTest()
            throws Exception
    {
        final Map<String, String> policyMap = new HashMap<>();
        policyMap.put( PwmPasswordRule.MinimumLowerCase.getKey(), "3" );

        // violations
        MatcherAssert.assertThat( doCheck( policyMap, "a" ), hasItems( PwmError.PASSWORD_NOT_ENOUGH_LOWER ) );
        MatcherAssert.assertThat( doCheck( policyMap, "ab" ), hasItems( PwmError.PASSWORD_NOT_ENOUGH_LOWER ) );
        MatcherAssert.assertThat( doCheck( policyMap, "abC" ),  hasItems( PwmError.PASSWORD_NOT_ENOUGH_LOWER ) );

        // not violations
        MatcherAssert.assertThat( doCheck( policyMap, "abc" ), not( hasItems( PwmError.PASSWORD_NOT_ENOUGH_LOWER ) ) );
        MatcherAssert.assertThat( doCheck( policyMap, "abcd" ), not( hasItems( PwmError.PASSWORD_NOT_ENOUGH_LOWER ) ) );
        MatcherAssert.assertThat( doCheck( policyMap, "abcdE" ), not( hasItems( PwmError.PASSWORD_NOT_ENOUGH_LOWER ) ) );
        MatcherAssert.assertThat( doCheck( policyMap, "123456abc" ), not( hasItems( PwmError.PASSWORD_NOT_ENOUGH_LOWER ) ) );
    }

    @Test
    public void maximumLowerCaseTest()
            throws Exception
    {
        final Map<String, String> policyMap = new HashMap<>();
        policyMap.put( PwmPasswordRule.MaximumLowerCase.getKey(), "3" );

        // violations
        MatcherAssert.assertThat( doCheck( policyMap, "abcd" ), hasItems( PwmError.PASSWORD_TOO_MANY_LOWER ) );
        MatcherAssert.assertThat( doCheck( policyMap, "abcde" ), hasItems( PwmError.PASSWORD_TOO_MANY_LOWER ) );

        // not violations
        MatcherAssert.assertThat( doCheck( policyMap, "a" ), not( hasItems( PwmError.PASSWORD_TOO_MANY_LOWER ) ) );
        MatcherAssert.assertThat( doCheck( policyMap, "ab" ), not( hasItems( PwmError.PASSWORD_TOO_MANY_LOWER ) ) );
        MatcherAssert.assertThat( doCheck( policyMap, "abc" ), not( hasItems( PwmError.PASSWORD_TOO_MANY_LOWER ) ) );
        MatcherAssert.assertThat( doCheck( policyMap, "abcD" ),  not( hasItems( PwmError.PASSWORD_TOO_MANY_LOWER ) ) );
    }

    @Test
    public void minimumSpecialTest()
            throws Exception
    {
        final Map<String, String> policyMap = new HashMap<>();
        policyMap.put( PwmPasswordRule.AllowSpecial.getKey(), "true" );
        policyMap.put( PwmPasswordRule.MinimumSpecial.getKey(), "3" );

        // violations
        MatcherAssert.assertThat( doCheck( policyMap, "!" ), hasItems( PwmError.PASSWORD_NOT_ENOUGH_SPECIAL ) );
        MatcherAssert.assertThat( doCheck( policyMap, "!!" ), hasItems( PwmError.PASSWORD_NOT_ENOUGH_SPECIAL ) );
        MatcherAssert.assertThat( doCheck( policyMap, "!!A" ),  hasItems( PwmError.PASSWORD_NOT_ENOUGH_SPECIAL ) );

        // not violations
        MatcherAssert.assertThat( doCheck( policyMap, "!!!" ), not( hasItems( PwmError.PASSWORD_NOT_ENOUGH_SPECIAL ) ) );
        MatcherAssert.assertThat( doCheck( policyMap, "!!!A" ), not( hasItems( PwmError.PASSWORD_NOT_ENOUGH_SPECIAL ) ) );
    }

    @Test
    public void maximumSpecialTest()
            throws Exception
    {
        final Map<String, String> policyMap = new HashMap<>();
        policyMap.put( PwmPasswordRule.AllowSpecial.getKey(), "true" );
        policyMap.put( PwmPasswordRule.MaximumSpecial.getKey(), "3" );

        // violations
        MatcherAssert.assertThat( doCheck( policyMap, "!!!!" ), hasItems( PwmError.PASSWORD_TOO_MANY_SPECIAL ) );
        MatcherAssert.assertThat( doCheck( policyMap, "!!!!!" ), hasItems( PwmError.PASSWORD_TOO_MANY_SPECIAL ) );

        // not violations
        MatcherAssert.assertThat( doCheck( policyMap, "!!!" ), not( hasItems( PwmError.PASSWORD_TOO_MANY_SPECIAL ) ) );
        MatcherAssert.assertThat( doCheck( policyMap, "!!!A" ),  not( hasItems( PwmError.PASSWORD_TOO_MANY_SPECIAL ) ) );
    }

    @Test
    public void minimumAlphaTest()
            throws Exception
    {
        final Map<String, String> policyMap = new HashMap<>();
        policyMap.put( PwmPasswordRule.MinimumAlpha.getKey(), "3" );

        // violations
        MatcherAssert.assertThat( doCheck( policyMap, "a" ), hasItems( PwmError.PASSWORD_NOT_ENOUGH_ALPHA ) );
        MatcherAssert.assertThat( doCheck( policyMap, "ab" ), hasItems( PwmError.PASSWORD_NOT_ENOUGH_ALPHA ) );
        MatcherAssert.assertThat( doCheck( policyMap, "ab1" ),  hasItems( PwmError.PASSWORD_NOT_ENOUGH_ALPHA ) );

        // not violations
        MatcherAssert.assertThat( doCheck( policyMap, "abc" ), not( hasItems( PwmError.PASSWORD_NOT_ENOUGH_ALPHA ) ) );
        MatcherAssert.assertThat( doCheck( policyMap, "abcd" ), not( hasItems( PwmError.PASSWORD_NOT_ENOUGH_ALPHA ) ) );
    }

    @Test
    public void maximumAlphaTest()
            throws Exception
    {
        final Map<String, String> policyMap = new HashMap<>();
        policyMap.put( PwmPasswordRule.MaximumAlpha.getKey(), "3" );

        // violations
        MatcherAssert.assertThat( doCheck( policyMap, "abcd" ), hasItems( PwmError.PASSWORD_TOO_MANY_ALPHA ) );
        MatcherAssert.assertThat( doCheck( policyMap, "abcd1" ), hasItems( PwmError.PASSWORD_TOO_MANY_ALPHA ) );

        // not violations
        MatcherAssert.assertThat( doCheck( policyMap, "abc" ), not( hasItems( PwmError.PASSWORD_TOO_MANY_ALPHA ) ) );
        MatcherAssert.assertThat( doCheck( policyMap, "abc1" ),  not( hasItems( PwmError.PASSWORD_TOO_MANY_ALPHA ) ) );
    }

    @Test
    public void minimumNonAlphaTest()
            throws Exception
    {
        final Map<String, String> policyMap = new HashMap<>();
        policyMap.put( PwmPasswordRule.MinimumNonAlpha.getKey(), "3" );

        // violations
        MatcherAssert.assertThat( doCheck( policyMap, "!!" ), hasItems( PwmError.PASSWORD_NOT_ENOUGH_NONALPHA ) );
        MatcherAssert.assertThat( doCheck( policyMap, "44" ), hasItems( PwmError.PASSWORD_NOT_ENOUGH_NONALPHA ) );
        MatcherAssert.assertThat( doCheck( policyMap, "5" ),  hasItems( PwmError.PASSWORD_NOT_ENOUGH_NONALPHA ) );

        // not violations
        MatcherAssert.assertThat( doCheck( policyMap, "!!!" ), not( hasItems( PwmError.PASSWORD_NOT_ENOUGH_NONALPHA ) ) );
        MatcherAssert.assertThat( doCheck( policyMap, ",,," ), not( hasItems( PwmError.PASSWORD_NOT_ENOUGH_NONALPHA ) ) );
    }

    @Test
    public void maximumNonAlphaTest()
            throws Exception
    {
        final Map<String, String> policyMap = new HashMap<>();
        policyMap.put( PwmPasswordRule.MaximumNonAlpha.getKey(), "3" );

        // violations
        MatcherAssert.assertThat( doCheck( policyMap, "1234" ), hasItems( PwmError.PASSWORD_TOO_MANY_NONALPHA ) );
        MatcherAssert.assertThat( doCheck( policyMap, "----" ), hasItems( PwmError.PASSWORD_TOO_MANY_NONALPHA ) );

        // not violations
        MatcherAssert.assertThat( doCheck( policyMap, "123" ), not( hasItems( PwmError.PASSWORD_TOO_MANY_NONALPHA ) ) );
        MatcherAssert.assertThat( doCheck( policyMap, "---" ),  not( hasItems( PwmError.PASSWORD_TOO_MANY_NONALPHA ) ) );
    }

    @Test
    public void minimumNumericTest()
            throws Exception
    {
        final Map<String, String> policyMap = new HashMap<>();
        policyMap.put( PwmPasswordRule.AllowNumeric.getKey(), "true" );
        policyMap.put( PwmPasswordRule.MinimumNumeric.getKey(), "3" );

        // violations
        MatcherAssert.assertThat( doCheck( policyMap, "1" ), hasItems( PwmError.PASSWORD_NOT_ENOUGH_NUM ) );
        MatcherAssert.assertThat( doCheck( policyMap, "12" ), hasItems( PwmError.PASSWORD_NOT_ENOUGH_NUM ) );
        MatcherAssert.assertThat( doCheck( policyMap, "12a" ),  hasItems( PwmError.PASSWORD_NOT_ENOUGH_NUM ) );

        // not violations
        MatcherAssert.assertThat( doCheck( policyMap, "123" ), not( hasItems( PwmError.PASSWORD_NOT_ENOUGH_NUM ) ) );
        MatcherAssert.assertThat( doCheck( policyMap, "1234" ), not( hasItems( PwmError.PASSWORD_NOT_ENOUGH_NUM ) ) );
    }

    @Test
    public void maximumNumericTest()
            throws Exception
    {
        final Map<String, String> policyMap = new HashMap<>();
        policyMap.put( PwmPasswordRule.AllowNumeric.getKey(), "true" );
        policyMap.put( PwmPasswordRule.MaximumNumeric.getKey(), "3" );

        // violations
        MatcherAssert.assertThat( doCheck( policyMap, "1234" ), hasItems( PwmError.PASSWORD_TOO_MANY_NUMERIC ) );
        MatcherAssert.assertThat( doCheck( policyMap, "12345" ), hasItems( PwmError.PASSWORD_TOO_MANY_NUMERIC ) );

        // not violations
        MatcherAssert.assertThat( doCheck( policyMap, "12" ), not( hasItems( PwmError.PASSWORD_TOO_MANY_NUMERIC ) ) );
        MatcherAssert.assertThat( doCheck( policyMap, "123" ),  not( hasItems( PwmError.PASSWORD_TOO_MANY_NUMERIC ) ) );
    }

    @Test
    public void minimumUniqueTest()
            throws Exception
    {
        final Map<String, String> policyMap = new HashMap<>();
        policyMap.put( PwmPasswordRule.MinimumUnique.getKey(), "4" );

        // violations
        MatcherAssert.assertThat( doCheck( policyMap, "aaa" ), hasItems( PwmError.PASSWORD_NOT_ENOUGH_UNIQUE ) );
        MatcherAssert.assertThat( doCheck( policyMap, "aaa2" ), hasItems( PwmError.PASSWORD_NOT_ENOUGH_UNIQUE ) );
        MatcherAssert.assertThat( doCheck( policyMap, "aaa23" ), hasItems( PwmError.PASSWORD_NOT_ENOUGH_UNIQUE ) );

        // not violations
        MatcherAssert.assertThat( doCheck( policyMap, "aaa234" ), not( hasItems( PwmError.PASSWORD_NOT_ENOUGH_UNIQUE ) ) );
        MatcherAssert.assertThat( doCheck( policyMap, "aaa2345" ), not( hasItems( PwmError.PASSWORD_NOT_ENOUGH_UNIQUE ) ) );
    }

    @Test
    public void maximumSequentialRepeatTest()
            throws Exception
    {
        final Map<String, String> policyMap = new HashMap<>();
        policyMap.put( PwmPasswordRule.MaximumSequentialRepeat.getKey(), "4" );

        // violations
        MatcherAssert.assertThat( doCheck( policyMap, "aaaaa" ), hasItems( PwmError.PASSWORD_TOO_MANY_REPEAT ) );
        MatcherAssert.assertThat( doCheck( policyMap, "aaaaaa" ), hasItems( PwmError.PASSWORD_TOO_MANY_REPEAT ) );

        // not violations
        MatcherAssert.assertThat( doCheck( policyMap, "aaa" ), not( hasItems( PwmError.PASSWORD_TOO_MANY_REPEAT ) ) );
        MatcherAssert.assertThat( doCheck( policyMap, "aaaa" ), not( hasItems( PwmError.PASSWORD_TOO_MANY_REPEAT ) ) );
        MatcherAssert.assertThat( doCheck( policyMap, "aaa23" ), not( hasItems( PwmError.PASSWORD_TOO_MANY_REPEAT ) ) );
    }

    @Test
    public void maximumRepeatTest()
            throws Exception
    {
        final Map<String, String> policyMap = new HashMap<>();
        policyMap.put( PwmPasswordRule.MaximumRepeat.getKey(), "4" );

        // violations
        MatcherAssert.assertThat( doCheck( policyMap, "aa2aaa" ), hasItems( PwmError.PASSWORD_TOO_MANY_REPEAT ) );
        MatcherAssert.assertThat( doCheck( policyMap, "aa2aaaa" ), hasItems( PwmError.PASSWORD_TOO_MANY_REPEAT ) );

        // not violations
        MatcherAssert.assertThat( doCheck( policyMap, "aa2a" ), not( hasItems( PwmError.PASSWORD_TOO_MANY_REPEAT ) ) );
        MatcherAssert.assertThat( doCheck( policyMap, "aa2aa" ), not( hasItems( PwmError.PASSWORD_TOO_MANY_REPEAT ) ) );
        MatcherAssert.assertThat( doCheck( policyMap, "aa2a23" ), not( hasItems( PwmError.PASSWORD_TOO_MANY_REPEAT ) ) );
    }

    @Test
    public void allowNumericTest()
            throws Exception
    {
        {
            final Map<String, String> policyMap = new HashMap<>();
            policyMap.put( PwmPasswordRule.AllowNumeric.getKey(), "true" );

            // not violations
            MatcherAssert.assertThat( doCheck( policyMap, "aaa" ), not( hasItems( PwmError.PASSWORD_TOO_MANY_NUMERIC ) ) );
            MatcherAssert.assertThat( doCheck( policyMap, "aaa2" ), not( hasItems( PwmError.PASSWORD_TOO_MANY_NUMERIC ) ) );
        }
        {
            final Map<String, String> policyMap = new HashMap<>();
            policyMap.put( PwmPasswordRule.AllowNumeric.getKey(), "false" );

            // violations
            MatcherAssert.assertThat( doCheck( policyMap, "aaa2" ), hasItems( PwmError.PASSWORD_TOO_MANY_NUMERIC ) );

            // not violations
            MatcherAssert.assertThat( doCheck( policyMap, "aaa" ), not( hasItems( PwmError.PASSWORD_TOO_MANY_NUMERIC ) ) );
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
            MatcherAssert.assertThat( doCheck( policyMap, "aaa" ), not( hasItems( PwmError.PASSWORD_TOO_MANY_SPECIAL ) ) );
            MatcherAssert.assertThat( doCheck( policyMap, "aaa^" ), not( hasItems( PwmError.PASSWORD_TOO_MANY_SPECIAL ) ) );
            MatcherAssert.assertThat( doCheck( policyMap, "123" ), not( hasItems( PwmError.PASSWORD_TOO_MANY_SPECIAL ) ) );
        }
        {
            final Map<String, String> policyMap = new HashMap<>();
            policyMap.put( PwmPasswordRule.AllowSpecial.getKey(), "false" );

            // violations
            MatcherAssert.assertThat( doCheck( policyMap, "aaa^" ), hasItems( PwmError.PASSWORD_TOO_MANY_SPECIAL ) );
            MatcherAssert.assertThat( doCheck( policyMap, "^" ), hasItems( PwmError.PASSWORD_TOO_MANY_SPECIAL ) );

            // not violations
            MatcherAssert.assertThat( doCheck( policyMap, "aaa" ), not( hasItems( PwmError.PASSWORD_TOO_MANY_SPECIAL ) ) );
            MatcherAssert.assertThat( doCheck( policyMap, "123" ), not( hasItems( PwmError.PASSWORD_TOO_MANY_SPECIAL ) ) );
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
            MatcherAssert.assertThat( doCheck( policyMap, "aaa" ), not( hasItems( PwmError.PASSWORD_TOO_MANY_NONALPHA ) ) );

            // not violations
            MatcherAssert.assertThat( doCheck( policyMap, "^" ), not( hasItems( PwmError.PASSWORD_TOO_MANY_NONALPHA ) ) );
            MatcherAssert.assertThat( doCheck( policyMap, "aaa^" ), not( hasItems( PwmError.PASSWORD_TOO_MANY_NONALPHA ) ) );
            MatcherAssert.assertThat( doCheck( policyMap, "123" ), not( hasItems( PwmError.PASSWORD_TOO_MANY_NONALPHA ) ) );
        }
        {
            final Map<String, String> policyMap = new HashMap<>();
            policyMap.put( PwmPasswordRule.AllowNonAlpha.getKey(), "false" );

            // violations
            MatcherAssert.assertThat( doCheck( policyMap, "^" ), hasItems( PwmError.PASSWORD_TOO_MANY_NONALPHA ) );
            MatcherAssert.assertThat( doCheck( policyMap, "aaa^" ), hasItems( PwmError.PASSWORD_TOO_MANY_NONALPHA ) );
            MatcherAssert.assertThat( doCheck( policyMap, "123" ), hasItems( PwmError.PASSWORD_TOO_MANY_NONALPHA ) );

            // not violations
            MatcherAssert.assertThat( doCheck( policyMap, "aaa" ), not( hasItems( PwmError.PASSWORD_TOO_MANY_NONALPHA ) ) );
        }
    }

    private Set<PwmError> doCheck(
            final Map<String, String> policy,
            final String password
    )
            throws PwmUnrecoverableException
    {
        final PwmDomain pwmDomain = pwmApplication.domains().get( DomainID.create( "default" ) );
        final Map<String, String> policyMap = new HashMap<>( PwmPasswordPolicy.defaultPolicy().getPolicyMap() );
        policyMap.putAll( policy );
        final PwmPasswordPolicy pwmPasswordPolicy = PwmPasswordPolicy.createPwmPasswordPolicy( PwmPasswordPolicy.defaultPolicy().getDomainID(), policyMap );
        final List<ErrorInformation> errorResults = PasswordRuleChecks.extendedPolicyRuleChecker(
                SessionLabel.TEST_SESSION_LABEL, pwmDomain, pwmPasswordPolicy, password, null, null );
        return errorResults.stream().map( ErrorInformation::getError ).collect( Collectors.toSet() );
    }

    private static <T> Matcher<T> not( final org.hamcrest.Matcher<T> matcher )
    {
        return CoreMatchers.not( matcher );
    }

    private static <T> Matcher<Iterable<T>> hasItems( final T... items )
    {
        return IsCollectionContaining.hasItems( items );
    }
}
