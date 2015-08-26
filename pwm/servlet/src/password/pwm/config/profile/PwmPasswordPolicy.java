/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2015 The PWM Project
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

import com.novell.ldapchai.ChaiPasswordPolicy;
import com.novell.ldapchai.ChaiPasswordRule;
import com.novell.ldapchai.util.DefaultChaiPasswordPolicy;
import com.novell.ldapchai.util.PasswordRuleHelper;
import com.novell.ldapchai.util.StringHelper;
import password.pwm.config.UserPermission;
import password.pwm.config.option.ADPolicyComplexity;
import password.pwm.health.HealthMessage;
import password.pwm.health.HealthRecord;
import password.pwm.util.logging.PwmLogger;

import java.io.Serializable;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;


/**
 * @author Jason D. Rivard
 */
public class PwmPasswordPolicy implements Profile,Serializable {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.forClass(PwmPasswordPolicy.class);

    private static final PwmPasswordPolicy defaultPolicy;

    private final Map<String, String> policyMap = new HashMap<>();

    private transient final ChaiPasswordPolicy chaiPasswordPolicy;

    private String profileID;
    private List<UserPermission> userPermissions;
    private String ruleText;

    public static PwmPasswordPolicy createPwmPasswordPolicy(
            final Map<String, String> policyMap,
            final ChaiPasswordPolicy chaiPasswordPolicy
    ) {
        return new PwmPasswordPolicy(policyMap, chaiPasswordPolicy);
    }

    public String getIdentifier() {
        return profileID;
    }

    public String getDisplayName(final Locale locale) {
        return getIdentifier();
    }

// -------------------------- STATIC METHODS --------------------------

    static {
        PwmPasswordPolicy newDefaultPolicy = null;
        try {
            final Map<String, String> defaultPolicyMap = new HashMap<>();
            for (final PwmPasswordRule rule : PwmPasswordRule.values()) {
                defaultPolicyMap.put(rule.getKey(), rule.getDefaultValue());
            }
            newDefaultPolicy = createPwmPasswordPolicy(defaultPolicyMap, null);
        } catch (Throwable t) {
            LOGGER.fatal("error initializing PwmPasswordPolicy class: " + t.getMessage(), t);
        }
        defaultPolicy = newDefaultPolicy;
    }

    public static PwmPasswordPolicy defaultPolicy() {
        return defaultPolicy;
    }


    private PwmPasswordPolicy(
            final Map<String, String> policyMap,
            final ChaiPasswordPolicy chaiPasswordPolicy
    ) {
        if (policyMap != null) {
            this.policyMap.putAll(policyMap);
        }
        if (chaiPasswordPolicy != null) {
            if (Boolean.parseBoolean(chaiPasswordPolicy.getValue(ChaiPasswordRule.ADComplexity))) {
                this.policyMap.put(PwmPasswordRule.ADComplexityLevel.getKey(), ADPolicyComplexity.AD2003.toString());
            } else if (Boolean.parseBoolean(chaiPasswordPolicy.getValue(ChaiPasswordRule.ADComplexity2008))) {
                this.policyMap.put(PwmPasswordRule.ADComplexityLevel.getKey(), ADPolicyComplexity.AD2008.toString());
            }
        }
        this.chaiPasswordPolicy = chaiPasswordPolicy;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("PwmPasswordPolicy");
        sb.append(": ");

        final List<String> outputList = new ArrayList<>();
        for (final String key : policyMap.keySet()) {
            final PwmPasswordRule rule = PwmPasswordRule.forKey(key);
            if (rule != null) {
                switch (rule) {
                    case DisallowedAttributes:
                    case DisallowedValues:
                        outputList.add(rule + "=[" + StringHelper.stringCollectionToString(StringHelper.tokenizeString(policyMap.get(key), "\n"), ", ") + "]");
                        break;
                    default:
                        outputList.add(rule + "=" + policyMap.get(key));
                        break;
                }
            } else {
                outputList.add(key + "=" + policyMap.get(key));
            }
        }

        sb.append("{");
        sb.append(StringHelper.stringCollectionToString(outputList, ", "));
        sb.append("}");

        return sb.toString();
    }

    public ChaiPasswordPolicy getChaiPasswordPolicy() {
        return chaiPasswordPolicy;
    }

    public RuleHelper getRuleHelper() {
        return new RuleHelper(this);
    }

    public String getValue(final PwmPasswordRule rule) {
        return policyMap.get(rule.getKey());
    }

    public void setProfileID(String profileID) {
        this.profileID = profileID;
    }

    public List<UserPermission> getUserPermissions() {
        return userPermissions;
    }

    public void setUserPermissions(List<UserPermission> userPermissions) {
        this.userPermissions = userPermissions;
    }

    public String getRuleText() {
        return ruleText;
    }

    public void setRuleText(String ruleText) {
        this.ruleText = ruleText;
    }

