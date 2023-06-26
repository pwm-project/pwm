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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.ThrowingConsumer;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import password.pwm.PwmApplication;
import password.pwm.PwmDomain;
import password.pwm.bean.DomainID;
import password.pwm.bean.SessionLabel;
import password.pwm.config.profile.PwmPasswordPolicy;
import password.pwm.config.profile.PwmPasswordRule;
import password.pwm.error.ErrorInformation;
import password.pwm.util.PasswordData;
import password.pwm.util.localdb.TestHelper;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

public class RandomPasswordGeneratorTest
{
    private static final int LOOP_COUNT = 1_000;

    @TempDir
    public Path temporaryFolder;

    @Test
    public void specialCharsRulesTest()
            throws Throwable
    {
        final int minSpecial = 33;
        final int maxSpecial = 44;
        final Map<String, String> policyMap = new HashMap<>( PwmPasswordPolicy.defaultPolicy().getPolicyMap() );
        policyMap.put( PwmPasswordRule.AllowSpecial.getKey(), "true" );
        policyMap.put( PwmPasswordRule.MinimumSpecial.getKey(), String.valueOf( minSpecial ) );
        policyMap.put( PwmPasswordRule.MaximumSpecial.getKey(), String.valueOf( maxSpecial ) );

        final ThrowingConsumer<String> charTypeCheck = passwordString ->
        {
            final long specialCount = passwordString.chars().filter( v -> !Character.isLetterOrDigit( v ) ).count();
            if ( specialCount < minSpecial || specialCount > maxSpecial )
            {
                Assertions.fail( () -> "generated password has incorrect special char count: " + specialCount + "; password: " + passwordString );
            }
        };

        generalPolicyTester( policyMap, List.of( charTypeCheck, new DupeValueChecker() ) );
    }

    @Test
    public void numericPolicyTest()
            throws Throwable
    {
        final int minNumeric = 33;
        final int maxNumeric = 44;
        final Map<String, String> policyMap = new HashMap<>( PwmPasswordPolicy.defaultPolicy().getPolicyMap() );
        policyMap.put( PwmPasswordRule.AllowNumeric.getKey(), "true" );
        policyMap.put( PwmPasswordRule.MinimumNumeric.getKey(), String.valueOf( minNumeric ) );
        policyMap.put( PwmPasswordRule.MaximumNumeric.getKey(), String.valueOf( maxNumeric ) );

        final ThrowingConsumer<String> charTypeCheck = passwordString ->
        {
            final long numericCount = passwordString.chars().filter( Character::isDigit ).count();
            if ( numericCount < minNumeric || numericCount > maxNumeric )
            {
                Assertions.fail( () -> "generated password has incorrect numeric char count: " + numericCount + "; password: " + passwordString );
            }
        };

        generalPolicyTester( policyMap, List.of( charTypeCheck, new DupeValueChecker() ) );
    }

    @ParameterizedTest
    @ValueSource( ints = { 10, 20, 50, 100, 150, 500, 1000, 2000, 5000 } )
    public void testLargePasswordSizes( final int minimumLength )
            throws Throwable
    {
        final int maxLength = minimumLength + 10;

        final Map<String, String> policyMap = new HashMap<>( PwmPasswordPolicy.defaultPolicy().getPolicyMap() );
        policyMap.put( PwmPasswordRule.AllowNumeric.getKey(), "true" );
        policyMap.put( PwmPasswordRule.MinimumLength.getKey(), Integer.toString( minimumLength ) );
        policyMap.put( PwmPasswordRule.MaximumLength.getKey(), Integer.toString( maxLength ) );

        generalPolicyTester( policyMap, List.of( new DupeValueChecker() ) );
    }

    @ParameterizedTest
    @ValueSource( ints = { 1, 2, 3, 4, 5, 6 } )
    public void testSmolPasswordSizes( final int maxLength )
            throws Throwable
    {
        final int minLength = maxLength - 1;

        final Map<String, String> policyMap = new HashMap<>( PwmPasswordPolicy.defaultPolicy().getPolicyMap() );
        policyMap.put( PwmPasswordRule.AllowNumeric.getKey(), "true" );
        policyMap.put( PwmPasswordRule.MinimumLength.getKey(), Integer.toString( minLength ) );
        policyMap.put( PwmPasswordRule.MaximumLength.getKey(), Integer.toString( maxLength ) );

        generalPolicyTester( policyMap, List.of() );
    }


    @Test
    public void testFixedPasswordSizes(  )
            throws Throwable
    {
        final int[] lengths = IntStream.range( 1, 100 ).toArray();

        for ( final int length : lengths )
        {
            final Map<String, String> policyMap = new HashMap<>( PwmPasswordPolicy.defaultPolicy().getPolicyMap() );
            policyMap.put( PwmPasswordRule.AllowNumeric.getKey(), "true" );
            policyMap.put( PwmPasswordRule.MinimumLength.getKey(), Integer.toString( length ) );
            policyMap.put( PwmPasswordRule.MaximumLength.getKey(), Integer.toString( length ) );

            final ThrowingConsumer<String> lengthCheck = passwordString ->
            {
                final long numericCount = passwordString.length();
                if ( numericCount < length || numericCount > length )
                {
                    Assertions.fail( () -> "generated password has incorrect char count: expected="
                            + length + ", actual="
                            + numericCount + "; password: " + passwordString );
                }
            };
            generalPolicyTester( policyMap, List.of( lengthCheck ) );
        }
    }

