/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package password.pwm.config.profile;

import com.novell.ldapchai.ChaiPasswordRule;
import password.pwm.AppProperty;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.i18n.Message;
import password.pwm.util.LocaleHelper;
import password.pwm.util.logging.PwmLogger;

import java.util.HashSet;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Set;

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
            true ),

    MinimumLength(
            ChaiPasswordRule.MinimumLength,
            PwmSetting.PASSWORD_POLICY_MINIMUM_LENGTH,
            ChaiPasswordRule.MinimumLength.getRuleType(),
            ChaiPasswordRule.MinimumLength.getDefaultValue(),
            false ),

    MaximumLength(
            ChaiPasswordRule.MaximumLength,
            PwmSetting.PASSWORD_POLICY_MAXIMUM_LENGTH,
            ChaiPasswordRule.MaximumLength.getRuleType(),
            ChaiPasswordRule.MaximumLength.getDefaultValue(),
            false ),

    MinimumUpperCase(
            ChaiPasswordRule.MinimumUpperCase,
            PwmSetting.PASSWORD_POLICY_MINIMUM_UPPERCASE,
            ChaiPasswordRule.MinimumUpperCase.getRuleType(),
            ChaiPasswordRule.MinimumUpperCase.getDefaultValue(),
            false ),

    MaximumUpperCase(
            ChaiPasswordRule.MaximumUpperCase,
            PwmSetting.PASSWORD_POLICY_MAXIMUM_UPPERCASE,
            ChaiPasswordRule.MaximumUpperCase.getRuleType(),
            ChaiPasswordRule.MaximumUpperCase.getDefaultValue(),
            false ),

    MinimumLowerCase(
            ChaiPasswordRule.MinimumLowerCase,
            PwmSetting.PASSWORD_POLICY_MINIMUM_LOWERCASE,
            ChaiPasswordRule.MinimumLowerCase.getRuleType(),
            ChaiPasswordRule.MinimumLowerCase.getDefaultValue(),
            false ),

    MaximumLowerCase(
            ChaiPasswordRule.MaximumLowerCase,
            PwmSetting.PASSWORD_POLICY_MAXIMUM_LOWERCASE,
            ChaiPasswordRule.MaximumLowerCase.getRuleType(),
            ChaiPasswordRule.MaximumLowerCase.getDefaultValue(),
            false ),

    AllowNumeric(
            ChaiPasswordRule.AllowNumeric,
            PwmSetting.PASSWORD_POLICY_ALLOW_NUMERIC,
            ChaiPasswordRule.AllowNumeric.getRuleType(),
            ChaiPasswordRule.AllowNumeric.getDefaultValue(),
            false ),

    MinimumNumeric(
            ChaiPasswordRule.MinimumNumeric,
            PwmSetting.PASSWORD_POLICY_MINIMUM_NUMERIC,
            ChaiPasswordRule.MinimumNumeric.getRuleType(),
            ChaiPasswordRule.MinimumNumeric.getDefaultValue(),
            false ),

    MaximumNumeric(
            ChaiPasswordRule.MaximumNumeric,
            PwmSetting.PASSWORD_POLICY_MAXIMUM_NUMERIC,
            ChaiPasswordRule.MaximumNumeric.getRuleType(),
            ChaiPasswordRule.MaximumNumeric.getDefaultValue(),
            false ),

    MinimumUnique(
            ChaiPasswordRule.MinimumUnique,
            PwmSetting.PASSWORD_POLICY_MINIMUM_UNIQUE,
            ChaiPasswordRule.MinimumUnique.getRuleType(),
            ChaiPasswordRule.MinimumUnique.getDefaultValue(),
            false ),

    MaximumUnique(
            ChaiPasswordRule.MaximumUnique,
            null,
            ChaiPasswordRule.MaximumUnique.getRuleType(),
            ChaiPasswordRule.MaximumUnique.getDefaultValue(),
            false ),

    AllowFirstCharNumeric(
            ChaiPasswordRule.AllowFirstCharNumeric,
            PwmSetting.PASSWORD_POLICY_ALLOW_FIRST_CHAR_NUMERIC,
            ChaiPasswordRule.AllowFirstCharNumeric.getRuleType(),
            ChaiPasswordRule.AllowFirstCharNumeric.getDefaultValue(),
            false ),

    AllowLastCharNumeric(
            ChaiPasswordRule.AllowLastCharNumeric,
            PwmSetting.PASSWORD_POLICY_ALLOW_LAST_CHAR_NUMERIC,
            ChaiPasswordRule.AllowLastCharNumeric.getRuleType(),
            ChaiPasswordRule.AllowLastCharNumeric.getDefaultValue(),
            false ),

    AllowSpecial(
            ChaiPasswordRule.AllowSpecial,
            PwmSetting.PASSWORD_POLICY_ALLOW_SPECIAL,
            ChaiPasswordRule.AllowSpecial.getRuleType(),
            ChaiPasswordRule.AllowSpecial.getDefaultValue(),
            false ),

    MinimumSpecial(
            ChaiPasswordRule.MinimumSpecial,
            PwmSetting.PASSWORD_POLICY_MINIMUM_SPECIAL,
            ChaiPasswordRule.MinimumSpecial.getRuleType(),
            ChaiPasswordRule.MinimumSpecial.getDefaultValue(),
            false ),

    MaximumSpecial(
            ChaiPasswordRule.MaximumSpecial,
            PwmSetting.PASSWORD_POLICY_MAXIMUM_SPECIAL,
            ChaiPasswordRule.MaximumSpecial.getRuleType(),
            ChaiPasswordRule.MaximumSpecial.getDefaultValue(),
            false ),

    AllowFirstCharSpecial(
            ChaiPasswordRule.AllowFirstCharSpecial,
            PwmSetting.PASSWORD_POLICY_ALLOW_FIRST_CHAR_SPECIAL,
            ChaiPasswordRule.AllowFirstCharSpecial.getRuleType(),
            ChaiPasswordRule.AllowFirstCharSpecial.getDefaultValue(),
            false ),

    AllowLastCharSpecial(
            ChaiPasswordRule.AllowLastCharSpecial,
            PwmSetting.PASSWORD_POLICY_ALLOW_LAST_CHAR_SPECIAL,
            ChaiPasswordRule.AllowLastCharSpecial.getRuleType(),
            ChaiPasswordRule.AllowLastCharSpecial.getDefaultValue(),
            false ),

    MaximumRepeat(
            ChaiPasswordRule.MaximumRepeat,
            PwmSetting.PASSWORD_POLICY_MAXIMUM_REPEAT,
            ChaiPasswordRule.MaximumRepeat.getRuleType(),
            ChaiPasswordRule.MaximumRepeat.getDefaultValue(),
            false ),

    MaximumSequentialRepeat(
            ChaiPasswordRule.MaximumSequentialRepeat,
            PwmSetting.PASSWORD_POLICY_MAXIMUM_SEQUENTIAL_REPEAT,
            ChaiPasswordRule.MaximumSequentialRepeat.getRuleType(),
            ChaiPasswordRule.MaximumSequentialRepeat.getDefaultValue(),
            false ),

    ChangeMessage(
            ChaiPasswordRule.ChangeMessage,
            PwmSetting.PASSWORD_POLICY_CHANGE_MESSAGE,
            ChaiPasswordRule.ChangeMessage.getRuleType(),
            ChaiPasswordRule.ChangeMessage.getDefaultValue(),
            false ),

    ExpirationInterval(
            ChaiPasswordRule.ExpirationInterval,
            null,
            ChaiPasswordRule.ExpirationInterval.getRuleType(),
            ChaiPasswordRule.ExpirationInterval.getDefaultValue(),
            false ),

    MinimumLifetime(
            ChaiPasswordRule.MinimumLifetime,
            PwmSetting.PASSWORD_POLICY_MINIMUM_LIFETIME,
            ChaiPasswordRule.MinimumLifetime.getRuleType(),
            ChaiPasswordRule.MinimumLifetime.getDefaultValue(),
            false ),

    CaseSensitive(
            ChaiPasswordRule.CaseSensitive,
            null,
            ChaiPasswordRule.CaseSensitive.getRuleType(),
            ChaiPasswordRule.CaseSensitive.getDefaultValue(),
            true ),

    EnforceAtLogin(
            ChaiPasswordRule.EnforceAtLogin,
            null,
            ChaiPasswordRule.EnforceAtLogin.getRuleType(),
            ChaiPasswordRule.EnforceAtLogin.getDefaultValue(),
            false ),

    ChallengeResponseEnabled(
            ChaiPasswordRule.ChallengeResponseEnabled,
            null,
            ChaiPasswordRule.ChallengeResponseEnabled.getRuleType(),
            ChaiPasswordRule.ChallengeResponseEnabled.getDefaultValue(),
            false ),

    UniqueRequired(
            ChaiPasswordRule.UniqueRequired,
            null,
            ChaiPasswordRule.UniqueRequired.getRuleType(),
            ChaiPasswordRule.UniqueRequired.getDefaultValue(),
            true ),

    DisallowedValues(
            ChaiPasswordRule.DisallowedValues,
            PwmSetting.PASSWORD_POLICY_DISALLOWED_VALUES,
            ChaiPasswordRule.DisallowedValues.getRuleType(),
            ChaiPasswordRule.DisallowedValues.getDefaultValue(),
            false ),

    DisallowedAttributes(
            ChaiPasswordRule.DisallowedAttributes,
            PwmSetting.PASSWORD_POLICY_DISALLOWED_ATTRIBUTES,
            ChaiPasswordRule.DisallowedAttributes.getRuleType(),
            ChaiPasswordRule.DisallowedAttributes.getDefaultValue(),
            false ),

    DisallowCurrent(
            null,
            PwmSetting.PASSWORD_POLICY_DISALLOW_CURRENT,
            ChaiPasswordRule.RuleType.BOOLEAN,
            "false",
            true ),

    AllowUserChange(
            ChaiPasswordRule.AllowUserChange,
            null,
            ChaiPasswordRule.AllowUserChange.getRuleType(),
            ChaiPasswordRule.AllowUserChange.getDefaultValue(),
            true ),

    AllowAdminChange(
            ChaiPasswordRule.AllowAdminChange,
            null,
            ChaiPasswordRule.AllowAdminChange.getRuleType(),
            ChaiPasswordRule.AllowAdminChange.getDefaultValue(),
            true ),

    ADComplexityMaxViolations(
            ChaiPasswordRule.ADComplexityMaxViolation,
            PwmSetting.PASSWORD_POLICY_AD_COMPLEXITY_MAX_VIOLATIONS,
            ChaiPasswordRule.ADComplexityMaxViolation.getRuleType(),
            ChaiPasswordRule.ADComplexityMaxViolation.getDefaultValue(),
            false ),


    // pwm specific rules
    // value will be imported indirectly from chai rule
    ADComplexityLevel(
            null,
            PwmSetting.PASSWORD_POLICY_AD_COMPLEXITY_LEVEL,
            ChaiPasswordRule.RuleType.OTHER,
            "NONE",
            false ),

    MaximumOldChars(
            null,
            PwmSetting.PASSWORD_POLICY_MAXIMUM_OLD_PASSWORD_CHARS,
            ChaiPasswordRule.RuleType.NUMERIC,
            "",
            false ),

    RegExMatch(
            null,
            PwmSetting.PASSWORD_POLICY_REGULAR_EXPRESSION_MATCH,
            ChaiPasswordRule.RuleType.OTHER,
            "",
            false ),

    RegExNoMatch(
            null,
            PwmSetting.PASSWORD_POLICY_REGULAR_EXPRESSION_NOMATCH,
            ChaiPasswordRule.RuleType.OTHER,
            "",
            false
    ),

    MinimumAlpha(
            null,
            PwmSetting.PASSWORD_POLICY_MINIMUM_ALPHA,
            ChaiPasswordRule.RuleType.MIN,
            "0",
            false ),

    MaximumAlpha(
            null,
            PwmSetting.PASSWORD_POLICY_MAXIMUM_ALPHA,
            ChaiPasswordRule.RuleType.MAX,
            "0",
            false
    ),

    MinimumNonAlpha(
            null,
            PwmSetting.PASSWORD_POLICY_MINIMUM_NON_ALPHA,
            ChaiPasswordRule.RuleType.MIN,
            "0",
            false ),

    MaximumNonAlpha(
            null,
            PwmSetting.PASSWORD_POLICY_MAXIMUM_NON_ALPHA,
            ChaiPasswordRule.RuleType.MAX,
            "0",
            false ),

    EnableWordlist(
            null,
            PwmSetting.PASSWORD_POLICY_ENABLE_WORDLIST,
            ChaiPasswordRule.RuleType.BOOLEAN,
            "true",
            true ),

    MinimumStrength(
            null,
            PwmSetting.PASSWORD_POLICY_MINIMUM_STRENGTH,
            ChaiPasswordRule.RuleType.MIN,
            "0",
            false ),

    MaximumConsecutive(
            null,
            PwmSetting.PASSWORD_POLICY_MAXIMUM_CONSECUTIVE,
            ChaiPasswordRule.RuleType.MIN,
            "0",
            false ),

    CharGroupsMinMatch(
            null,
            PwmSetting.PASSWORD_POLICY_CHAR_GROUPS_MIN_MATCH,
            ChaiPasswordRule.RuleType.MIN,
            "0",
            false ),

    CharGroupsValues(
            null,
            PwmSetting.PASSWORD_POLICY_CHAR_GROUPS,
            ChaiPasswordRule.RuleType.OTHER,
            "",
            false ),

    AllowMacroInRegExSetting(
            AppProperty.ALLOW_MACRO_IN_REGEX_SETTING,
            ChaiPasswordRule.RuleType.BOOLEAN,
            "true",
            false ),;

    private static final PwmLogger LOGGER = PwmLogger.forClass( PwmPasswordRule.class );

    static
    {
        try
        {
            final Set<String> keys = new HashSet<>();
            for ( final PwmSetting setting : PwmSetting.values() )
            {
                keys.add( setting.getKey() );
            }
            assert keys.size() == PwmSetting.values().length;
        }
        catch ( Throwable t )
        {
            LOGGER.fatal( "error initializing PwmPasswordRule class: " + t.getMessage(), t );
        }
    }

    private final ChaiPasswordRule chaiPasswordRule;
    private final PwmSetting pwmSetting;
    private final AppProperty appProperty;
    private final ChaiPasswordRule.RuleType ruleType;
    private final String defaultValue;
    private final boolean positiveBooleanMerge;

    PwmPasswordRule(
            final ChaiPasswordRule chaiPasswordRule,
            final PwmSetting pwmSetting,
            final ChaiPasswordRule.RuleType ruleType,
            final String defaultValue,
            final boolean positiveBooleanMerge
    )
    {
        this.pwmSetting = pwmSetting;
        this.chaiPasswordRule = chaiPasswordRule;
        this.appProperty = null;
        this.ruleType = ruleType;
        this.defaultValue = defaultValue;
        this.positiveBooleanMerge = positiveBooleanMerge;
    }

    PwmPasswordRule(
            final AppProperty appProperty,
            final ChaiPasswordRule.RuleType ruleType,
            final String defaultValue,
            final boolean positiveBooleanMerge
    )
    {
        this.pwmSetting = null;
        this.chaiPasswordRule = null;
        this.appProperty = appProperty;
        this.ruleType = ruleType;
        this.defaultValue = defaultValue;
        this.positiveBooleanMerge = positiveBooleanMerge;
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

    public static PwmPasswordRule forKey( final String key )
    {
        if ( key == null )
        {
            return null;
        }

        for ( final PwmPasswordRule rule : values() )
        {
            if ( key.equals( rule.getKey() ) )
            {
                return rule;
            }
        }

        return null;
    }

    public String getLabel( final Locale locale, final Configuration config )
    {
        final String key = "Rule_" + this.toString();
        try
        {
            return LocaleHelper.getLocalizedMessage( locale, key, config, Message.class );
        }
        catch ( MissingResourceException e )
        {
            return "MissingKey-" + key;
        }
    }
}
