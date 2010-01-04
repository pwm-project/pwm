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

package password.pwm;

import com.novell.ldapchai.ChaiPasswordPolicy;
import com.novell.ldapchai.ChaiPasswordRule;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.util.DefaultChaiPasswordPolicy;
import com.novell.ldapchai.util.PasswordRuleHelper;
import com.novell.ldapchai.util.StringHelper;
import password.pwm.config.PwmPasswordRule;
import password.pwm.config.PwmSetting;
import password.pwm.util.PwmLogger;
import password.pwm.util.TimeDuration;

import java.io.Serializable;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;


/**
 * @author Jason D. Rivard
 */
public class PwmPasswordPolicy implements Serializable {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(PwmPasswordPolicy.class);

    private static final PwmPasswordPolicy defaultPolicy;

    private final Map<String,String> policyMap = new HashMap<String, String>();

    private transient final ChaiPasswordPolicy chaiPasswordPolicy;

// -------------------------- STATIC METHODS --------------------------

    static {
        final Map<String,String> defaultPolicyMap = new HashMap<String,String>();
        for (final PwmPasswordRule rule : PwmPasswordRule.values()) {
            defaultPolicyMap.put(rule.getKey(),rule.getDefaultValue());
        }
        defaultPolicy = new PwmPasswordPolicy(defaultPolicyMap,null);
    }

    public static PwmPasswordPolicy defaultPolicy() {
        return defaultPolicy;
    }


// --------------------------- CONSTRUCTORS ---------------------------

    private PwmPasswordPolicy(final Map<String,String> policyMap, final ChaiPasswordPolicy chaiPasswordPolicy) {
        if (policyMap != null) {
            this.policyMap.putAll(policyMap);
        }
        this.chaiPasswordPolicy = chaiPasswordPolicy;
    }

// ------------------------ CANONICAL METHODS ------------------------

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("PwmPasswordPolicy");
        sb.append(": ");

