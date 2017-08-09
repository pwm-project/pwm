/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
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

package password.pwm.http.servlet.newuser;

import password.pwm.config.value.data.FormConfiguration;
import password.pwm.util.form.FormUtility;
import password.pwm.config.PwmSetting;
import password.pwm.config.profile.NewUserProfile;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmDataValidationException;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.http.bean.NewUserBean;
import password.pwm.svc.token.TokenPayload;
import password.pwm.util.PasswordData;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.SecureService;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

class NewUserFormUtils {

    private static final PwmLogger LOGGER = PwmLogger.forClass(NewUserFormUtils.class);


    static NewUserForm readFromRequest(
            final PwmRequest pwmRequest,
            final NewUserBean newUserBean

    )
            throws PwmDataValidationException, PwmUnrecoverableException
    {

        final Locale userLocale = pwmRequest.getLocale();
        final List<FormConfiguration> newUserForm = NewUserServlet.getFormDefinition(pwmRequest);
        final Map<FormConfiguration, String> userFormValues = FormUtility.readFormValuesFromRequest(pwmRequest,
                newUserForm, userLocale);
        final PasswordData passwordData1 = pwmRequest.readParameterAsPassword(NewUserServlet.FIELD_PASSWORD1);
        final PasswordData passwordData2 = pwmRequest.readParameterAsPassword(NewUserServlet.FIELD_PASSWORD2);

        final NewUserProfile newUserProfile = NewUserServlet.getNewUserProfile(pwmRequest);
        return injectRemoteValuesIntoForm(userFormValues, newUserBean.getRemoteInputData(), newUserProfile, passwordData1, passwordData2);
    }

    static NewUserForm readFromJsonRequest(
            final PwmRequest pwmRequest,
            final NewUserBean newUserBean
    )
            throws IOException, PwmUnrecoverableException, PwmDataValidationException
    {

        final Locale userLocale = pwmRequest.getLocale();
        final List<FormConfiguration> newUserForm = NewUserServlet.getFormDefinition(pwmRequest);
        final Map<String, String> jsonBodyMap = pwmRequest.readBodyAsJsonStringMap();
        final Map<FormConfiguration, String> userFormValues = FormUtility.readFormValuesFromMap(jsonBodyMap,
                newUserForm, userLocale);

        final PasswordData passwordData1 = jsonBodyMap.containsKey(NewUserServlet.FIELD_PASSWORD1) && !jsonBodyMap.get(
                NewUserServlet.FIELD_PASSWORD1).isEmpty()
                ? new PasswordData(jsonBodyMap.get(NewUserServlet.FIELD_PASSWORD1))
                : null;

        final PasswordData passwordData2 = jsonBodyMap.containsKey(NewUserServlet.FIELD_PASSWORD2) && !jsonBodyMap.get(
                NewUserServlet.FIELD_PASSWORD2).isEmpty()
                ? new PasswordData(jsonBodyMap.get(NewUserServlet.FIELD_PASSWORD2))
                : null;

        final NewUserProfile newUserProfile = NewUserServlet.getNewUserProfile(pwmRequest);
        return injectRemoteValuesIntoForm(userFormValues, newUserBean.getRemoteInputData(), newUserProfile, passwordData1, passwordData2);
    }

    static NewUserTokenData fromTokenPayload(
            final PwmRequest pwmRequest,
            final TokenPayload tokenPayload
    )
            throws PwmOperationalException, PwmUnrecoverableException
    {
        final SecureService secureService = pwmRequest.getPwmApplication().getSecureService();

        final Map<String, String> payloadMap = tokenPayload.getData();

        if (!payloadMap.containsKey(NewUserServlet.TOKEN_PAYLOAD_ATTR)) {
            throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_TOKEN_INCORRECT, "token is missing new user form data"));
        }

        final String encryptedTokenData = payloadMap.get(NewUserServlet.TOKEN_PAYLOAD_ATTR);

        return secureService.decryptObject(encryptedTokenData, NewUserTokenData.class);
    }

    static Map<String, String> toTokenPayload(
            final PwmRequest pwmRequest,
            final NewUserBean newUserBean
    )
            throws PwmUnrecoverableException
    {

        final NewUserTokenData newUserTokenData = new NewUserTokenData(
                newUserBean.getProfileID(),
                newUserBean.getNewUserForm(),
                newUserBean.getRemoteInputData()
        );

        final SecureService secureService = pwmRequest.getPwmApplication().getSecureService();
        final String encodedTokenData = secureService.encryptObjectToString(newUserTokenData);
        final Map<String, String> payloadMap = new HashMap<>();
        payloadMap.put(NewUserServlet.TOKEN_PAYLOAD_ATTR, encodedTokenData);
        return payloadMap;
    }

    static Map<String,String> getLdapDataFromNewUserForm(final NewUserProfile newUserProfile, final NewUserForm newUserForm) {
        final Map<String,String> ldapData = new LinkedHashMap<>();
        final List<FormConfiguration> formConfigurations = newUserProfile.readSettingAsForm(PwmSetting.NEWUSER_FORM);
        for (final FormConfiguration formConfiguration : formConfigurations) {
            if (formConfiguration.getSource() == FormConfiguration.Source.ldap) {
                final String attrName = formConfiguration.getName();
                final String value = newUserForm.getFormData().get(attrName);
                if (!StringUtil.isEmpty(value)) {
                    ldapData.put(attrName, value);
                }
            }
        }
        return ldapData;
    }

    static void injectRemoteValuesIntoForm(final NewUserBean newUserBean, final NewUserProfile newUserProfile)
            throws PwmUnrecoverableException
    {
        final NewUserForm oldForm = newUserBean.getNewUserForm();
        final List<FormConfiguration> formConfigurations = newUserProfile.readSettingAsForm(PwmSetting.NEWUSER_FORM);
        final Map<FormConfiguration,String> userFormValues = FormUtility.asFormConfigurationMap(formConfigurations, oldForm.getFormData());
        final Map<String,String> injectedValues = newUserBean.getRemoteInputData();
        final NewUserForm newUserForm = injectRemoteValuesIntoForm(userFormValues, injectedValues, newUserProfile, oldForm.getNewUserPassword(), oldForm.getConfirmPassword());
        newUserBean.setNewUserForm(newUserForm);
    }

    private static NewUserForm injectRemoteValuesIntoForm(
            final Map<FormConfiguration, String> userFormValues,
            final Map<String,String> injectedValues,
            final NewUserProfile newUserProfile,
            final PasswordData passwordData1,
            final PasswordData passwordData2
    ) {
        final Map<String,String> newFormValues = new LinkedHashMap<>();
        newFormValues.putAll(FormUtility.asStringMap(userFormValues));

        final List<FormConfiguration> formConfigurations = newUserProfile.readSettingAsForm(PwmSetting.NEWUSER_FORM);
        if (injectedValues != null) {
            for (final FormConfiguration formConfiguration : formConfigurations) {
                final String name = formConfiguration.getName();
                final boolean formHasValue = !StringUtil.isEmpty(newFormValues.get(name));

                if (formConfiguration.isReadonly() || (!formHasValue && injectedValues.containsKey(name))) {
                    newFormValues.put(formConfiguration.getName(), injectedValues.get(formConfiguration.getName()));
                }
            }
        }

        return new NewUserForm(newFormValues, passwordData1, passwordData2);
    }
}
