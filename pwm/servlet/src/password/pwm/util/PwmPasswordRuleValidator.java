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

package password.pwm.util;

import com.google.gson.reflect.TypeToken;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiError;
import com.novell.ldapchai.exception.ChaiPasswordPolicyException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.PwmService;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.ADPolicyComplexity;
import password.pwm.config.profile.PwmPasswordPolicy;
import password.pwm.config.profile.PwmPasswordRule;
import password.pwm.error.*;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.operations.PasswordUtility;
import password.pwm.util.stats.Statistic;
import password.pwm.ws.client.rest.RestClientHelper;
import password.pwm.ws.server.rest.RestStatusServer;

import java.util.*;
import java.util.regex.Pattern;

public class PwmPasswordRuleValidator {

    private static final PwmLogger LOGGER = PwmLogger.forClass(PwmPasswordRuleValidator.class);

    private final PwmApplication pwmApplication;
    private final PwmPasswordPolicy policy;
    private final Locale locale;

    public PwmPasswordRuleValidator(final PwmApplication pwmApplication, final PwmPasswordPolicy policy) {
        this.pwmApplication = pwmApplication;
        this.policy = policy;
        this.locale = PwmConstants.DEFAULT_LOCALE;
    }

    public PwmPasswordRuleValidator(
            PwmApplication pwmApplication,
            PwmPasswordPolicy policy,
            Locale locale
    )
    {
        this.pwmApplication = pwmApplication;
        this.policy = policy;
        this.locale = locale;
    }

    public boolean testPassword(
            final PasswordData password,
            final PasswordData oldPassword,
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
                user.testPasswordPolicy(password.getStringValue());
            } catch (UnsupportedOperationException e) {
                LOGGER.trace("Unsupported operation was thrown while validating password: " + e.toString());
            } catch (ChaiUnavailableException e) {
                pwmApplication.getStatisticsManager().incrementValue(Statistic.LDAP_UNAVAILABLE_COUNT);
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
            final PasswordData password,
            final PasswordData oldPassword,
            final UserInfoBean uiBean
    )
            throws PwmUnrecoverableException
    {
        final List<ErrorInformation> internalResults = internalPwmPolicyValidator(password, oldPassword, uiBean, false);
        if (pwmApplication != null) {
            final List<ErrorInformation> externalResults = invokeExternalRuleMethods(pwmApplication.getConfig(), policy, password, uiBean);
            internalResults.addAll(externalResults);
        }
        return internalResults;
    }

    public List<ErrorInformation> internalPwmPolicyValidator(
            final PasswordData password,
            final PasswordData oldPassword,
            final UserInfoBean uiBean,
            final boolean failFast
    )
            throws PwmUnrecoverableException
    {
        final String passwordString = password == null ? "" : password.getStringValue();
        final String oldPasswordString = oldPassword == null ? null : oldPassword.getStringValue();
        return internalPwmPolicyValidator(passwordString, oldPasswordString, uiBean, failFast);
    }

