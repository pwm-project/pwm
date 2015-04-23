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

package password.pwm.http.servlet;

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiException;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.Permission;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.EmailItemBean;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.*;
import password.pwm.error.*;
import password.pwm.event.AuditEvent;
import password.pwm.event.AuditRecord;
import password.pwm.http.HttpMethod;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmSession;
import password.pwm.http.bean.UpdateProfileBean;
import password.pwm.i18n.Message;
import password.pwm.ldap.UserDataReader;
import password.pwm.ldap.UserStatusReader;
import password.pwm.util.Helper;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;
import password.pwm.util.operations.ActionExecutor;
import password.pwm.util.stats.Statistic;
import password.pwm.ws.server.RestResultBean;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.*;

/**
 * User interaction servlet for updating user attributes
 *
 * @author Jason D. Rivard
 */
public class UpdateProfileServlet extends PwmServlet {

    private static final PwmLogger LOGGER = PwmLogger.forClass(UpdateProfileServlet.class);

    public enum UpdateProfileAction implements PwmServlet.ProcessAction {
        updateProfile(HttpMethod.POST),
        agree(HttpMethod.POST),
        confirm(HttpMethod.POST),
        unConfirm(HttpMethod.POST),
        validate(HttpMethod.POST),

        ;

        private final HttpMethod method;

        UpdateProfileAction(HttpMethod method)
        {
            this.method = method;
        }

        public Collection<HttpMethod> permittedMethods()
        {
            return Collections.singletonList(method);
        }
    }

