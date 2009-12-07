/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
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

package password.pwm.config;

import com.novell.ldapchai.ChaiPasswordRule;

import java.util.HashSet;
import java.util.Set;

/**
 * Password rules
 *
 * @author Jason D. Rivard
 */
public enum PwmPasswordRule {
    // rules from chai policy rules:
    PolicyEnabled           (ChaiPasswordRule.PolicyEnabled            ,null                                                    ,ChaiPasswordRule.PolicyEnabled           .getRuleType(), ChaiPasswordRule.PolicyEnabled           .getDefaultValue(),true),
    MinimumLength           (ChaiPasswordRule.MinimumLength            ,PwmSetting.PASSWORD_POLICY_MINIMUM_LENGTH               ,ChaiPasswordRule.MinimumLength           .getRuleType(), ChaiPasswordRule.MinimumLength           .getDefaultValue(),false),
    MaximumLength           (ChaiPasswordRule.MaximumLength            ,PwmSetting.PASSWORD_POLICY_MAXIMUM_LENGTH               ,ChaiPasswordRule.MaximumLength           .getRuleType(), ChaiPasswordRule.MaximumLength           .getDefaultValue(),false),
    MinimumUpperCase        (ChaiPasswordRule.MinimumUpperCase         ,PwmSetting.PASSWORD_POLICY_MINIMUM_UPPERCASE            ,ChaiPasswordRule.MinimumUpperCase        .getRuleType(), ChaiPasswordRule.MinimumUpperCase        .getDefaultValue(),false),
    MaximumUpperCase        (ChaiPasswordRule.MaximumUpperCase         ,PwmSetting.PASSWORD_POLICY_MAXIMUM_UPPERCASE            ,ChaiPasswordRule.MaximumUpperCase        .getRuleType(), ChaiPasswordRule.MaximumUpperCase        .getDefaultValue(),false),
    MinimumLowerCase        (ChaiPasswordRule.MinimumLowerCase         ,PwmSetting.PASSWORD_POLICY_MINIMUM_LOWERCASE            ,ChaiPasswordRule.MinimumLowerCase        .getRuleType(), ChaiPasswordRule.MinimumLowerCase        .getDefaultValue(),false),
    MaximumLowerCase        (ChaiPasswordRule.MaximumLowerCase         ,PwmSetting.PASSWORD_POLICY_MAXIMUM_LOWERCASE            ,ChaiPasswordRule.MaximumLowerCase        .getRuleType(), ChaiPasswordRule.MaximumLowerCase        .getDefaultValue(),false),
    AllowNumeric            (ChaiPasswordRule.AllowNumeric             ,PwmSetting.PASSWORD_POLICY_ALLOW_NUMERIC                ,ChaiPasswordRule.AllowNumeric            .getRuleType(), ChaiPasswordRule.AllowNumeric            .getDefaultValue(),false),
    MinimumNumeric          (ChaiPasswordRule.MinimumNumeric           ,PwmSetting.PASSWORD_POLICY_MINIMUM_NUMERIC              ,ChaiPasswordRule.MinimumNumeric          .getRuleType(), ChaiPasswordRule.MinimumNumeric          .getDefaultValue(),false),
    MaximumNumeric          (ChaiPasswordRule.MaximumNumeric           ,PwmSetting.PASSWORD_POLICY_MAXIMUM_NUMERIC              ,ChaiPasswordRule.MaximumNumeric          .getRuleType(), ChaiPasswordRule.MaximumNumeric          .getDefaultValue(),false),
    MinimumUnique           (ChaiPasswordRule.MinimumUnique            ,PwmSetting.PASSWORD_POLICY_MINIMUM_UNIQUE               ,ChaiPasswordRule.MinimumUnique           .getRuleType(), ChaiPasswordRule.MinimumUnique           .getDefaultValue(),false),
    MaximumUnique           (ChaiPasswordRule.MaximumUnique            ,null                                                    ,ChaiPasswordRule.MaximumUnique           .getRuleType(), ChaiPasswordRule.MaximumUnique           .getDefaultValue(),false),
    AllowFirstCharNumeric   (ChaiPasswordRule.AllowFirstCharNumeric    ,PwmSetting.PASSWORD_POLICY_ALLOW_FIRST_CHAR_NUMERIC     ,ChaiPasswordRule.AllowFirstCharNumeric   .getRuleType(), ChaiPasswordRule.AllowFirstCharNumeric   .getDefaultValue(),false),
    AllowLastCharNumeric    (ChaiPasswordRule.AllowLastCharNumeric     ,PwmSetting.PASSWORD_POLICY_ALLOW_LAST_CHAR_NUMERIC      ,ChaiPasswordRule.AllowLastCharNumeric    .getRuleType(), ChaiPasswordRule.AllowLastCharNumeric    .getDefaultValue(),false),
    AllowSpecial            (ChaiPasswordRule.AllowSpecial             ,PwmSetting.PASSWORD_POLICY_ALLOW_SPECIAL                ,ChaiPasswordRule.AllowSpecial            .getRuleType(), ChaiPasswordRule.AllowSpecial            .getDefaultValue(),false),
    MinimumSpecial          (ChaiPasswordRule.MinimumSpecial           ,PwmSetting.PASSWORD_POLICY_MINIMUM_SPECIAL              ,ChaiPasswordRule.MinimumSpecial          .getRuleType(), ChaiPasswordRule.MinimumSpecial          .getDefaultValue(),false),
    MaximumSpecial          (ChaiPasswordRule.MaximumSpecial           ,PwmSetting.PASSWORD_POLICY_MAXIMUM_SPECIAL              ,ChaiPasswordRule.MaximumSpecial          .getRuleType(), ChaiPasswordRule.MaximumSpecial          .getDefaultValue(),false),
    AllowFirstCharSpecial   (ChaiPasswordRule.AllowFirstCharSpecial    ,PwmSetting.PASSWORD_POLICY_ALLOW_FIRST_CHAR_SPECIAL     ,ChaiPasswordRule.AllowFirstCharSpecial   .getRuleType(), ChaiPasswordRule.AllowFirstCharSpecial   .getDefaultValue(),false),
    AllowLastCharSpecial    (ChaiPasswordRule.AllowLastCharSpecial     ,PwmSetting.PASSWORD_POLICY_ALLOW_LAST_CHAR_SPECIAL      ,ChaiPasswordRule.AllowLastCharSpecial    .getRuleType(), ChaiPasswordRule.AllowLastCharSpecial    .getDefaultValue(),false),
    MaximumRepeat           (ChaiPasswordRule.MaximumRepeat            ,PwmSetting.PASSWORD_POLICY_MAXIMUM_REPEAT               ,ChaiPasswordRule.MaximumRepeat           .getRuleType(), ChaiPasswordRule.MaximumRepeat           .getDefaultValue(),false),
    MaximumSequentialRepeat (ChaiPasswordRule.MaximumSequentialRepeat  ,PwmSetting.PASSWORD_POLICY_MAXIMUM_SEQUENTIAL_REPEAT    ,ChaiPasswordRule.MaximumSequentialRepeat .getRuleType(), ChaiPasswordRule.MaximumSequentialRepeat .getDefaultValue(),false),
    ChangeMessage           (ChaiPasswordRule.ChangeMessage            ,null                                                    ,ChaiPasswordRule.ChangeMessage           .getRuleType(), ChaiPasswordRule.ChangeMessage           .getDefaultValue(),false),
    ExpirationInterval      (ChaiPasswordRule.ExpirationInterval       ,null                                                    ,ChaiPasswordRule.ExpirationInterval      .getRuleType(), ChaiPasswordRule.ExpirationInterval      .getDefaultValue(),false),
    MinimumLifetime         (ChaiPasswordRule.MinimumLifetime          ,null                                                    ,ChaiPasswordRule.MinimumLifetime         .getRuleType(), ChaiPasswordRule.MinimumLifetime         .getDefaultValue(),false),
    CaseSensitive           (ChaiPasswordRule.CaseSensitive            ,null                                                    ,ChaiPasswordRule.CaseSensitive           .getRuleType(), ChaiPasswordRule.CaseSensitive           .getDefaultValue(),true),
    EnforceAtLogin          (ChaiPasswordRule.EnforceAtLogin           ,null                                                    ,ChaiPasswordRule.EnforceAtLogin          .getRuleType(), ChaiPasswordRule.EnforceAtLogin          .getDefaultValue(),false),
    ChallengeResponseEnabled(ChaiPasswordRule.ChallengeResponseEnabled ,null                                                    ,ChaiPasswordRule.ChallengeResponseEnabled.getRuleType(), ChaiPasswordRule.ChallengeResponseEnabled.getDefaultValue(),false),
    UniqueRequired          (ChaiPasswordRule.UniqueRequired           ,null                                                    ,ChaiPasswordRule.UniqueRequired          .getRuleType(), ChaiPasswordRule.UniqueRequired          .getDefaultValue(),true),
    DisallowedValues        (ChaiPasswordRule.DisallowedValues         ,null                                                    ,ChaiPasswordRule.DisallowedValues        .getRuleType(), ChaiPasswordRule.DisallowedValues        .getDefaultValue(),false),
    DisallowedAttributes    (ChaiPasswordRule.DisallowedAttributes     ,null                                                    ,ChaiPasswordRule.DisallowedAttributes    .getRuleType(), ChaiPasswordRule.DisallowedAttributes    .getDefaultValue(),false),
    ADComplexity            (ChaiPasswordRule.ADComplexity             ,PwmSetting.PASSWORD_POLICY_AD_COMPLEXITY                ,ChaiPasswordRule.ADComplexity            .getRuleType(), ChaiPasswordRule.ADComplexity            .getDefaultValue(),true),

