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

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import password.pwm.AppProperty;
import password.pwm.PwmDomain;
import password.pwm.config.profile.PwmPasswordPolicy;
import password.pwm.config.profile.PwmPasswordRule;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.CollectionUtil;

import java.util.Collection;
import java.util.Collections;

@Value
@Builder( toBuilder = true, access = AccessLevel.PRIVATE )
public class RandomGeneratorConfig
{
    private static final int MINIMUM_STRENGTH = 0;
    private static final int MAXIMUM_STRENGTH = 100;

    /**
     * A set of phrases (Strings) used to generate the pwmRandom passwords.  There must be enough
     * values in the phrases to build a random password that meets rule requirements
     */
    @Builder.Default
    private Collection<String> seedlistPhrases = Collections.emptySet();

    /**
     * The minimum length desired for the password.  The algorithm will attempt to make
     * the returned value at least this long, but it is not guaranteed.
     */
    private int minimumLength;

    private int maximumLength;

    /**
     * The minimum length desired strength.  The algorithm will attempt to make
     * the returned value at least this strong, but it is not guaranteed.
     */
    private int minimumStrength;

    private int jitter;

    private int maximumAttempts;

    public static RandomGeneratorConfig make(
            final PwmDomain pwmDomain,
            final PwmPasswordPolicy pwmPasswordPolicy
    )
            throws PwmUnrecoverableException
    {

        return make( pwmDomain, pwmPasswordPolicy, RandomGeneratorConfigRequest.builder().build() );
    }

    public static RandomGeneratorConfig make(
            final PwmDomain pwmDomain,
            final PwmPasswordPolicy pwmPasswordPolicy,
            final RandomGeneratorConfigRequest request
    )
            throws PwmUnrecoverableException
    {
        final RandomGeneratorConfig config = RandomGeneratorConfig.builder()
                .maximumAttempts( Integer.parseInt( pwmDomain.getConfig().readAppProperty( AppProperty.PASSWORD_RANDOMGEN_MAX_ATTEMPTS ) ) )
                .jitter( Integer.parseInt( pwmDomain.getConfig().readAppProperty( AppProperty.PASSWORD_RANDOMGEN_JITTER_COUNT ) ) )
                .maximumLength( figureMaximumLength( pwmDomain, pwmPasswordPolicy, request.getMaximumLength() ) )
                .minimumLength( figureMinimumLength( pwmDomain, pwmPasswordPolicy, request.getMinimumLength() ) )
                .minimumStrength( figureMinimumStrength( pwmDomain, pwmPasswordPolicy, request.getMinimumStrength() ) )
                .seedlistPhrases( CollectionUtil.isEmpty( request.getSeedlistPhrases() )
                        ? RandomPasswordGenerator.DEFAULT_SEED_PHRASES : request.getSeedlistPhrases() )
                .build();

        config.validateSettings( pwmDomain );

        return config;
    }

    private static int figureMaximumLength( final PwmDomain pwmDomain, final PwmPasswordPolicy pwmPasswordPolicy, final int requestedValue )
    {
        int policyMax = requestedValue;
        if ( requestedValue <= 0 )
        {
            policyMax = Integer.parseInt( pwmDomain.getConfig().readAppProperty( AppProperty.PASSWORD_RANDOMGEN_MAX_LENGTH ) );
        }
        if ( pwmPasswordPolicy != null )
        {
            policyMax = Math.min( policyMax, pwmPasswordPolicy.getRuleHelper().readIntValue( PwmPasswordRule.MaximumLength ) );
        }
        return policyMax;
    }

    private static int figureMinimumLength( final PwmDomain pwmDomain, final PwmPasswordPolicy pwmPasswordPolicy, final int requestedValue )
    {
        int returnVal = requestedValue;
        if ( requestedValue <= 0 )
        {
            returnVal = Integer.parseInt( pwmDomain.getConfig().readAppProperty( AppProperty.PASSWORD_RANDOMGEN_MIN_LENGTH ) );
        }
        if ( pwmPasswordPolicy != null )
        {
            final int policyMin = pwmPasswordPolicy.getRuleHelper().readIntValue( PwmPasswordRule.MinimumLength );
            if ( policyMin > 0 )
            {
                returnVal = Math.min( returnVal, pwmPasswordPolicy.getRuleHelper().readIntValue( PwmPasswordRule.MinimumLength ) );
            }
        }
        return returnVal;
    }

    private static int figureMinimumStrength( final PwmDomain pwmDomain, final PwmPasswordPolicy pwmPasswordPolicy, final int requestedValue )
    {
        int policyMin = requestedValue;
        if ( requestedValue <= 0 )
        {
            policyMin = Integer.parseInt( pwmDomain.getConfig().readAppProperty( AppProperty.PASSWORD_RANDOMGEN_DEFAULT_STRENGTH ) );
        }

        if ( pwmPasswordPolicy != null )
        {
            policyMin = Math.max( policyMin, pwmPasswordPolicy.getRuleHelper().readIntValue( PwmPasswordRule.MinimumStrength ) );
        }
        return policyMin;
    }

    void validateSettings( final PwmDomain pwmDomain )
            throws PwmUnrecoverableException
    {
        final int maxLength = Integer.parseInt(
                pwmDomain.getConfig().readAppProperty( AppProperty.PASSWORD_RANDOMGEN_MAX_LENGTH ) );
        if ( this.getMinimumLength() > maxLength )
        {
            throw new PwmUnrecoverableException( new ErrorInformation(
                    PwmError.ERROR_INTERNAL,
                    "minimum random generated password length exceeds preset random generator threshold"
            ) );
        }

        if ( this.getMaximumLength() > maxLength )
        {
            throw new PwmUnrecoverableException( new ErrorInformation(
                    PwmError.ERROR_INTERNAL,
                    "maximum random generated password length exceeds preset random generator threshold"
            ) );
        }

        if ( this.getMinimumStrength() > RandomGeneratorConfig.MAXIMUM_STRENGTH )
        {
            throw new PwmUnrecoverableException( new ErrorInformation(
                    PwmError.ERROR_INTERNAL,
                    "minimum random generated password strength exceeds maximum possible"
            ) );
        }
    }
}