    protected UpdateProfileAction readProcessAction(final PwmRequest request)
            throws PwmUnrecoverableException
    {
        try {
            return UpdateProfileAction.valueOf(request.readParameterAsString(PwmConstants.PARAM_ACTION_REQUEST));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    protected void processAction(final PwmRequest pwmRequest)
            throws ServletException, IOException, ChaiUnavailableException, PwmUnrecoverableException
    {
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final UpdateProfileBean updateProfileBean = pwmSession.getUpdateProfileBean();

        if (!pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.UPDATE_PROFILE_ENABLE)) {
            pwmRequest.respondWithError(new ErrorInformation(PwmError.ERROR_SERVICE_NOT_AVAILABLE));
            return;
        }

        if (!pwmSession.getSessionManager().checkPermission(pwmApplication, Permission.PROFILE_UPDATE)) {
            pwmRequest.respondWithError(new ErrorInformation(PwmError.ERROR_UNAUTHORIZED));
            return;
        }

        final UpdateProfileAction action = readProcessAction(pwmRequest);
        if (action != null) {
            pwmRequest.validatePwmFormID();
            switch(action) {
                case updateProfile:
                    handleUpdateRequest(pwmRequest,updateProfileBean);
                    break;

                case agree:
                    handleAgreeRequest(pwmRequest, updateProfileBean);
                    break;

                case confirm:
                    updateProfileBean.setConfirmationPassed(true);
                    break;

                case unConfirm:
                    handleUnconfirm(updateProfileBean);
                    break;

                case validate:
                    restValidateForm(pwmRequest, updateProfileBean);
                    return;
            }
        }

        advanceToNextStep(pwmRequest, updateProfileBean);
    }

    protected static void restValidateForm(
            final PwmRequest pwmRequest,
            final UpdateProfileBean updateProfileBean
    )
            throws IOException, ServletException, PwmUnrecoverableException, ChaiUnavailableException
    {
        boolean success = true;
        String userMessage = Message.getLocalizedMessage(pwmRequest.getLocale(), Message.Success_UpdateForm, pwmRequest.getConfig());
        final Map<FormConfiguration, String> formValues = updateProfileBean.getFormData();

        try {
            // read in the responses from the request
            readFromJsonRequest(pwmRequest, updateProfileBean);

            // verify form meets the form requirements
            verifyFormAttributes(pwmRequest, formValues, true);
        } catch (PwmOperationalException e) {
            success = false;
            userMessage = e.getErrorInformation().toUserStr(pwmRequest.getPwmSession(), pwmRequest.getPwmApplication());
        }

        final LinkedHashMap<String, String> outputMap = new LinkedHashMap<>();
        outputMap.put("version", "1");
        outputMap.put("message", userMessage);
        outputMap.put("success", String.valueOf(success));

        pwmRequest.outputJsonResult(new RestResultBean(outputMap));
    }

    private void handleUnconfirm(
            final UpdateProfileBean updateProfileBean
    ) {
        updateProfileBean.setFormSubmitted(false);
        updateProfileBean.setConfirmationPassed(false);
    }

    private void advanceToNextStep(
            final PwmRequest pwmRequest,
            final UpdateProfileBean updateProfileBean
    )
            throws IOException, ServletException, PwmUnrecoverableException, ChaiUnavailableException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();

        final String updateProfileAgreementText = pwmApplication.getConfig().readSettingAsLocalizedString(
                PwmSetting.UPDATE_PROFILE_AGREEMENT_MESSAGE,
                pwmSession.getSessionStateBean().getLocale()
        );

        if (updateProfileAgreementText != null && updateProfileAgreementText.length() > 0) {
            if (!updateProfileBean.isAgreementPassed()) {
                final MacroMachine macroMachine = pwmRequest.getPwmSession().getSessionManager().getMacroMachine(pwmRequest.getPwmApplication());
                final String expandedText = macroMachine.expandMacros(updateProfileAgreementText);
                pwmRequest.setAttribute(PwmConstants.REQUEST_ATTR.AgreementText, expandedText);
                pwmRequest.forwardToJsp(PwmConstants.JSP_URL.UPDATE_ATTRIBUTES_AGREEMENT);
                return;
            }
        }

        final Map<FormConfiguration, String> formValues = updateProfileBean.getFormData();
        if (!updateProfileBean.isFormSubmitted()) {
            final Map<FormConfiguration,String> formMap = updateProfileBean.getFormData();
            final List<FormConfiguration> formFields = pwmApplication.getConfig().readSettingAsForm(PwmSetting.UPDATE_PROFILE_FORM);
            populateFormFromLdap(formFields, pwmRequest.getSessionLabel(), formMap, pwmSession.getSessionManager().getUserDataReader(pwmApplication));
            forwardToForm(pwmRequest);
            return;
        }

        //make sure there is form data in the bean.
        if (updateProfileBean.getFormData() == null) {
            forwardToForm(pwmRequest);
            return;
        }

        // validate the form data.
        try {
            // verify form meets the form requirements
            verifyFormAttributes(pwmRequest, formValues, true);
        } catch (PwmOperationalException e) {
            LOGGER.error(pwmSession, e.getMessage());
            pwmRequest.setResponseError(e.getErrorInformation());
            forwardToForm(pwmRequest);
            return;
        }

        final boolean requireConfirmation = pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.UPDATE_PROFILE_SHOW_CONFIRMATION);
        if (requireConfirmation && !updateProfileBean.isConfirmationPassed()) {
            forwardToConfirmForm(pwmRequest);
            return;
        }

