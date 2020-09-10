/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2020 The PWM Project
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

package password.pwm.http.servlet.newuser;

import password.pwm.config.PwmSetting;
import password.pwm.config.profile.NewUserProfile;
import password.pwm.config.value.data.FormConfiguration;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmDataValidationException;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.http.bean.NewUserBean;
import password.pwm.svc.token.TokenPayload;
import password.pwm.util.PasswordData;
import password.pwm.util.form.FormUtility;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.SecureService;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

class NewUserFormUtils
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( NewUserFormUtils.class );


    static NewUserForm readFromRequest(
            final PwmRequest pwmRequest,
            final NewUserBean newUserBean

    )
            throws PwmDataValidationException, PwmUnrecoverableException
    {

        final Locale userLocale = pwmRequest.getLocale();
        final List<FormConfiguration> newUserForm = NewUserServlet.getFormDefinition( pwmRequest );
        final Map<FormConfiguration, String> userFormValues = FormUtility.readFormValuesFromRequest( pwmRequest,
                newUserForm, userLocale );
        final PasswordData passwordData1 = pwmRequest.readParameterAsPassword( NewUserServlet.FIELD_PASSWORD1 );
        final PasswordData passwordData2 = pwmRequest.readParameterAsPassword( NewUserServlet.FIELD_PASSWORD2 );

        final NewUserProfile newUserProfile = NewUserServlet.getNewUserProfile( pwmRequest );
        return injectRemoteValuesIntoForm( userFormValues, newUserBean.getRemoteInputData(), newUserProfile, passwordData1, passwordData2 );
    }

    static NewUserForm readFromJsonRequest(
            final PwmRequest pwmRequest,
            final NewUserBean newUserBean
    )
            throws IOException, PwmUnrecoverableException, PwmDataValidationException
    {

        final Locale userLocale = pwmRequest.getLocale();
        final List<FormConfiguration> newUserForm = NewUserServlet.getFormDefinition( pwmRequest );
        final Map<String, String> jsonBodyMap = pwmRequest.readBodyAsJsonStringMap();
        final Map<FormConfiguration, String> userFormValues = FormUtility.readFormValuesFromMap( jsonBodyMap,
                newUserForm, userLocale );

        final PasswordData passwordData1 = jsonBodyMap.containsKey( NewUserServlet.FIELD_PASSWORD1 ) && !jsonBodyMap.get(
                NewUserServlet.FIELD_PASSWORD1 ).isEmpty()
                ? new PasswordData( jsonBodyMap.get( NewUserServlet.FIELD_PASSWORD1 ) )
                : null;

        final PasswordData passwordData2 = jsonBodyMap.containsKey( NewUserServlet.FIELD_PASSWORD2 ) && !jsonBodyMap.get(
                NewUserServlet.FIELD_PASSWORD2 ).isEmpty()
                ? new PasswordData( jsonBodyMap.get( NewUserServlet.FIELD_PASSWORD2 ) )
                : null;

        final NewUserProfile newUserProfile = NewUserServlet.getNewUserProfile( pwmRequest );
        return injectRemoteValuesIntoForm( userFormValues, newUserBean.getRemoteInputData(), newUserProfile, passwordData1, passwordData2 );
    }

    static NewUserTokenData fromTokenPayload(
            final PwmRequest pwmRequest,
            final TokenPayload tokenPayload
    )
            throws PwmOperationalException, PwmUnrecoverableException
    {
        final SecureService secureService = pwmRequest.getPwmApplication().getSecureService();

        final Map<String, String> payloadMap = tokenPayload.getData();

        if ( !payloadMap.containsKey( NewUserServlet.TOKEN_PAYLOAD_ATTR ) )
        {
            throw new PwmOperationalException( new ErrorInformation( PwmError.ERROR_TOKEN_INCORRECT, "token is missing new user form data" ) );
        }

        final String encryptedTokenData = payloadMap.get( NewUserServlet.TOKEN_PAYLOAD_ATTR );

        return secureService.decryptObject( encryptedTokenData, NewUserTokenData.class );
    }

    static Map<String, String> toTokenPayload(
            final PwmRequest pwmRequest,
            final NewUserBean newUserBean
    )
            throws PwmUnrecoverableException
    {
        final NewUserTokenData newUserTokenData = new NewUserTokenData();
        newUserTokenData.setProfileID( newUserBean.getProfileID() );
        newUserTokenData.setFormData( newUserBean.getNewUserForm() );
        newUserTokenData.setInjectionData( newUserBean.getRemoteInputData() );
        newUserTokenData.setCurrentTokenField( newUserBean.getCurrentTokenField() );
        newUserTokenData.setCompletedTokenFields( newUserBean.getCompletedTokenFields() );

        final SecureService secureService = pwmRequest.getPwmApplication().getSecureService();
        final String encodedTokenData = secureService.encryptObjectToString( newUserTokenData );
        final Map<String, String> payloadMap = new HashMap<>();
        payloadMap.put( NewUserServlet.TOKEN_PAYLOAD_ATTR, encodedTokenData );
        return payloadMap;
    }

    static Map<String, String> getLdapDataFromNewUserForm( final NewUserProfile newUserProfile, final NewUserForm newUserForm )
    {
        final Map<String, String> ldapData = new LinkedHashMap<>();
        final List<FormConfiguration> formConfigurations = newUserProfile.readSettingAsForm( PwmSetting.NEWUSER_FORM );
        for ( final FormConfiguration formConfiguration : formConfigurations )
        {
            if ( formConfiguration.getSource() == FormConfiguration.Source.ldap )
            {
                final String attrName = formConfiguration.getName();
                final String value = newUserForm.getFormData().get( attrName );
                if ( !StringUtil.isEmpty( value ) )
                {
                    ldapData.put( attrName, value );
                }
            }
        }
        return ldapData;
    }

    static void injectRemoteValuesIntoForm( final NewUserBean newUserBean, final NewUserProfile newUserProfile )
            throws PwmUnrecoverableException
    {
        final NewUserForm oldForm = newUserBean.getNewUserForm();
        final List<FormConfiguration> formConfigurations = newUserProfile.readSettingAsForm( PwmSetting.NEWUSER_FORM );
        final Map<FormConfiguration, String> userFormValues = FormUtility.asFormConfigurationMap( formConfigurations, oldForm.getFormData() );
        final Map<String, String> injectedValues = newUserBean.getRemoteInputData();
        final NewUserForm newUserForm = injectRemoteValuesIntoForm( userFormValues, injectedValues, newUserProfile, oldForm.getNewUserPassword(), oldForm.getConfirmPassword() );
        newUserBean.setNewUserForm( newUserForm );
    }

    private static NewUserForm injectRemoteValuesIntoForm(
            final Map<FormConfiguration, String> userFormValues,
            final Map<String, String> injectedValues,
            final NewUserProfile newUserProfile,
            final PasswordData passwordData1,
            final PasswordData passwordData2
    )
    {
        final Map<String, String> newFormValues = new LinkedHashMap<>( FormUtility.asStringMap( userFormValues ) );

        final List<FormConfiguration> formConfigurations = newUserProfile.readSettingAsForm( PwmSetting.NEWUSER_FORM );
        if ( injectedValues != null )
        {
            for ( final FormConfiguration formConfiguration : formConfigurations )
            {
                final String name = formConfiguration.getName();
                final boolean formHasValue = !StringUtil.isEmpty( newFormValues.get( name ) );

                if ( formConfiguration.isReadonly() || ( !formHasValue && injectedValues.containsKey( name ) ) )
                {
                    newFormValues.put( formConfiguration.getName(), injectedValues.get( formConfiguration.getName() ) );
                }
            }
        }

        return new NewUserForm( newFormValues, passwordData1, passwordData2 );
    }
}
