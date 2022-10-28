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

package password.pwm.config.profile;

import com.novell.ldapchai.ChaiPasswordRule;
import password.pwm.AppProperty;
import password.pwm.PwmConstants;
import password.pwm.config.DomainConfig;
import password.pwm.config.PwmSetting;
import password.pwm.config.SettingReader;
import password.pwm.config.option.ADPolicyComplexity;
import password.pwm.util.java.EnumUtil;
import password.pwm.util.java.StringUtil;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

class PwmPasswordRuleFunctions
{
    static final RuleMergeFunction DEFAULT_RULE_MERGE_SINGLETON = new DefaultRuleMerge();
    static final Map<PwmPasswordRule, RuleMergeFunction> RULE_MERGE_FUNCTIONS = makeMergeRuleFunctions();
    static final NewRuleValueFunction DEFAULT_NEW_RULE_VALUE_FUNCTION = new DefaultNewRuleValue();
    static final Map<PwmPasswordRule, NewRuleValueFunction> NEW_RULE_VALUE_FUNCTION = makeNewRuleFunctions();

    interface RuleMergeFunction
    {
        Optional<String> apply( PwmPasswordRule rule, String value1, String value2 );
    }

    interface NewRuleValueFunction
    {
        Optional<String> apply( DomainConfig domainConfig, SettingReader settingReader, PwmPasswordRule pwmPasswordRule );
    }

    private static Map<PwmPasswordRule, RuleMergeFunction> makeMergeRuleFunctions()
    {
        final StringGroupRuleMerge stringGroupRuleMerge = new StringGroupRuleMerge();
        final MinimumIntervalRuleMerge minimumIntervalRuleMerge = new MinimumIntervalRuleMerge();

        final Map<PwmPasswordRule, RuleMergeFunction> map = new EnumMap<>( PwmPasswordRule.class );
        map.put( PwmPasswordRule.ADComplexityLevel, new AdComplexityLevelMerge() );
        map.put( PwmPasswordRule.ChangeMessage, new ChangePasswordMessageMerge() );
        map.put( PwmPasswordRule.DisallowedValues, stringGroupRuleMerge );
        map.put( PwmPasswordRule.DisallowedAttributes, stringGroupRuleMerge );
        map.put( PwmPasswordRule.RegExMatch, stringGroupRuleMerge );
        map.put( PwmPasswordRule.RegExNoMatch, stringGroupRuleMerge );
        map.put( PwmPasswordRule.CharGroupsValues, stringGroupRuleMerge );
        map.put( PwmPasswordRule.ExpirationInterval, minimumIntervalRuleMerge );
        map.put( PwmPasswordRule.MinimumLifetime, minimumIntervalRuleMerge );
        return Collections.unmodifiableMap( map );
    }

    private static Map<PwmPasswordRule, NewRuleValueFunction> makeNewRuleFunctions()
    {
        final NewLineSeperatedMultiStringNewRuleValue newLineSeperatedMultiStringNewRuleValue = new NewLineSeperatedMultiStringNewRuleValue();
        final ColonSeperatedMultiStringNewRuleValue colonSeperatedMultiStringNewRuleValue = new ColonSeperatedMultiStringNewRuleValue();

        final Map<PwmPasswordRule, NewRuleValueFunction> map = new EnumMap<>( PwmPasswordRule.class );
        map.put( PwmPasswordRule.DisallowedAttributes, newLineSeperatedMultiStringNewRuleValue );
        map.put( PwmPasswordRule.DisallowedValues, newLineSeperatedMultiStringNewRuleValue );
        map.put( PwmPasswordRule.CharGroupsValues, newLineSeperatedMultiStringNewRuleValue );
        map.put( PwmPasswordRule.RegExMatch, colonSeperatedMultiStringNewRuleValue );
        map.put( PwmPasswordRule.RegExNoMatch, colonSeperatedMultiStringNewRuleValue );
        map.put( PwmPasswordRule.ChangeMessage, new ChangePasswordNewRuleValue() );
        map.put( PwmPasswordRule.ADComplexityLevel, new AdComplexityNewRuleValue() );
        map.put( PwmPasswordRule.AllowMacroInRegExSetting, new AllowMacroInRegExNewRuleValue() );
        return Collections.unmodifiableMap( map );
    }

