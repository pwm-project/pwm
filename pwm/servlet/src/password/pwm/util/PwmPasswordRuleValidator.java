/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2012 The PWM Project
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

package password.pwm.util;

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiError;
import com.novell.ldapchai.exception.ChaiPasswordPolicyException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.*;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.Configuration;
import password.pwm.config.PwmPasswordRule;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmDataValidationException;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.operations.PasswordUtility;
import password.pwm.util.stats.Statistic;

import java.util.*;
import java.util.regex.Pattern;

public class PwmPasswordRuleValidator {

    private static final PwmLogger LOGGER = PwmLogger.getLogger(PwmPasswordRuleValidator.class);

    private PwmApplication pwmApplication;
    private PwmPasswordPolicy policy;

    public PwmPasswordRuleValidator(final PwmApplication pwmApplication, final PwmPasswordPolicy policy) {
        this.pwmApplication = pwmApplication;
        this.policy = policy;
    }

    public boolean testPassword(
            final String password,
            final String oldPassword,
            final UserInfoBean userInfoBean
    )
            throws PwmUnrecoverableException, PwmDataValidationException, ChaiUnavailableException
    {
        return testPassword(password, oldPassword, userInfoBean, null);
    }

    public boolean testPassword(
            final String password,
            final String oldPassword,
            final UserInfoBean userInfoBean,
            final ChaiUser user
    )
            throws PwmDataValidationException, ChaiUnavailableException, PwmUnrecoverableException {
        final List<ErrorInformation> errorResults = validate(password, oldPassword, userInfoBean);

        if (!errorResults.isEmpty()) {
            throw new PwmDataValidationException(errorResults.iterator().next());
        }

        if (user != null) {
            try {
                LOGGER.trace("calling chai directory password validation checker");
                user.testPasswordPolicy(password);
            } catch (UnsupportedOperationException e) {
                LOGGER.trace("Unsupported operation was thrown while validating password: " + e.toString());
            } catch (ChaiUnavailableException e) {
                pwmApplication.getStatisticsManager().incrementValue(Statistic.LDAP_UNAVAILABLE_COUNT);
                pwmApplication.setLastLdapFailure(new ErrorInformation(PwmError.ERROR_DIRECTORY_UNAVAILABLE,e.getMessage()));
                LOGGER.warn("ChaiUnavailableException was thrown while validating password: " + e.toString());
                throw e;
            } catch (ChaiPasswordPolicyException e) {
                final ChaiError passwordError = e.getErrorCode();
                final PwmError pwmError = PwmError.forChaiError(passwordError);
                final ErrorInformation info = new ErrorInformation(pwmError == null ? PwmError.PASSWORD_UNKNOWN_VALIDATION : pwmError);
                LOGGER.trace("ChaiPasswordPolicyException was thrown while validating password: " + e.toString());
                errorResults.add(info);
            }
        }

        if (!errorResults.isEmpty()) {
            throw new PwmDataValidationException(errorResults.iterator().next());
        }

        return true;
    }


    /**
     * Validates a password against the configured rules of PWM.  No directory operations
     * are performed here.
     *
     * @param password        desired new password
     * @return true if the password is okay, never returns false.
     */
    private List<ErrorInformation> validate(
            final String password,
            final String oldPassword,
            final UserInfoBean uiBean
    )
            throws PwmUnrecoverableException
    {
        final List<ErrorInformation> internalResults = internalPwmPolicyValidator(password, oldPassword, uiBean);
        if (pwmApplication != null) {
            final List<ErrorInformation> externalResults = invokeExternalRuleMethods(pwmApplication.getConfig(), policy, password);
            internalResults.addAll(externalResults);
        }
        return internalResults;
    }