        final List<String> outputList = new ArrayList<String>();
        for (final String key : policyMap.keySet()) {
            final PwmPasswordRule rule = PwmPasswordRule.forKey(key);
            if (rule != null) {
                switch (rule) {
                    case DisallowedAttributes:
                    case DisallowedValues:
                        outputList.add(rule + "=[" + StringHelper.stringCollectionToString(StringHelper.tokenizeString(policyMap.get(key),"\n"),", ") + "]");
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

// -------------------------- OTHER METHODS --------------------------

    /*
    public boolean testPassword(final String password)
            throws ChaiPasswordPolicyException
    {
        final boolean chaiResult = super.testPassword(password);

        //check number of alpha characters
        {
            final int passwordLength = password.length();
            int numberOfAlphaChars = 0;
            for (int i = 0; i < passwordLength; i++) {
                if (Character.isLetter(password.charAt(i))) {
                    numberOfAlphaChars++;
                }
            }

            if (this.getMaximumAlpha() > 0 && numberOfAlphaChars > this.getMaximumAlpha()) {
                throw new ChaiPasswordPolicyException(ChaiPasswordPolicyException.PASSWORD_ERROR.TOO_MANY_ALPHA);
            }

            if (this.getMinimumAlpha() > 0 && numberOfAlphaChars < this.getMaximumAlpha()) {
                throw new ChaiPasswordPolicyException(ChaiPasswordPolicyException.PASSWORD_ERROR.NOT_ENOUGH_ALPHA);
            }
        }

        // check regex match
        {
            for (final Pattern pattern : getRegexPatterns()) {
                final Matcher matcher = pattern.matcher(password);
                if (!matcher.matches()) {
                    LOGGER.debug("password failed validation of regex required pattern: " + pattern.toString());
                    throw new ChaiPasswordPolicyException(ChaiPasswordPolicyException.PASSWORD_ERROR.BADPASSWORD);
                }
            }
        }

        // check regex no match
        {
            for (final Pattern pattern : getRegexNoPatterns()) {
                final Matcher matcher = pattern.matcher(password);
                if (matcher.matches()) {
                    LOGGER.debug("password failed validation of regex non-permitted pattern: " + pattern.toString());
                    throw new ChaiPasswordPolicyException(ChaiPasswordPolicyException.PASSWORD_ERROR.BADPASSWORD);
                }
            }
        }


        return chaiResult;
    }
    */

// -------------------------- OTHER METHODS --------------------------

    public ChaiPasswordPolicy getChaiPasswordPolicy() {
        return chaiPasswordPolicy;
    }

    public RuleHelper getRuleHelper() {
        return new RuleHelper(this);
    }

    public String getValue(final PwmPasswordRule rule) {
        return policyMap.get(rule.getKey());
    }

    public PwmPasswordPolicy merge(final PwmPasswordPolicy otherPolicy) {
        if (otherPolicy == null) {
            return this;
        }

        final Map<String, String> newPasswordPolicies = new HashMap<String, String>();

        for (final PwmPasswordRule rule : PwmPasswordRule.values()) {
            final String ruleKey = rule.getKey();
            if (this.policyMap.containsKey(ruleKey) || otherPolicy.policyMap.containsKey(ruleKey)) {
                switch (rule) {
                    case DisallowedValues:
                    case DisallowedAttributes:
                    case RegExMatch:
                    case RegExNoMatch:
                        final String seperator = (rule == PwmPasswordRule.RegExMatch || rule == PwmPasswordRule.RegExNoMatch) ? ";;;" : "\n";
                        final Set<String> cominedSet = new HashSet<String>();
                        cominedSet.addAll(StringHelper.tokenizeString(this.policyMap.get(rule.getKey()),seperator));
                        cominedSet.addAll(StringHelper.tokenizeString(otherPolicy.policyMap.get(rule.getKey()),seperator));
                        newPasswordPolicies.put(ruleKey, StringHelper.stringCollectionToString(cominedSet, seperator));
                        break;

                    case ChangeMessage:
                        final String thisChangeMessage = getValue(PwmPasswordRule.ChangeMessage);
                        if (thisChangeMessage == null || thisChangeMessage.length() < 0) {
                            newPasswordPolicies.put(ruleKey,otherPolicy.getValue(PwmPasswordRule.ChangeMessage));
                        } else {
                            newPasswordPolicies.put(ruleKey,getValue(PwmPasswordRule.ChangeMessage));
                        }
                        break;

                    case ExpirationInterval:
                        newPasswordPolicies.put(ruleKey,mergeMin(policyMap.get(ruleKey),otherPolicy.policyMap.get(ruleKey)));
                        break;
                    
                    case MinimumLifetime:
                        newPasswordPolicies.put(ruleKey,mergeMin(policyMap.get(ruleKey),otherPolicy.policyMap.get(ruleKey)));
                        break;

                    default:
                        switch (rule.getRuleType()) {
                            case MIN:
                                newPasswordPolicies.put(ruleKey,mergeMin(policyMap.get(ruleKey),otherPolicy.policyMap.get(ruleKey)));
                                break;

                            case MAX:
                                newPasswordPolicies.put(ruleKey,mergeMax(policyMap.get(ruleKey),otherPolicy.policyMap.get(ruleKey)));
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
        return new PwmPasswordPolicy(newPasswordPolicies, backingPolicy);
    }

    protected static String mergeMin(final String value1, final String value2)
    {
        final int iValue1 = StringHelper.convertStrToInt(value1,0);
        final int iValue2 = StringHelper.convertStrToInt(value2,0);

        // take the largest value
        return iValue1 > iValue2 ? value1 : value2;
    }

    protected static String mergeMax(final String value1, final String value2)
    {
        final int iValue1 = StringHelper.convertStrToInt(value1,0);
        final int iValue2 = StringHelper.convertStrToInt(value2,0);

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
        return new PwmPasswordPolicy(policyMap, null);
    }

    public static PwmPasswordPolicy createPwmPasswordPolicy(final PwmSession pwmSession, final ChaiUser theUser)
            throws ChaiUnavailableException
    {
        final long methodStartTime = System.currentTimeMillis();
        PwmPasswordPolicy returnPolicy = pwmSession.getConfig().getGlobalPasswordPolicy();

        if (pwmSession.getConfig().readSettingAsBoolean(PwmSetting.EDIRECTORY_READ_PASSWORD_POLICY)) {
            PwmPasswordPolicy userPolicy = null;
            try {
                final Map<String,String> ruleMap = new HashMap<String,String>();
                final ChaiPasswordPolicy chaiPolicy = theUser.getPasswordPolicy();
                if (chaiPolicy != null) {
                    for (final String key : chaiPolicy.getKeys()) {
                        ruleMap.put(key,chaiPolicy.getValue(key));
                    }
                    userPolicy = new PwmPasswordPolicy(ruleMap,chaiPolicy);
                }
            } catch (ChaiOperationException e) {
                LOGGER.trace(pwmSession, "unable to read ldap password policy: " + e.getMessage());
            }
            if (userPolicy != null) {
                if (userPolicy.getChaiPasswordPolicy() != null && userPolicy.getChaiPasswordPolicy().getPolicyEntry() != null) {
                    LOGGER.debug(pwmSession, "discovered assigned password policy for " + theUser.getEntryDN() + " at " + userPolicy.getChaiPasswordPolicy().getPolicyEntry().getEntryDN() + " " + userPolicy.toString());
                } else {
                    LOGGER.debug(pwmSession, "discovered assigned password policy for " + theUser.getEntryDN() + " " + userPolicy.toString());
                }
                final PwmPasswordPolicy mergedPolicy = returnPolicy.merge(userPolicy);
                returnPolicy = mergedPolicy;
                LOGGER.debug(pwmSession, "merged password policy with PWM configured policy: " + mergedPolicy.toString());
            } else {
                LOGGER.debug(pwmSession, "unable to discover an ldap assigned password policy, using pwm global policy: " + returnPolicy.toString());
            }
        }

        LOGGER.trace(pwmSession, "createPwmPasswordPolicy completed in " + TimeDuration.fromCurrent(methodStartTime).asCompactString());
        return returnPolicy;
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
            return readRegExSetting(passwordPolicy.policyMap.get(PwmPasswordRule.RegExMatch.getKey()));
        }

        public List<Pattern> getRegExNoMatch() {
            return readRegExSetting(passwordPolicy.policyMap.get(PwmPasswordRule.RegExNoMatch.getKey()));
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
            final int defaultValue = StringHelper.convertStrToInt(rule.getDefaultValue(),0);
            return StringHelper.convertStrToInt(value,defaultValue);
        }

        public boolean readBooleanValue(final PwmPasswordRule rule) {
            if (rule.getRuleType() != ChaiPasswordRule.RuleType.BOOLEAN) {
                throw new IllegalArgumentException("attempt to read non-boolean rule value as boolean for rule " + rule);
            }

            final String value = passwordPolicy.policyMap.get(rule.getKey());
            return StringHelper.convertStrToBoolean(value);
        }

        private static List<Pattern> readRegExSetting(final String input) {
            if (input == null) {
                return Collections.emptyList();
            }

            final List<String> values = new ArrayList<String>(StringHelper.tokenizeString(input, ";;;"));
            final List<Pattern> patterns = new ArrayList<Pattern>();

            for (final String value : values) {
                if (value != null && value.length() > 0) {
                    try {
                        final Pattern loopPattern = Pattern.compile(value);
                        patterns.add(loopPattern);
                    } catch (PatternSyntaxException e) {
                        LOGGER.warn("Messages reading config value '" + input + "' is not a valid regular expression " + e.getMessage());
                    }
                }
            }

            return patterns;
        }

        public String getChangeMessage() {
            final String changeMessage = passwordPolicy.getValue(PwmPasswordRule.ChangeMessage);
            return changeMessage == null ? "" : changeMessage;
        }
    }

}