    private static class NewLineSeperatedMultiStringNewRuleValue implements NewRuleValueFunction
    {
        @Override
        public Optional<String> apply(
                final DomainConfig domainConfig,
                final SettingReader settingReader,
                final PwmPasswordRule pwmPasswordRule
        )
        {
            return Optional.of( StringUtil.collectionToString(
                    settingReader.readSettingAsStringArray( pwmPasswordRule.getPwmSetting() ), "\n" ) );
        }
    }

    private static class ColonSeperatedMultiStringNewRuleValue implements NewRuleValueFunction
    {
        @Override
        public Optional<String> apply(
                final DomainConfig domainConfig,
                final SettingReader settingReader,
                final PwmPasswordRule pwmPasswordRule
        )
        {
            return Optional.of( StringUtil.collectionToString(
                    settingReader.readSettingAsStringArray( pwmPasswordRule.getPwmSetting() ), ";;;" ) );
        }
    }

    private static class ChangePasswordNewRuleValue implements NewRuleValueFunction
    {
        @Override
        public Optional<String> apply(
                final DomainConfig domainConfig,
                final SettingReader settingReader,
                final PwmPasswordRule pwmPasswordRule
        )
        {
            final String settingValue = settingReader.readSettingAsLocalizedString( pwmPasswordRule.getPwmSetting(), PwmConstants.DEFAULT_LOCALE );
            return Optional.of( settingValue == null ? "" : settingValue );
        }
    }

    private static class AdComplexityNewRuleValue implements NewRuleValueFunction
    {
        @Override
        public Optional<String> apply(
                final DomainConfig domainConfig,
                final SettingReader settingReader,
                final PwmPasswordRule pwmPasswordRule
        )
        {
            return Optional.of( settingReader.readSettingAsEnum( pwmPasswordRule.getPwmSetting(), ADPolicyComplexity.class ).toString() );
        }
    }

    private static class AllowMacroInRegExNewRuleValue implements NewRuleValueFunction
    {
        @Override
        public Optional<String> apply(
                final DomainConfig domainConfig,
                final SettingReader settingReader,
                final PwmPasswordRule pwmPasswordRule
        )
        {
            return Optional.of( domainConfig.readAppProperty( AppProperty.ALLOW_MACRO_IN_REGEX_SETTING ) );
        }
    }

    private static class DefaultNewRuleValue implements NewRuleValueFunction
    {
        @Override
        public Optional<String> apply(
                final DomainConfig domainConfig,
                final SettingReader settingReader,
                final PwmPasswordRule pwmPasswordRule
        )
        {
            final PwmSetting pwmSetting = pwmPasswordRule.getPwmSetting();
            final ChaiPasswordRule.RuleType ruleType = pwmPasswordRule.getRuleType();

            if ( ruleType == ChaiPasswordRule.RuleType.MAX
                    || ruleType == ChaiPasswordRule.RuleType.MIN
                    || ruleType == ChaiPasswordRule.RuleType.NUMERIC
            )
            {
                return Optional.of( String.valueOf( settingReader.readSettingAsLong( pwmSetting ) ) );
            }
            else if ( ruleType == ChaiPasswordRule.RuleType.BOOLEAN )
            {
                return Optional.of( String.valueOf( settingReader.readSettingAsBoolean( pwmSetting ) ) );
            }

            return Optional.of( settingReader.readSettingAsString( pwmSetting ) );
        }
    }