    public PwmPasswordPolicy merge(final PwmPasswordPolicy otherPolicy) {
        if (otherPolicy == null) {
            return this;
        }

        final Map<String, String> newPasswordPolicies = new HashMap<>();

        for (final PwmPasswordRule rule : PwmPasswordRule.values()) {
            final String ruleKey = rule.getKey();
            if (this.policyMap.containsKey(ruleKey) || otherPolicy.policyMap.containsKey(ruleKey)) {
                switch (rule) {
                    case DisallowedValues:
                    case DisallowedAttributes:
                    case RegExMatch:
                    case RegExNoMatch:
                    case CharGroupsValues:
                        final String seperator = (rule == PwmPasswordRule.RegExMatch || rule == PwmPasswordRule.RegExNoMatch) ? ";;;" : "\n";
                        final Set<String> combinedSet = new HashSet<>();
                        combinedSet.addAll(StringHelper.tokenizeString(this.policyMap.get(rule.getKey()), seperator));
                        combinedSet.addAll(StringHelper.tokenizeString(otherPolicy.policyMap.get(rule.getKey()), seperator));
                        newPasswordPolicies.put(ruleKey, StringHelper.stringCollectionToString(combinedSet, seperator));
                        break;

                    case ChangeMessage:
                        final String thisChangeMessage = getValue(PwmPasswordRule.ChangeMessage);
                        if (thisChangeMessage == null || thisChangeMessage.length() < 1) {
                            newPasswordPolicies.put(ruleKey, otherPolicy.getValue(PwmPasswordRule.ChangeMessage));
                        } else {
                            newPasswordPolicies.put(ruleKey, getValue(PwmPasswordRule.ChangeMessage));
                        }
                        break;

                    case ExpirationInterval:
                        newPasswordPolicies.put(ruleKey, mergeMin(policyMap.get(ruleKey), otherPolicy.policyMap.get(ruleKey)));
                        break;

                    case MinimumLifetime:
                        newPasswordPolicies.put(ruleKey, mergeMin(policyMap.get(ruleKey), otherPolicy.policyMap.get(ruleKey)));
                        break;

                    default:
                        switch (rule.getRuleType()) {
                            case MIN:
                                newPasswordPolicies.put(ruleKey, mergeMin(policyMap.get(ruleKey), otherPolicy.policyMap.get(ruleKey)));
                                break;

                            case MAX:
                                newPasswordPolicies.put(ruleKey, mergeMax(policyMap.get(ruleKey), otherPolicy.policyMap.get(ruleKey)));
                                break;

                            case BOOLEAN:
                                final boolean localValue = StringHelper.convertStrToBoolean(policyMap.get(ruleKey));
                                final boolean otherValue = StringHelper.convertStrToBoolean(otherPolicy.policyMap.get(ruleKey));

                                if (rule.isPositiveBooleanMerge()) {
                                    newPasswordPolicies.put(ruleKey, String.valueOf(localValue || otherValue));
                                } else {
                                    newPasswordPolicies.put(ruleKey, String.valueOf(localValue && otherValue));
                                }
                                break;
                        }
                }
            }
        }

        final ChaiPasswordPolicy backingPolicy = this.chaiPasswordPolicy != null ? chaiPasswordPolicy : otherPolicy.chaiPasswordPolicy;
        final PwmPasswordPolicy returnPolicy = createPwmPasswordPolicy(newPasswordPolicies, backingPolicy);
        final String newRuleText = (ruleText != null && !ruleText.isEmpty()) ? ruleText : otherPolicy.ruleText;
        returnPolicy.setRuleText(newRuleText);
        return returnPolicy;
    }

    protected static String mergeMin(final String value1, final String value2) {
        final int iValue1 = StringHelper.convertStrToInt(value1, 0);
        final int iValue2 = StringHelper.convertStrToInt(value2, 0);

        // take the largest value
        return iValue1 > iValue2 ? value1 : value2;
    }

    protected static String mergeMax(final String value1, final String value2) {
        final int iValue1 = StringHelper.convertStrToInt(value1, 0);
        final int iValue2 = StringHelper.convertStrToInt(value2, 0);

        final String returnValue;

        // if one of the values is zero, take the other one.
        if (iValue1 == 0 || iValue2 == 0) {
            returnValue = iValue1 > iValue2 ? value1 : value2;

            // else take the smaller value
        } else {
            returnValue = iValue1 < iValue2 ? value1 : value2;
        }

        return returnValue;
    }

    public static PwmPasswordPolicy createPwmPasswordPolicy(final Map<String, String> policyMap) {
        return createPwmPasswordPolicy(policyMap, null);
    }


// -------------------------- INNER CLASSES --------------------------

    public static class RuleHelper {
        private final PwmPasswordPolicy passwordPolicy;
        private final PasswordRuleHelper chaiRuleHelper;

        public RuleHelper(final PwmPasswordPolicy passwordPolicy) {
            this.passwordPolicy = passwordPolicy;
            chaiRuleHelper = DefaultChaiPasswordPolicy.createDefaultChaiPasswordPolicy(passwordPolicy.policyMap).getRuleHelper();
        }

        public List<String> getDisallowedValues() {
            return chaiRuleHelper.getDisallowedValues();
        }

        public List<String> getDisallowedAttributes() {
            return chaiRuleHelper.getDisallowedAttributes();
        }

        public List<Pattern> getRegExMatch() {
            return readRegExSetting(PwmPasswordRule.RegExMatch);
        }