    public List<ErrorInformation> internalPwmPolicyValidator(
            final String password,
            final String oldPassword,
            final UserInfoBean uiBean
    )
            throws PwmUnrecoverableException
    {
        // null check
        if (password == null) {
            return Collections.singletonList(new ErrorInformation(PwmError.ERROR_UNKNOWN, "empty (null) new password"));
        }

        final List<ErrorInformation> errorList = new ArrayList<ErrorInformation>();
        final PwmPasswordPolicy.RuleHelper ruleHelper = policy.getRuleHelper();
        final PasswordCharCounter charCounter = new PasswordCharCounter(password);

        //check against old password
        if (oldPassword != null && oldPassword.length() > 0 && ruleHelper.readBooleanValue(PwmPasswordRule.DisallowCurrent)) {
            if (oldPassword != null && oldPassword.length() > 0) {
                if (oldPassword.equalsIgnoreCase(password)) {
                    errorList.add(new ErrorInformation(PwmError.PASSWORD_SAMEASOLD));
                }
            }

            //check chars from old password
            final int maxOldAllowed = ruleHelper.readIntValue(PwmPasswordRule.MaximumOldChars);
            if (maxOldAllowed > 0) {
                if (oldPassword != null && oldPassword.length() > 0) {
                    final String lPassword = password.toLowerCase();
                    final Set<Character> dupeChars = new HashSet<Character>();

                    //add all dupes to the set.
                    for (final char loopChar : oldPassword.toLowerCase().toCharArray()) {
                        if (lPassword.indexOf(loopChar) != -1) {
                            dupeChars.add(loopChar);
                        }
                    }

                    //count the number of (unique) set elements.
                    if (dupeChars.size() >= maxOldAllowed) {
                        errorList.add(new ErrorInformation(PwmError.PASSWORD_TOO_MANY_OLD_CHARS));
                    }
                }
            }
        }

        final int passwordLength = password.length();

        //Check minimum length
        if (passwordLength < ruleHelper.readIntValue(PwmPasswordRule.MinimumLength)) {
            errorList.add(new ErrorInformation(PwmError.PASSWORD_TOO_SHORT));
        }

        //Check maximum length
        {
            final int passwordMaximumLength = ruleHelper.readIntValue(PwmPasswordRule.MaximumLength);
            if (passwordMaximumLength > 0 && passwordLength > passwordMaximumLength) {
                errorList.add(new ErrorInformation(PwmError.PASSWORD_TOO_LONG));
            }
        }

        // check ad-complexity
        if (ruleHelper.readBooleanValue(PwmPasswordRule.ADComplexity)) {
            errorList.addAll(checkPasswordForADComplexity(uiBean, password, charCounter));
        }

        //check number of numeric characters
        {
            final int numberOfNumericChars = charCounter.getNumericChars();
            if (ruleHelper.readBooleanValue(PwmPasswordRule.AllowNumeric)) {
                if (numberOfNumericChars < ruleHelper.readIntValue(PwmPasswordRule.MinimumNumeric)) {
                    errorList.add(new ErrorInformation(PwmError.PASSWORD_NOT_ENOUGH_NUM));
                }

                final int maxNumeric = ruleHelper.readIntValue(PwmPasswordRule.MaximumNumeric);
                if (maxNumeric > 0 && numberOfNumericChars > maxNumeric) {
                    errorList.add(new ErrorInformation(PwmError.PASSWORD_TOO_MANY_NUMERIC));
                }

                if (!ruleHelper.readBooleanValue(PwmPasswordRule.AllowFirstCharNumeric) && charCounter.isFirstNumeric()) {
                    errorList.add(new ErrorInformation(PwmError.PASSWORD_FIRST_IS_NUMERIC));
                }

                if (!ruleHelper.readBooleanValue(PwmPasswordRule.AllowLastCharNumeric) && charCounter.isLastNumeric()) {
                    errorList.add(new ErrorInformation(PwmError.PASSWORD_LAST_IS_NUMERIC));
                }
            } else {
                if (numberOfNumericChars > 0) {
                    errorList.add(new ErrorInformation(PwmError.PASSWORD_TOO_MANY_NUMERIC));
                }
            }
        }

        //check number of upper characters
        {
            final int numberOfUpperChars = charCounter.getUpperChars();
            if (numberOfUpperChars < ruleHelper.readIntValue(PwmPasswordRule.MinimumUpperCase)) {
                errorList.add(new ErrorInformation(PwmError.PASSWORD_NOT_ENOUGH_UPPER));
            }

            final int maxUpper = ruleHelper.readIntValue(PwmPasswordRule.MaximumUpperCase);
            if (maxUpper > 0 && numberOfUpperChars > maxUpper) {
                errorList.add(new ErrorInformation(PwmError.PASSWORD_TOO_MANY_UPPER));
            }
        }

        //check number of alpha characters
        {
            final int numberOfAlphaChars = charCounter.getAlphaChars();
            if (numberOfAlphaChars < ruleHelper.readIntValue(PwmPasswordRule.MinimumAlpha)) {
                errorList.add(new ErrorInformation(PwmError.PASSWORD_NOT_ENOUGH_ALPHA));
            }

            final int maxAlpha = ruleHelper.readIntValue(PwmPasswordRule.MaximumAlpha);
            if (maxAlpha > 0 && numberOfAlphaChars > maxAlpha) {
                errorList.add(new ErrorInformation(PwmError.PASSWORD_TOO_MANY_ALPHA));
            }
        }

        //check number of non-alpha characters
        {
            final int numberOfNonAlphaChars = charCounter.getNonAlphaChars();

            if (numberOfNonAlphaChars < ruleHelper.readIntValue(PwmPasswordRule.MinimumNonAlpha)) {
                errorList.add(new ErrorInformation(PwmError.PASSWORD_NOT_ENOUGH_NON_ALPHA));
            }

            final int maxNonAlpha = ruleHelper.readIntValue(PwmPasswordRule.MaximumNonAlpha);
            if (maxNonAlpha > 0 && numberOfNonAlphaChars > maxNonAlpha) {
                errorList.add(new ErrorInformation(PwmError.PASSWORD_TOO_MANY_NON_ALPHA));
            }
        }

        //check number of lower characters
        {
            final int numberOfLowerChars = charCounter.getLowerChars();
            if (numberOfLowerChars < ruleHelper.readIntValue(PwmPasswordRule.MinimumLowerCase)) {
                errorList.add(new ErrorInformation(PwmError.PASSWORD_NOT_ENOUGH_LOWER));
            }

            final int maxLower = ruleHelper.readIntValue(PwmPasswordRule.MaximumLowerCase);
            if (maxLower > 0 && numberOfLowerChars > maxLower) {
                errorList.add(new ErrorInformation(PwmError.PASSWORD_TOO_MANY_UPPER));
            }
        }

        //check number of special characters
        {
            final int numberOfSpecialChars = charCounter.getSpecialChars();
            if (ruleHelper.readBooleanValue(PwmPasswordRule.AllowSpecial)) {
                if (numberOfSpecialChars < ruleHelper.readIntValue(PwmPasswordRule.MinimumSpecial)) {
                    errorList.add(new ErrorInformation(PwmError.PASSWORD_NOT_ENOUGH_SPECIAL));
                }

                final int maxSpecial = ruleHelper.readIntValue(PwmPasswordRule.MaximumSpecial);
                if (maxSpecial > 0 && numberOfSpecialChars > maxSpecial) {
                    errorList.add(new ErrorInformation(PwmError.PASSWORD_TOO_MANY_SPECIAL));
                }

                if (!ruleHelper.readBooleanValue(PwmPasswordRule.AllowFirstCharSpecial) && charCounter.isFirstSpecial()) {
                    errorList.add(new ErrorInformation(PwmError.PASSWORD_FIRST_IS_SPECIAL));
                }

                if (!ruleHelper.readBooleanValue(PwmPasswordRule.AllowLastCharSpecial) && charCounter.isLastSpecial()) {
                    errorList.add(new ErrorInformation(PwmError.PASSWORD_LAST_IS_SPECIAL));
                }
            } else {
                if (numberOfSpecialChars > 0) {
                    errorList.add(new ErrorInformation(PwmError.PASSWORD_TOO_MANY_SPECIAL));
                }
            }
        }

        //Check maximum character repeats (sequential)
        {
            final int maxSequentialRepeat = ruleHelper.readIntValue(PwmPasswordRule.MaximumSequentialRepeat);
            if (maxSequentialRepeat > 0 && charCounter.getSequentialRepeatedChars() > maxSequentialRepeat) {
                errorList.add(new ErrorInformation(PwmError.PASSWORD_TOO_MANY_REPEAT));
            }

            //Check maximum character repeats (overall)
            final int maxRepeat = ruleHelper.readIntValue(PwmPasswordRule.MaximumRepeat);
            if (maxRepeat > 0 && charCounter.getRepeatedChars() > maxRepeat) {
                errorList.add(new ErrorInformation(PwmError.PASSWORD_TOO_MANY_REPEAT));
            }
        }

        //Check minimum unique character
        {
            final int minUnique = ruleHelper.readIntValue(PwmPasswordRule.MinimumUnique);
            if (minUnique > 0 && charCounter.getUniqueChars() < minUnique) {
                errorList.add(new ErrorInformation(PwmError.PASSWORD_NOT_ENOUGH_UNIQUE));
            }
        }

        // check against disallowed values;
        if (!ruleHelper.getDisallowedValues().isEmpty()) {
            final String lcasePwd = password.toLowerCase();
            final Set<String> paramValues = new HashSet<String>(ruleHelper.getDisallowedValues());

            for (final String loopValue : paramValues) {
                if (loopValue != null && loopValue.length() > 0) {
                    final String loweredLoop = loopValue.toLowerCase();
                    if (lcasePwd.contains(loweredLoop)) {
                        errorList.add(new ErrorInformation(PwmError.PASSWORD_USING_DISALLOWED_VALUE));
                    }
                }
            }
        }

        // check disallowed attributes.
        if (!policy.getRuleHelper().getDisallowedAttributes().isEmpty()) {
            final List paramConfigs = policy.getRuleHelper().getDisallowedAttributes();
            if (uiBean != null) {
                final Map<String,String> userValues = uiBean.getCachedPasswordRuleAttributes();
                final String lcasePwd = password.toLowerCase();
                for (final Object paramConfig : paramConfigs) {
                    final String attr = (String) paramConfig;
                    final String userValue = userValues.get(attr) == null ? "" : userValues.get(attr).toLowerCase();

                    // if the password is greater then 1 char and the value is contained within it then disallow
                    if (userValue.length() > 1 && lcasePwd.contains(userValue)) {
                        LOGGER.trace("password rejected, same as user attr " + attr);
                        errorList.add(new ErrorInformation(PwmError.PASSWORD_SAMEASATTR));
                    }

                    // if the password is 1 char and the value is the same then disallow
                    if (lcasePwd.equalsIgnoreCase(userValue)) {
                        LOGGER.trace("password rejected, same as user attr " + attr);
                        errorList.add(new ErrorInformation(PwmError.PASSWORD_SAMEASATTR));
                    }
                }
            }
        }

        {   // check password strength
            final int requiredPasswordStrength = ruleHelper.readIntValue(PwmPasswordRule.MinimumStrength);
            if (requiredPasswordStrength > 0) {
                if (pwmApplication != null) {
                    final int passwordStrength = PasswordUtility.checkPasswordStrength(pwmApplication.getConfig(), password);
                    if (passwordStrength < requiredPasswordStrength) {
                        errorList.add(new ErrorInformation(PwmError.PASSWORD_TOO_WEAK));
                        //LOGGER.trace(pwmSession, "password rejected, password strength of " + passwordStrength + " is lower than policy requirement of " + requiredPasswordStrength);
                    }
                }
            }
        }

        // check regex matches.
        for (final Pattern pattern : ruleHelper.getRegExMatch()) {
            if (!pattern.matcher(password).matches()) {
                errorList.add(new ErrorInformation(PwmError.PASSWORD_INVALID_CHAR));
                //LOGGER.trace(pwmSession, "password rejected, does not match configured regex pattern: " + pattern.toString());
            }
        }

        // check no-regex matches.
        for (final Pattern pattern : ruleHelper.getRegExNoMatch()) {
            if (pattern.matcher(password).matches()) {
                errorList.add(new ErrorInformation(PwmError.PASSWORD_INVALID_CHAR));
                //LOGGER.trace(pwmSession, "password rejected, matches configured no-regex pattern: " + pattern.toString());
            }
        }

        // check if the password is in the dictionary.
        if (ruleHelper.readBooleanValue(PwmPasswordRule.EnableWordlist)) {
            if (pwmApplication != null) {
                if (pwmApplication.getWordlistManager().status() == PwmService.STATUS.OPEN) {
                    final boolean found = pwmApplication.getWordlistManager().containsWord(password);

                    if (found) {
                        //LOGGER.trace(pwmSession, "password rejected, in wordlist file");
                        errorList.add(new ErrorInformation(PwmError.PASSWORD_INWORDLIST));
                    }
                } else {
                    //LOGGER.warn(pwmSession, "password wordlist checking enabled, but wordlist is not available, skipping wordlist check");
                }
            }
        }

        // check for shared (global) password history
        if (pwmApplication != null) {
            if (pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.PASSWORD_SHAREDHISTORY_ENABLE) && pwmApplication.getSharedHistoryManager().status() == PwmService.STATUS.OPEN) {
                final boolean found = pwmApplication.getSharedHistoryManager().containsWord(password);

                if (found) {
                    //LOGGER.trace(pwmSession, "password rejected, in global shared history");
                    errorList.add(new ErrorInformation(PwmError.PASSWORD_INWORDLIST));
                }
            }
        }

