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

package password.pwm;

import com.novell.ldapchai.ChaiFactory;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiError;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiPasswordPolicyException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiProvider;
import com.novell.ldapchai.util.SearchHelper;
import password.pwm.bean.SessionStateBean;
import password.pwm.config.Configuration;
import password.pwm.config.FormConfiguration;
import password.pwm.config.PwmPasswordRule;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmDataValidationException;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.Helper;
import password.pwm.util.PasswordCharCounter;
import password.pwm.util.PwmLogger;
import password.pwm.util.operations.PasswordUtility;
import password.pwm.util.stats.Statistic;

import javax.servlet.http.HttpServletRequest;
import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Static utility class for validating parameters, passwords and user input.
 *
 * @author Jason D. Rivard
 */
public class Validator {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(Validator.class);

    public static final String PARAM_CONFIRM_SUFFIX = "_confirm";



// -------------------------- STATIC METHODS --------------------------

    public static boolean readBooleanFromRequest(
            final HttpServletRequest req,
            final String value
    ) {
        if (req == null) {
            return false;
        }

        final String theString = req.getParameter(value);

        return theString != null && (theString.equalsIgnoreCase("true") ||
                theString.equalsIgnoreCase("1") ||
                theString.equalsIgnoreCase("yes") ||
                theString.equalsIgnoreCase("y"));

    }

    public static boolean testPasswordAgainstPolicy(
            final String password,
            final PwmSession pwmSession,
            final PwmApplication pwmApplication
    )
            throws PwmDataValidationException, ChaiUnavailableException, PwmUnrecoverableException {
        final PwmPasswordPolicy policy = pwmSession.getUserInfoBean().getPasswordPolicy();
        final String oldPassword = pwmSession.getUserInfoBean().getUserCurrentPassword();
        return testPasswordAgainstPolicy(password, oldPassword, pwmSession, pwmApplication, policy, true);
    }

    /**
     * Validates a password against the configured rules of PWM.  No directory operations
     * are performed here.
     *
     * @param password        desired new password
     * @param pwmSession      current pwmSesssion of user being tested.
     * @param policy          to be used during the test
     * @return true if the password is okay, never returns false.
     * @throws password.pwm.error.PwmDataValidationException
     *                                  contains information about why the password was rejected.
     * @throws password.pwm.error.PwmUnrecoverableException             if an unexpected error occurs
     *                                  contains information about why the password was rejected.
     * @throws ChaiUnavailableException if LDAP server is unreachable
     */
    public static boolean testPasswordAgainstPolicy(
            final String password,
            final String oldPassword,
            final PwmSession pwmSession,
            final PwmApplication pwmApplication,
            final PwmPasswordPolicy policy,
            final boolean testAgainstLdap
    )
            throws PwmDataValidationException, ChaiUnavailableException, PwmUnrecoverableException {
        final List<ErrorInformation> errorResults = pwmPasswordPolicyValidator(password, oldPassword, pwmSession, policy, pwmApplication);

        if (!errorResults.isEmpty()) {
            throw new PwmDataValidationException(errorResults.iterator().next());
        }

        if (testAgainstLdap) {
            try {
                LOGGER.trace(pwmSession, "calling chai directory password validation checker");
                final ChaiProvider provider = pwmSession.getSessionManager().getChaiProvider();
                final ChaiUser actor = ChaiFactory.createChaiUser(pwmSession.getUserInfoBean().getUserDN(), provider);
                actor.testPasswordPolicy(password);
            } catch (UnsupportedOperationException e) {
                LOGGER.trace(pwmSession, "Unsupported operation was thrown while validating password: " + e.toString());
            } catch (ChaiUnavailableException e) {
                pwmApplication.getStatisticsManager().incrementValue(Statistic.LDAP_UNAVAILABLE_COUNT);
                pwmApplication.setLastLdapFailure(new ErrorInformation(PwmError.ERROR_DIRECTORY_UNAVAILABLE,e.getMessage()));
                LOGGER.warn(pwmSession, "ChaiUnavailableException was thrown while validating password: " + e.toString());
                throw e;
            } catch (ChaiPasswordPolicyException e) {
                final ChaiError passwordError = e.getErrorCode();
                final PwmError pwmError = PwmError.forChaiError(passwordError);
                final ErrorInformation info = new ErrorInformation(pwmError == null ? PwmError.PASSWORD_UNKNOWN_VALIDATION : pwmError);
                LOGGER.trace(pwmSession, "ChaiPasswordPolicyException was thrown while validating password: " + e.toString());
                errorResults.add(info);
            }
        }

        if (!errorResults.isEmpty()) {
            throw new PwmDataValidationException(errorResults.iterator().next());
        }

        return true;
    }

