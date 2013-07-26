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

import com.google.gson.Gson;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiProvider;
import com.novell.ldapchai.util.SearchHelper;
import password.pwm.bean.SessionStateBean;
import password.pwm.config.Configuration;
import password.pwm.config.FormConfiguration;
import password.pwm.config.PwmSetting;
import password.pwm.error.*;
import password.pwm.util.PwmLogger;
import password.pwm.util.operations.UserSearchEngine;
import password.pwm.util.operations.UserStatusHelper;

import javax.servlet.http.HttpServletRequest;
import java.io.Serializable;
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

    public static int readIntegerFromRequest(
            final HttpServletRequest req,
            final String paramName,
            final int defaultValue
    ) {
        if (req == null) {
            return defaultValue;
        }

        final String theString = req.getParameter(paramName);

        try {
            return Integer.valueOf(theString);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

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

    public static Map<FormConfiguration, String> readFormValuesFromRequest(
            final HttpServletRequest req,
            final Collection<FormConfiguration> formItems,
            final Locale locale
    )
            throws PwmDataValidationException, PwmUnrecoverableException
    {
        final Map<String,String> tempMap = readRequestParametersAsMap(req);
        return readFormValuesFromMap(tempMap, formItems, locale);
    }


    public static Map<FormConfiguration, String> readFormValuesFromMap(
            final Map<String,String> inputMap,
            final Collection<FormConfiguration> formItems,
            final Locale locale
    )
            throws PwmDataValidationException, PwmUnrecoverableException {
        if (formItems == null || formItems.isEmpty()) {
            return Collections.emptyMap();
        }

        final Map<FormConfiguration, String> returnMap = new LinkedHashMap<FormConfiguration,String>();

        if (inputMap == null) {
            return returnMap;
        }

        for (final FormConfiguration formItem : formItems) {
            final String keyName = formItem.getName();
            final String value = inputMap.get(keyName);

            if (formItem.isRequired()) {
                if (value == null || value.length() < 0) {
                    final String errorMsg = "missing required value for field '" + formItem.getName() + "'";
                    final ErrorInformation error = new ErrorInformation(PwmError.ERROR_FIELD_REQUIRED, errorMsg, new String[]{formItem.getLabel(locale)});
                    throw new PwmDataValidationException(error);
                }
            }

            if (formItem.isConfirmationRequired()) {
                final String confirmValue = inputMap.get(keyName + PARAM_CONFIRM_SUFFIX);
                if (!confirmValue.equals(value)) {
                    final String errorMsg = "incorrect confirmation value for field '" + formItem.getName() + "'";
                    final ErrorInformation error = new ErrorInformation(PwmError.ERROR_FIELD_BAD_CONFIRM, errorMsg, new String[]{formItem.getLabel(locale)});
                    throw new PwmDataValidationException(error);
                }
            }
            if (value != null && !formItem.isReadonly()) {
                returnMap.put(formItem,value);
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
            if (submittedPwmFormID == null || submittedPwmFormID.length() < pwmFormID.length()) {
                throw new PwmUnrecoverableException(PwmError.ERROR_INVALID_FORMID);
            }

            if (!pwmFormID.equals(submittedPwmFormID.substring(0,pwmFormID.length()))) {
                throw new PwmUnrecoverableException(PwmError.ERROR_INVALID_FORMID);
            }
        }

        if (pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.SECURITY_ENABLE_REQUEST_SEQUENCE)) {
            try {
                final String submittedSequenceCounterStr = submittedPwmFormID.substring(pwmFormID.length(),submittedPwmFormID.length());
                final long submittedSequenceCounter = Long.parseLong(submittedSequenceCounterStr,36);
                if (submittedSequenceCounter != requestSequenceCounter) {
                    throw new PwmUnrecoverableException(PwmError.ERROR_INCORRECT_REQUEST_SEQUENCE);
                }
            } catch (NumberFormatException e) {
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

    public static String sanatizeInputValue(final Configuration config, final String input, int maxLength) {

        String theString = input == null ? "" : input.trim();

        if (maxLength < 1) {
            maxLength = 10 * 1024;
        }

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
     *
     * @param formValues - a Map containing String keys of parameter names and ParamConfigs as values
     * @throws password.pwm.error.PwmDataValidationException - If there is a problem with any of the fields
     * @throws com.novell.ldapchai.exception.ChaiUnavailableException
     *                             if ldap server becomes unavailable
     * @throws password.pwm.error.PwmUnrecoverableException
     *                             if an unexpected error occurs
     */
    public static void validateParmValuesMeetRequirements(
            final Map<FormConfiguration, String> formValues, final Locale locale
    )
            throws PwmUnrecoverableException, ChaiUnavailableException, PwmDataValidationException
    {
        for (final FormConfiguration formItem : formValues.keySet()) {
            final String value = formValues.get(formItem);
            formItem.checkValue(value,locale);
        }
    }


    public static void validateAttributeUniqueness(
            final PwmApplication pwmApplication,
            final ChaiProvider chaiProvider,
            final Map<FormConfiguration,String> formValues,
            final Locale locale,
            final SessionManager sessionManager,
            final Collection<String> excludeDN
    )
            throws PwmDataValidationException, ChaiUnavailableException, ChaiOperationException, PwmUnrecoverableException
    {
        final Map<String, String> filterClauses = new HashMap<String, String>();
        final Map<String,String> labelMap = new HashMap<String,String>();
        final List<String> uniqueAttributes = new ArrayList();
        for (final FormConfiguration formItem : formValues.keySet()) {
            if (formItem.isUnique() && !formItem.isReadonly()) {
                if (formItem.getType() != FormConfiguration.Type.hidden) {
                    uniqueAttributes.add(formItem.getName());
                    final String value = formValues.get(formItem);
                    if (value != null && value.length() > 0) {
                        filterClauses.put(formItem.getName(), value);
                        labelMap.put(formItem.getName(), formItem.getLabel(locale));
                    }
                }
            }
        }

        if (filterClauses.isEmpty()) { // nothing to search
            return;
        }

        final StringBuilder filter = new StringBuilder();
        {
            filter.append("(&"); // outer;

            // object classes;
            filter.append("(|");
            for (final String objectClass : pwmApplication.getConfig().readSettingAsStringArray(PwmSetting.DEFAULT_OBJECT_CLASSES)) {
                filter.append("(objectClass=").append(objectClass).append(")");
            }
            filter.append(")");

            // attributes
            filter.append("(|");
            for (final String name : filterClauses.keySet()) {
                final String value = filterClauses.get(name);
                filter.append("(").append(name).append("=").append(UserSearchEngine.escapeLdapString(value)).append(")");
            }
            filter.append(")");

            filter.append(")");
        }

        final Boolean NEGATIVE_CACHE_HIT = Boolean.TRUE;
        final String cacheKey = "attr_unique_check_" + filter.toString();
        if (sessionManager != null) {
            final Object cacheValue = sessionManager.getTypingCacheValue(cacheKey);
            if (cacheValue != null) {
                if (NEGATIVE_CACHE_HIT.equals(cacheValue)) {
                    return;
                } else {
                    throw new PwmDataValidationException((ErrorInformation)cacheValue);
                }
            }
        }

        final SearchHelper searchHelper = new SearchHelper();
        searchHelper.setFilterAnd(filterClauses);

        final UserSearchEngine.SearchConfiguration searchConfiguration = new UserSearchEngine.SearchConfiguration();
        searchConfiguration.setFilter(filter.toString());
        searchConfiguration.setChaiProvider(chaiProvider);

        int resultSearchSizeLimit = 1 + (excludeDN == null ? 0 : excludeDN.size());

        try {
            final UserSearchEngine userSearchEngine = new UserSearchEngine(pwmApplication);
            final Map<ChaiUser,Map<String,String>> results = new LinkedHashMap<ChaiUser, Map<String, String>>(userSearchEngine.performMultiUserSearch(null,searchConfiguration,resultSearchSizeLimit,Collections.<String>emptyList()));

            if (excludeDN != null && !excludeDN.isEmpty()) {
                for (final String loopDN : excludeDN) {
                    for (final Iterator<ChaiUser> iterator = results.keySet().iterator(); iterator.hasNext(); ) {
                        final ChaiUser loopUser = iterator.next();
                        if (loopDN.equals(loopUser.getEntryDN())) {
                            iterator.remove();
                        }
                    }
                }
            }

            if (!results.isEmpty()) {
                final ChaiUser theUser = results.keySet().iterator().next();
                if (labelMap.size() == 1) { // since only one value searched, it must be that one value
                    final String attributeName = labelMap.values().iterator().next();
                    LOGGER.trace("found duplicate value for attribute '" + attributeName + "' on entry " + theUser.getEntryDN());
                    final ErrorInformation error = new ErrorInformation(PwmError.ERROR_FIELD_DUPLICATE, null, new String[]{attributeName});
                    throw new PwmDataValidationException(error);
                }

                // do a compare on a user values to find one that matches.
                for (final String name : filterClauses.keySet()) {
                    final String value = filterClauses.get(name);
                    if (theUser.compareStringAttribute(name,value)) {
                        final String label = labelMap.get(name);
                        LOGGER.trace("found duplicate value for attribute '" + label + "' on entry " + theUser.getEntryDN());
                        final ErrorInformation error = new ErrorInformation(PwmError.ERROR_FIELD_DUPLICATE, null, new String[]{label});
                        throw new PwmDataValidationException(error);
                    }
                }

                // user didn't match on the compare.. shouldn't get here but just in case
                final ErrorInformation error = new ErrorInformation(PwmError.ERROR_FIELD_DUPLICATE, null);
                throw new PwmDataValidationException(error);
            }
        } catch (PwmOperationalException e) {
            if (sessionManager != null) {
                sessionManager.putLruTypingCacheValue(cacheKey,e.getErrorInformation());
            }
            throw new PwmDataValidationException(e.getErrorInformation());
        }
        if (sessionManager != null) {
            sessionManager.putLruTypingCacheValue(cacheKey,NEGATIVE_CACHE_HIT);
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