    @Test
    public void policy1Test()
            throws Throwable
    {
        final Map<String, String> policyMap = new HashMap<>( PwmPasswordPolicy.defaultPolicy().getPolicyMap() );
        policyMap.put( "chai.pwrule.ADComplexityMaxViolation", "2" );
        policyMap.put( "chai.pwrule.caseSensitive", "true" );
        policyMap.put( "chai.pwrule.changeMessage", "" );
        policyMap.put( "chai.pwrule.disallowedAttributes", "cn\ngivenName\nsn" );
        policyMap.put( "chai.pwrule.disallowedValues", "password\ntest" );
        policyMap.put( "chai.pwrule.expirationInterval", "2592000" );
        policyMap.put( "chai.pwrule.length.max", "64" );
        policyMap.put( "chai.pwrule.length.min", "2" );
        policyMap.put( "chai.pwrule.lifetime.minimum", "0" );
        policyMap.put( "chai.pwrule.lower.max", "0" );
        policyMap.put( "chai.pwrule.lower.min", "0" );
        policyMap.put( "chai.pwrule.numeric.allow", "true" );
        policyMap.put( "chai.pwrule.numeric.allowFirst", "true" );
        policyMap.put( "chai.pwrule.numeric.allowLast", "true" );
        policyMap.put( "chai.pwrule.numeric.max", "0" );
        policyMap.put( "chai.pwrule.numeric.min", "0" );
        policyMap.put( "chai.pwrule.policyEnabled", "true" );
        policyMap.put( "chai.pwrule.repeat.max", "0" );
        policyMap.put( "chai.pwrule.sequentialRepeat.max", "0" );
        policyMap.put( "chai.pwrule.special.allow", "true" );
        policyMap.put( "chai.pwrule.special.allowFirst", "true" );
        policyMap.put( "chai.pwrule.special.allowLast", "true" );
        policyMap.put( "chai.pwrule.special.max", "0" );
        policyMap.put( "chai.pwrule.special.min", "0" );
        policyMap.put( "chai.pwrule.unique.min", "0" );
        policyMap.put( "chai.pwrule.uniqueRequired", "false" );
        policyMap.put( "chai.pwrule.upper.max", "0" );
        policyMap.put( "chai.pwrule.upper.min", "0" );
        policyMap.put( "password.policy.ADComplexityLevel", "NONE" );
        policyMap.put( "password.policy.allowMacroInRegexSetting", "true" );
        policyMap.put( "password.policy.allowNonAlpha", "true" );
        policyMap.put( "password.policy.charGroup.minimumMatch", "0" );
        policyMap.put( "password.policy.charGroup.regExValues", ".*[0-9]\n.*[a-z]\n.*[A-Z]\n.*[^A-Za-z0-9]" );
        policyMap.put( "password.policy.checkWordlist", "false" );
        policyMap.put( "password.policy.disallowCurrent", "false" );
        policyMap.put( "password.policy.maximumAlpha", "0" );
        policyMap.put( "password.policy.maximumConsecutive", "0" );
        policyMap.put( "password.policy.maximumNonAlpha", "0" );
        policyMap.put( "password.policy.minimumAlpha", "0" );
        policyMap.put( "password.policy.minimumNonAlpha", "0" );
        policyMap.put( "password.policy.minimumStrength", "0" );
        policyMap.put( "password.policy.regExMatch", "" );
        policyMap.put( "password.policy.regExNoMatch", "" );
        generalPolicyTester( policyMap, List.of( new DupeValueChecker() ) );
    }


    private void generalPolicyTester( final Map<String, String> policyMap, final List<ThrowingConsumer<String>> extraChecks )
            throws Throwable
    {
        final PwmApplication pwmApplication = TestHelper.makeTestPwmApplication( temporaryFolder );
        final PwmDomain pwmDomain = pwmApplication.domains().get( DomainID.DOMAIN_ID_DEFAULT );

        final PwmPasswordPolicy pwmPasswordPolicy = PwmPasswordPolicy.createPwmPasswordPolicy(
                PwmPasswordPolicy.defaultPolicy().getDomainID(),
                policyMap );


        for ( int i = 0; i < LOOP_COUNT; i++ )
        {
            final PasswordData passwordData = PasswordUtility.generateRandom(
                    SessionLabel.TEST_SESSION_LABEL,
                    pwmPasswordPolicy,
                    pwmDomain );

            final String passwordString = passwordData.getStringValue();

            final List<ErrorInformation> errors = PasswordRuleChecks.extendedPolicyRuleChecker( SessionLabel.TEST_SESSION_LABEL, pwmDomain,
                    pwmPasswordPolicy, passwordString, null, null, PwmPasswordRuleValidator.Flag.FailFast );
            Assertions.assertTrue( errors.isEmpty(), () -> "random generated rule failed validation check: " + errors.get( 0 ).toDebugStr() );

            for ( final ThrowingConsumer<String> extraCheck : extraChecks )
            {
                extraCheck.accept( passwordString );
            }
        }
    }

    private static class DupeValueChecker implements ThrowingConsumer<String>
    {
        final Set<String> seenValues = new HashSet<>();

        @Override
        public void accept( final String passwordString )
                throws Throwable
        {
            if ( seenValues.contains( passwordString ) )
            {
                Assertions.fail( "repeated random generated password" );
            }
            seenValues.add( passwordString );
        }
    }
}