        public List<Pattern> getRegExNoMatch() {
            return readRegExSetting(PwmPasswordRule.RegExNoMatch);
        }

        public List<Pattern> getCharGroupValues() {
            return readRegExSetting(PwmPasswordRule.CharGroupsValues);
        }


        public int readIntValue(final PwmPasswordRule rule) {
            if (
                    (rule.getRuleType() != ChaiPasswordRule.RuleType.MIN) &&
                            (rule.getRuleType() != ChaiPasswordRule.RuleType.MAX) &&
                            (rule.getRuleType() != ChaiPasswordRule.RuleType.NUMERIC)
                    ) {
                throw new IllegalArgumentException("attempt to read non-numeric rule value as int for rule " + rule);
            }

            final String value = passwordPolicy.policyMap.get(rule.getKey());
            final int defaultValue = StringHelper.convertStrToInt(rule.getDefaultValue(), 0);
            return StringHelper.convertStrToInt(value, defaultValue);
        }

        public boolean readBooleanValue(final PwmPasswordRule rule) {
            if (rule.getRuleType() != ChaiPasswordRule.RuleType.BOOLEAN) {
                throw new IllegalArgumentException("attempt to read non-boolean rule value as boolean for rule " + rule);
            }

            final String value = passwordPolicy.policyMap.get(rule.getKey());
            return StringHelper.convertStrToBoolean(value);
        }

        private List<Pattern> readRegExSetting(final PwmPasswordRule rule) {
            final String input = passwordPolicy.policyMap.get(rule.getKey());
            if (input == null) {
                return Collections.emptyList();
            }
            final String separator = (rule == PwmPasswordRule.RegExMatch || rule == PwmPasswordRule.RegExNoMatch) ? ";;;" : "\n";
            final List<String> values = new ArrayList<>(StringHelper.tokenizeString(input, separator));
            final List<Pattern> patterns = new ArrayList<>();

            for (final String value : values) {
                if (value != null && value.length() > 0) {
                    try {
                        final Pattern loopPattern = Pattern.compile(value);
                        patterns.add(loopPattern);
                    } catch (PatternSyntaxException e) {
                        LOGGER.warn("reading password rule value '" + value + "' for rule " + rule.getKey() + " is not a valid regular expression " + e.getMessage());
                    }
                }
            }

            return patterns;
        }

        public String getChangeMessage() {
            final String changeMessage = passwordPolicy.getValue(PwmPasswordRule.ChangeMessage);
            return changeMessage == null ? "" : changeMessage;
        }

        public ADPolicyComplexity getADComplexityLevel() {
            final String strLevel = passwordPolicy.getValue(PwmPasswordRule.ADComplexityLevel);
            if (strLevel == null || strLevel.isEmpty()) {
                return ADPolicyComplexity.NONE;
            }
            return ADPolicyComplexity.valueOf(strLevel);
        }
    }

    public Map<String, String> getPolicyMap() {
        return Collections.unmodifiableMap(policyMap);
    }

    @Override
    public ProfileType profileType() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<UserPermission> getPermissionMatches() {
        throw new UnsupportedOperationException();
    }

    public List<HealthRecord> health(final Locale locale) {
        final RuleHelper ruleHelper = this.getRuleHelper();
        final List<HealthRecord> returnList = new ArrayList<>();
        final Map<PwmPasswordRule, PwmPasswordRule> rulePairs = new LinkedHashMap<>();
        rulePairs.put(PwmPasswordRule.MinimumLength, PwmPasswordRule.MaximumLength);
        rulePairs.put(PwmPasswordRule.MinimumLowerCase, PwmPasswordRule.MaximumLowerCase);
        rulePairs.put(PwmPasswordRule.MinimumUpperCase, PwmPasswordRule.MaximumUpperCase);
        rulePairs.put(PwmPasswordRule.MinimumNumeric, PwmPasswordRule.MaximumNumeric);
        rulePairs.put(PwmPasswordRule.MinimumSpecial, PwmPasswordRule.MaximumSpecial);
        rulePairs.put(PwmPasswordRule.MinimumAlpha, PwmPasswordRule.MaximumAlpha);
        rulePairs.put(PwmPasswordRule.MinimumNonAlpha, PwmPasswordRule.MaximumNonAlpha);
        rulePairs.put(PwmPasswordRule.MinimumUnique, PwmPasswordRule.MaximumUnique);

        for (final PwmPasswordRule minRule : rulePairs.keySet()) {
            final PwmPasswordRule maxRule = rulePairs.get(minRule);

            final int minValue = ruleHelper.readIntValue(minRule);
            final int maxValue = ruleHelper.readIntValue(maxRule);
            if (maxValue > 0 && minValue > maxValue) {
                final String detailMsg = minRule.getLabel(locale, null) + " (" + minValue + ")"
                        + " > "
                        + maxRule.getLabel(locale, null) + " (" + maxValue + ")";
                returnList.add(HealthRecord.forMessage(HealthMessage.Config_PasswordPolicyProblem, profileID, detailMsg));
            }
        }

        return Collections.unmodifiableList(returnList);
    }
}