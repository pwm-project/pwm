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

import com.novell.ldapchai.exception.ImpossiblePasswordPolicyException;
import password.pwm.PwmDomain;
import password.pwm.bean.SessionLabel;
import password.pwm.config.profile.PwmPasswordPolicy;
import password.pwm.config.profile.PwmPasswordRule;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.secure.PwmRandom;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

record RandomGeneratorRequest(
        SessionLabel sessionLabel,
        PwmPasswordPolicy randomGenPolicy,
        RandomGeneratorConfig randomGeneratorConfig,
        Map<PasswordCharType, Integer> maxCharsPerType,
        PwmRandom pwmRandom,
        PwmDomain pwmDomain
)
{
    RandomGeneratorRequest(
            final SessionLabel sessionLabel,
            final PwmPasswordPolicy randomGenPolicy,
            final RandomGeneratorConfig randomGeneratorConfig,
            final Map<PasswordCharType, Integer> maxCharsPerType,
            final PwmRandom pwmRandom,
            final PwmDomain pwmDomain
    )
    {
        this.sessionLabel = Objects.requireNonNull( sessionLabel );
        this.randomGenPolicy = Objects.requireNonNull( randomGenPolicy );
        this.randomGeneratorConfig = Objects.requireNonNull( randomGeneratorConfig );
        this.maxCharsPerType = CollectionUtil.stripNulls( maxCharsPerType );
        this.pwmRandom = Objects.requireNonNull( pwmRandom );
        this.pwmDomain = Objects.requireNonNull( pwmDomain );
    }

    public static RandomGeneratorRequest create(
            final SessionLabel sessionLabel,
            final PwmPasswordPolicy passwordPolicy,
            final PwmDomain pwmDomain
    )
            throws PwmUnrecoverableException
    {
        final RandomGeneratorConfig randomGeneratorConfig = RandomGeneratorConfig.make( pwmDomain, passwordPolicy );

        final Map<PasswordCharType, Integer> maxCharsPerType = PasswordCharType.maxCharPerPolicy( randomGeneratorConfig, passwordPolicy );

        return new RandomGeneratorRequest(
                sessionLabel,
                passwordPolicy,
                randomGeneratorConfig,
                maxCharsPerType,
                pwmDomain.getSecureService().pwmRandom(),
                pwmDomain
        );
    }

    /**
     * <p>Creates a new password that satisfies the password rules.  All rules are checked for.  If for some
     * reason the pwmRandom algorithm can not generate a valid password, null will be returned.</p>
     *
     * <p>If there is an identifiable reason the password can not be created (such as mis-configured rules) then
     * an {@link com.novell.ldapchai.exception.ImpossiblePasswordPolicyException} will be thrown.</p>
     *
     * @param sessionLabel          A valid pwmSession
     * @param randomGeneratorConfig Policy to be used during generation
     * @param pwmDomain        Used to read configuration, seedmanager and other services.
     * @return A randomly generated password value that meets the requirements of this {@code PasswordPolicy}
     * @throws ImpossiblePasswordPolicyException If there is no way to create a password using the configured rules and
     *                                        default seed phrase
     * @throws PwmUnrecoverableException if the operation can not be completed
     */
    public static RandomGeneratorRequest create(
            final SessionLabel sessionLabel,
            final RandomGeneratorConfig randomGeneratorConfig,
            final PwmDomain pwmDomain
    )
            throws PwmUnrecoverableException
    {
        // determine the password policy to use for random generation
        final PwmPasswordPolicy randomGenPolicy = makeRandomGenPwdPolicy( randomGeneratorConfig, pwmDomain );

        final Map<PasswordCharType, Integer> maxCharsPerType = PasswordCharType.maxCharPerPolicy( randomGeneratorConfig, randomGenPolicy );

        return new RandomGeneratorRequest(
                sessionLabel,
                randomGenPolicy,
                randomGeneratorConfig,
                maxCharsPerType,
                pwmDomain.getSecureService().pwmRandom(),
                pwmDomain
        );
    }

    static PwmPasswordPolicy makeRandomGenPwdPolicy(
            final RandomGeneratorConfig randomGeneratorConfig,
            final PwmDomain pwmDomain    )
    {
        final PwmPasswordPolicy defaultPolicy = PwmPasswordPolicy.defaultPolicy();
        final Map<String, String> newPolicyMap = new HashMap<>( defaultPolicy.getPolicyMap() );

        newPolicyMap.put( PwmPasswordRule.MaximumLength.getKey(), String.valueOf( randomGeneratorConfig.maximumLength() ) );
        if ( randomGeneratorConfig.minimumLength() > defaultPolicy.ruleHelper().readIntValue( PwmPasswordRule.MinimumLength ) )
        {
            newPolicyMap.put( PwmPasswordRule.MinimumLength.getKey(), String.valueOf( randomGeneratorConfig.minimumLength() ) );
        }
        if ( randomGeneratorConfig.maximumLength() < defaultPolicy.ruleHelper().readIntValue( PwmPasswordRule.MaximumLength ) )
        {
            newPolicyMap.put( PwmPasswordRule.MaximumLength.getKey(), String.valueOf( randomGeneratorConfig.maximumLength() ) );
        }
        if ( randomGeneratorConfig.minimumStrength() > defaultPolicy.ruleHelper().readIntValue( PwmPasswordRule.MinimumStrength ) )
        {
            newPolicyMap.put( PwmPasswordRule.MinimumStrength.getKey(), String.valueOf( randomGeneratorConfig.minimumStrength() ) );
        }
        return PwmPasswordPolicy.createPwmPasswordPolicy( pwmDomain.getDomainID(), newPolicyMap );
    }

}