        return errorList;
    }



    /**
     * Check a supplied password for it's validity according to AD compexity rules.
     * - Not contain the user's account name or parts of the user's full name that exceed two consecutive characters
     * - Be at least six characters in length
     * - Contain characters from three of the following five categories:
     * - English uppercase characters (A through Z)
     * - English lowercase characters (a through z)
     * - Base 10 digits (0 through 9)
     * - Non-alphabetic characters (for example, !, $, #, %)
     * - Any character categorized as an alphabetic but is not uppercase or lowercase.
     * <p/>
     * See this article: http://technet.microsoft.com/en-us/library/cc786468%28WS.10%29.aspx
     *
     * @param userInfoBean userInfoBean
     * @param password    password to test
     * @param charCounter associated charCounter for the password.
     * @return list of errors if the password does not meet requirements, or an empty list if the password complies
     *         with AD requirements
     */
    private static List<ErrorInformation> checkPasswordForADComplexity(
            final UserInfoBean userInfoBean,
            final String password,
            final PasswordCharCounter charCounter
    ) {
        final List<ErrorInformation> errorList = new ArrayList<ErrorInformation>();

        if (userInfoBean != null && userInfoBean.getCachedPasswordRuleAttributes() != null) {
            final Map<String,String> userAttrs = userInfoBean.getCachedPasswordRuleAttributes();
            final String samAccountName = userAttrs.get("sAMAccountName");
            if (samAccountName != null
                    && samAccountName.length() > 2
                    && samAccountName.length() >= password.length()) {
                if (password.toLowerCase().contains(samAccountName.toLowerCase())) {
                    errorList.add(new ErrorInformation(PwmError.PASSWORD_INWORDLIST));
                    LOGGER.trace("Password violation due to ADComplexity check: Password contains sAMAccountName");
                }
            }
            final String displayName = userAttrs.get("displayName");
            if (displayName != null && displayName.length() > 2) {
                if (checkContainsTokens(password, displayName)) {
                    errorList.add(new ErrorInformation(PwmError.PASSWORD_INWORDLIST));
                    LOGGER.trace("Password violation due to ADComplexity check: Tokens from displayName used in password");
                }
            }
        }

        int complexityPoints = 0;
        if (charCounter.getUpperChars() > 0) {
            complexityPoints++;
        }
        if (charCounter.getLowerChars() > 0) {
            complexityPoints++;
        }
        if (charCounter.getNumericChars() > 0) {
            complexityPoints++;
        }
        if (charCounter.getSpecialChars() > 0) {
            complexityPoints++;
        }
        if (charCounter.getOtherLetter() > 0) {
            complexityPoints++;
        }

        if (complexityPoints < 3) {
            if (charCounter.getUpperChars() < 1) {
                errorList.add(new ErrorInformation(PwmError.PASSWORD_NOT_ENOUGH_UPPER));
            }
            if (charCounter.getLowerChars() < 1) {
                errorList.add(new ErrorInformation(PwmError.PASSWORD_NOT_ENOUGH_LOWER));
            }
            if (charCounter.getNumericChars() < 1) {
                errorList.add(new ErrorInformation(PwmError.PASSWORD_NOT_ENOUGH_NUM));
            }
            if (charCounter.getSpecialChars() < 1) {
                errorList.add(new ErrorInformation(PwmError.PASSWORD_NOT_ENOUGH_SPECIAL));
            }
            if (charCounter.getOtherLetter() < 1) {
                errorList.add(new ErrorInformation(PwmError.PASSWORD_UNKNOWN_VALIDATION));
            }
        }

        return errorList;
    }

    private static boolean checkContainsTokens(final String baseValue, final String checkPattern) {
        if (baseValue == null || baseValue.length() == 0)
            return false;

        if (checkPattern == null || checkPattern.length() == 0)
            return false;

        final String baseValueLower = baseValue.toLowerCase();
        final String[] tokens = checkPattern.toLowerCase().split("[,\\.\\-\u2013\u2014_ \u00a3\\t]+");
        if (tokens != null && tokens.length > 0) {
            for (final String token : tokens) {
                if (token.length() > 2) {
                    if (baseValueLower.contains(token))
                        return true;
                }
            }
        }
        return false;
    }

    public static List<ErrorInformation> invokeExternalRuleMethods(
            final Configuration config,
            final PwmPasswordPolicy pwmPasswordPolicy,
            final String password) {
        final List<String> externalMethods = config.readSettingAsStringArray(PwmSetting.EXTERNAL_RULE_METHODS);
        final List<ErrorInformation> returnList = new ArrayList<ErrorInformation>();

        // process any configured external change password methods configured.
        for (final String classNameString : externalMethods) {
            if (classNameString != null && classNameString.length() > 0) {
                try {
                    // load up the class and get an instance.
                    final Class<?> theClass = Class.forName(classNameString);
                    final ExternalRuleMethod externalClass = (ExternalRuleMethod) theClass.newInstance();
                    final List<ErrorInformation> loopReturnList = new ArrayList<ErrorInformation>();

                    // invoke the passwordChange method;
                    final ExternalRuleMethod.RuleValidatorResult result = externalClass.validatePasswordRules(pwmPasswordPolicy, password);
                    if (result != null && result.getPwmErrors() != null) {
                        for (final ErrorInformation errorInformation : result.getPwmErrors()) {
                            loopReturnList.add(errorInformation);
                            LOGGER.debug("externalRuleMethod '" + classNameString + "' returned a value of " + errorInformation.toDebugStr());
                        }
                    }
                    if (result != null && result.getStringErrors() != null) {
                        for (final String errorString : result.getStringErrors()) {
                            final ErrorInformation errorInformation = new ErrorInformation(PwmError.PASSWORD_UNKNOWN_VALIDATION, errorString);
                            loopReturnList.add(errorInformation);
                            LOGGER.debug("externalRuleMethod '" + classNameString + "' returned a value of " + errorInformation.toDebugStr());
                        }
                    }
                    if (loopReturnList.isEmpty()) {
                        LOGGER.debug("externalRuleMethod '" + classNameString + "' returned no values");
                    }
                    returnList.addAll(loopReturnList);
                } catch (ClassCastException e) {
                    LOGGER.warn("configured external class " + classNameString + " is not an instance of " + ExternalChangeMethod.class.getName());
                } catch (ClassNotFoundException e) {
                    LOGGER.warn("unable to load configured external class: " + classNameString + " " + e.getMessage() + "; perhaps the class is not in the classpath?");
                } catch (IllegalAccessException e) {
                    LOGGER.warn("unable to load configured external class: " + classNameString + " " + e.getMessage());
                } catch (InstantiationException e) {
                    LOGGER.warn("unable to load configured external class: " + classNameString + " " + e.getMessage());
                }
            }
        }

        return returnList;
    }

}