    public static Map<FormConfiguration, String> readFormValuesFromRequest(
            final HttpServletRequest req,
            final Collection<FormConfiguration> formConfigurations
    )
            throws PwmDataValidationException, PwmUnrecoverableException
    {
        final Map<String,String> tempMap = readRequestParametersAsMap(req);
        return readFormValuesFromMap(tempMap, formConfigurations);
    }


    public static Map<FormConfiguration, String> readFormValuesFromMap(
            final Map<String,String> inputMap,
            final Collection<FormConfiguration> formConfigurations
    )
            throws PwmDataValidationException, PwmUnrecoverableException {
        if (formConfigurations == null || formConfigurations.isEmpty()) {
            return Collections.emptyMap();
        }

        final Map<FormConfiguration, String> returnMap = new LinkedHashMap<FormConfiguration,String>();
        for (final FormConfiguration formConfiguration : formConfigurations) {
            returnMap.put(formConfiguration,"");
        }

        if (inputMap == null) {
            return returnMap;
        }

        for (final FormConfiguration formConfiguration : formConfigurations) {
            final String keyName = formConfiguration.getAttributeName();
            final String value = inputMap.get(keyName);

            if (formConfiguration.isRequired()) {
                if (value == null || value.length() < 0) {
                    final String errorMsg = "missing required value for field '" + formConfiguration.getAttributeName() + "'";
                    final ErrorInformation error = new ErrorInformation(PwmError.ERROR_FIELD_REQUIRED, errorMsg, formConfiguration.getLabel());
                    throw new PwmDataValidationException(error);
                }
            }

            if (formConfiguration.isConfirmationRequired()) {
                final String confirmValue = inputMap.get(keyName + PARAM_CONFIRM_SUFFIX);
                if (!confirmValue.equals(value)) {
                    final String errorMsg = "incorrect confirmation value for field '" + formConfiguration.getAttributeName() + "'";
                    final ErrorInformation error = new ErrorInformation(PwmError.ERROR_FIELD_BAD_CONFIRM, errorMsg, formConfiguration.getLabel());
                    throw new PwmDataValidationException(error);
                }
            }
            if (value != null) {
                returnMap.put(formConfiguration,value);
            }
        }

        return returnMap;
    }

    public static String readStringFromRequest(
            final HttpServletRequest req,
            final String value
    ) throws PwmUnrecoverableException {
        final Set<String> results = readStringsFromRequest(req, value, PwmConstants.HTTP_PARAMETER_READ_LENGTH);
        if (results == null || results.isEmpty()) {
            return "";
        }

        return results.iterator().next();
    }

    public static void validatePwmFormID(final HttpServletRequest req) throws PwmUnrecoverableException {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();
        final String pwmFormID = ssBean.getSessionVerificationKey();
        final long requestSequenceCounter = ssBean.getRequestCounter();

        final String submittedPwmFormID = req.getParameter(PwmConstants.PARAM_FORM_ID);

        if (pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.SECURITY_ENABLE_FORM_NONCE)) {
            if (submittedPwmFormID == null || submittedPwmFormID.length() < 1) {
                LOGGER.warn(pwmSession, "form submitted with missing pwmFormID value");
                throw new PwmUnrecoverableException(PwmError.ERROR_INVALID_FORMID);
            }

            if (!pwmFormID.equals(submittedPwmFormID.substring(0,pwmFormID.length()))) {
                LOGGER.warn(pwmSession, "form submitted with incorrect pwmFormID value");
                throw new PwmUnrecoverableException(PwmError.ERROR_INVALID_FORMID);
            }
        }