    // pwm specific rules
    MaximumOldChars         (null                                      ,PwmSetting.PASSWORD_POLICY_MAXIMUM_OLD_PASSWORD_CHARS   ,ChaiPasswordRule.RuleType.NUMERIC, "",false),
    RegExMatch              (null                                      ,PwmSetting.PASSWORD_POLICY_REGULAR_EXPRESSION_MATCH     ,ChaiPasswordRule.RuleType.OTHER, "",false),
    RegExNoMatch            (null                                      ,PwmSetting.PASSWORD_POLICY_REGULAR_EXPRESSION_NOMATCH   ,ChaiPasswordRule.RuleType.OTHER, "",false),
    MinimumAlpha            (null                                      ,PwmSetting.PASSWORD_POLICY_MINIMUM_ALPHA                ,ChaiPasswordRule.RuleType.MIN, "0",false),
    MaximumAlpha            (null                                      ,PwmSetting.PASSWORD_POLICY_MAXIMUM_ALPHA                ,ChaiPasswordRule.RuleType.MAX, "0",false),

    ;

    static {
        Set<String> keys = new HashSet<String>();
        for (PwmSetting setting : PwmSetting.values()) keys.add(setting.getKey());
        assert keys.size() == PwmSetting.values().length;
    }

    private final ChaiPasswordRule chaiPasswordRule;
    private final PwmSetting pwmSetting;
    private final ChaiPasswordRule.RuleType ruleType;
    private final String defaultValue;
    private final boolean positiveBooleanMerge;

    PwmPasswordRule(final ChaiPasswordRule chaiPasswordRule, final PwmSetting pwmSetting, final ChaiPasswordRule.RuleType ruleType, final String defaultValue, final boolean positiveBooleanMerge) {
        this.pwmSetting = pwmSetting;
        this.chaiPasswordRule = chaiPasswordRule;
        this.ruleType = ruleType;
        this.defaultValue = defaultValue;
        this.positiveBooleanMerge = positiveBooleanMerge;
    }

    public String getKey() {
        return null != chaiPasswordRule ? chaiPasswordRule.getKey() : pwmSetting.getKey();
    }

    public String getPwmConfigName() {
        return pwmSetting != null ? pwmSetting.getKey() : null;
    }

    public ChaiPasswordRule.RuleType getRuleType() {
        return ruleType;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public boolean isPositiveBooleanMerge() {
        return positiveBooleanMerge;
    }

    public static PwmPasswordRule forKey(final String key) {
        if (key == null) {
            return null;
        }

        for (final PwmPasswordRule rule : values()) {
            if (key.equals(rule.getKey())) {
                return rule;
            }
        }

        return null;
    }
}
