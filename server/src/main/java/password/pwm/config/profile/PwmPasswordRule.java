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
import password.pwm.config.DomainConfig;
import password.pwm.config.PwmSetting;
import password.pwm.i18n.Message;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.java.JavaHelper;

import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.TreeMap;

/**
 * Password rules.
 *
 * @author Jason D. Rivard
 */
public enum PwmPasswordRule
{
    PolicyEnabled(
            ChaiPasswordRule.PolicyEnabled,
            null,
            ChaiPasswordRule.PolicyEnabled.getRuleType(),
            ChaiPasswordRule.PolicyEnabled.getDefaultValue(),
            Flag.positiveBooleanMerge ),

    MinimumLength(
            ChaiPasswordRule.MinimumLength,
            PwmSetting.PASSWORD_POLICY_MINIMUM_LENGTH,
            ChaiPasswordRule.MinimumLength.getRuleType(),
            ChaiPasswordRule.MinimumLength.getDefaultValue() ),

    MaximumLength(
            ChaiPasswordRule.MaximumLength,
            PwmSetting.PASSWORD_POLICY_MAXIMUM_LENGTH,
            ChaiPasswordRule.MaximumLength.getRuleType(),
            ChaiPasswordRule.MaximumLength.getDefaultValue() ),

    MinimumUpperCase(
            ChaiPasswordRule.MinimumUpperCase,
            PwmSetting.PASSWORD_POLICY_MINIMUM_UPPERCASE,
            ChaiPasswordRule.MinimumUpperCase.getRuleType(),
            ChaiPasswordRule.MinimumUpperCase.getDefaultValue() ),

    MaximumUpperCase(
            ChaiPasswordRule.MaximumUpperCase,
            PwmSetting.PASSWORD_POLICY_MAXIMUM_UPPERCASE,
            ChaiPasswordRule.MaximumUpperCase.getRuleType(),
            ChaiPasswordRule.MaximumUpperCase.getDefaultValue() ),

    MinimumLowerCase(
            ChaiPasswordRule.MinimumLowerCase,
            PwmSetting.PASSWORD_POLICY_MINIMUM_LOWERCASE,
            ChaiPasswordRule.MinimumLowerCase.getRuleType(),
            ChaiPasswordRule.MinimumLowerCase.getDefaultValue() ),

    MaximumLowerCase(
            ChaiPasswordRule.MaximumLowerCase,
            PwmSetting.PASSWORD_POLICY_MAXIMUM_LOWERCASE,
            ChaiPasswordRule.MaximumLowerCase.getRuleType(),
            ChaiPasswordRule.MaximumLowerCase.getDefaultValue() ),

    AllowNumeric(
            ChaiPasswordRule.AllowNumeric,
            PwmSetting.PASSWORD_POLICY_ALLOW_NUMERIC,
            ChaiPasswordRule.AllowNumeric.getRuleType(),
            ChaiPasswordRule.AllowNumeric.getDefaultValue() ),

    MinimumNumeric(
            ChaiPasswordRule.MinimumNumeric,
            PwmSetting.PASSWORD_POLICY_MINIMUM_NUMERIC,
            ChaiPasswordRule.MinimumNumeric.getRuleType(),
            ChaiPasswordRule.MinimumNumeric.getDefaultValue() ),

    MaximumNumeric(
            ChaiPasswordRule.MaximumNumeric,
            PwmSetting.PASSWORD_POLICY_MAXIMUM_NUMERIC,
            ChaiPasswordRule.MaximumNumeric.getRuleType(),
            ChaiPasswordRule.MaximumNumeric.getDefaultValue() ),

    MinimumUnique(
            ChaiPasswordRule.MinimumUnique,
            PwmSetting.PASSWORD_POLICY_MINIMUM_UNIQUE,
            ChaiPasswordRule.MinimumUnique.getRuleType(),
            ChaiPasswordRule.MinimumUnique.getDefaultValue() ),

    MaximumUnique(
            ChaiPasswordRule.MaximumUnique,
            null,
            ChaiPasswordRule.MaximumUnique.getRuleType(),
            ChaiPasswordRule.MaximumUnique.getDefaultValue() ),

    AllowFirstCharNumeric(
            ChaiPasswordRule.AllowFirstCharNumeric,
            PwmSetting.PASSWORD_POLICY_ALLOW_FIRST_CHAR_NUMERIC,
            ChaiPasswordRule.AllowFirstCharNumeric.getRuleType(),
            ChaiPasswordRule.AllowFirstCharNumeric.getDefaultValue() ),