        if (pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.SECURITY_ENABLE_REQUEST_SEQUENCE)) {
            try {
                final String submittedSequenceCounterStr = submittedPwmFormID.substring(pwmFormID.length(),submittedPwmFormID.length());
                final long submittedSequenceCounter = Long.parseLong(submittedSequenceCounterStr,36);
                if (submittedSequenceCounter != requestSequenceCounter) {
                    LOGGER.warn(pwmSession, "form submitted with incorrect pwmFormID-requestSequence value");
                    throw new PwmUnrecoverableException(PwmError.ERROR_INCORRECT_REQUEST_SEQUENCE);
                }
            } catch (NumberFormatException e) {
                LOGGER.warn(pwmSession, "unable to parse pwmFormID-requestSequence value: " + e.getMessage());
                throw new PwmUnrecoverableException(PwmError.ERROR_INCORRECT_REQUEST_SEQUENCE);
            }
        }
    }

    public static String readStringFromRequest(
            final HttpServletRequest req,
            final String value,
            final int maxLength,
            final String defaultValue
    ) throws PwmUnrecoverableException {

        final String result = readStringFromRequest(req, value, maxLength);
        if (result == null || result.length() < 1) {
            return defaultValue;
        }

        return result;
    }

    public static String readStringFromRequest(
            final HttpServletRequest req,
            final String value,
            final int maxLength
    ) throws PwmUnrecoverableException {
        final Set<String> results = readStringsFromRequest(req, value, maxLength);
        if (results == null || results.isEmpty()) {
            return "";
        }

        return results.iterator().next();
    }

    public static Set<String> readStringsFromRequest(
            final HttpServletRequest req,
            final String value,
            final int maxLength
    ) throws PwmUnrecoverableException {
        if (req == null) {
            return Collections.emptySet();
        }

        if (req.getParameter(value) == null) {
            return Collections.emptySet();
        }

        final PwmApplication theManager = ContextManager.getPwmApplication(req);

        final String theStrings[] = req.getParameterValues(value);
        final Set<String> resultSet = new HashSet<String>();

        for (String theString : theStrings) {
            if (req.getCharacterEncoding() == null) {
                try {
                    final byte[] stringBytesISO = theString.getBytes("ISO-8859-1");
                    theString = new String(stringBytesISO, "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    LOGGER.warn("suspicious input: error attempting to decode request: " + e.getMessage());
                }
            }

            final String sanatizedValue = sanatizeInputValue(theManager.getConfig(), theString, maxLength);

            if (sanatizedValue.length() > 0) {
                resultSet.add(sanatizedValue);
            }
        }

        return resultSet;
    }

    public static String sanatizeInputValue(final Configuration config, final String input, final int maxLength) {

        String theString = input;

        theString = theString.trim();

        // strip off any length beyond the specified maxLength.
        if (theString.length() > maxLength) {
            theString = theString.substring(0, maxLength);
        }

        // strip off any disallowed chars.
        if (config != null) {
            final List<String> disallowedInputs = config.readSettingAsStringArray(PwmSetting.DISALLOWED_HTTP_INPUTS);
            for (final String testString : disallowedInputs) {
                final String newString = theString.replaceAll(testString, "");
                if (!newString.equals(theString)) {
                    LOGGER.warn("removing potentially malicious string values from input, converting '" + input + "' newValue=" + newString + "' pattern='" + testString + "'");
                    theString = newString;
                }
            }
        }

        return theString;
    }

    /**
     * Validates each of the parameters in the supplied map against the vales in the embedded config
     * and checks to make sure the ParamConfig value meets the requiremetns of the ParamConfig itself.
     *
     * @param formValues - a Map containing String keys of parameter names and ParamConfigs as values
     * @throws password.pwm.error.PwmDataValidationException - If there is a problem with any of the fields
     * @throws com.novell.ldapchai.exception.ChaiUnavailableException
     *                             if ldap server becomes unavailable
     * @throws password.pwm.error.PwmUnrecoverableException
     *                             if an unexpected error occurs
     */
    public static void validateParmValuesMeetRequirements(
            final PwmApplication pwmApplication,
            final Map<FormConfiguration, String> formValues
    )
            throws PwmUnrecoverableException, ChaiUnavailableException, PwmDataValidationException
    {
        for (final FormConfiguration formConfiguration : formValues.keySet()) {
            final String value = formValues.get(formConfiguration);
            formConfiguration.checkValue(pwmApplication, value);
        }
    }

    public static void validateNumericString(
            final String numstr,
            final Integer lowerbound,
            final Integer upperbound,
            final PwmSession pwmSession
    ) throws PwmUnrecoverableException, NumberFormatException, PwmDataValidationException {
        final Integer num;
        try {
            num = Integer.parseInt(numstr);
        } catch (Exception e) {
            final ErrorInformation error = new ErrorInformation(PwmError.NUMBERVALIDATION_INVALIDNUMER, null, numstr);
            LOGGER.trace(pwmSession, "value \""+numstr+"\" is not a valid number");
            throw new PwmDataValidationException(error);
        }
        if (num < lowerbound) {
            final ErrorInformation error = new ErrorInformation(PwmError.NUMBERVALIDATION_LOWERBOUND, null, lowerbound.toString());
            LOGGER.trace(pwmSession, "value "+numstr+" below lower bound ("+lowerbound.toString()+")");
            throw new PwmDataValidationException(error);
        }
        if (num > upperbound) {
            final ErrorInformation error = new ErrorInformation(PwmError.NUMBERVALIDATION_UPPERBOUND, null, upperbound.toString());
            LOGGER.trace(pwmSession, "value "+numstr+" above upper bound ("+upperbound.toString()+")");
            throw new PwmDataValidationException(error);
        }
    }

    /**
     * Validates a password against the configured rules of PWM.  No directory operations
     * are performed here.
     *
     * @param password        desired new password
     * @param pwmSession      current pwmSession of user being tested.
     * @param policy          to be used during the test
     * @param pwmApplication  PWM PwmApplication (needed for access to seedlist/wordlists when pwmSession is null)
     * @return true if the password is okay, never returns false.
     */
    public static List<ErrorInformation> pwmPasswordPolicyValidator(
            final String password,
            final String oldPassword,
            final PwmSession pwmSession,
            final PwmPasswordPolicy policy,
            final PwmApplication pwmApplication
    ) throws PwmUnrecoverableException {
        final List<ErrorInformation> internalResults = internalPwmPolicyValidator(password, oldPassword, pwmSession,  policy, pwmApplication);
        if (pwmApplication != null) {
            final List<ErrorInformation> externalResults = Helper.invokeExternalRuleMethods(pwmApplication.getConfig(), pwmSession, policy, password);
            internalResults.addAll(externalResults);
        }
        return internalResults;
    }


    private static List<ErrorInformation> internalPwmPolicyValidator(
            final String password,
            final String oldPassword,
            final PwmSession pwmSession,
            final PwmPasswordPolicy policy,
            final PwmApplication pwmApplication
    ) throws PwmUnrecoverableException {
        // null check
        if (password == null) {
            return Collections.singletonList(new ErrorInformation(PwmError.ERROR_UNKNOWN, "empty (null) new password"));
        }

        final List<ErrorInformation> errorList = new ArrayList<ErrorInformation>();
        final PwmPasswordPolicy.RuleHelper ruleHelper = policy.getRuleHelper();
        final PasswordCharCounter charCounter = new PasswordCharCounter(password);

        //check against old password
        if (oldPassword != null && oldPassword.length() > 0) {
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
            errorList.addAll(checkPasswordForADComplexity(pwmSession, password, charCounter));
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
            if (pwmSession != null) {
                final Map<String,String> userValues = pwmSession.getUserInfoBean().getAllUserAttributes();
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
                    final int passwordStrength = PasswordUtility.checkPasswordStrength(pwmApplication.getConfig(), pwmSession, password);
                    if (passwordStrength < requiredPasswordStrength) {
                        errorList.add(new ErrorInformation(PwmError.PASSWORD_TOO_WEAK));
                        LOGGER.trace(pwmSession, "password rejected, password strength of " + passwordStrength + " is lower than policy requirement of " + requiredPasswordStrength);
                    }
                }
            }
        }

        // check regex matches.
        for (final Pattern pattern : ruleHelper.getRegExMatch()) {
            if (!pattern.matcher(password).matches()) {
                errorList.add(new ErrorInformation(PwmError.PASSWORD_INVALID_CHAR));
                LOGGER.trace(pwmSession, "password rejected, does not match configured regex pattern: " + pattern.toString());
            }
        }

        // check no-regex matches.
        for (final Pattern pattern : ruleHelper.getRegExNoMatch()) {
            if (pattern.matcher(password).matches()) {
                errorList.add(new ErrorInformation(PwmError.PASSWORD_INVALID_CHAR));
                LOGGER.trace(pwmSession, "password rejected, matches configured no-regex pattern: " + pattern.toString());
            }
        }

        // check if the password is in the dictionary.
        if (ruleHelper.readBooleanValue(PwmPasswordRule.EnableWordlist)) {
            if (pwmApplication != null) {
                if (pwmApplication.getWordlistManager().status() == PwmService.STATUS.OPEN) {
                    final boolean found = pwmApplication.getWordlistManager().containsWord(pwmSession, password);

                    if (found) {
                        LOGGER.trace(pwmSession, "password rejected, in wordlist file");
                        errorList.add(new ErrorInformation(PwmError.PASSWORD_INWORDLIST));
                    }
                } else {
                    LOGGER.warn(pwmSession, "password wordlist checking enabled, but wordlist is not available, skipping wordlist check");
                }
            }
        }

        // check for shared (global) password history
        if (pwmApplication != null) {
            if (pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.PASSWORD_SHAREDHISTORY_ENABLE) && pwmApplication.getSharedHistoryManager().status() == PwmService.STATUS.OPEN) {
                final boolean found = pwmApplication.getSharedHistoryManager().containsWord(pwmSession, password);

                if (found) {
                    LOGGER.trace(pwmSession, "password rejected, in global shared history");
                    errorList.add(new ErrorInformation(PwmError.PASSWORD_INWORDLIST));
                }
            }
        }

        return errorList;
    }

    private static boolean checkContains(final String baseValue, final String checkPattern, final int maxMatchChars) {
        if (baseValue == null || baseValue.length() < 1) {
            return false;
        }

        if (checkPattern == null || checkPattern.length() < 1) {
            return false;
        }

        if (checkPattern.length() <= maxMatchChars) {
            return false;
        }

        if (baseValue.length() < maxMatchChars) {
            return false;
        }

        final String lowerBaseValue = baseValue.toLowerCase();
        final String lowerCheckPattern = checkPattern.toLowerCase();

        for (int i = 0; i < lowerCheckPattern.length() - (maxMatchChars); i++) {
            final String loopPattern = lowerCheckPattern.substring(i, i + maxMatchChars + 1);
            if (lowerBaseValue.contains(loopPattern)) {
                return true;
            }
        }

        return false;
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
     * @param pwmSession  current pwmSession (used for logging)
     * @param password    password to test
     * @param charCounter associated charCounter for the password.
     * @return list of errors if the password does not meet requirements, or an empty list if the password complies
     *         with AD requirements
     */
    private static List<ErrorInformation> checkPasswordForADComplexity(
            final PwmSession pwmSession,
            final String password,
            final PasswordCharCounter charCounter
    ) {
        final List<ErrorInformation> errorList = new ArrayList<ErrorInformation>();

        if (pwmSession != null && pwmSession.getUserInfoBean() != null && pwmSession.getUserInfoBean().getAllUserAttributes() != null) {
            final Map<String,String> userAttrs = pwmSession.getUserInfoBean().getAllUserAttributes();
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
            LOGGER.trace(pwmSession, "Password violation due to ADComplexity check: Password not complex enough");
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

    public static void validateAttributeUniqueness(
            final ChaiProvider chaiProvider,
            final Configuration config,
            final Map<FormConfiguration,String> formValues,
            final List<String> uniqueAttributes
    )
            throws PwmDataValidationException, ChaiUnavailableException, ChaiOperationException, PwmUnrecoverableException
    {
        final Map<String,String> objectClasses = new HashMap<String,String>();
        for (final String loopStr : config.readSettingAsStringArray(PwmSetting.DEFAULT_OBJECT_CLASSES)) {
            objectClasses.put("objectClass",loopStr);
        }

        for (final FormConfiguration formConfiguration : formValues.keySet()) {
            if (uniqueAttributes.contains(formConfiguration.getAttributeName())) {
                final String value = formValues.get(formConfiguration);

                final Map<String, String> filterClauses = new HashMap<String, String>();
                filterClauses.put(formConfiguration.getAttributeName(), value);
                filterClauses.putAll(objectClasses);
                final SearchHelper searchHelper = new SearchHelper();
                searchHelper.setFilterAnd(filterClauses);

                final List<String> searchBases = config.readSettingAsStringArray(PwmSetting.LDAP_CONTEXTLESS_ROOT);
                for (final String loopBase : searchBases) {
                    final Set<String> resultDNs = new HashSet<String>(chaiProvider.search(loopBase, searchHelper).keySet());
                    if (resultDNs.size() > 0) {
                        final ErrorInformation error = new ErrorInformation(PwmError.ERROR_FIELD_DUPLICATE, null, formConfiguration.getLabel());
                        throw new PwmDataValidationException(error);
                    }
                }

            }
        }
    }

    public static Map<String,String> readRequestParametersAsMap(final HttpServletRequest req)
            throws PwmUnrecoverableException
    {
        if (req == null) {
            return Collections.emptyMap();
        }

        final Map<String,String> tempMap = new LinkedHashMap<String,String>();
        for (Enumeration keyEnum = req.getParameterNames(); keyEnum.hasMoreElements();) {
            final String keyName = keyEnum.nextElement().toString();
            final String value = readStringFromRequest(req,keyName);
            tempMap.put(keyName,value);
        }
        return tempMap;
    }

}