        try {
            // write the form values
            final ChaiUser theUser = pwmSession.getSessionManager().getActor(pwmApplication);
            doProfileUpdate(pwmRequest, formValues, theUser);
            pwmRequest.forwardToSuccessPage(Message.Success_UpdateProfile);
            return;
        } catch (PwmException e) {
            LOGGER.error(pwmSession, e.getMessage());
            pwmRequest.setResponseError(e.getErrorInformation());
        } catch (ChaiException e) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UPDATE_ATTRS_FAILURE,e.toString());
            LOGGER.error(pwmSession, errorInformation.toDebugStr());
            pwmRequest.setResponseError(errorInformation);
        }

        forwardToForm(pwmRequest);
    }

    private void handleAgreeRequest(
            final PwmRequest pwmRequest,
            final UpdateProfileBean updateProfileBean
    )
            throws ServletException, IOException, PwmUnrecoverableException, ChaiUnavailableException
    {
        LOGGER.debug(pwmRequest, "user accepted agreement");

        if (!updateProfileBean.isAgreementPassed()) {
            updateProfileBean.setAgreementPassed(true);
            AuditRecord auditRecord = pwmRequest.getPwmApplication().getAuditManager().createUserAuditRecord(
                    AuditEvent.AGREEMENT_PASSED,
                    pwmRequest.getUserInfoIfLoggedIn(),
                    pwmRequest.getSessionLabel(),
                    "UpdateProfile"
            );
            pwmRequest.getPwmApplication().getAuditManager().submit(auditRecord);
        }
    }

    public static void populateFormFromLdap(
            final List<FormConfiguration> formFields,
            final SessionLabel sessionLabel,
            final Map<FormConfiguration, String> formMap,
            final UserDataReader userDataReader
    )
            throws PwmUnrecoverableException, ChaiUnavailableException
    {
        LOGGER.trace(sessionLabel, "loading existing user profile data from ldap");
        final Map<String,String> userData = new LinkedHashMap<>();
        try {
            userData.putAll(userDataReader.readStringAttributes(FormConfiguration.convertToListOfNames(formFields), true));
        } catch (ChaiOperationException e) {
            LOGGER.error(sessionLabel, "unexpected error reading profile data attributes: " + e.getMessage());
        }

        for (final FormConfiguration formItem : formFields) {
            final String attrName = formItem.getName();
            if (!formMap.containsKey(attrName)) {
                if (userData.containsKey(attrName)) {
                    formMap.put(formItem, userData.get(attrName));
                }
            }
        }
    }


    private void readFormParametersFromRequest(
            final PwmRequest pwmRequest,
            final UpdateProfileBean updateProfileBean
    )
            throws PwmUnrecoverableException, PwmDataValidationException, ChaiUnavailableException
    {
        final List<FormConfiguration> formFields = pwmRequest.getConfig().readSettingAsForm(PwmSetting.UPDATE_PROFILE_FORM);

        final Map<FormConfiguration,String> existingForm = updateProfileBean.getFormData();

        //read the values from the request
        existingForm.putAll(FormUtility.readFormValuesFromRequest(pwmRequest, formFields,
                pwmRequest.getLocale()));
    }

    private static void readFromJsonRequest(
            final PwmRequest pwmRequest,
            final UpdateProfileBean updateProfileBean
    )
            throws PwmDataValidationException, PwmUnrecoverableException, IOException
    {
        final List<FormConfiguration> formFields = pwmRequest.getConfig().readSettingAsForm(PwmSetting.UPDATE_PROFILE_FORM);
        final Map<FormConfiguration,String> existingForm = updateProfileBean.getFormData();

        final Map<String, String> clientValues = pwmRequest.readBodyAsJsonStringMap();

        if (clientValues != null) {
            existingForm.putAll(FormUtility.readFormValuesFromMap(clientValues, formFields, pwmRequest.getLocale()));
        }
    }


    private void handleUpdateRequest(
            final PwmRequest pwmRequest,
            final UpdateProfileBean updateProfileBean
    )
            throws PwmUnrecoverableException, ChaiUnavailableException, IOException, ServletException
    {

        try {
            readFormParametersFromRequest(pwmRequest, updateProfileBean);
        } catch (PwmOperationalException e) {
            LOGGER.error(pwmRequest, e.getMessage());
            pwmRequest.setResponseError(e.getErrorInformation());
        }

        updateProfileBean.setFormSubmitted(true);
    }

    public static void doProfileUpdate(
            final PwmRequest pwmRequest,
            final Map<FormConfiguration, String> formValues,
            final ChaiUser theUser
    )
            throws PwmUnrecoverableException, ChaiUnavailableException, PwmOperationalException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final UserInfoBean uiBean = pwmRequest.getPwmSession().getUserInfoBean();

        // verify form meets the form requirements (may be redundant, but shouldn't hurt)
        verifyFormAttributes(pwmRequest, formValues, false);

        // write values.
        LOGGER.info("updating profile for " + pwmRequest.getPwmSession().getUserInfoBean().getUserIdentity());

        pwmRequest.getPwmSession().getSessionManager().getChaiProvider();

        Helper.writeFormValuesToLdap(pwmRequest.getPwmApplication(), pwmRequest.getPwmSession(), theUser, formValues, false);

        final UserIdentity userIdentity = uiBean.getUserIdentity();

        // re-populate the uiBean because we have changed some values.
        final UserStatusReader userStatusReader = new UserStatusReader(pwmRequest.getPwmApplication(), pwmRequest.getSessionLabel());
        userStatusReader.populateActorUserInfoBean(
                pwmRequest.getPwmSession(),
                userIdentity
        );

        // clear cached read attributes.
        pwmRequest.getPwmSession().getSessionManager().clearUserDataReader();

        {  // execute configured actions
            final List<ActionConfiguration> actions = pwmApplication.getConfig().readSettingAsAction(PwmSetting.UPDATE_PROFILE_WRITE_ATTRIBUTES);
            if (actions != null && !actions.isEmpty()) {
                LOGGER.debug(pwmRequest, "executing configured actions to user " + userIdentity);


                final ActionExecutor actionExecutor = new ActionExecutor.ActionExecutorSettings(pwmApplication, userIdentity)
                        .setExpandPwmMacros(true)
                        .createActionExecutor();

                actionExecutor.executeActions(actions, pwmSession);
            }
        }

        sendProfileUpdateEmailNotice(pwmSession,pwmApplication);

        // mark the event log
        pwmApplication.getAuditManager().submit(AuditEvent.UPDATE_PROFILE, pwmSession.getUserInfoBean(), pwmSession);

        // mark the uiBean so we user isn't recycled to the update profile page by the CommandServlet
        uiBean.setRequiresUpdateProfile(false);

        // clear out the updateProfileBean
        pwmSession.clearUpdateProfileBean();

        // success, so forward to success page
        pwmApplication.getStatisticsManager().incrementValue(Statistic.UPDATE_ATTRIBUTES);
    }

    private static void verifyFormAttributes(
            final PwmRequest pwmRequest,
            final Map<FormConfiguration, String> formValues,
            final boolean allowResultCaching
    )
            throws PwmOperationalException, PwmUnrecoverableException
    {
        final Locale userLocale = pwmRequest.getLocale();

        // see if the values meet form requirements.
        FormUtility.validateFormValues(pwmRequest.getConfig(), formValues, userLocale);

        // check unique fields against ldap
            FormUtility.validateFormValueUniqueness(
                    pwmRequest.getPwmApplication(),
                    formValues,
                    userLocale,
                    Collections.singletonList(pwmRequest.getPwmSession().getUserInfoBean().getUserIdentity()),
                    allowResultCaching
            );
    }

    private static void sendProfileUpdateEmailNotice(
            final PwmSession pwmSession,
            final PwmApplication pwmApplication
    )
            throws PwmUnrecoverableException, ChaiUnavailableException {
        final Configuration config = pwmApplication.getConfig();
        final Locale locale = pwmSession.getSessionStateBean().getLocale();
        final EmailItemBean configuredEmailSetting = config.readSettingAsEmail(PwmSetting.EMAIL_UPDATEPROFILE, locale);

        if (configuredEmailSetting == null) {
            LOGGER.debug(pwmSession, "skipping send profile update email for '" + pwmSession.getUserInfoBean().getUserIdentity() + "' no email configured");
            return;
        }

        pwmApplication.getEmailQueue().submitEmail(
                configuredEmailSetting,
                pwmSession.getUserInfoBean(),
                pwmSession.getSessionManager().getMacroMachine(pwmApplication)
        );
    }
    
    protected void forwardToForm(final PwmRequest pwmRequest) 
            throws ServletException, PwmUnrecoverableException, IOException 
    {
        final List<FormConfiguration> form = pwmRequest.getConfig().readSettingAsForm(PwmSetting.UPDATE_PROFILE_FORM);
        final Map<FormConfiguration, String> formData = pwmRequest.getPwmSession().getUpdateProfileBean().getFormData();
        pwmRequest.addFormInfoToRequestAttr(form, formData, false, false);
        pwmRequest.forwardToJsp(PwmConstants.JSP_URL.UPDATE_ATTRIBUTES);
    }

    protected void forwardToConfirmForm(final PwmRequest pwmRequest)
            throws ServletException, PwmUnrecoverableException, IOException
    {
        final List<FormConfiguration> form = pwmRequest.getConfig().readSettingAsForm(PwmSetting.UPDATE_PROFILE_FORM);
        final Map<FormConfiguration, String> formData = pwmRequest.getPwmSession().getUpdateProfileBean().getFormData();
        pwmRequest.addFormInfoToRequestAttr(form, formData, true, false);
        pwmRequest.forwardToJsp(PwmConstants.JSP_URL.UPDATE_ATTRIBUTES_CONFIRM);
    }
}

