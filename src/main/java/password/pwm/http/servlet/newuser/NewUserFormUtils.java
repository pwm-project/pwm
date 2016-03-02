/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2016 The PWM Project
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

import password.pwm.AppProperty;
import password.pwm.config.FormConfiguration;
import password.pwm.config.FormUtility;
import password.pwm.config.PwmSetting;
import password.pwm.config.profile.NewUserProfile;
import password.pwm.error.PwmDataValidationException;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.http.bean.NewUserBean;
import password.pwm.svc.token.TokenPayload;
import password.pwm.util.PasswordData;
import password.pwm.util.logging.PwmLogger;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

class NewUserFormUtils {

    private static final PwmLogger LOGGER = PwmLogger.forClass(NewUserFormUtils.class);


    static NewUserBean.NewUserForm readFromRequest(PwmRequest pwmRequest)
            throws PwmDataValidationException, PwmUnrecoverableException
    {
        final Locale userLocale = pwmRequest.getLocale();
        final List<FormConfiguration> newUserForm = NewUserServlet.getFormDefinition(pwmRequest);
        final Map<FormConfiguration, String> userFormValues = FormUtility.readFormValuesFromRequest(pwmRequest,
                newUserForm, userLocale);
        final PasswordData passwordData1 = pwmRequest.readParameterAsPassword(NewUserServlet.FIELD_PASSWORD1);
        final PasswordData passwordData2 = pwmRequest.readParameterAsPassword(NewUserServlet.FIELD_PASSWORD2);
        return new NewUserBean.NewUserForm(FormUtility.asStringMap(userFormValues), passwordData1, passwordData2);
    }

    static NewUserBean.NewUserForm readFromJsonRequest(final PwmRequest pwmRequest)
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
        return new NewUserBean.NewUserForm(FormUtility.asStringMap(userFormValues), passwordData1, passwordData2);
    }

    static NewUserServlet.NewUserTokenData fromTokenPayload(
            final PwmRequest pwmRequest,
            final TokenPayload tokenPayload
    )
            throws PwmOperationalException, PwmUnrecoverableException
    {
        final Locale userLocale = pwmRequest.getLocale();

        final Map<String, String> payloadMap = tokenPayload.getData();

        final NewUserProfile newUserProfile;
        {
            final String profileID = payloadMap.get(NewUserServlet.TOKEN_PAYLOAD_ATTR);
            payloadMap.remove(NewUserServlet.TOKEN_PAYLOAD_ATTR);
            if (profileID == null || profileID.isEmpty()) {
                // typically missing because issued with code before newuser profile existed, so assume  only profile
                if (pwmRequest.getConfig().getNewUserProfiles().size() > 1) {
                    throw new PwmOperationalException(PwmError.ERROR_TOKEN_INCORRECT, "token data missing reference to new user profileID");
                }
                newUserProfile = pwmRequest.getConfig().getNewUserProfiles().values().iterator().next();
            } else {
                if (!pwmRequest.getConfig().getNewUserProfiles().keySet().contains(profileID)) {
                    throw new PwmOperationalException(PwmError.ERROR_TOKEN_INCORRECT, "token data references an invalid new user profileID");
                }
                newUserProfile = pwmRequest.getConfig().getNewUserProfiles().get(profileID);
            }
        }

        final List<FormConfiguration> newUserFormDefinition = newUserProfile.readSettingAsForm(PwmSetting.NEWUSER_FORM);
        final Map<FormConfiguration, String> userFormValues = FormUtility.readFormValuesFromMap(payloadMap,
                newUserFormDefinition, userLocale);
        final PasswordData passwordData;
        if (payloadMap.containsKey(NewUserServlet.FIELD_PASSWORD1)) {
            final String passwordInToken = payloadMap.get(NewUserServlet.FIELD_PASSWORD1);
            String decryptedPassword = passwordInToken;
            try {
                decryptedPassword = pwmRequest.getPwmApplication().getSecureService().decryptStringValue(passwordInToken);
            } catch (PwmUnrecoverableException e) {
                final boolean allowUnencryptedPassword = Boolean.parseBoolean(pwmRequest.getConfig().readAppProperty(AppProperty.NEWUSER_TOKEN_ALLOW_PLAIN_PW));
                if (allowUnencryptedPassword && e.getError() == PwmError.ERROR_CRYPT_ERROR) {
                    LOGGER.warn(pwmRequest, "error decrypting password in tokenPayload, will use raw password value: " + e.getMessage());
                } else {
                    throw e;
                }
            }
            passwordData = new PasswordData(decryptedPassword);
        } else {
            passwordData = null;
        }
        final NewUserBean.NewUserForm newUserForm = new NewUserBean.NewUserForm(FormUtility.asStringMap(userFormValues), passwordData, passwordData);
        return new NewUserServlet.NewUserTokenData(newUserProfile.getIdentifier(), newUserForm);
    }

    static Map<String, String> toTokenPayload(
            final PwmRequest pwmRequest,
            NewUserBean.NewUserForm newUserForm
    )
            throws PwmUnrecoverableException
    {
        final Map<String, String> payloadMap = new LinkedHashMap<>();
        payloadMap.put(NewUserServlet.TOKEN_PAYLOAD_ATTR, pwmRequest.getPwmApplication().getSessionStateService().getBean(pwmRequest, NewUserBean.class).getProfileID());
        payloadMap.putAll(newUserForm.getFormData());
        final String encryptedPassword = pwmRequest.getPwmApplication().getSecureService().encryptToString(
                newUserForm.getNewUserPassword().getStringValue()
        );
        payloadMap.put(NewUserServlet.FIELD_PASSWORD1, encryptedPassword);
        return payloadMap;
    }
}
