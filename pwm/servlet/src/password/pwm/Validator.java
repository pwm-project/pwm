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

import com.novell.ldapchai.ChaiFactory;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiPasswordPolicyException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiProvider;
import password.pwm.config.Message;
import password.pwm.config.ParameterConfig;
import password.pwm.config.PwmPasswordRule;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmException;
import password.pwm.error.ValidationException;
import password.pwm.util.PwmLogger;
import password.pwm.util.stats.Statistic;
import password.pwm.wordlist.WordlistStatus;

import javax.servlet.http.HttpServletRequest;
import java.io.UnsupportedEncodingException;
import java.util.*;

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

    public static int checkPasswordStrength(final PwmSession pwmSession, final String password)
    {
        final ContextManager theManager = pwmSession.getContextManager();

        if (theManager.getWordlistManager().containsWord(pwmSession, password)) {
            return 0;
        }

        if (theManager.getSeedlistManager().containsWord(pwmSession, password)) {
            return 0;
        }

        if (theManager.getSharedHistoryManager().containsWord(pwmSession, password)) {
            return 0;
        }

        return PasswordUtility.judgePassword(password);
    }

    public static boolean readBooleanFromRequest(
            final HttpServletRequest req,
            final String value
    )
    {
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
            final boolean testOldPassword
    )
            throws PwmException, ChaiUnavailableException
    {
        final PwmPasswordPolicy policy = pwmSession.getUserInfoBean().getPasswordPolicy();
        return testPasswordAgainstPolicy(password, pwmSession, testOldPassword, policy);
    }

    /**
     * Validates a password against the configured rules of PWM.  No directory operations
     * are performed here.
     *
     * @param password desired new password
     * @param testOldPassword if the old password should be tested, the old password will be retreived from the pwmSession
     * @param pwmSession current pwmSesssion of user being tested.
     * @param policy to be used during the test
     * @return true if the password is okay, never returns false.
     * @throws password.pwm.error.ValidationException
     *          contains information about why the password was rejected.
     * @throws PwmException if an unexpected error occurs
     *          contains information about why the password was rejected.
     * @throws ChaiUnavailableException if LDAP server is unreachable
     */
    public static boolean testPasswordAgainstPolicy(
            final String password,
            final PwmSession pwmSession,
            final boolean testOldPassword,
            final PwmPasswordPolicy policy
    )
            throws PwmException, ChaiUnavailableException
    {
        final List<ErrorInformation> errorResults = pwmPasswordPolicyValidator(password, pwmSession, testOldPassword, policy);

        if (!errorResults.isEmpty()) {
            throw ValidationException.createValidationException(errorResults.iterator().next());
        }

        try {
            LOGGER.trace(pwmSession, "calling chai directory password validation checker");
            final ChaiProvider provider = pwmSession.getSessionManager().getChaiProvider();
            final ChaiUser actor = ChaiFactory.createChaiUser(pwmSession.getUserInfoBean().getUserDN(), provider);
            actor.testPasswordPolicy(password);
        } catch (UnsupportedOperationException e) {
            LOGGER.trace(pwmSession, "Unsupported operation was thrown while validating password: " + e.toString());
        } catch (PwmException e) {
            LOGGER.warn(pwmSession, "PwmException was thrown while validating password: " + e.toString());
            throw e;
        } catch (ChaiUnavailableException e) {
            pwmSession.getContextManager().getStatisticsManager().incrementValue(Statistic.LDAP_UNAVAILABLE_COUNT);
            pwmSession.getContextManager().setLastLdapFailure();
            LOGGER.warn(pwmSession, "ChaiUnavailableException was thrown while validating password: " + e.toString());
            throw e;
        } catch (ChaiPasswordPolicyException e) {
            final ErrorInformation info = new ErrorInformation(Message.forResourceKey(e.getPasswordError().getErrorKey()));
            LOGGER.trace(pwmSession, "ChaiPasswordPolicyException was thrown while validating password: " + e.toString());
            errorResults.add(info);
        }

        if (!errorResults.isEmpty()) {
            throw ValidationException.createValidationException(errorResults.iterator().next());
        }

        return true;
    }

    public static void updateParamValues(
            final PwmSession pwmSession,
            final HttpServletRequest req,
            final Map<String, ParameterConfig> parameterConfigs
    )
            throws ValidationException
    {
        if (req == null || parameterConfigs == null) {
            return;
        }
        parameterConfigs.entrySet();
        for (final Map.Entry<String,ParameterConfig> entry : parameterConfigs.entrySet()) {
            final ParameterConfig paramConfig = entry.getValue();
            final String value = readStringFromRequest(req, paramConfig.getAttributeName(), 512);

            if (paramConfig.isConfirmationRequired()) {
                final String confirmValue = readStringFromRequest(req, paramConfig.getAttributeName() + PARAM_CONFIRM_SUFFIX, 512);

                if (!confirmValue.equals(value)) {
                    final ErrorInformation error = new ErrorInformation(Message.ERROR_FIELD_BAD_CONFIRM, null, paramConfig.getLabel());
                    LOGGER.trace(pwmSession, "bad field confirmation for " + paramConfig.getLabel());
                    throw ValidationException.createValidationException(error);
                }
            }

            paramConfig.setValue(value);
        }
    }

    public static String readStringFromRequest(
            final HttpServletRequest req,
            final String value,
            final int maxLength
    )
    {
        final ContextManager theManager = ContextManager.getContextManager(req);

        if (req == null) {
            return "";
        }

        if (req.getParameter(value) == null) {
            return "";
        }

        String theString = req.getParameter(value);

        if (req.getCharacterEncoding() == null) {
            try {
                final byte[] stringBytesISO = theString.getBytes("ISO-8859-1");
                theString = new String(stringBytesISO, "UTF-8");
            } catch(UnsupportedEncodingException e) {
                LOGGER.error("error attempting to decode request: " + e.getMessage());
            }
        }

        theString = theString.trim();

        // strip off any length beyond the specified maxLength.
        if (theString.length() > maxLength) {
            theString = theString.substring(0, maxLength);
        }

        // strip off any disallowed chars.
        final String disallowedInputs = theManager.getParameter(Constants.CONTEXT_PARAM.DISALLOWED_INPUTS);
        if (disallowedInputs != null) {
            for (final String testString : disallowedInputs.split(";;;")) {
                if (theString.matches(testString)) {
                    final String newString = theString.replaceAll(testString, "");
                    LOGGER.warn("removing potentially malicious string values from input field: " + testString + " newValue: " + newString);
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
     * @param parameterConfigs - a Map containing String keys of parameter names and ParamConfigs as values
     * @param pwmSession          bean helper
     * @throws ValidationException - If there is a problem with any of the fields
     * @throws com.novell.ldapchai.exception.ChaiUnavailableException if ldap server becomes unavailable
     * @throws password.pwm.error.PwmException if an unexpected error occurs
     */
    public static void validateParmValuesMeetRequirements(
            final Map<String, ParameterConfig> parameterConfigs,
            final PwmSession pwmSession
    )
            throws PwmException, ChaiUnavailableException
    {
        for (final Map.Entry<String,ParameterConfig> entry : parameterConfigs.entrySet()) {
            entry.getValue().valueIsValid(pwmSession);
        }
    }


    protected static class PasswordCharCounter {
        private final CharSequence password;
        private final int passwordLength;

        public PasswordCharCounter(final CharSequence password)
        {
            this.password = password;
            this.passwordLength = password.length();
        }

        public int getNumericChars()
        {
            int numberOfNumericChars = 0;
            for (int i = 0; i < passwordLength; i++)
                if (Character.isDigit(password.charAt(i)))
                    numberOfNumericChars++;

            return numberOfNumericChars;
        }

        public int getUpperChars()
        {
            int numberOfUpperChars = 0;
            for (int i = 0; i < passwordLength; i++)
                if (Character.isUpperCase(password.charAt(i)))
                    numberOfUpperChars++;

            return numberOfUpperChars;
        }

        public int getAlphaChars()
        {
            int numberOfAlphaChars = 0;
            for (int i = 0; i < passwordLength; i++)
                if (Character.isLetter(password.charAt(i)))
                    numberOfAlphaChars++;

            return numberOfAlphaChars;
        }

        public int getLowerChars()
        {
            int numberOfLowerChars = 0;
            for (int i = 0; i < passwordLength; i++)
                if (Character.isLowerCase(password.charAt(i)))
                    numberOfLowerChars++;

            return numberOfLowerChars;
        }

        public int getSpecialChars()
        {
            int numberOfSpecial = 0;
            for (int i = 0; i < passwordLength; i++)
                if (!Character.isLetterOrDigit(password.charAt(i)))
                    numberOfSpecial++;

            return numberOfSpecial;
        }

        public int getRepeatedChars()
        {
            int numberOfRepeats = 0;
            final CharSequence passwordL = password.toString().toLowerCase();

            for (int i = 0; i < passwordLength - 1; i++) {
                int loopRepeats = 0;
                final char loopChar = passwordL.charAt(i);
                for (int j = i; j < passwordLength; j++)
                    if (loopChar == passwordL.charAt(j))
                        loopRepeats++;

                if (loopRepeats > numberOfRepeats)
                    numberOfRepeats = loopRepeats;
            }
            return numberOfRepeats;
        }

        public int getSequentialRepeatedChars()
        {
            int numberOfRepeats = 0;
            final CharSequence passwordL = password.toString().toLowerCase();

            for (int i = 0; i < passwordLength - 1; i++) {
                int loopRepeats = 0;
                final char loopChar = passwordL.charAt(i);
                for (int j = i; j < passwordLength; j++)
                    if (loopChar == passwordL.charAt(j))
                        loopRepeats++;
                    else
                        break;

                if (loopRepeats > numberOfRepeats)
                    numberOfRepeats = loopRepeats;
            }
            return numberOfRepeats;
        }

        public int getUniqueChars()
        {
            final StringBuilder sb = new StringBuilder();
            final String passwordL = password.toString().toLowerCase();
            for (int i = 0; i < passwordLength; i++) {
                final char loopChar = passwordL.charAt(i);
                if (sb.indexOf(String.valueOf(loopChar)) == -1)
                    sb.append(loopChar);
            }
            return sb.length();
        }

        public boolean isFirstNumeric()
        {
            return Character.isDigit(password.charAt(0));
        }

        public boolean isLastNumeric()
        {
            return Character.isDigit(password.charAt(password.length() - 1));
        }

        public boolean isFirstSpecial()
        {
            return !Character.isLetterOrDigit(password.charAt(0));
        }

        public boolean isLastSpecial()
        {
            return !Character.isLetterOrDigit(password.charAt(password.length() - 1));
        }
    }




    /**
     * Validates a password against the configured rules of PWM.  No directory operations
     * are performed here.
     *
     * @param password desired new password
     * @param testOldPassword if the old password should be tested, the old password will be retreived from the pwmSession
     * @param pwmSession current pwmSesssion of user being tested.
     * @param policy to be used during the test
     * @return true if the password is okay, never returns false.
     **/
    public static List<ErrorInformation> pwmPasswordPolicyValidator(
            final String password,
            final PwmSession pwmSession,
            final boolean testOldPassword,
            final PwmPasswordPolicy policy
    )

    {
        // null check
        if (password == null) {
            return Collections.singletonList(new ErrorInformation(Message.ERROR_UNKNOWN,"empty (null) new password"));
        }

        final List<ErrorInformation> errorList = new ArrayList<ErrorInformation>();
        final PwmPasswordPolicy.RuleHelper ruleHelper = policy.getRuleHelper();
        final PasswordCharCounter charCounter = new PasswordCharCounter(password);

        //check against old password
        if (testOldPassword) {
            final String oldPassword = pwmSession.getUserInfoBean().getUserCurrentPassword();
            if (oldPassword != null && oldPassword.length() > 0) {
                if (oldPassword.equalsIgnoreCase(password)) {
                    errorList.add(new ErrorInformation(Message.PASSWORD_SAMEASOLD));
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
                        errorList.add(new ErrorInformation(Message.PASSWORD_TOO_MANY_OLD_CHARS));
                    }
                }
            }
        }

        final int passwordLength = password.length();

        //Check minimum length
        if (passwordLength < ruleHelper.readIntValue(PwmPasswordRule.MinimumLength)) {
            errorList.add(new ErrorInformation(Message.PASSWORD_TOO_SHORT));
        }

        //Check maximum length
        {
            final int passwordMaximumLength = ruleHelper.readIntValue(PwmPasswordRule.MaximumLength);
            if (passwordMaximumLength > 0 && passwordLength > passwordMaximumLength) {
                errorList.add(new ErrorInformation(Message.PASSWORD_TOO_LONG));
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
                    errorList.add(new ErrorInformation(Message.PASSWORD_NOT_ENOUGH_NUM));
                }

                final int maxNumeric = ruleHelper.readIntValue(PwmPasswordRule.MaximumNumeric);
                if (maxNumeric > 0 && numberOfNumericChars > maxNumeric) {
                    errorList.add(new ErrorInformation(Message.PASSWORD_TOO_MANY_NUMERIC));
                }

                if (!ruleHelper.readBooleanValue(PwmPasswordRule.AllowFirstCharNumeric) && charCounter.isFirstNumeric()) {
                    errorList.add(new ErrorInformation(Message.PASSWORD_FIRST_IS_NUMERIC));
                }

                if (!ruleHelper.readBooleanValue(PwmPasswordRule.AllowLastCharNumeric) && charCounter.isLastNumeric()) {
                    errorList.add(new ErrorInformation(Message.PASSWORD_LAST_IS_NUMERIC));
                }
            } else {
                if (numberOfNumericChars > 0) {
                    errorList.add(new ErrorInformation(Message.PASSWORD_TOO_MANY_NUMERIC));
                }
            }
        }

        //check number of upper characters
        {
            final int numberOfUpperChars = charCounter.getUpperChars();
            if (numberOfUpperChars < ruleHelper.readIntValue(PwmPasswordRule.MinimumUpperCase)) {
                errorList.add(new ErrorInformation(Message.PASSWORD_NOT_ENOUGH_UPPER));
            }

            final int maxUpper = ruleHelper.readIntValue(PwmPasswordRule.MaximumUpperCase);
            if (maxUpper > 0 && numberOfUpperChars > maxUpper) {
                errorList.add(new ErrorInformation(Message.PASSWORD_TOO_MANY_UPPER));
            }
        }

        //check number of alpha characters
        {
            final int numberOfAlphaChars = charCounter.getAlphaChars();
            if (numberOfAlphaChars < ruleHelper.readIntValue(PwmPasswordRule.MinimumAlpha)) {
                errorList.add(new ErrorInformation(Message.PASSWORD_NOT_ENOUGH_ALPHA));
            }

            final int maxAlpha = ruleHelper.readIntValue(PwmPasswordRule.MaximumAlpha);
            if (maxAlpha > 0 && numberOfAlphaChars > maxAlpha) {
                errorList.add(new ErrorInformation(Message.PASSWORD_TOO_MANY_ALPHA));
            }
        }

        //check number of lower characters
        {
            final int numberOfLowerChars = charCounter.getLowerChars();
            if (numberOfLowerChars < ruleHelper.readIntValue(PwmPasswordRule.MinimumLowerCase)) {
                errorList.add(new ErrorInformation(Message.PASSWORD_NOT_ENOUGH_LOWER));
            }

            final int maxLower = ruleHelper.readIntValue(PwmPasswordRule.MaximumLowerCase);
            if (maxLower > 0 && numberOfLowerChars > maxLower) {
                errorList.add(new ErrorInformation(Message.PASSWORD_TOO_MANY_UPPER));
            }
        }

        //check number of special characters
        {
            final int numberOfSpecialChars = charCounter.getSpecialChars();
            if (ruleHelper.readBooleanValue(PwmPasswordRule.AllowSpecial)) {
                if (numberOfSpecialChars < ruleHelper.readIntValue(PwmPasswordRule.MinimumSpecial)) {
                    errorList.add(new ErrorInformation(Message.PASSWORD_NOT_ENOUGH_SPECIAL));
                }

                final int maxSpecial = ruleHelper.readIntValue(PwmPasswordRule.MaximumSpecial);
                if (maxSpecial > 0 && numberOfSpecialChars > maxSpecial) {
                    errorList.add(new ErrorInformation(Message.PASSWORD_TOO_MANY_SPECIAL));
                }

                if (!ruleHelper.readBooleanValue(PwmPasswordRule.AllowFirstCharSpecial) && charCounter.isFirstSpecial()) {
                    errorList.add(new ErrorInformation(Message.PASSWORD_FIRST_IS_SPECIAL));
                }

                if (!ruleHelper.readBooleanValue(PwmPasswordRule.AllowLastCharSpecial) && charCounter.isLastSpecial()) {
                    errorList.add(new ErrorInformation(Message.PASSWORD_LAST_IS_SPECIAL));
                }
            } else {
                if (numberOfSpecialChars > 0) {
                    errorList.add(new ErrorInformation(Message.PASSWORD_TOO_MANY_SPECIAL));
                }
            }
        }

        //Check maximum character repeats (sequential)
        {
            final int maxSequentialRepeat = ruleHelper.readIntValue(PwmPasswordRule.MaximumSequentialRepeat);
            if (maxSequentialRepeat > 0 && charCounter.getSequentialRepeatedChars() > maxSequentialRepeat) {
                errorList.add(new ErrorInformation(Message.PASSWORD_TOO_MANY_REPEAT));
            }

            //Check maximum character repeats (overall)
            final int maxRepeat = ruleHelper.readIntValue(PwmPasswordRule.MaximumRepeat);
            if (maxRepeat > 0 && charCounter.getRepeatedChars() > maxRepeat) {
                errorList.add(new ErrorInformation(Message.PASSWORD_TOO_MANY_REPEAT));
            }
        }

        //Check minimum unique character
        {
            final int minUnique = ruleHelper.readIntValue(PwmPasswordRule.MinimumUnique);
            if (minUnique > 0 && charCounter.getUniqueChars() < minUnique) {
                errorList.add(new ErrorInformation(Message.PASSWORD_NOT_ENOUGH_UNIQUE));
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
                        errorList.add(new ErrorInformation(Message.PASSWORD_USING_DISALLOWED_VALUE));
                    }
                }
            }
        }

        if (!policy.getRuleHelper().getDisallowedAttributes().isEmpty()) {
            final List paramConfigs = policy.getRuleHelper().getDisallowedAttributes();
            final Properties userValues = pwmSession.getUserInfoBean().getAllUserAttributes();
            final String lcasePwd = password.toLowerCase();
            for (final Object paramConfig : paramConfigs) {
                final String attr = (String) paramConfig;
                final String userValue = userValues.getProperty(attr, "").toLowerCase();

                // if the password is greater then 1 char and the value is contained within it then disallow
                if (userValue.length() > 1 && lcasePwd.indexOf(userValue) != -1) {
                    LOGGER.trace("password rejected, same as user attr " + attr);
                    errorList.add(new ErrorInformation(Message.PASSWORD_SAMEASATTR));
                }

                // if the password is 1 char and the value is the same then disallow
                if (lcasePwd.equalsIgnoreCase(userValue)) {
                    LOGGER.trace("password rejected, same as user attr " + attr);
                    errorList.add(new ErrorInformation(Message.PASSWORD_SAMEASATTR));
                }
            }
        }

        final ContextManager theManager = pwmSession.getContextManager();

        // check if the password is in the dictionary.
        if (theManager.getWordlistManager().getStatus() != WordlistStatus.CLOSED) {
            final boolean found = theManager.getWordlistManager().containsWord(pwmSession, password);

            if (found) {
                LOGGER.trace(pwmSession, "password rejected, in wordlist file");
                errorList.add(new ErrorInformation(Message.PASSWORD_INWORDLIST));
            }
        }

        if (theManager.getSharedHistoryManager().getStatus() == WordlistStatus.OPEN) {
            final boolean found = theManager.getSharedHistoryManager().containsWord(pwmSession,password);

            if (found) {
                LOGGER.trace(pwmSession, "password rejected, in global history");
                errorList.add(new ErrorInformation(Message.PASSWORD_INWORDLIST));
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

        for (int i = 0; i < lowerCheckPattern.length() - (maxMatchChars ) ; i++) {
            final String loopPattern = lowerCheckPattern.substring(i,i+ maxMatchChars + 1);
            if (lowerBaseValue.contains(loopPattern)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check a supplied password for it's validity according to AD compexity rules.
     *   - Not contain the user's account name or parts of the user's full name that exceed two consecutive characters
     *   - Be at least six characters in length
     *   - Contain characters from three of the following four categories:
     *       - English uppercase characters (A through Z)
     *       - English lowercase characters (a through z)
     *       - Base 10 digits (0 through 9)
     *       - Non-alphabetic characters (for example, !, $, #, %)
     *
     * @param pwmSession current pwmSession (used for logging)
     * @param password password to test
     * @param charCounter associated charCounter for the password.
     * @return list of errors if the password does not meet requirements, or an empty list if the password complies
     *  with AD requirements
     */
    private static List<ErrorInformation> checkPasswordForADComplexity(
            final PwmSession pwmSession,
            final String password,
            final PasswordCharCounter charCounter
    )
    {
        List<ErrorInformation> errorList = new ArrayList<ErrorInformation>();
        if (password.length() < 6) {
            LOGGER.trace(pwmSession, "Password violation due to ADComplexity check: Password too short (6 char minimum)");
            errorList.add(new ErrorInformation(Message.PASSWORD_TOO_SHORT));
        }

        final Properties userAttrs = pwmSession.getUserInfoBean().getAllUserAttributes();
        if (userAttrs != null) {
            if (checkContains(password, userAttrs.getProperty("cn"),2)) { errorList.add(new ErrorInformation(Message.PASSWORD_INWORDLIST)); }
            if (checkContains(password, userAttrs.getProperty("displayName"),2)) { errorList.add(new ErrorInformation(Message.PASSWORD_INWORDLIST)); }
            if (checkContains(password, userAttrs.getProperty("fullName"),2)) { errorList.add(new ErrorInformation(Message.PASSWORD_INWORDLIST)); }
        }

        int complexityPoints = 0;
        if (charCounter.getUpperChars() > 0) { complexityPoints++; }
        if (charCounter.getLowerChars() > 0) { complexityPoints++; }
        if (charCounter.getNumericChars() > 0) { complexityPoints++; }
        if (charCounter.getSpecialChars() > 0) { complexityPoints++; }

        if (complexityPoints < 3) {
            LOGGER.trace(pwmSession, "Password violation due to ADComplexity check: Password not complex enough");
            if (charCounter.getUpperChars() < 1) { errorList.add(new ErrorInformation(Message.PASSWORD_NOT_ENOUGH_UPPER)); }
            if (charCounter.getLowerChars() < 1) { errorList.add(new ErrorInformation(Message.PASSWORD_NOT_ENOUGH_LOWER)); }
            if (charCounter.getNumericChars() < 1) { errorList.add(new ErrorInformation(Message.PASSWORD_NOT_ENOUGH_NUM)); }
            if (charCounter.getSpecialChars() < 1) { errorList.add(new ErrorInformation(Message.PASSWORD_NOT_ENOUGH_SPECIAL)); }
        }

        return errorList;
    }
}

