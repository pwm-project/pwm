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

import password.pwm.bean.SessionLabel;
import password.pwm.config.DomainConfig;
import password.pwm.config.option.ADPolicyComplexity;
import password.pwm.config.profile.PwmPasswordPolicy;
import password.pwm.config.profile.PwmPasswordRule;
import password.pwm.i18n.Display;
import password.pwm.i18n.Message;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroRequest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;

/**
 * That is one long class name.  But it does what it says.
 */
public class PasswordRequirementViewableRuleGenerator
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( PasswordRequirementViewableRuleGenerator.class );

    private static final String UNKNOWN_MESSAGE_STRING = "UNKNOWN MESSAGE STRING";

    private static final List<RuleTextGenerator> GENERATORS
            = JavaHelper.instancesOfSealedInterface( RuleTextGenerator.class );

    public static List<String> generate(
            final PwmPasswordPolicy passwordPolicy,
            final DomainConfig config,
            final Locale locale,
            final MacroRequest macroRequest
    )
    {
        final GeneratorContext policyValues = new GeneratorContext( passwordPolicy, passwordPolicy.ruleHelper(), locale, config, macroRequest );


        return GENERATORS.stream()
                .map( gen -> gen.generate( policyValues ) )
                .flatMap( Collection::stream )
                .toList();
    }

    private sealed interface RuleTextGenerator
    {
        List<String> generate( GeneratorContext policyValues );
    }

    private record GeneratorContext(
            PwmPasswordPolicy passwordPolicy,
            PasswordRuleReaderHelper ruleHelper,
            Locale locale,
            DomainConfig config,
            MacroRequest macroRequest
    )
    {
    }

    private static final class CaseSensitiveRuleTextGenerator implements RuleTextGenerator
    {
        @Override
        public List<String> generate( final GeneratorContext policyValues )
        {
            if ( policyValues.ruleHelper().readBooleanValue( PwmPasswordRule.CaseSensitive ) )
            {
                return Collections.singletonList( getLocalString( Message.Requirement_CaseSensitive, null, policyValues ) );
            }
            else
            {
                return Collections.singletonList( getLocalString( Message.Requirement_NotCaseSensitive, null, policyValues ) );
            }
        }
    }

    private static final class MinLengthRuleTextGenerator implements RuleTextGenerator
    {
        @Override
        public List<String> generate( final GeneratorContext policyValues )
        {
            int value = policyValues.ruleHelper().readIntValue( PwmPasswordRule.MinimumLength );
            final ADPolicyComplexity adPolicyLevel = policyValues.ruleHelper().getADComplexityLevel();

            if ( adPolicyLevel == ADPolicyComplexity.AD2003 || adPolicyLevel == ADPolicyComplexity.AD2008 )
            {
                if ( value < 6 )
                {
                    value = 6;
                }
            }
            if ( value > 0 )
            {
                return Collections.singletonList( getLocalString( Message.Requirement_MinLength, value, policyValues ) );
            }
            return Collections.emptyList();
        }
    }

    private static final class MaxLengthRuleTextGenerator implements RuleTextGenerator
    {
        @Override
        public List<String> generate( final GeneratorContext policyValues )
        {
            final int value = policyValues.ruleHelper().readIntValue( PwmPasswordRule.MaximumLength );
            if ( value > 0 && value < 64 )
            {
                return Collections.singletonList( getLocalString( Message.Requirement_MaxLength, value, policyValues ) );
            }
            return Collections.emptyList();
        }
    }

    private static final class MinAlphaRuleTextGenerator implements RuleTextGenerator
    {
        @Override
        public List<String> generate( final GeneratorContext policyValues )
        {
            final int value = policyValues.ruleHelper().readIntValue( PwmPasswordRule.MinimumAlpha );
            if ( value > 0 )
            {
                return Collections.singletonList( getLocalString( Message.Requirement_MinAlpha, value, policyValues ) );
            }
            return Collections.emptyList();
        }
    }

    private static final class MaxAlphaRuleTextGenerator implements RuleTextGenerator
    {
        @Override
        public List<String> generate( final GeneratorContext policyValues )
        {
            final int value = policyValues.ruleHelper().readIntValue( PwmPasswordRule.MaximumAlpha );
            if ( value > 0 )
            {
                return Collections.singletonList( getLocalString( Message.Requirement_MaxAlpha, value, policyValues ) );
            }
            return Collections.emptyList();
        }
    }

    private static final class NumericCharsRuleTextGenerator implements RuleTextGenerator
    {
        @Override
        public List<String> generate( final GeneratorContext policyValues )
        {
            final PasswordRuleReaderHelper ruleHelper = policyValues.ruleHelper();
            if ( !ruleHelper.readBooleanValue( PwmPasswordRule.AllowNumeric ) )
            {
                return Collections.singletonList( getLocalString( Message.Requirement_AllowNumeric, null, policyValues ) );
            }
            else
            {
                final List<String> returnValues = new ArrayList<>(  );
                final int minValue = ruleHelper.readIntValue( PwmPasswordRule.MinimumNumeric );
                if ( minValue > 0 )
                {
                    returnValues.add( getLocalString( Message.Requirement_MinNumeric, minValue, policyValues ) );
                }

                final int maxValue = ruleHelper.readIntValue( PwmPasswordRule.MaximumNumeric );
                if ( maxValue > 0 )
                {
                    returnValues.add( getLocalString( Message.Requirement_MaxNumeric, maxValue, policyValues ) );
                }

                if ( !ruleHelper.readBooleanValue( PwmPasswordRule.AllowFirstCharNumeric ) )
                {
                    returnValues.add( getLocalString( Message.Requirement_FirstNumeric, maxValue, policyValues ) );
                }

                if ( !ruleHelper.readBooleanValue( PwmPasswordRule.AllowLastCharNumeric ) )
                {
                    returnValues.add( getLocalString( Message.Requirement_LastNumeric, maxValue, policyValues ) );
                }
                return returnValues;
            }
        }
    }

    private static final class SpecialCharsRuleTextGenerator implements RuleTextGenerator
    {
        @Override
        public List<String> generate( final GeneratorContext policyValues )
        {
            final PasswordRuleReaderHelper ruleHelper = policyValues.ruleHelper();
            if ( !ruleHelper.readBooleanValue( PwmPasswordRule.AllowSpecial ) )
            {
                return Collections.singletonList( getLocalString( Message.Requirement_AllowSpecial, null, policyValues ) );
            }
            else
            {
                final List<String> returnValues = new ArrayList<>(  );
                final int minValue = ruleHelper.readIntValue( PwmPasswordRule.MinimumSpecial );
                if ( minValue > 0 )
                {
                    returnValues.add( getLocalString( Message.Requirement_MinSpecial, minValue, policyValues ) );
                }

                final int maxValue = ruleHelper.readIntValue( PwmPasswordRule.MaximumSpecial );
                if ( maxValue > 0 )
                {
                    returnValues.add( getLocalString( Message.Requirement_MaxSpecial, maxValue, policyValues ) );
                }

                if ( !ruleHelper.readBooleanValue( PwmPasswordRule.AllowFirstCharSpecial ) )
                {
                    returnValues.add( getLocalString( Message.Requirement_FirstSpecial, maxValue, policyValues ) );
                }

                if ( !ruleHelper.readBooleanValue( PwmPasswordRule.AllowLastCharSpecial ) )
                {
                    returnValues.add( getLocalString( Message.Requirement_LastSpecial, maxValue, policyValues ) );
                }
                return Collections.unmodifiableList( returnValues );
            }
        }
    }

    private static final class MaximumRepeatRuleTextGenerator implements RuleTextGenerator
    {
        @Override
        public List<String> generate( final GeneratorContext policyValues )
        {
            final int value = policyValues.ruleHelper().readIntValue( PwmPasswordRule.MaximumRepeat );
            if ( value > 0 )
            {
                return Collections.singletonList( getLocalString( Message.Requirement_MaxRepeat, value, policyValues ) );
            }
            return Collections.emptyList();
        }
    }

    private static final class MaximumSequentialRepeatRuleTextGenerator implements RuleTextGenerator
    {
        @Override
        public List<String> generate( final GeneratorContext policyValues )
        {
            final int value = policyValues.ruleHelper().readIntValue( PwmPasswordRule.MaximumSequentialRepeat );
            if ( value > 0 )
            {
                return Collections.singletonList( getLocalString( Message.Requirement_MaxSeqRepeat, value, policyValues ) );
            }
            return Collections.emptyList();
        }
    }

    private static final class MinimumLowerRuleTextGenerator implements RuleTextGenerator
    {
        @Override
        public List<String> generate( final GeneratorContext policyValues )
        {
            final int value = policyValues.ruleHelper().readIntValue( PwmPasswordRule.MinimumLowerCase );
            if ( value > 0 )
            {
                return Collections.singletonList( getLocalString( Message.Requirement_MinLower, value, policyValues ) );
            }
            return Collections.emptyList();
        }
    }

    private static final class MaximumLowerRuleTextGenerator implements RuleTextGenerator
    {
        @Override
        public List<String> generate( final GeneratorContext policyValues )
        {
            final int value = policyValues.ruleHelper().readIntValue( PwmPasswordRule.MaximumLowerCase );
            if ( value > 0 )
            {
                return Collections.singletonList( getLocalString( Message.Requirement_MaxLower, value, policyValues ) );
            }
            return Collections.emptyList();
        }
    }

    private static final class MinimumUpperRuleTextGenerator implements RuleTextGenerator
    {
        @Override
        public List<String> generate( final GeneratorContext policyValues )
        {
            final int value = policyValues.ruleHelper().readIntValue( PwmPasswordRule.MinimumUpperCase );
            if ( value > 0 )
            {
                return Collections.singletonList( getLocalString( Message.Requirement_MinUpper, value, policyValues ) );
            }
            return Collections.emptyList();
        }
    }

    private static final class MaximumUpperRuleTextGenerator implements RuleTextGenerator
    {
        @Override
        public List<String> generate( final GeneratorContext policyValues )
        {
            final int value = policyValues.ruleHelper().readIntValue( PwmPasswordRule.MaximumUpperCase );
            if ( value > 0 )
            {
                return Collections.singletonList( getLocalString( Message.Requirement_MaxUpper, value, policyValues ) );
            }
            return Collections.emptyList();
        }
    }

    private static final class MinimumUniqueRuleTextGenerator implements RuleTextGenerator
    {
        @Override
        public List<String> generate( final GeneratorContext policyValues )
        {
            final int value = policyValues.ruleHelper().readIntValue( PwmPasswordRule.MinimumUnique );
            if ( value > 0 )
            {
                return Collections.singletonList( getLocalString( Message.Requirement_MinUnique, value, policyValues ) );
            }
            return Collections.emptyList();
        }
    }

    private static final class DisallowedValuesRuleTextGenerator implements RuleTextGenerator
    {
        @Override
        public List<String> generate( final GeneratorContext policyValues )
        {
            final List<String> setValue = policyValues.ruleHelper().getDisallowedValues();
            if ( !setValue.isEmpty() )
            {
                final StringBuilder fieldValue = new StringBuilder();
                for ( final String loopValue : setValue )
                {
                    fieldValue.append( ' ' );

                    final String expandedValue = policyValues.macroRequest().expandMacros( loopValue );
                    fieldValue.append( StringUtil.escapeHtml( expandedValue ) );
                }
                return Collections.singletonList( getLocalString( Message.Requirement_DisAllowedValues, fieldValue.toString(), policyValues ) );
            }
            return Collections.emptyList();
        }
    }

    private static final class WordlistRuleTextGenerator implements RuleTextGenerator
    {
        @Override
        public List<String> generate( final GeneratorContext policyValues )
        {
            if ( policyValues.ruleHelper().readBooleanValue( PwmPasswordRule.EnableWordlist ) )
            {
                return Collections.singletonList( getLocalString( Message.Requirement_WordList, "", policyValues ) );
            }
            return Collections.emptyList();
        }
    }

    private static final class DisallowedAttributesRuleTextGenerator implements RuleTextGenerator
    {
        @Override
        public List<String> generate( final GeneratorContext policyValues )
        {
            final List<String> setValue = policyValues.ruleHelper().getDisallowedAttributes();
            final ADPolicyComplexity adPolicyLevel = policyValues.ruleHelper().getADComplexityLevel();
            if ( !setValue.isEmpty() || adPolicyLevel == ADPolicyComplexity.AD2003 )
            {
                return Collections.singletonList(  getLocalString( Message.Requirement_DisAllowedAttributes, "", policyValues ) );
            }
            return Collections.emptyList();
        }
    }

    private static final class MaximumOldCharsRuleTextGenerator implements RuleTextGenerator
    {
        @Override
        public List<String> generate( final GeneratorContext policyValues )
        {
            final int value = policyValues.ruleHelper().readIntValue( PwmPasswordRule.MaximumOldChars );
            if ( value > 0 )
            {
                return Collections.singletonList( getLocalString( Message.Requirement_OldChar, value, policyValues ) );
            }
            return Collections.emptyList();
        }
    }

    private static final class MinimumLifetimeRuleTextGenerator implements RuleTextGenerator
    {
        @Override
        public List<String> generate( final GeneratorContext policyValues )
        {
            final int value = policyValues.ruleHelper().readIntValue( PwmPasswordRule.MinimumLifetime );

            if ( value <= 0 )
            {
                return Collections.emptyList();
            }

            final int secondsPerDay = 60 * 60 * 24;

            final String durationStr;
            if ( value % secondsPerDay == 0 )
            {
                final int valueAsDays = value / ( 60 * 60 * 24 );
                final Display key = valueAsDays <= 1 ? Display.Display_Day : Display.Display_Days;
                durationStr = valueAsDays + " " + LocaleHelper.getLocalizedMessage( policyValues.locale(), key, policyValues.config() );
            }
            else
            {
                final int valueAsHours = value / ( 60 * 60 );
                final Display key = valueAsHours <= 1 ? Display.Display_Hour : Display.Display_Hours;
                durationStr = valueAsHours + " " + LocaleHelper.getLocalizedMessage( policyValues.locale(), key, policyValues.config() );
            }

            final String userMsg = Message.getLocalizedMessage( policyValues.locale(), Message.Requirement_MinimumFrequency, policyValues.config(), durationStr );
            return Collections.singletonList( userMsg );
        }
    }

    private static final class ADRuleTextGenerator implements RuleTextGenerator
    {
        @Override
        public List<String> generate( final GeneratorContext policyValues )
        {
            final ADPolicyComplexity adPolicyLevel = policyValues.ruleHelper().getADComplexityLevel();
            if ( adPolicyLevel == ADPolicyComplexity.AD2003 )
            {
                return Collections.singletonList( getLocalString( Message.Requirement_ADComplexity, "", policyValues ) );
            }
            else if ( adPolicyLevel == ADPolicyComplexity.AD2008 )
            {
                final int maxViolations = policyValues.ruleHelper().readIntValue( PwmPasswordRule.ADComplexityMaxViolations );
                final int minGroups = 5 - maxViolations;
                return Collections.singletonList( getLocalString( Message.Requirement_ADComplexity2008, String.valueOf( minGroups ), policyValues ) );
            }
            return Collections.emptyList();
        }
    }

    private static final class UniqueRequiredRuleTextGenerator implements RuleTextGenerator
    {
        @Override
        public List<String> generate( final GeneratorContext policyValues )
        {
            if ( policyValues.ruleHelper().readBooleanValue( PwmPasswordRule.UniqueRequired ) )
            {
                return Collections.singletonList( getLocalString( Message.Requirement_UniqueRequired, "", policyValues ) );
            }
            return Collections.emptyList();
        }
    }

    private static String getLocalString( final Message message, final int size, final GeneratorContext policyValues )
    {
        final Message effectiveMessage = size > 1 && message.getPluralMessage() != null
                ? message.getPluralMessage()
                : message;

        try
        {
            return Message.getLocalizedMessage( policyValues.locale(), effectiveMessage, policyValues.config(), String.valueOf( size ) );
        }
        catch ( final MissingResourceException e )
        {
            LOGGER.error( SessionLabel.SYSTEM_LABEL, () -> "unable to display requirement tag for message '"
                    + message.toString() + "': " + e.getMessage() );
        }
        return UNKNOWN_MESSAGE_STRING;
    }

    private static String getLocalString( final Message message, final String field, final GeneratorContext policyValues )
    {
        try
        {
            return Message.getLocalizedMessage( policyValues.locale(), message, policyValues.config(), field );
        }
        catch ( final MissingResourceException e )
        {
            LOGGER.error(  SessionLabel.SYSTEM_LABEL, () -> "unable to display requirement tag for message '"
                    + message.toString() + "': " + e.getMessage() );
        }
        return UNKNOWN_MESSAGE_STRING;
    }
}
