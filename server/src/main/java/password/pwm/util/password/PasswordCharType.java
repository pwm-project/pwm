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

import password.pwm.config.profile.PwmPasswordPolicy;
import password.pwm.config.profile.PwmPasswordRule;
import password.pwm.error.PwmError;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public enum PasswordCharType
{
    UPPERCASE(
            Character::isUpperCase,
            PwmError.PASSWORD_TOO_MANY_UPPER,
            PwmError.PASSWORD_NOT_ENOUGH_UPPER ),

    LOWERCASE(
            Character::isLowerCase,
            PwmError.PASSWORD_TOO_MANY_LOWER,
            PwmError.PASSWORD_NOT_ENOUGH_LOWER ),
    SPECIAL(
            character -> !Character.isLetterOrDigit( character ),
            PwmError.PASSWORD_TOO_MANY_SPECIAL,
            PwmError.PASSWORD_NOT_ENOUGH_SPECIAL ),
    NUMBER(
            Character::isDigit,
            PwmError.PASSWORD_TOO_MANY_NUMERIC,
            PwmError.PASSWORD_NOT_ENOUGH_NUM ),
    LETTER(
            Character::isLetter,
            PwmError.PASSWORD_TOO_MANY_ALPHA,
            PwmError.PASSWORD_NOT_ENOUGH_ALPHA ),
    NON_LETTER(
            character -> !Character.isLetter( character ),
            PwmError.PASSWORD_TOO_MANY_NONALPHA,
            PwmError.PASSWORD_NOT_ENOUGH_NONALPHA ),
    OTHER_LETTER(
            character -> Character.getType( character ) == Character.OTHER_LETTER,
            null,
            null );

    private final transient CharTester charTester;
    private final PwmError tooManyError;
    private final PwmError tooFewError;

    private static final Set<PasswordCharType> UNIQUE_TYPES = Set.of( UPPERCASE, LOWERCASE, SPECIAL, NUMBER );

    PasswordCharType(
            final CharTester charClassType,
            final PwmError tooManyError,
            final PwmError tooFewError
    )
    {
        this.charTester = charClassType;
        this.tooFewError = tooFewError;
        this.tooManyError = tooManyError;
    }

    boolean isCharType( final char character )
    {
        return charTester.isType( character );
    }

    public Optional<PwmError> getTooManyError()
    {
        return Optional.ofNullable( tooManyError );
    }

    public Optional<PwmError> getTooFewError()
    {
        return Optional.ofNullable( tooFewError );
    }

    public static String charsOfType( final String input, final PasswordCharType charType )
    {
        final CharTester charTester = charType.getCharTester();
        return charsOfTester( input, charTester );
    }

    public static String charsExceptOfType( final String input, final PasswordCharType charType )
    {
        final CharTester charTester = charType.getCharTester();
        final CharTester inverseTester = character -> !charTester.isType( character );
        return charsOfTester( input, inverseTester );
    }

    private static String charsOfTester( final String input, final CharTester charTester )
    {
        Objects.requireNonNull( input );
        Objects.requireNonNull( charTester );

        final int passwordLength = input.length();
        final StringBuilder sb = new StringBuilder();
        for ( int i = 0; i < passwordLength; i++ )
        {
            final char nextChar = input.charAt( i );
            if ( charTester.isType( nextChar ) )
            {
                sb.append( nextChar );
            }
        }
        return sb.toString();
    }

    private interface CharTester
    {
        boolean isType( char character );
    }

    private CharTester getCharTester()
    {
        return charTester;
    }

    public static Set<PasswordCharType> uniqueTypes()
    {
        return UNIQUE_TYPES;
    }

    public static Map<PasswordCharType, Integer> maxCharPerPolicy(
            final RandomGeneratorConfig randomGeneratorConfig,
            final PwmPasswordPolicy pwmPasswordPolicy
    )
    {
        final Map<PasswordCharType, Integer> returnMap = new EnumMap<>( PasswordCharType.class );
        final PasswordRuleReaderHelper ruleHelper = pwmPasswordPolicy.ruleHelper();

        for ( final CharTypeRuleAssociations charTypeRuleAssociations : CHAR_TYPE_RULE_ASSOCIATIONS_LIST )
        {
            returnMap.put( charTypeRuleAssociations.passwordCharType(), 0 );
            if ( charTypeRuleAssociations.allowRule() == null || ruleHelper.readBooleanValue( charTypeRuleAssociations.allowRule() ) )
            {
                final int maxOfType = ruleHelper.readIntValue( charTypeRuleAssociations.maxRule() );
                final int suggestedCount;
                if ( maxOfType > 0 )
                {
                    suggestedCount = Math.min( maxOfType, randomGeneratorConfig.maximumLength() );
                }
                else
                {

                    suggestedCount = randomGeneratorConfig.minimumLength();
                }
                returnMap.put( charTypeRuleAssociations.passwordCharType(), suggestedCount );
            }
        }

        return Map.copyOf( returnMap );
    }

    private static final List<CharTypeRuleAssociations> CHAR_TYPE_RULE_ASSOCIATIONS_LIST = List.of(
            new CharTypeRuleAssociations( PasswordCharType.UPPERCASE, null, PwmPasswordRule.MinimumUpperCase, PwmPasswordRule.MaximumUpperCase ),
            new CharTypeRuleAssociations( PasswordCharType.LOWERCASE, null, PwmPasswordRule.MinimumLowerCase, PwmPasswordRule.MaximumLowerCase ),
            new CharTypeRuleAssociations( PasswordCharType.NUMBER, PwmPasswordRule.AllowNumeric, PwmPasswordRule.MinimumNumeric, PwmPasswordRule.MaximumNumeric ),
            new CharTypeRuleAssociations( PasswordCharType.SPECIAL, PwmPasswordRule.AllowSpecial, PwmPasswordRule.MinimumSpecial, PwmPasswordRule.MaximumSpecial ) );

    private record CharTypeRuleAssociations(
            PasswordCharType passwordCharType,
            PwmPasswordRule allowRule,
            PwmPasswordRule minRule,
            PwmPasswordRule maxRule
    )
    {
    }

}