    private static class AdComplexityLevelMerge implements RuleMergeFunction
    {
        @Override
        public Optional<String> apply( final PwmPasswordRule rule, final String value1, final String value2 )
        {
            final TreeSet<ADPolicyComplexity> seenValues = new TreeSet<>();
            seenValues.add( EnumUtil.readEnumFromString( ADPolicyComplexity.class, value1 ).orElse( ADPolicyComplexity.NONE ) );
            seenValues.add( EnumUtil.readEnumFromString( ADPolicyComplexity.class, value2 ).orElse( ADPolicyComplexity.NONE ) );
            return Optional.of( seenValues.last().name() );
        }
    }

    private static class ChangePasswordMessageMerge implements RuleMergeFunction
    {
        @Override
        public Optional<String> apply( final PwmPasswordRule rule, final String value1, final String value2 )
        {
            return Optional.of( StringUtil.isEmpty( value1 ) ? value2 : value1 );
        }
    }

    private static class StringGroupRuleMerge implements RuleMergeFunction
    {
        @Override
        public Optional<String> apply( final PwmPasswordRule rule, final String value1, final String value2 )
        {
            final String separator = ( rule == PwmPasswordRule.RegExMatch || rule == PwmPasswordRule.RegExNoMatch ) ? ";;;" : "\n";
            final Set<String> combinedSet = new LinkedHashSet<>();
            combinedSet.addAll( StringUtil.tokenizeString( value1, separator ) );
            combinedSet.addAll( StringUtil.tokenizeString( value2, separator ) );
            return Optional.of( StringUtil.collectionToString( combinedSet, separator ) );
        }
    }

    private static class MinimumIntervalRuleMerge implements RuleMergeFunction
    {
        @Override
        public Optional<String> apply( final PwmPasswordRule rule, final String value1, final String value2 )
        {
            final String minLocalValue = StringUtil.defaultString( value1, rule.getDefaultValue() );
            final String minOtherValue = StringUtil.defaultString( value2, rule.getDefaultValue() );
            return Optional.of( mergeMin( minLocalValue, minOtherValue ) );
        }
    }

    private static class DefaultRuleMerge implements RuleMergeFunction
    {
        @Override
        public Optional<String> apply( final PwmPasswordRule rule, final String value1, final String value2 )
        {
            final String localValueString = StringUtil.defaultString( value1, rule.getDefaultValue() );
            final String otherValueString = StringUtil.defaultString( value2, rule.getDefaultValue() );

            final ChaiPasswordRule.RuleType ruleType = rule.getRuleType();

            if ( ruleType == ChaiPasswordRule.RuleType.MIN )
            {
                return Optional.of( mergeMin( localValueString, otherValueString ) );
            }
            else if ( ruleType == ChaiPasswordRule.RuleType.MAX )
            {
                return Optional.of( mergeMax( localValueString, otherValueString ) );
            }
            else if ( ruleType == ChaiPasswordRule.RuleType.BOOLEAN )
            {
                final boolean localValue = StringUtil.convertStrToBoolean( localValueString );
                final boolean otherValue = StringUtil.convertStrToBoolean( otherValueString );

                if ( rule.isPositiveBooleanMerge() )
                {
                    return Optional.of( String.valueOf( localValue || otherValue ) );
                }
                else
                {
                    return Optional.of( String.valueOf( localValue && otherValue ) );
                }
            }

            return Optional.empty();
        }
    }

    protected static String mergeMin( final String value1, final String value2 )
    {
        final int iValue1 = StringUtil.convertStrToInt( value1, 0 );
        final int iValue2 = StringUtil.convertStrToInt( value2, 0 );

        // take the largest value
        return iValue1 > iValue2 ? value1 : value2;
    }

    protected static String mergeMax( final String value1, final String value2 )
    {
        final int iValue1 = StringUtil.convertStrToInt( value1, 0 );
        final int iValue2 = StringUtil.convertStrToInt( value2, 0 );

        final String returnValue;

        // if one of the values is zero, take the other one.
        if ( iValue1 == 0 || iValue2 == 0 )
        {
            returnValue = iValue1 > iValue2 ? value1 : value2;

            // else take the smaller value
        }
        else
        {
            returnValue = iValue1 < iValue2 ? value1 : value2;
        }

        return returnValue;
    }
}
