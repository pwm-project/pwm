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

import password.pwm.AppProperty;
import password.pwm.PwmDomain;
import password.pwm.config.profile.PwmPasswordPolicy;
import password.pwm.config.profile.PwmPasswordRule;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;

public record RandomGeneratorConfig(
        SeedMachine seedMachine,
        int minimumLength,
        int maximumLength,
        int minimumStrength,
        int maximumAttempts
)
{
    private static final int MINIMUM_LENGTH = 0;
    private static final int MAXIMUM_LENGTH = 1_000_000;
    private static final int MINIMUM_STRENGTH = 0;
    private static final int MAXIMUM_STRENGTH = 100;

    public RandomGeneratorConfig(
            final SeedMachine seedMachine,
            final int minimumLength,
            final int maximumLength,
            final int minimumStrength,
            final int maximumAttempts
    )
    {
        this.seedMachine = seedMachine;
        this.minimumLength = minimumLength;
        this.maximumLength = maximumLength;
        this.minimumStrength = minimumStrength;
        this.maximumAttempts = maximumAttempts;

        if ( minimumLength < MINIMUM_LENGTH )
        {
            throw new IllegalArgumentException( "minimumLength too low" );
        }

        if ( maximumLength > MAXIMUM_LENGTH )
        {
            throw new IllegalArgumentException( "maximumLength too large" );
        }

        if ( minimumStrength < MINIMUM_STRENGTH )
        {
            throw new IllegalArgumentException( "minimumStrength too low" );
        }

        if ( minimumStrength > MAXIMUM_STRENGTH )
        {
            throw new IllegalArgumentException( "minimumStrength too large" );
        }
    }

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
        final RandomGeneratorConfig config = new RandomGeneratorConfig(
                SeedMachine.create( pwmDomain.getSecureService().pwmRandom(), request.getSeedlistPhrases() ),
                figureMinimumLength( pwmDomain, pwmPasswordPolicy, request ),
                figureMaximumLength( pwmDomain, pwmPasswordPolicy, request ),
                figureMinimumStrength( pwmDomain, pwmPasswordPolicy, request.getMinimumStrength() ),
                Integer.parseInt( pwmDomain.getConfig().readAppProperty( AppProperty.PASSWORD_RANDOMGEN_MAX_ATTEMPTS ) ) );

        config.validateSettings( pwmDomain );

        return config;
    }

    private static int figureMaximumLength(
            final PwmDomain pwmDomain,
            final PwmPasswordPolicy pwmPasswordPolicy,
            final RandomGeneratorConfigRequest request
    )
    {
        final int requestedValue = request.getMaximumLength();
        final int maxRandomGenLength = Integer.parseInt( pwmDomain.getConfig().readAppProperty( AppProperty.PASSWORD_RANDOMGEN_MAX_LENGTH ) );

        int policyValue = -1;
        if ( pwmPasswordPolicy != null )
        {
            policyValue = pwmPasswordPolicy.ruleHelper().readIntValue( PwmPasswordRule.MaximumLength );
        }

        if ( requestedValue > 0 && requestedValue < policyValue )
        {
            return Math.min( maxRandomGenLength, requestedValue );
        }

        if ( policyValue > 0 )
        {
            return Math.min( maxRandomGenLength, policyValue );
        }

        return 50;
    }

    private static int figureMinimumLength(
            final PwmDomain pwmDomain,
            final PwmPasswordPolicy pwmPasswordPolicy,
            final RandomGeneratorConfigRequest request
    )
    {
        final int requestedValue = request.getMinimumLength();
        int returnVal = requestedValue;

        if ( requestedValue <= 0 )
        {
            returnVal = Integer.parseInt( pwmDomain.getConfig().readAppProperty( AppProperty.PASSWORD_RANDOMGEN_MIN_LENGTH ) );
        }

        if ( pwmPasswordPolicy != null )
        {
            final PasswordRuleReaderHelper ruleHelper = pwmPasswordPolicy.ruleHelper();
            final int policyMin = ruleHelper.readIntValue( PwmPasswordRule.MinimumLength );
            if ( policyMin > 0 )
            {
                returnVal = Math.max( returnVal, ruleHelper.readIntValue( PwmPasswordRule.MinimumLength ) );
            }

            final int policyMaxLength = ruleHelper.readIntValue( PwmPasswordRule.MaximumLength );
            if ( policyMaxLength > 0 && returnVal > policyMaxLength )
            {
                returnVal = policyMaxLength;
            }
        }

        final int requestMaxLength = request.getMaximumLength();
        if ( requestMaxLength > 0 && returnVal > requestMaxLength )
        {
            returnVal = requestMaxLength;
        }

        return returnVal;
    }

    private static int figureMinimumStrength( final PwmDomain pwmDomain, final PwmPasswordPolicy pwmPasswordPolicy, final int requestedValue )
    {
        int returnValue = requestedValue;
        if ( requestedValue <= 0 )
        {
            returnValue = Integer.parseInt( pwmDomain.getConfig().readAppProperty( AppProperty.PASSWORD_RANDOMGEN_DEFAULT_STRENGTH ) );
        }

        if ( pwmPasswordPolicy != null )
        {
            final int policyValue = pwmPasswordPolicy.ruleHelper().readIntValue( PwmPasswordRule.MinimumStrength );
            if ( policyValue > 0 )
            {
                returnValue = Math.max( MAXIMUM_STRENGTH, policyValue );
            }
        }
        return returnValue;
    }

    void validateSettings( final PwmDomain pwmDomain )
            throws PwmUnrecoverableException
    {
        final int maxLength = Integer.parseInt(
                pwmDomain.getConfig().readAppProperty( AppProperty.PASSWORD_RANDOMGEN_MAX_LENGTH ) );
        if ( this.minimumLength() > maxLength )
        {
            throw new PwmUnrecoverableException( new ErrorInformation(
                    PwmError.ERROR_INTERNAL,
                    "minimum random generated password length exceeds preset random generator threshold"
            ) );
        }

        if ( this.minimumLength() > this.maximumLength() )
        {
            throw new PwmUnrecoverableException( new ErrorInformation(
                    PwmError.ERROR_INTERNAL,
                    "random generated password minimum length exceeds maximum length value"
            ) );
        }

        if ( this.maximumLength() > maxLength )
        {
            throw new PwmUnrecoverableException( new ErrorInformation(
                    PwmError.ERROR_INTERNAL,
                    "maximum random generated password length exceeds preset random generator threshold"
            ) );
        }

        if ( this.minimumStrength() > RandomGeneratorConfig.MAXIMUM_STRENGTH )
        {
            throw new PwmUnrecoverableException( new ErrorInformation(
                    PwmError.ERROR_INTERNAL,
                    "minimum random generated password strength exceeds maximum possible"
            ) );
        }
    }

}
