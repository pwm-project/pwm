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

package password.pwm.config;

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.util.SearchHelper;
import password.pwm.PwmApplication;
import password.pwm.Validator;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.error.*;
import password.pwm.http.PwmRequest;
import password.pwm.ldap.UserSearchEngine;
import password.pwm.util.JsonUtil;
import password.pwm.util.StringUtil;
import password.pwm.util.cache.CacheKey;
import password.pwm.util.cache.CachePolicy;
import password.pwm.util.cache.CacheService;
import password.pwm.util.logging.PwmLogger;

import java.util.*;

public class FormUtility {

    private static final PwmLogger LOGGER = PwmLogger.forClass(FormUtility.class);

    final private static String NEGATIVE_CACHE_HIT = "NEGATIVE_CACHE_HIT";


    public static Map<FormConfiguration, String> readFormValuesFromMap(
            final Map<String,String> inputMap,
            final Collection<FormConfiguration> formItems,
            final Locale locale
    )
            throws PwmDataValidationException, PwmUnrecoverableException
    {
        if (formItems == null || formItems.isEmpty()) {
            return Collections.emptyMap();
        }

        final Map<FormConfiguration, String> returnMap = new LinkedHashMap<>();

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
                final String confirmValue = inputMap.get(keyName + Validator.PARAM_CONFIRM_SUFFIX);
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

    public static Map<String,String> asStringMap(Map<FormConfiguration, String> input) {
        final Map<String,String> returnObj = new HashMap<>();
        for (final FormConfiguration formConfiguration : input.keySet()) {
            returnObj.put(formConfiguration.getName(), input.get(formConfiguration));
        }
        return returnObj;
    }

    public static Map<FormConfiguration, String> readFormValuesFromRequest(
            final PwmRequest pwmRequest,
            final Collection<FormConfiguration> formItems,
            final Locale locale
    )
            throws PwmDataValidationException, PwmUnrecoverableException
    {
        final Map<String,String> tempMap = pwmRequest.readParametersAsMap();
        return readFormValuesFromMap(tempMap, formItems, locale);
    }

    public static void validateFormValueUniqueness(
            final PwmApplication pwmApplication,
            final Map<FormConfiguration, String> formValues,
            final Locale locale,
            final Collection<UserIdentity> excludeDN,
            final boolean allowResultCaching
    )
            throws PwmDataValidationException, PwmUnrecoverableException
    {
        final Map<String, String> filterClauses = new HashMap<>();
        final Map<String,String> labelMap = new HashMap<>();
        for (final FormConfiguration formItem : formValues.keySet()) {
            if (formItem.isUnique() && !formItem.isReadonly()) {
                if (formItem.getType() != FormConfiguration.Type.hidden) {
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
                filter.append("(").append(name).append("=").append(StringUtil.escapeLdapFilter(value)).append(")");
            }
            filter.append(")");

            filter.append(")");
        }

        final CacheService cacheService = pwmApplication.getCacheService();
        final CacheKey cacheKey = CacheKey.makeCacheKey(
                Validator.class, null, "attr_unique_check_" + filter.toString()
        );
        if (allowResultCaching && cacheService != null) {
            final String cacheValue = cacheService.get(cacheKey);
            if (cacheValue != null) {
                if (NEGATIVE_CACHE_HIT.equals(cacheValue)) {
                    return;
                } else {
                    final ErrorInformation errorInformation = JsonUtil.deserialize(cacheValue,ErrorInformation.class);
                    throw new PwmDataValidationException(errorInformation);
                }
            }
        }

        final SearchHelper searchHelper = new SearchHelper();
        searchHelper.setFilterAnd(filterClauses);

        final UserSearchEngine.SearchConfiguration searchConfiguration = new UserSearchEngine.SearchConfiguration();
        searchConfiguration.setFilter(filter.toString());

        int resultSearchSizeLimit = 1 + (excludeDN == null ? 0 : excludeDN.size());
        final CachePolicy cachePolicy = CachePolicy.makePolicyWithExpirationMS(30 * 1000);

        try {
            final UserSearchEngine userSearchEngine = new UserSearchEngine(pwmApplication, SessionLabel.SYSTEM_LABEL);
            final Map<UserIdentity,Map<String,String>> results = new LinkedHashMap<>(userSearchEngine.performMultiUserSearch(searchConfiguration,resultSearchSizeLimit,Collections.<String>emptyList()));

            if (excludeDN != null && !excludeDN.isEmpty()) {
                for (final UserIdentity loopIgnoreIdentity : excludeDN) {
                    for (final Iterator<UserIdentity> iterator = results.keySet().iterator(); iterator.hasNext(); ) {
                        final UserIdentity loopUser = iterator.next();
                        if (loopIgnoreIdentity.equals(loopUser)) {
                            iterator.remove();
                        }
                    }
                }
            }

            if (!results.isEmpty()) {
                final UserIdentity userIdentity = results.keySet().iterator().next();
                if (labelMap.size() == 1) { // since only one value searched, it must be that one value
                    final String attributeName = labelMap.values().iterator().next();
                    LOGGER.trace("found duplicate value for attribute '" + attributeName + "' on entry " + userIdentity);
                    final ErrorInformation error = new ErrorInformation(PwmError.ERROR_FIELD_DUPLICATE, null, new String[]{attributeName});
                    throw new PwmDataValidationException(error);
                }

                // do a compare on a user values to find one that matches.

                for (final String name : filterClauses.keySet()) {
                    final String value = filterClauses.get(name);
                    final boolean compareResult;
                    try {
                        final ChaiUser theUser = pwmApplication.getProxiedChaiUser(userIdentity);
                        compareResult = theUser.compareStringAttribute(name, value);
                    } catch (ChaiOperationException | ChaiUnavailableException e) {
                        final PwmError error = PwmError.forChaiError(e.getErrorCode());
                        throw new PwmUnrecoverableException(error.toInfo());
                    }

                    if (compareResult) {
                        final String label = labelMap.get(name);
                        LOGGER.trace("found duplicate value for attribute '" + label + "' on entry " + userIdentity);
                        final ErrorInformation error = new ErrorInformation(PwmError.ERROR_FIELD_DUPLICATE, null, new String[]{label});
                        throw new PwmDataValidationException(error);
                    }
                }

                // user didn't match on the compare.. shouldn't read here but just in case
                final ErrorInformation error = new ErrorInformation(PwmError.ERROR_FIELD_DUPLICATE, null);
                throw new PwmDataValidationException(error);
            }
        } catch (PwmOperationalException e) {
            if (cacheService != null) {
                final String jsonPayload = JsonUtil.serialize(e.getErrorInformation());
                cacheService.put(cacheKey, cachePolicy, jsonPayload);
            }
            throw new PwmDataValidationException(e.getErrorInformation());
        }
        if (allowResultCaching && cacheService != null) {
            cacheService.put(cacheKey, cachePolicy, NEGATIVE_CACHE_HIT);
        }
    }

    /**
     * Validates each of the parameters in the supplied map against the vales in the embedded config
     * and checks to make sure the ParamConfig value meets the requirements of the ParamConfig itself.
     *
     *
     * @param formValues - a Map containing String keys of parameter names and ParamConfigs as values
     * @throws password.pwm.error.PwmDataValidationException - If there is a problem with any of the fields
     * @throws password.pwm.error.PwmUnrecoverableException
     *                             if an unexpected error occurs
     */
    public static void validateFormValues(
            final Configuration configuration,
            final Map<FormConfiguration, String> formValues,
            final Locale locale
    )
            throws PwmUnrecoverableException, PwmDataValidationException
    {
        for (final FormConfiguration formItem : formValues.keySet()) {
            final String value = formValues.get(formItem);
            formItem.checkValue(configuration,value,locale);
        }
    }

    public static String ldapSearchFilterForForm(final PwmApplication pwmApplication, final Collection<FormConfiguration> formElements)
            throws PwmUnrecoverableException
    {
        if (formElements == null || formElements.isEmpty()) {
            final String errorMsg = "can not auto-generate ldap search filter for form with no required form items";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR,null,new String[]{errorMsg});
            throw new PwmUnrecoverableException(errorInformation);
        }



        final StringBuilder sb = new StringBuilder();
        sb.append("(&");

        final List<String> objectClasses = pwmApplication.getConfig().readSettingAsStringArray(PwmSetting.DEFAULT_OBJECT_CLASSES);
        if (objectClasses != null && !objectClasses.isEmpty()) {
            if (objectClasses.size() == 1) {
                sb.append("(objectclass=");
                sb.append(objectClasses.iterator().next());
                sb.append(")");
            } else {
                sb.append("(|");
                for (final String objectClassValue : objectClasses) {
                    sb.append("(objectclass=");
                    sb.append(objectClassValue);
                    sb.append("(");
                }
                sb.append(")");
            }
        }

        for (final FormConfiguration formConfiguration : formElements) {
            final String formElementName = formConfiguration.getName();
            sb.append("(");
            sb.append(formElementName);
            sb.append("=");
            sb.append("%").append(formElementName).append("%");
            sb.append(")");
        }

        sb.append(")");
        return sb.toString();
    }
}
