/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2019 The PWM Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package password.pwm.util.form;

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiException;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.util.SearchHelper;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.TokenDestinationItem;
import password.pwm.bean.UserIdentity;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.profile.LdapProfile;
import password.pwm.config.value.data.FormConfiguration;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmDataValidationException;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.ldap.UserInfo;
import password.pwm.ldap.search.SearchConfiguration;
import password.pwm.ldap.search.UserSearchEngine;
import password.pwm.svc.cache.CacheKey;
import password.pwm.svc.cache.CachePolicy;
import password.pwm.svc.cache.CacheService;
import password.pwm.util.Validator;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class FormUtility
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( FormUtility.class );

    public enum Flag
    {
        ReturnEmptyValues
    }

    private static final String NEGATIVE_CACHE_HIT = "NEGATIVE_CACHE_HIT";

    public static Map<FormConfiguration, String> readFormValuesFromMap(
            final Map<String, String> inputMap,
            final Collection<FormConfiguration> formItems,
            final Locale locale
    )
            throws PwmDataValidationException, PwmUnrecoverableException
    {
        if ( formItems == null || formItems.isEmpty() )
        {
            return Collections.emptyMap();
        }

        final Map<FormConfiguration, String> returnMap = new LinkedHashMap<>();

        if ( inputMap == null )
        {
            return returnMap;
        }

        for ( final FormConfiguration formItem : formItems )
        {
            final String keyName = formItem.getName();
            final String value = inputMap.get( keyName );

            if ( formItem.isRequired() && !formItem.isReadonly() && formItem.getType() != FormConfiguration.Type.photo )
            {
                if ( StringUtil.isEmpty( value ) )
                {
                    final String errorMsg = "missing required value for field '" + formItem.getName() + "'";
                    final ErrorInformation error = new ErrorInformation( PwmError.ERROR_FIELD_REQUIRED, errorMsg, new String[]
                            {
                                    formItem.getLabel( locale ),
                            }
                    );
                    throw new PwmDataValidationException( error );
                }
            }

            if ( formItem.isConfirmationRequired() )
            {
                final String confirmValue = inputMap.get( keyName + Validator.PARAM_CONFIRM_SUFFIX );
                if ( confirmValue == null || !confirmValue.equals( value ) )
                {
                    final String errorMsg = "incorrect confirmation value for field '" + formItem.getName() + "'";
                    final ErrorInformation error = new ErrorInformation( PwmError.ERROR_FIELD_BAD_CONFIRM, errorMsg, new String[]
                            {
                                    formItem.getLabel( locale ),
                            }
                    );
                    throw new PwmDataValidationException( error );
                }
            }

            if ( formItem.getType() == FormConfiguration.Type.checkbox )
            {
                final String parsedValue = parseInputValueToFormValue( formItem, value );
                returnMap.put( formItem, parsedValue );
            }
            else if ( value != null && !formItem.isReadonly() )
            {
                final String parsedValue = parseInputValueToFormValue( formItem, value );
                returnMap.put( formItem, parsedValue );
            }

        }

        return returnMap;
    }

    private static String parseInputValueToFormValue( final FormConfiguration formConfiguration, final String input )
    {
        if ( formConfiguration.getType() == FormConfiguration.Type.checkbox )
        {
            final boolean bValue = checkboxValueIsChecked( input );
            return bValue ? "TRUE" : "FALSE";
        }

        return input;
    }

    public static boolean checkboxValueIsChecked( final String value )
    {
        boolean booleanValue = false;
        if ( value != null )
        {
            if ( Boolean.parseBoolean( value ) )
            {
                booleanValue = true;
            }
            else if ( "on".equalsIgnoreCase( value ) )
            {
                booleanValue = true;
            }
            else if ( "checked".equalsIgnoreCase( value ) )
            {
                booleanValue = true;
            }
        }
        return booleanValue;
    }

    public static Map<String, String> asStringMap( final Map<FormConfiguration, String> input )
    {
        final Map<String, String> returnObj = new LinkedHashMap<>();
        for ( final Map.Entry<FormConfiguration, String> entry : input.entrySet() )
        {
            final FormConfiguration formConfiguration = entry.getKey();
            returnObj.put( formConfiguration.getName(), entry.getValue() );
            if ( formConfiguration.isConfirmationRequired() )
            {
                final String confirmFieldName = formConfiguration.getName() + Validator.PARAM_CONFIRM_SUFFIX;
                returnObj.put( confirmFieldName, input.get( formConfiguration ) );
            }

        }
        return returnObj;
    }

    public static Map<FormConfiguration, String> asFormConfigurationMap(
            final List<FormConfiguration> formConfigurations,
            final Map<String, String> values
    )
    {
        final Map<FormConfiguration, String> returnMap = new LinkedHashMap<>();
        for ( final FormConfiguration formConfiguration : formConfigurations )
        {
            final String name = formConfiguration.getName();
            final String value = values.get( name );
            returnMap.put( formConfiguration, value );
        }
        return returnMap;
    }


    public static Map<FormConfiguration, String> readFormValuesFromRequest(
            final PwmRequest pwmRequest,
            final Collection<FormConfiguration> formItems,
            final Locale locale
    )
            throws PwmDataValidationException, PwmUnrecoverableException
    {
        final Map<String, String> tempMap = pwmRequest.readParametersAsMap();
        return readFormValuesFromMap( tempMap, formItems, locale );
    }

    public enum ValidationFlag
    {
        allowResultCaching,
        checkReadOnlyAndHidden,
    }

    @SuppressWarnings( "checkstyle:MethodLength" )
    public static void validateFormValueUniqueness(
            final PwmApplication pwmApplication,
            final Map<FormConfiguration, String> formValues,
            final Locale locale,
            final Collection<UserIdentity> excludeDN,
            final ValidationFlag... validationFlags
    )
            throws PwmDataValidationException, PwmUnrecoverableException
    {
        final boolean allowResultCaching = JavaHelper.enumArrayContainsValue( validationFlags, ValidationFlag.allowResultCaching );

        final Map<String, String> filterClauses = new HashMap<>();
        final Map<String, String> labelMap = new HashMap<>();
        for ( final Map.Entry<FormConfiguration, String> entry : formValues.entrySet() )
        {
            final FormConfiguration formItem = entry.getKey();
            if ( formItem.isUnique() )
            {
                final boolean checkReadOnlyAndHidden = JavaHelper.enumArrayContainsValue( validationFlags, ValidationFlag.checkReadOnlyAndHidden );
                final boolean itemIsReadOnly = formItem.isReadonly();
                final boolean itemIsHidden = formItem.getType() == FormConfiguration.Type.hidden;

                if ( ( !itemIsHidden && !itemIsReadOnly ) || checkReadOnlyAndHidden )
                {
                    final String value = formValues.get( formItem );
                    if ( !StringUtil.isEmpty( value ) )
                    {
                        filterClauses.put( formItem.getName(), value );
                        labelMap.put( formItem.getName(), formItem.getLabel( locale ) );
                    }
                }
            }
        }

        if ( filterClauses.isEmpty() )
        {
            // nothing to search
            return;
        }

        final StringBuilder filter = new StringBuilder();
        {
            // outer;
            filter.append( "(&" );

            // object classes;
            filter.append( "(|" );
            for ( final String objectClass : pwmApplication.getConfig().readSettingAsStringArray( PwmSetting.DEFAULT_OBJECT_CLASSES ) )
            {
                filter.append( "(objectClass=" ).append( objectClass ).append( ")" );
            }
            filter.append( ")" );

            // attributes
            filter.append( "(|" );
            for ( final Map.Entry<String, String> entry : filterClauses.entrySet() )
            {
                final String name = entry.getKey();
                final String value = entry.getValue();
                filter.append( "(" ).append( name ).append( "=" ).append( StringUtil.escapeLdapFilter( value ) ).append( ")" );
            }
            filter.append( ")" );

            filter.append( ")" );
        }

        final CacheService cacheService = pwmApplication.getCacheService();
        final CacheKey cacheKey = CacheKey.newKey(
                Validator.class, null, "attr_unique_check_" + filter.toString()
        );
        if ( allowResultCaching && cacheService != null )
        {
            final String cacheValue = cacheService.get( cacheKey, String.class );
            if ( cacheValue != null )
            {
                if ( NEGATIVE_CACHE_HIT.equals( cacheValue ) )
                {
                    return;
                }
                else
                {
                    final ErrorInformation errorInformation = JsonUtil.deserialize( cacheValue, ErrorInformation.class );
                    throw new PwmDataValidationException( errorInformation );
                }
            }
        }

        final SearchHelper searchHelper = new SearchHelper();
        searchHelper.setFilterAnd( filterClauses );

        final SearchConfiguration searchConfiguration = SearchConfiguration.builder()
                .filter( filter.toString() )
                .build();

        final int resultSearchSizeLimit = 1 + ( excludeDN == null ? 0 : excludeDN.size() );
        final long cacheLifetimeMS = Long.parseLong( pwmApplication.getConfig().readAppProperty( AppProperty.CACHE_FORM_UNIQUE_VALUE_LIFETIME_MS ) );
        final CachePolicy cachePolicy = CachePolicy.makePolicyWithExpirationMS( cacheLifetimeMS );

        try
        {
            final UserSearchEngine userSearchEngine = pwmApplication.getUserSearchEngine();
            final Map<UserIdentity, Map<String, String>> results = new LinkedHashMap<>( userSearchEngine.performMultiUserSearch(
                    searchConfiguration,
                    resultSearchSizeLimit,
                    Collections.emptyList(),
                    SessionLabel.SYSTEM_LABEL
            ) );

            if ( excludeDN != null && !excludeDN.isEmpty() )
            {
                for ( final UserIdentity loopIgnoreIdentity : excludeDN )
                {
                    results.keySet().removeIf( loopIgnoreIdentity::equals );
                }
            }

            if ( !results.isEmpty() )
            {
                final UserIdentity userIdentity = results.keySet().iterator().next();
                if ( labelMap.size() == 1 )
                {
                    // since only one value searched, it must be that one value
                    final String attributeName = labelMap.values().iterator().next();
                    LOGGER.trace( () -> "found duplicate value for attribute '" + attributeName + "' on entry " + userIdentity );
                    final ErrorInformation error = new ErrorInformation( PwmError.ERROR_FIELD_DUPLICATE, null, new String[]
                            {
                                    attributeName,
                            }
                    );
                    throw new PwmDataValidationException( error );
                }

                // do a compare on a user values to find one that matches.
                for ( final Map.Entry<String, String> entry : filterClauses.entrySet() )
                {
                    final String name = entry.getKey();
                    final String value = entry.getValue();
                    final boolean compareResult;
                    try
                    {
                        final ChaiUser theUser = pwmApplication.getProxiedChaiUser( userIdentity );
                        compareResult = theUser.compareStringAttribute( name, value );
                    }
                    catch ( final ChaiOperationException | ChaiUnavailableException e )
                    {
                        final PwmError error = PwmError.forChaiError( e.getErrorCode() );
                        throw new PwmUnrecoverableException( error.toInfo() );
                    }

                    if ( compareResult )
                    {
                        final String label = labelMap.get( name );
                        LOGGER.trace( () ->  "found duplicate value for attribute '" + label + "' on entry " + userIdentity );
                        final ErrorInformation error = new ErrorInformation( PwmError.ERROR_FIELD_DUPLICATE, null, new String[]
                                {
                                        label,
                                }
                                );
                        throw new PwmDataValidationException( error );
                    }
                }

                // user didn't match on the compare.. shouldn't read here but just in case
                final ErrorInformation error = new ErrorInformation( PwmError.ERROR_FIELD_DUPLICATE, null );
                throw new PwmDataValidationException( error );
            }
        }
        catch ( final PwmOperationalException e )
        {
            if ( cacheService != null )
            {
                final String jsonPayload = JsonUtil.serialize( e.getErrorInformation() );
                cacheService.put( cacheKey, cachePolicy, jsonPayload );
            }
            throw new PwmDataValidationException( e.getErrorInformation() );
        }
        if ( allowResultCaching && cacheService != null )
        {
            cacheService.put( cacheKey, cachePolicy, NEGATIVE_CACHE_HIT );
        }
    }

    /**
     * Validates each of the parameters in the supplied map against the vales in the embedded config
     * and checks to make sure the ParamConfig value meets the requirements of the ParamConfig itself.
     *
     * @param formValues - a Map containing String keys of parameter names and ParamConfigs as values
     * @param locale used for error messages
     * @param configuration current application configuration
     *
     * @throws password.pwm.error.PwmDataValidationException - If there is a problem with any of the fields
     * @throws password.pwm.error.PwmUnrecoverableException  if an unexpected error occurs
     */
    public static void validateFormValues(
            final Configuration configuration,
            final Map<FormConfiguration, String> formValues,
            final Locale locale
    )
            throws PwmUnrecoverableException, PwmDataValidationException
    {
        for ( final Map.Entry<FormConfiguration, String> entry : formValues.entrySet() )
        {
            final FormConfiguration formItem = entry.getKey();
            final String value = entry.getValue();
            formItem.checkValue( configuration, value, locale );
        }
    }

    public static String ldapSearchFilterForForm( final PwmApplication pwmApplication, final Collection<FormConfiguration> formElements )
            throws PwmUnrecoverableException
    {
        if ( formElements == null || formElements.isEmpty() )
        {
            final String errorMsg = "can not auto-generate ldap search filter for form with no required form items";
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.CONFIG_FORMAT_ERROR, null, new String[]
                    {
                            errorMsg,
                    }
                    );
            throw new PwmUnrecoverableException( errorInformation );
        }


        final StringBuilder sb = new StringBuilder();
        sb.append( "(&" );

        final List<String> objectClasses = pwmApplication.getConfig().readSettingAsStringArray( PwmSetting.DEFAULT_OBJECT_CLASSES );
        if ( objectClasses != null && !objectClasses.isEmpty() )
        {
            if ( objectClasses.size() == 1 )
            {
                sb.append( "(objectclass=" );
                sb.append( objectClasses.iterator().next() );
                sb.append( ")" );
            }
            else
            {
                sb.append( "(|" );
                for ( final String objectClassValue : objectClasses )
                {
                    sb.append( "(objectclass=" );
                    sb.append( objectClassValue );
                    sb.append( ")" );
                }
                sb.append( ")" );
            }
        }

        for ( final FormConfiguration formConfiguration : formElements )
        {
            final String formElementName = formConfiguration.getName();
            sb.append( "(" );
            sb.append( formElementName );
            sb.append( "=" );
            sb.append( "%" ).append( formElementName ).append( "%" );
            sb.append( ")" );
        }

        sb.append( ")" );
        return sb.toString();
    }

    public static void populateFormMapFromLdap(
            final List<FormConfiguration> formFields,
            final SessionLabel sessionLabel,
            final Map<FormConfiguration, String> formMap,
            final UserInfo userInfo
    )
            throws PwmUnrecoverableException
    {
        final Map<FormConfiguration, List<String>> valueMap = populateFormMapFromLdap( formFields, sessionLabel, userInfo );
        for ( final FormConfiguration formConfiguration : formFields )
        {
            if ( valueMap.containsKey( formConfiguration ) )
            {
                final List<String> values = valueMap.get( formConfiguration );
                if ( values != null && !values.isEmpty() )
                {
                    final String value = values.iterator().next();
                    formMap.put( formConfiguration, value );
                }
            }
        }
    }

    public static Map<FormConfiguration, List<String>> populateFormMapFromLdap(
            final List<FormConfiguration> formFields,
            final SessionLabel sessionLabel,
            final UserInfo userInfo,
            final Flag... flags
    )
            throws PwmUnrecoverableException
    {
        final boolean includeNulls = JavaHelper.enumArrayContainsValue( flags, Flag.ReturnEmptyValues );
        final List<String> formFieldNames = FormConfiguration.convertToListOfNames( formFields );
        LOGGER.trace( sessionLabel, () -> "preparing to load form data from ldap for fields " + JsonUtil.serializeCollection( formFieldNames ) );
        final Map<String, List<String>> dataFromLdap = new LinkedHashMap<>();
        try
        {
            for ( final FormConfiguration formConfiguration : formFields )
            {
                if ( formConfiguration.getSource() == FormConfiguration.Source.ldap || formConfiguration.getSource() == null )
                {
                    final String attribute = formConfiguration.getName();
                    if ( formConfiguration.isMultivalue() )
                    {
                        final List<String> values = userInfo.readMultiStringAttribute( attribute );
                        if ( includeNulls || ( values != null && !values.isEmpty() ) )
                        {
                            dataFromLdap.put( attribute, values );
                        }
                    }
                    else if ( formConfiguration.getType() == FormConfiguration.Type.photo )
                    {
                        final byte[] byteValue = userInfo.readBinaryAttribute( attribute );
                        if ( byteValue != null && byteValue.length > 0 )
                        {
                            final String b64value = StringUtil.base64Encode( byteValue );
                            dataFromLdap.put( attribute, Collections.singletonList( b64value ) );
                        }
                    }
                    else
                    {
                        final String value = userInfo.readStringAttribute( attribute );
                        if ( includeNulls || ( value != null ) )
                        {
                            dataFromLdap.put( attribute, Collections.singletonList( value ) );
                        }
                    }
                }
            }
        }
        catch ( final Exception e )
        {
            PwmError error = null;
            if ( e instanceof ChaiException )
            {
                error = PwmError.forChaiError( ( ( ChaiException ) e ).getErrorCode() );
            }
            if ( error == null || error == PwmError.ERROR_INTERNAL )
            {
                error = PwmError.ERROR_LDAP_DATA_ERROR;
            }

            final ErrorInformation errorInformation = new ErrorInformation( error, "error reading current profile values: " + e.getMessage() );
            LOGGER.error( sessionLabel, () -> errorInformation.getDetailedErrorMsg() );
            throw new PwmUnrecoverableException( errorInformation );
        }

        final Map<FormConfiguration, List<String>> returnMap = new LinkedHashMap<>();
        for ( final FormConfiguration formItem : formFields )
        {
            final String attrName = formItem.getName();
            if ( dataFromLdap.containsKey( attrName ) )
            {
                final List<String> values = new ArrayList<>();
                for ( final String value : dataFromLdap.get( attrName ) )
                {
                    final String parsedValue = parseInputValueToFormValue( formItem, value );
                    values.add( parsedValue );
                    LOGGER.trace( sessionLabel, () -> "loaded value for form item '" + attrName + "' with value=" + value );
                }

                returnMap.put( formItem, values );
            }
        }
        return returnMap;
    }

    public static Map<FormConfiguration, String> multiValueMapToSingleValue( final Map<FormConfiguration, List<String>> input )
    {
        final Map<FormConfiguration, String> returnMap = new LinkedHashMap<>();
        for ( final Map.Entry<FormConfiguration, List<String>> entry : input.entrySet() )
        {
            final FormConfiguration formConfiguration = entry.getKey();
            final List<String> listValue = entry.getValue();
            final String value = listValue != null && !listValue.isEmpty()
                    ? listValue.iterator().next()
                    : null;
            returnMap.put( formConfiguration, value );
        }
        return returnMap;
    }

    public static Map<String, TokenDestinationItem.Type> identifyFormItemsNeedingPotentialTokenValidation(
            final LdapProfile ldapProfile,
            final Collection<FormConfiguration> formConfigurations
    )
    {
        final Map<PwmSetting, TokenDestinationItem.Type> settingTypeMap = TokenDestinationItem.getSettingToDestTypeMap();
        final Map<String, TokenDestinationItem.Type> returnObj = new LinkedHashMap<>(  );

        for ( final Map.Entry<PwmSetting, TokenDestinationItem.Type> entry : settingTypeMap.entrySet() )
        {
            final String attrName = ldapProfile.readSettingAsString( entry.getKey() );
            if ( !StringUtil.isEmpty( attrName ) )
            {
                for ( final FormConfiguration formConfiguration : formConfigurations )
                {
                    if ( attrName.equalsIgnoreCase( formConfiguration.getName() ) )
                    {
                        returnObj.put( attrName, entry.getValue() );
                    }
                }
            }
        }

        return returnObj;
    }

    public static Map<String, FormConfiguration> asFormNameMap( final List<FormConfiguration> formConfigurations )
    {
        final Map<String, FormConfiguration> returnMap = new LinkedHashMap<>();
        for ( final FormConfiguration formConfiguration : formConfigurations )
        {
            returnMap.put( formConfiguration.getName(), formConfiguration );
        }
        return returnMap;
    }
}