    AllowLastCharNumeric(
            ChaiPasswordRule.AllowLastCharNumeric,
            PwmSetting.PASSWORD_POLICY_ALLOW_LAST_CHAR_NUMERIC,
            ChaiPasswordRule.AllowLastCharNumeric.getRuleType(),
            ChaiPasswordRule.AllowLastCharNumeric.getDefaultValue() ),

    AllowSpecial(
            ChaiPasswordRule.AllowSpecial,
            PwmSetting.PASSWORD_POLICY_ALLOW_SPECIAL,
            ChaiPasswordRule.AllowSpecial.getRuleType(),
            ChaiPasswordRule.AllowSpecial.getDefaultValue() ),

    MinimumSpecial(
            ChaiPasswordRule.MinimumSpecial,
            PwmSetting.PASSWORD_POLICY_MINIMUM_SPECIAL,
            ChaiPasswordRule.MinimumSpecial.getRuleType(),
            ChaiPasswordRule.MinimumSpecial.getDefaultValue() ),

    MaximumSpecial(
            ChaiPasswordRule.MaximumSpecial,
            PwmSetting.PASSWORD_POLICY_MAXIMUM_SPECIAL,
            ChaiPasswordRule.MaximumSpecial.getRuleType(),
            ChaiPasswordRule.MaximumSpecial.getDefaultValue() ),

    AllowFirstCharSpecial(
            ChaiPasswordRule.AllowFirstCharSpecial,
            PwmSetting.PASSWORD_POLICY_ALLOW_FIRST_CHAR_SPECIAL,
            ChaiPasswordRule.AllowFirstCharSpecial.getRuleType(),
            ChaiPasswordRule.AllowFirstCharSpecial.getDefaultValue() ),

    AllowLastCharSpecial(
            ChaiPasswordRule.AllowLastCharSpecial,
            PwmSetting.PASSWORD_POLICY_ALLOW_LAST_CHAR_SPECIAL,
            ChaiPasswordRule.AllowLastCharSpecial.getRuleType(),
            ChaiPasswordRule.AllowLastCharSpecial.getDefaultValue() ),

    MaximumRepeat(
            ChaiPasswordRule.MaximumRepeat,
            PwmSetting.PASSWORD_POLICY_MAXIMUM_REPEAT,
            ChaiPasswordRule.MaximumRepeat.getRuleType(),
            ChaiPasswordRule.MaximumRepeat.getDefaultValue() ),

    MaximumSequentialRepeat(
            ChaiPasswordRule.MaximumSequentialRepeat,
            PwmSetting.PASSWORD_POLICY_MAXIMUM_SEQUENTIAL_REPEAT,
            ChaiPasswordRule.MaximumSequentialRepeat.getRuleType(),
            ChaiPasswordRule.MaximumSequentialRepeat.getDefaultValue() ),

    ChangeMessage(
            ChaiPasswordRule.ChangeMessage,
            PwmSetting.PASSWORD_POLICY_CHANGE_MESSAGE,
            ChaiPasswordRule.ChangeMessage.getRuleType(),
            ChaiPasswordRule.ChangeMessage.getDefaultValue() ),

    ExpirationInterval(
            ChaiPasswordRule.ExpirationInterval,
            null,
            ChaiPasswordRule.ExpirationInterval.getRuleType(),
            ChaiPasswordRule.ExpirationInterval.getDefaultValue() ),

    MinimumLifetime(
            ChaiPasswordRule.MinimumLifetime,
            PwmSetting.PASSWORD_POLICY_MINIMUM_LIFETIME,
            ChaiPasswordRule.MinimumLifetime.getRuleType(),
            ChaiPasswordRule.MinimumLifetime.getDefaultValue() ),

    CaseSensitive(
            ChaiPasswordRule.CaseSensitive,
            null,
            ChaiPasswordRule.CaseSensitive.getRuleType(),
            ChaiPasswordRule.CaseSensitive.getDefaultValue(),
            Flag.positiveBooleanMerge ),

    EnforceAtLogin(
            ChaiPasswordRule.EnforceAtLogin,
            null,
            ChaiPasswordRule.EnforceAtLogin.getRuleType(),
            ChaiPasswordRule.EnforceAtLogin.getDefaultValue() ),

