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

import org.apache.commons.lang3.mutable.MutableInt;
import password.pwm.PwmDomain;
import password.pwm.bean.SessionLabel;
import password.pwm.config.DomainConfig;
import password.pwm.config.PwmSetting;
import password.pwm.config.profile.PwmPasswordPolicy;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsClient;
import password.pwm.util.PasswordData;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogLevel;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmRandom;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Random password generator.
 *
 * @author Jason D. Rivard
 */
final class RandomPasswordGenerator
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( RandomPasswordGenerator.class );

    private record MutatorResult(
            String password,
            boolean validPassword,
            int rounds
    )
    {
    }

    private RandomPasswordGenerator( )
    {
    }

    public static PasswordData generate(
            final RandomGeneratorRequest request
    )
            throws PwmUnrecoverableException
    {
        final SessionLabel sessionLabel = request.sessionLabel();
        final PwmPasswordPolicy randomGenPolicy = request.randomGenPolicy();
        final RandomGeneratorConfig randomGeneratorConfig = request.randomGeneratorConfig();
        final PwmDomain pwmDomain = request.pwmDomain();

        final Instant startTime = Instant.now();

        randomGeneratorConfig.validateSettings( pwmDomain );

        // read a rule validator
        // modify until it passes all the rules
        final MutatorResult mutatorResult = mutatePassword( request );

        // report outcome
        if ( mutatorResult.validPassword() )
        {
            final Supplier<CharSequence> logMsg = () -> "finished random password generation after "
                    + mutatorResult.rounds() + " rounds.";
            LOGGER.trace( sessionLabel, logMsg, TimeDuration.fromCurrent( startTime ) );
            //System.out.println( logMsg.get() );
        }
        else
        {
            if ( LOGGER.isInterestingLevel( PwmLogLevel.ERROR ) )
            {
                final PwmPasswordRuleValidator pwmPasswordRuleValidator = PwmPasswordRuleValidator.create( sessionLabel, pwmDomain, randomGenPolicy );
                final int errors = pwmPasswordRuleValidator.internalPwmPolicyValidator( mutatorResult.password(), null, null ).size();
                final int judgeLevel = PasswordUtility.judgePasswordStrength( pwmDomain.getConfig(), mutatorResult.password() );
                final Supplier<CharSequence> logMsg = () -> "failed random password generation after "
                        + mutatorResult.rounds() + " rounds. " + "(errors=" + errors + ", judgeLevel=" + judgeLevel;
                LOGGER.error( sessionLabel, logMsg, TimeDuration.fromCurrent( startTime ) );
                //System.out.println( logMsg.get() );
            }
        }

        StatisticsClient.incrementStat( pwmDomain, Statistic.GENERATED_PASSWORDS );

        LOGGER.trace( sessionLabel, () -> "real-time random password generator called", TimeDuration.fromCurrent( startTime ) );

        //System.out.println( "total: " + TimeDuration.compactFromCurrent( startTime ) );
        return new PasswordData( mutatorResult.password() );
    }


    private static MutatorResult mutatePassword(
            final RandomGeneratorRequest request
    )
            throws PwmUnrecoverableException
    {
        final RandomGeneratorConfig effectiveConfig = request.randomGeneratorConfig();
        final PwmPasswordPolicy randomGenPolicy = request.randomGenPolicy();

        final int maxTryCount = effectiveConfig.maximumAttempts();

        final PwmPasswordRuleValidator pwmPasswordRuleValidator = PwmPasswordRuleValidator.create(
                request.sessionLabel(), request.pwmDomain(), randomGenPolicy, PwmPasswordRuleValidator.Flag.FailFast );

        final String newPassword = generateNewPassword( request );


        int tryCount = 0;
        boolean validPassword = false;

        final MutablePassword mutablePassword = new MutablePassword( request, request.randomGeneratorConfig().seedMachine(), request.pwmRandom(), newPassword );

        while ( !validPassword && tryCount < maxTryCount )
        {
            tryCount++;
            validPassword = true;

            final List<ErrorInformation> errors = pwmPasswordRuleValidator.internalPwmPolicyValidator(
                    mutablePassword.value(), null, null );

            if ( errors != null && !errors.isEmpty() )
            {
                validPassword = false;
                modifyPasswordBasedOnErrors( mutablePassword, errors );
            }
            else if ( checkPasswordAgainstDisallowedHttpValues( request.pwmDomain().getConfig(), mutablePassword.value() ) )
            {
                validPassword = false;
                mutablePassword.reset( generateNewPassword( request ) );
            }
        }

        return new MutatorResult( mutablePassword.value(), validPassword, tryCount );
    }

    private static void modifyPasswordBasedOnErrors(
            final MutablePassword mutablePassword,
            final List<ErrorInformation> errors
    )
    {
        if ( errors == null || errors.isEmpty() )
        {
            return;
        }

        final Set<PwmError> errorMessages = EnumSet.noneOf( PwmError.class );
        errors.forEach( errorInfo -> errorMessages.add( errorInfo.getError() ) );

        boolean touched = false;

        if ( errorMessages.contains( PwmError.PASSWORD_TOO_SHORT ) )
        {
            mutablePassword.addRandChar();
            touched = true;
        }

        if ( errorMessages.contains( PwmError.PASSWORD_TOO_LONG ) )
        {
            mutablePassword.deleteRandChar();
            touched = true;
        }

        if ( errorMessages.contains( PwmError.PASSWORD_FIRST_IS_NUMERIC )
                || errorMessages.contains( PwmError.PASSWORD_FIRST_IS_SPECIAL ) )
        {
            mutablePassword.deleteFirstChar();
            touched = true;
        }

        if ( errorMessages.contains( PwmError.PASSWORD_LAST_IS_NUMERIC )
                || errorMessages.contains( PwmError.PASSWORD_LAST_IS_SPECIAL ) )
        {
            mutablePassword.deleteLastChar();
            touched = true;
        }

        if ( errorMessages.contains( PwmError.PASSWORD_TOO_WEAK ) )
        {
            mutablePassword.randomPasswordCharModifier();
            touched = true;
        }

        if ( checkForTooFewErrors( mutablePassword, errorMessages  ) )
        {
            touched = true;
        }

        if ( checkForTooManyErrors( mutablePassword, errorMessages  ) )
        {
            touched = true;
        }

        if ( !touched )
        {
            // dunno what is wrong, try just deleting a pwmRandom char, and hope a re-insert will add another.
            mutablePassword.randomPasswordCharModifier();
        }
    }

    private static boolean checkForTooFewErrors(
            final MutablePassword mutablePassword,
            final Set<PwmError> errorMessages
    )
    {
        boolean touched = false;

        for ( final PasswordCharType passwordCharType : PasswordCharType.values() )
        {
            final Optional<PwmError> tooFewError = passwordCharType.getTooFewError();
            if ( tooFewError.isPresent() && errorMessages.contains( tooFewError.get() ) )
            {
                if ( mutablePassword.getPwmRandom().nextBoolean() )
                {
                    mutablePassword.deleteRandCharExceptType( passwordCharType );
                }
                mutablePassword.addRandChar( passwordCharType );
                touched = true;
            }
        }

        return touched;
    }

    private static boolean checkForTooManyErrors(
            final MutablePassword mutablePassword,
            final Set<PwmError> errorMessages
    )
    {
        boolean touched = false;

        for ( final PasswordCharType passwordCharType : PasswordCharType.values() )
        {
            final Optional<PwmError> tooManyError = passwordCharType.getTooManyError();
            if ( tooManyError.isPresent() && errorMessages.contains( tooManyError.get() ) )
            {
                final PasswordCharCounter passwordCharCounter = mutablePassword.getPasswordCharCounter();
                if ( passwordCharCounter.hasCharsOfType( passwordCharType )  )
                {
                    mutablePassword.deleteRandChar( passwordCharType );
                    if ( mutablePassword.getPwmRandom().nextBoolean() )
                    {
                        mutablePassword.addRandCharExceptType( passwordCharType );
                    }
                    touched = true;
                }
            }
        }

        return touched;
    }

    private static boolean checkPasswordAgainstDisallowedHttpValues( final DomainConfig config, final String password )
    {
        if ( config != null && password != null )
        {
            final List<String> disallowedInputs = config.getAppConfig().readSettingAsStringArray( PwmSetting.DISALLOWED_HTTP_INPUTS );
            for ( final String loopRegex : disallowedInputs )
            {
                if ( password.matches( loopRegex ) )
                {
                    return true;
                }
            }
        }
        return false;
    }

    private static String generateNewPassword(
            final RandomGeneratorRequest request
    )
    {
        final RandomGeneratorConfig randomGeneratorConfig = request.randomGeneratorConfig();
        final PwmRandom pwmRandom = request.pwmRandom();
        final SeedMachine seedMachine = request.randomGeneratorConfig().seedMachine();

        final int effectiveLengthRange = randomGeneratorConfig.maximumLength() - randomGeneratorConfig.minimumLength();
        final int desiredLength = effectiveLengthRange > 1
                ? randomGeneratorConfig.minimumLength() + pwmRandom.nextInt( effectiveLengthRange )
                : randomGeneratorConfig.maximumLength();

        final Map<PasswordCharType, MutableInt> charTypeCounter = request.maxCharsPerType().entrySet()
                .stream()
                .filter( entry -> entry.getValue() > 0 )
                .collect( Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> new MutableInt( entry.getValue() ) ) );

        final StringBuilder password = new StringBuilder( desiredLength );

        // list copy of charTypeCounter.keySet() required because cannot pick random value from set/map.  SequencedMap would be a better fit if it existed.
        final List<PasswordCharType> list = new ArrayList<>( charTypeCounter.keySet() );

        while ( password.length() < desiredLength && !charTypeCounter.isEmpty() )
        {
            final PasswordCharType type = list.get( pwmRandom.nextInt( list.size() ) );
            if ( charTypeCounter.get( type ).decrementAndGet() == 0 )
            {
                charTypeCounter.remove( type );
                list.remove( type );
            }

            final String seedChars = seedMachine.charsOfType( type );
            final char nextChar = seedChars.charAt( pwmRandom.nextInt( seedChars.length() ) );
            password.append( nextChar );
        }

        while ( password.length() < desiredLength )
        {
            password.append( seedMachine.getRandomSeed() );
        }

        return password.toString();
    }
}