    public List<ErrorInformation> internalPwmPolicyValidator(
            final String passwordString,
            final String oldPasswordString,
            final UserInfoBean uiBean,
            final boolean failFast
    )
            throws PwmUnrecoverableException
    {
        // null check
        if (passwordString == null) {
            return Collections.singletonList(new ErrorInformation(PwmError.ERROR_UNKNOWN, "empty (null) new password"));
        }

        final List<ErrorInformation> errorList = new ArrayList<>();
        final PwmPasswordPolicy.RuleHelper ruleHelper = policy.getRuleHelper();

        //check against old password
        if (oldPasswordString != null && oldPasswordString.length() > 0 && ruleHelper.readBooleanValue(PwmPasswordRule.DisallowCurrent)) {
            if (oldPasswordString.length() > 0) {
                if (oldPasswordString.equalsIgnoreCase(passwordString)) {
                    errorList.add(new ErrorInformation(PwmError.PASSWORD_SAMEASOLD));
                }
            }

            //check chars from old password
            final int maxOldAllowed = ruleHelper.readIntValue(PwmPasswordRule.MaximumOldChars);
            if (maxOldAllowed > 0) {
                if (oldPasswordString.length() > 0) {
                    final String lPassword = passwordString.toLowerCase();
                    final Set<Character> dupeChars = new HashSet<>();

                    //add all dupes to the set.
                    for (final char loopChar : oldPasswordString.toLowerCase().toCharArray()) {
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

        if (failFast && errorList.size() > 1) {
            return errorList;
        }

        errorList.addAll(basicSyntaxRuleChecks(passwordString,policy,uiBean));

        if (failFast && errorList.size() > 1) {
            return errorList;
        }

        // check against disallowed values;
        if (!ruleHelper.getDisallowedValues().isEmpty()) {
            final String lcasePwd = passwordString.toLowerCase();
            final Set<String> paramValues = new HashSet<>(ruleHelper.getDisallowedValues());

            for (final String loopValue : paramValues) {
                if (loopValue != null && loopValue.length() > 0) {
                    final String loweredLoop = loopValue.toLowerCase();
                    if (lcasePwd.contains(loweredLoop)) {
                        errorList.add(new ErrorInformation(PwmError.PASSWORD_USING_DISALLOWED_VALUE));
                    }
                }
            }
        }

        if (failFast && errorList.size() > 1) {
            return errorList;
        }

        // check disallowed attributes.
        if (!policy.getRuleHelper().getDisallowedAttributes().isEmpty()) {
            final List paramConfigs = policy.getRuleHelper().getDisallowedAttributes();
            if (uiBean != null) {
                final Map<String,String> userValues = uiBean.getCachedPasswordRuleAttributes();
                final String lcasePwd = passwordString.toLowerCase();
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

        if (failFast && errorList.size() > 1) {
            return errorList;
        }

        {   // check password strength
            final int requiredPasswordStrength = ruleHelper.readIntValue(PwmPasswordRule.MinimumStrength);
            if (requiredPasswordStrength > 0) {
                if (pwmApplication != null) {
                    final int passwordStrength = PasswordUtility.judgePasswordStrength(passwordString);
                    if (passwordStrength < requiredPasswordStrength) {
                        errorList.add(new ErrorInformation(PwmError.PASSWORD_TOO_WEAK));
                        //LOGGER.trace(pwmSession, "password rejected, password strength of " + passwordStrength + " is lower than policy requirement of " + requiredPasswordStrength);
                    }
                }
            }
        }

        if (failFast && errorList.size() > 1) {
            return errorList;
        }

        // check regex matches.
        for (final Pattern pattern : ruleHelper.getRegExMatch()) {
            if (!pattern.matcher(passwordString).matches()) {
                errorList.add(new ErrorInformation(PwmError.PASSWORD_INVALID_CHAR));
                //LOGGER.trace(pwmSession, "password rejected, does not match configured regex pattern: " + pattern.toString());
            }
        }

        if (failFast && errorList.size() > 1) {
            return errorList;
        }

        // check no-regex matches.
        for (final Pattern pattern : ruleHelper.getRegExNoMatch()) {
            if (pattern.matcher(passwordString).matches()) {
                errorList.add(new ErrorInformation(PwmError.PASSWORD_INVALID_CHAR));
                //LOGGER.trace(pwmSession, "password rejected, matches configured no-regex pattern: " + pattern.toString());
            }
        }

        if (failFast && errorList.size() > 1) {
            return errorList;
        }

        // check char group matches
        if (ruleHelper.readIntValue(PwmPasswordRule.CharGroupsMinMatch) > 0) {
            final List<Pattern> ruleGroups = ruleHelper.getCharGroupValues();
            if (ruleGroups != null && !ruleGroups.isEmpty()) {
                final int requiredMatches = ruleHelper.readIntValue(PwmPasswordRule.CharGroupsMinMatch);
                int matches = 0;
                for (final Pattern pattern : ruleGroups) {
                    if (pattern.matcher(passwordString).find()) {
                        matches++;
                    }
                }
                if (matches < requiredMatches) {
                    errorList.add(new ErrorInformation(PwmError.PASSWORD_NOT_ENOUGH_GROUPS));
                }
            }
            if (failFast && errorList.size() > 1) {
                return errorList;
            }
        }

        if (failFast && errorList.size() > 1) {
            return errorList;
        }

        // check if the password is in the dictionary.
        if (ruleHelper.readBooleanValue(PwmPasswordRule.EnableWordlist)) {
            if (pwmApplication != null) {
                if (pwmApplication.getWordlistManager().status() == PwmService.STATUS.OPEN) {
                    final boolean found = pwmApplication.getWordlistManager().containsWord(passwordString);

                    if (found) {
                        //LOGGER.trace(pwmSession, "password rejected, in wordlist file");
                        errorList.add(new ErrorInformation(PwmError.PASSWORD_INWORDLIST));
                    }
                } else {
                    /* noop */
                    //LOGGER.warn(pwmSession, "password wordlist checking enabled, but wordlist is not available, skipping wordlist check");
                }
            }
            if (failFast && errorList.size() > 1) {
                return errorList;
            }
        }

        if (failFast && errorList.size() > 1) {
            return errorList;
        }

        // check for shared (global) password history
        if (pwmApplication != null) {
            if (pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.PASSWORD_SHAREDHISTORY_ENABLE) && pwmApplication.getSharedHistoryManager().status() == PwmService.STATUS.OPEN) {
                final boolean found = pwmApplication.getSharedHistoryManager().containsWord(passwordString);

                if (found) {
                    //LOGGER.trace(pwmSession, "password rejected, in global shared history");
                    errorList.add(new ErrorInformation(PwmError.PASSWORD_INWORDLIST));
                }
            }
            if (failFast && errorList.size() > 1) {
                return errorList;
            }
        }

        return errorList;
    }



    /**
     * Check a supplied password for it's validity according to AD complexity rules.
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
            final ADPolicyComplexity complexityLevel,
            final UserInfoBean userInfoBean,
            final String password,
            final PasswordCharCounter charCounter,
            final int maxGroupViolationCount
    ) {
        final List<ErrorInformation> errorList = new ArrayList<>();

        if (password == null || password.length() < 6) {
            errorList.add(new ErrorInformation(PwmError.PASSWORD_TOO_SHORT));
            return errorList;
        }

        final int maxLength = complexityLevel == ADPolicyComplexity.AD2003 ? 128 : 512;
        if (password.length() > maxLength) {
            errorList.add(new ErrorInformation(PwmError.PASSWORD_TOO_LONG));
            return errorList;
        }

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
        if (charCounter.getUpperCharCount() > 0) {
            complexityPoints++;
        }
        if (charCounter.getLowerCharCount() > 0) {
            complexityPoints++;
        }
        if (charCounter.getNumericCharCount() > 0) {
            complexityPoints++;
        }
        switch (complexityLevel) {
            case AD2003:
                if (charCounter.getSpecialCharsCount() > 0 || charCounter.getOtherLetterCharCount() > 0) {
                    complexityPoints++;
                }
                break;

            case AD2008:
                if (charCounter.getSpecialCharsCount() > 0) {
                    complexityPoints++;
                }
                if (charCounter.getOtherLetterCharCount() > 0) {
                    complexityPoints++;
                }
                break;
        }

        switch (complexityLevel) {
            case AD2008:
                final int totalGroups = 5;
                final int violations = totalGroups - complexityPoints;
                if (violations <= maxGroupViolationCount) {
                    return errorList;
                }
                break;

            case AD2003:
                if (complexityPoints < 3) {
                    return errorList;
                }
                break;
        }

        if (charCounter.getUpperCharCount() < 1) {
            errorList.add(new ErrorInformation(PwmError.PASSWORD_NOT_ENOUGH_UPPER));
        }
        if (charCounter.getLowerCharCount() < 1) {
            errorList.add(new ErrorInformation(PwmError.PASSWORD_NOT_ENOUGH_LOWER));
        }
        if (charCounter.getNumericCharCount() < 1) {
            errorList.add(new ErrorInformation(PwmError.PASSWORD_NOT_ENOUGH_NUM));
        }
        if (charCounter.getSpecialCharsCount() < 1) {
            errorList.add(new ErrorInformation(PwmError.PASSWORD_NOT_ENOUGH_SPECIAL));
        }
        if (charCounter.getOtherLetterCharCount() < 1) {
            errorList.add(new ErrorInformation(PwmError.PASSWORD_UNKNOWN_VALIDATION));
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

    final static private String REST_RESPONSE_KEY_ERROR = "error";
    final static private String REST_RESPONSE_KEY_ERROR_MSG = "errorMessage";

    public List<ErrorInformation> invokeExternalRuleMethods(
            final Configuration config,
            final PwmPasswordPolicy pwmPasswordPolicy,
            final PasswordData password,
            final UserInfoBean uiBean
    )
            throws PwmUnrecoverableException
    {
        final List<ErrorInformation> returnedErrors = new ArrayList<>();
        final String restURL = config.readSettingAsString(PwmSetting.EXTERNAL_PWCHECK_REST_URLS);
        final boolean haltOnError = Boolean.parseBoolean(config.readAppProperty(AppProperty.WS_REST_CLIENT_PWRULE_HALTONERROR));
        final Map<String,Object> sendData = new LinkedHashMap<>();


        if (restURL == null || restURL.isEmpty()) {
            return Collections.emptyList();
        }

        {
            final String passwordStr = password == null ? "" : password.getStringValue();
            sendData.put("password", passwordStr);
        }

        if (pwmPasswordPolicy != null) {
            final LinkedHashMap<String,Object> policyData = new LinkedHashMap<>();
            for (final PwmPasswordRule rule : PwmPasswordRule.values()) {
                policyData.put(rule.name(),pwmPasswordPolicy.getValue(rule));
            }
            sendData.put("policy",policyData);
        }
        if (uiBean != null) {
            final RestStatusServer.JsonStatusData jsonStatusData = RestStatusServer.JsonStatusData.fromUserInfoBean(uiBean, pwmApplication.getConfig(), locale);
            sendData.put("userInfo", jsonStatusData);
        }

        final String jsonRequestBody = JsonUtil.serializeMap(sendData);
        try {
            final String responseBody = RestClientHelper.makeOutboundRestWSCall(pwmApplication, locale, restURL,
                    jsonRequestBody);
            final Map<String,Object> responseMap = JsonUtil.deserialize(responseBody, 
                    new TypeToken<Map<String, Object>>() {}
            );
            if (responseMap.containsKey(REST_RESPONSE_KEY_ERROR) && Boolean.parseBoolean(responseMap.get(
                    REST_RESPONSE_KEY_ERROR).toString())) {
                if (responseMap.containsKey(REST_RESPONSE_KEY_ERROR_MSG)) {
                    final String errorMessage = responseMap.get(REST_RESPONSE_KEY_ERROR_MSG).toString();
                    LOGGER.trace("external web service reported error: " + errorMessage);
                    returnedErrors.add(new ErrorInformation(PwmError.PASSWORD_CUSTOM_ERROR,errorMessage,errorMessage,null));
                } else {
                    LOGGER.trace("external web service reported error without specifying an errorMessage");
                    returnedErrors.add(new ErrorInformation(PwmError.PASSWORD_CUSTOM_ERROR));
                }
            } else {
                LOGGER.trace("external web service did not report an error");
            }

        } catch (PwmOperationalException e) {
            final String errorMsg = "error executing external rule REST call: " + e.getMessage();
            LOGGER.error(errorMsg);
            if (haltOnError) {
                throw new PwmUnrecoverableException(e.getErrorInformation(),e);
            }
            throw new IllegalStateException("http response error code: " + e.getMessage());
        }
        return returnedErrors;
    }

    private static List<ErrorInformation> basicSyntaxRuleChecks(
            final String password,
            final PwmPasswordPolicy policy,
            final UserInfoBean uiBean
    ) {
        final List<ErrorInformation> errorList = new ArrayList<>();
        final PwmPasswordPolicy.RuleHelper ruleHelper = policy.getRuleHelper();
        final PasswordCharCounter charCounter = new PasswordCharCounter(password);

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

        //check number of numeric characters
        {
            final int numberOfNumericChars = charCounter.getNumericCharCount();
            if (ruleHelper.readBooleanValue(PwmPasswordRule.AllowNumeric)) {
                if (numberOfNumericChars < ruleHelper.readIntValue(PwmPasswordRule.MinimumNumeric)) {
                    errorList.add(new ErrorInformation(PwmError.PASSWORD_NOT_ENOUGH_NUM));
                }

                final int maxNumeric = ruleHelper.readIntValue(PwmPasswordRule.MaximumNumeric);
                if (maxNumeric > 0 && numberOfNumericChars > maxNumeric) {
                    errorList.add(new ErrorInformation(PwmError.PASSWORD_TOO_MANY_NUMERIC));
                }

                if (!ruleHelper.readBooleanValue(
                        PwmPasswordRule.AllowFirstCharNumeric) && charCounter.isFirstNumeric()) {
                    errorList.add(new ErrorInformation(PwmError.PASSWORD_FIRST_IS_NUMERIC));
                }

                if (!ruleHelper.readBooleanValue(
                        PwmPasswordRule.AllowLastCharNumeric) && charCounter.isLastNumeric()) {
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
            final int numberOfUpperChars = charCounter.getUpperCharCount();
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
            final int numberOfAlphaChars = charCounter.getAlphaCharCount();
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
            final int numberOfNonAlphaChars = charCounter.getNonAlphaCharCount();

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
            final int numberOfLowerChars = charCounter.getLowerCharCount();
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
            final int numberOfSpecialChars = charCounter.getSpecialCharsCount();
            if (ruleHelper.readBooleanValue(PwmPasswordRule.AllowSpecial)) {
                if (numberOfSpecialChars < ruleHelper.readIntValue(PwmPasswordRule.MinimumSpecial)) {
                    errorList.add(new ErrorInformation(PwmError.PASSWORD_NOT_ENOUGH_SPECIAL));
                }

                final int maxSpecial = ruleHelper.readIntValue(PwmPasswordRule.MaximumSpecial);
                if (maxSpecial > 0 && numberOfSpecialChars > maxSpecial) {
                    errorList.add(new ErrorInformation(PwmError.PASSWORD_TOO_MANY_SPECIAL));
                }

                if (!ruleHelper.readBooleanValue(
                        PwmPasswordRule.AllowFirstCharSpecial) && charCounter.isFirstSpecial()) {
                    errorList.add(new ErrorInformation(PwmError.PASSWORD_FIRST_IS_SPECIAL));
                }

                if (!ruleHelper.readBooleanValue(
                        PwmPasswordRule.AllowLastCharSpecial) && charCounter.isLastSpecial()) {
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

        // check ad-complexity
        {
            final ADPolicyComplexity complexityLevel = ruleHelper.getADComplexityLevel();
            if (complexityLevel == ADPolicyComplexity.AD2003 || complexityLevel == ADPolicyComplexity.AD2008) {
                final int maxGroupViolations = ruleHelper.readIntValue(PwmPasswordRule.ADComplexityMaxViolations);
                errorList.addAll(checkPasswordForADComplexity(complexityLevel, uiBean, password, charCounter,
                        maxGroupViolations));
            }
        }

        // check consecutive characters
        {
            final int maximumConsecutive = ruleHelper.readIntValue(PwmPasswordRule.MaximumConsecutive);
            if (maximumConsecutive > 0 && passwordLength >= maximumConsecutive) {
                final char[] lowerPassCharArray = password.toLowerCase().toCharArray();
                boolean violated = false;
                for (int position = 0; (position+maximumConsecutive <= lowerPassCharArray.length && !violated); position++) {
                    int violationCharCount = 1;
                    int direction = 0;
                    int previousCharPoint = Character.codePointAt(lowerPassCharArray,position);

                    for (int distance = 1; violationCharCount >= 0 && !violated; distance++) {
                        int nextCharPoint = Character.codePointAt(lowerPassCharArray, position + distance);

                        if ((direction == 0 || direction == 1) && (previousCharPoint == nextCharPoint +1)) {
                            direction = 1;
                            violationCharCount++;
                        } else if ((direction == 0 || direction == -1) && (previousCharPoint == nextCharPoint -1)) {
                            direction = -1;
                            violationCharCount++;
                        } else {
                            violationCharCount = -1;
                        }

                        if (violationCharCount > maximumConsecutive) {
                            violated= true;
                        } else {
                            previousCharPoint = nextCharPoint;
                        }
                    }
                }
                if (violated) {
                    errorList.add(new ErrorInformation(PwmError.PASSWORD_TOO_MANY_CONSECUTIVE));
                }
            }

        }

        return errorList;
    }
}