    ChallengeResponseEnabled(
            ChaiPasswordRule.ChallengeResponseEnabled,
            null,
            ChaiPasswordRule.ChallengeResponseEnabled.getRuleType(),
            ChaiPasswordRule.ChallengeResponseEnabled.getDefaultValue() ),

    UniqueRequired(
            ChaiPasswordRule.UniqueRequired,
            null,
            ChaiPasswordRule.UniqueRequired.getRuleType(),
            ChaiPasswordRule.UniqueRequired.getDefaultValue(),
            Flag.positiveBooleanMerge ),

    DisallowedValues(
            ChaiPasswordRule.DisallowedValues,
            PwmSetting.PASSWORD_POLICY_DISALLOWED_VALUES,
            ChaiPasswordRule.DisallowedValues.getRuleType(),
            ChaiPasswordRule.DisallowedValues.getDefaultValue() ),

    DisallowedAttributes(
            ChaiPasswordRule.DisallowedAttributes,
            PwmSetting.PASSWORD_POLICY_DISALLOWED_ATTRIBUTES,
            ChaiPasswordRule.DisallowedAttributes.getRuleType(),
            ChaiPasswordRule.DisallowedAttributes.getDefaultValue() ),

    DisallowCurrent(
            null,
            PwmSetting.PASSWORD_POLICY_DISALLOW_CURRENT,
            ChaiPasswordRule.RuleType.BOOLEAN,
            "false",
            Flag.positiveBooleanMerge ),

    AllowUserChange(
            ChaiPasswordRule.AllowUserChange,
            null,
            ChaiPasswordRule.AllowUserChange.getRuleType(),
            ChaiPasswordRule.AllowUserChange.getDefaultValue(),
            Flag.positiveBooleanMerge ),

    AllowAdminChange(
            ChaiPasswordRule.AllowAdminChange,
            null,
            ChaiPasswordRule.AllowAdminChange.getRuleType(),
            ChaiPasswordRule.AllowAdminChange.getDefaultValue(),
            Flag.positiveBooleanMerge ),

    ADComplexityMaxViolations(
            ChaiPasswordRule.ADComplexityMaxViolation,
            PwmSetting.PASSWORD_POLICY_AD_COMPLEXITY_MAX_VIOLATIONS,
            ChaiPasswordRule.ADComplexityMaxViolation.getRuleType(),
            ChaiPasswordRule.ADComplexityMaxViolation.getDefaultValue() ),

    AllowNonAlpha(
            null,
            PwmSetting.PASSWORD_POLICY_ALLOW_NON_ALPHA,
            ChaiPasswordRule.AllowNonAlpha.getRuleType(),
            ChaiPasswordRule.AllowNonAlpha.getDefaultValue() ),

    MinimumNonAlpha(
            null,
            PwmSetting.PASSWORD_POLICY_MINIMUM_NON_ALPHA,
            ChaiPasswordRule.RuleType.MIN,
            "0" ),

    MaximumNonAlpha(
            null,
            PwmSetting.PASSWORD_POLICY_MAXIMUM_NON_ALPHA,
            ChaiPasswordRule.RuleType.MAX,
            "0" ),

    // pwm specific rules
    // value will be imported indirectly from chai rule
    ADComplexityLevel(
            null,
            PwmSetting.PASSWORD_POLICY_AD_COMPLEXITY_LEVEL,
            ChaiPasswordRule.RuleType.OTHER,
            "NONE" ),

    MaximumOldChars(
            null,
            PwmSetting.PASSWORD_POLICY_MAXIMUM_OLD_PASSWORD_CHARS,
            ChaiPasswordRule.RuleType.NUMERIC,
            "" ),

    RegExMatch(
            null,
            PwmSetting.PASSWORD_POLICY_REGULAR_EXPRESSION_MATCH,
            ChaiPasswordRule.RuleType.OTHER,
            "" ),

    RegExNoMatch(
            null,
            PwmSetting.PASSWORD_POLICY_REGULAR_EXPRESSION_NOMATCH,
            ChaiPasswordRule.RuleType.OTHER,
            "" ),

    MinimumAlpha(
            null,
            PwmSetting.PASSWORD_POLICY_MINIMUM_ALPHA,
            ChaiPasswordRule.RuleType.MIN,
            "0" ),

    MaximumAlpha(
            null,
            PwmSetting.PASSWORD_POLICY_MAXIMUM_ALPHA,
            ChaiPasswordRule.RuleType.MAX,
            "0" ),

    EnableWordlist(
            null,
            PwmSetting.PASSWORD_POLICY_ENABLE_WORDLIST,
            ChaiPasswordRule.RuleType.BOOLEAN,
            "false",
            Flag.positiveBooleanMerge ),

    MinimumStrength(
            null,
            PwmSetting.PASSWORD_POLICY_MINIMUM_STRENGTH,
            ChaiPasswordRule.RuleType.MIN,
            "0" ),

    MaximumConsecutive(
            null,
            PwmSetting.PASSWORD_POLICY_MAXIMUM_CONSECUTIVE,
            ChaiPasswordRule.RuleType.MIN,
            "0" ),

    CharGroupsMinMatch(
            null,
            PwmSetting.PASSWORD_POLICY_CHAR_GROUPS_MIN_MATCH,
            ChaiPasswordRule.RuleType.MIN,
            "0" ),

    CharGroupsValues(
            null,
            PwmSetting.PASSWORD_POLICY_CHAR_GROUPS,
            ChaiPasswordRule.RuleType.OTHER,
            "" ),

    AllowMacroInRegExSetting(
            AppProperty.ALLOW_MACRO_IN_REGEX_SETTING,
            ChaiPasswordRule.RuleType.BOOLEAN,
            "true" ),;

    private final ChaiPasswordRule chaiPasswordRule;
    private final PwmSetting pwmSetting;
    private final AppProperty appProperty;
    private final ChaiPasswordRule.RuleType ruleType;
    private final String defaultValue;
    private final boolean positiveBooleanMerge;

    private enum Flag
    {
        positiveBooleanMerge,
    }

    PwmPasswordRule(
            final ChaiPasswordRule chaiPasswordRule,
            final PwmSetting pwmSetting,
            final ChaiPasswordRule.RuleType ruleType,
            final String defaultValue,
            final Flag... flags
    )
    {
        this.pwmSetting = pwmSetting;
        this.chaiPasswordRule = chaiPasswordRule;
        this.appProperty = null;
        this.ruleType = ruleType;
        this.defaultValue = defaultValue;
        this.positiveBooleanMerge = JavaHelper.enumArrayContainsValue( flags, Flag.positiveBooleanMerge );
    }

    PwmPasswordRule(
            final AppProperty appProperty,
            final ChaiPasswordRule.RuleType ruleType,
            final String defaultValue,
            final Flag... flags
    )
    {
        this.pwmSetting = null;
        this.chaiPasswordRule = null;
        this.appProperty = appProperty;
        this.ruleType = ruleType;
        this.defaultValue = defaultValue;
        this.positiveBooleanMerge = JavaHelper.enumArrayContainsValue( flags, Flag.positiveBooleanMerge );
    }

    public String getKey( )
    {
        if ( chaiPasswordRule != null )
        {
            return chaiPasswordRule.getKey();
        }
        if ( pwmSetting != null )
        {
            return pwmSetting.getKey();
        }
        if ( appProperty != null )
        {
            return appProperty.getKey();
        }

        return this.name();
    }

    public PwmSetting getPwmSetting( )
    {
        return pwmSetting;
    }

    public AppProperty getAppProperty( )
    {
        return appProperty;
    }

    public ChaiPasswordRule.RuleType getRuleType( )
    {
        return ruleType;
    }

    public String getDefaultValue( )
    {
        return defaultValue;
    }

    public boolean isPositiveBooleanMerge( )
    {
        return positiveBooleanMerge;
    }

    public String getLabel( final Locale locale, final DomainConfig config )
    {
        final String key = "Rule_" + this;
        try
        {
            return LocaleHelper.getLocalizedMessage( locale, key, config, Message.class );
        }
        catch ( final MissingResourceException e )
        {
            return "MissingKey-" + key;
        }
    }

    public static List<PwmPasswordRule> sortedByLabel ( final Locale locale, final DomainConfig config )
    {
        final TreeMap<String, PwmPasswordRule> sortedMap = new TreeMap<>();
        for ( final PwmPasswordRule rule : PwmPasswordRule.values() )
        {
            sortedMap.put( rule.getLabel( locale, config ), rule );
        }
        return List.copyOf( sortedMap.values() );
    }
}
