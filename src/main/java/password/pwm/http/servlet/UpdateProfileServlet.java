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

package password.pwm.http.servlet;

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.EmailItemBean;
import password.pwm.bean.TokenVerificationProgress;
import password.pwm.bean.UserIdentity;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.ActionConfiguration;
import password.pwm.config.Configuration;
import password.pwm.config.FormConfiguration;
import password.pwm.config.FormUtility;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.TokenStorageMethod;
import password.pwm.config.profile.LdapProfile;
import password.pwm.config.profile.UpdateAttributesProfile;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmDataValidationException;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpMethod;
import password.pwm.http.JspUrl;
import password.pwm.http.ProcessStatus;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmSession;
import password.pwm.http.bean.UpdateProfileBean;
import password.pwm.i18n.Message;
import password.pwm.ldap.LdapOperationsHelper;
import password.pwm.ldap.UserDataReader;
import password.pwm.ldap.UserStatusReader;
import password.pwm.svc.event.AuditEvent;
import password.pwm.svc.event.AuditRecord;
import password.pwm.svc.event.AuditRecordFactory;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.token.TokenPayload;
import password.pwm.svc.token.TokenService;
import password.pwm.svc.token.TokenType;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;
import password.pwm.util.operations.ActionExecutor;
import password.pwm.ws.server.RestResultBean;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * User interaction servlet for updating user attributes
 *
 * @author Jason D. Rivard
 */
@WebServlet(
        name="UpdateProfileServlet",
        urlPatterns = {
                PwmConstants.URL_PREFIX_PRIVATE + "/updateprofile",
                PwmConstants.URL_PREFIX_PRIVATE + "/UpdateProfile"
        }
)
public class UpdateProfileServlet extends ControlledPwmServlet {

    private static final PwmLogger LOGGER = PwmLogger.forClass(UpdateProfileServlet.class);

    public enum UpdateProfileAction implements AbstractPwmServlet.ProcessAction {
        updateProfile(HttpMethod.POST),
        agree(HttpMethod.POST),
        confirm(HttpMethod.POST),
        unConfirm(HttpMethod.POST),
        validate(HttpMethod.POST),
        enterCode(HttpMethod.POST),

        ;

        private final HttpMethod method;

        UpdateProfileAction(final HttpMethod method)
        {
            this.method = method;
        }

        public Collection<HttpMethod> permittedMethods()
        {
            return Collections.singletonList(method);
        }
    }

    @Override
    public Class<? extends ProcessAction> getProcessActionsClass() {
        return UpdateProfileAction.class;
    }

    private static UpdateAttributesProfile getProfile(final PwmRequest pwmRequest) {
        return pwmRequest.getPwmSession().getSessionManager().getUpdateAttributeProfile(pwmRequest.getPwmApplication());
    }

    private static UpdateProfileBean getBean(final PwmRequest pwmRequest) throws PwmUnrecoverableException {
        return pwmRequest.getPwmApplication().getSessionStateService().getBean(pwmRequest, UpdateProfileBean.class);
    }

    @ActionHandler(action = "enterCode")
    ProcessStatus handleEnterCodeRequest(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException, IOException, ServletException, ChaiUnavailableException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final UpdateProfileBean updateProfileBean = getBean(pwmRequest);
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final String userEnteredCode = pwmRequest.readParameterAsString(PwmConstants.PARAM_TOKEN);

        boolean tokenPassed = false;
        ErrorInformation errorInformation = null;
        try {
            final TokenPayload tokenPayload = pwmApplication.getTokenService().processUserEnteredCode(
                    pwmSession,
                    pwmRequest.getUserInfoIfLoggedIn(),
                    null,
                    userEnteredCode
            );
            if (tokenPayload != null) {
                if (TokenType.UPDATE_EMAIL.matchesName(tokenPayload.getName())) {
                    LOGGER.debug(pwmRequest, "email token passed");

                    updateProfileBean.getTokenVerificationProgress().getPassedTokens().add(TokenVerificationProgress.TokenChannel.EMAIL);
                    updateProfileBean.getTokenVerificationProgress().getIssuedTokens().add(TokenVerificationProgress.TokenChannel.EMAIL);
                    updateProfileBean.getTokenVerificationProgress().setPhase(null);
                    tokenPassed = true;
                } else if (TokenType.UPDATE_SMS.matchesName(tokenPayload.getName())) {
                    LOGGER.debug(pwmRequest, "SMS token passed");
                    updateProfileBean.getTokenVerificationProgress().getPassedTokens().add(TokenVerificationProgress.TokenChannel.SMS);
                    updateProfileBean.getTokenVerificationProgress().getIssuedTokens().add(TokenVerificationProgress.TokenChannel.SMS);
                    updateProfileBean.getTokenVerificationProgress().setPhase(null);
                    tokenPassed = true;
                } else {
                    final String errorMsg = "token name/type is not recognized: " + tokenPayload.getName();
                    errorInformation = new ErrorInformation(PwmError.ERROR_TOKEN_INCORRECT,errorMsg);
                }
            }
        } catch (PwmOperationalException e) {
            final String errorMsg = "token incorrect: " + e.getMessage();
            errorInformation = new ErrorInformation(PwmError.ERROR_TOKEN_INCORRECT, errorMsg);
        }


        if (!tokenPassed) {
            if (errorInformation == null) {
                errorInformation = new ErrorInformation(PwmError.ERROR_TOKEN_INCORRECT);
            }
            LOGGER.debug(pwmSession, errorInformation.toDebugStr());
            setLastError(pwmRequest, errorInformation);
        }

        return ProcessStatus.Continue;
    }

    @ActionHandler(action = "validate")
    ProcessStatus restValidateForm(
            final PwmRequest pwmRequest
    )
            throws IOException, ServletException, PwmUnrecoverableException, ChaiUnavailableException
    {
        final UpdateProfileBean updateProfileBean = getBean(pwmRequest);
        final UpdateAttributesProfile updateAttributesProfile = getProfile(pwmRequest);

        boolean success = true;
        String userMessage = Message.getLocalizedMessage(pwmRequest.getLocale(), Message.Success_UpdateForm, pwmRequest.getConfig());

        try {
            // read in the responses from the request
            final Map<FormConfiguration,String> formValues = readFromJsonRequest(pwmRequest, updateAttributesProfile, updateProfileBean);

            // verify form meets the form requirements
            verifyFormAttributes(pwmRequest, formValues, true);

            updateProfileBean.getFormData().putAll(FormUtility.asStringMap(formValues));
        } catch (PwmOperationalException e) {
            success = false;
            userMessage = e.getErrorInformation().toUserStr(pwmRequest.getPwmSession(), pwmRequest.getPwmApplication());
        }

        final LinkedHashMap<String, String> outputMap = new LinkedHashMap<>();
        outputMap.put("version", "1");
        outputMap.put("message", userMessage);
        outputMap.put("success", String.valueOf(success));

        pwmRequest.outputJsonResult(new RestResultBean(outputMap));

        return ProcessStatus.Halt;
    }

    @ActionHandler(action = "unConfirm")
    ProcessStatus handleUnconfirm(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException
    {
        final UpdateProfileBean updateProfileBean = getBean(pwmRequest);

        updateProfileBean.setFormSubmitted(false);
        updateProfileBean.setConfirmationPassed(false);
        updateProfileBean.clearTokenVerificationProgress();

        return ProcessStatus.Continue;
    }

    @ActionHandler(action = "agree")
    ProcessStatus handleAgreeRequest(final PwmRequest pwmRequest)
            throws ServletException, IOException, PwmUnrecoverableException, ChaiUnavailableException
    {
        LOGGER.debug(pwmRequest, "user accepted agreement");

        final UpdateProfileBean updateProfileBean = getBean(pwmRequest);
        if (!updateProfileBean.isAgreementPassed()) {
            updateProfileBean.setAgreementPassed(true);
            final AuditRecord auditRecord = new AuditRecordFactory(pwmRequest).createUserAuditRecord(
                    AuditEvent.AGREEMENT_PASSED,
                    pwmRequest.getUserInfoIfLoggedIn(),
                    pwmRequest.getSessionLabel(),
                    "UpdateProfile"
            );
            pwmRequest.getPwmApplication().getAuditManager().submit(auditRecord);
        }

        return ProcessStatus.Continue;
    }

    @ActionHandler(action = "confirm")
    ProcessStatus handleConfirmRequest(final PwmRequest pwmRequest)
            throws PwmUnrecoverableException
    {
        final UpdateProfileBean updateProfileBean = getBean(pwmRequest);
        updateProfileBean.setConfirmationPassed(true);

        return ProcessStatus.Continue;
    }

    @ActionHandler(action = "updateProfile")
    ProcessStatus handleUpdateRequest(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException, ChaiUnavailableException, IOException, ServletException
    {
        final UpdateProfileBean updateProfileBean = getBean(pwmRequest);
        final UpdateAttributesProfile updateAttributesProfile = getProfile(pwmRequest);

        try {
            readFormParametersFromRequest(pwmRequest, updateAttributesProfile, updateProfileBean);
        } catch (PwmOperationalException e) {
            LOGGER.error(pwmRequest, e.getMessage());
            setLastError(pwmRequest, e.getErrorInformation());
        }

        updateProfileBean.setFormSubmitted(true);

        return ProcessStatus.Continue;
    }

    protected void nextStep(
            final PwmRequest pwmRequest)
            throws IOException, ServletException, PwmUnrecoverableException, ChaiUnavailableException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final UpdateProfileBean updateProfileBean = getBean(pwmRequest);
        final UpdateAttributesProfile updateAttributesProfile = getProfile(pwmRequest);
        final PwmSession pwmSession = pwmRequest.getPwmSession();

        final String updateProfileAgreementText = updateAttributesProfile.readSettingAsLocalizedString(
                PwmSetting.UPDATE_PROFILE_AGREEMENT_MESSAGE,
                pwmSession.getSessionStateBean().getLocale()
        );

        if (updateProfileAgreementText != null && updateProfileAgreementText.length() > 0) {
            if (!updateProfileBean.isAgreementPassed()) {
                final MacroMachine macroMachine = pwmRequest.getPwmSession().getSessionManager().getMacroMachine(pwmRequest.getPwmApplication());
                final String expandedText = macroMachine.expandMacros(updateProfileAgreementText);
                pwmRequest.setAttribute(PwmRequest.Attribute.AgreementText, expandedText);
                pwmRequest.forwardToJsp(JspUrl.UPDATE_ATTRIBUTES_AGREEMENT);
                return;
            }
        }

        //make sure there is form data in the bean.
        if (!updateProfileBean.isFormLdapLoaded()) {
            updateProfileBean.getFormData().clear();
            updateProfileBean.getFormData().putAll((formDataFromLdap(pwmRequest, updateAttributesProfile)));
            updateProfileBean.setFormLdapLoaded(true);
            forwardToForm(pwmRequest, updateAttributesProfile, updateProfileBean);
            return;
        }

        if (!updateProfileBean.isFormSubmitted()) {
            forwardToForm(pwmRequest, updateAttributesProfile, updateProfileBean);
            return;
        }


        // validate the form data.
        try {
            // verify form meets the form requirements
            final List<FormConfiguration> formFields = updateAttributesProfile.readSettingAsForm(PwmSetting.UPDATE_PROFILE_FORM);
            final Map<FormConfiguration, String> formValues = FormUtility.readFormValuesFromMap(updateProfileBean.getFormData(), formFields, pwmRequest.getLocale());
            verifyFormAttributes(pwmRequest, formValues, true);
        } catch (PwmException e) {
            LOGGER.error(pwmSession, e.getMessage());
            setLastError(pwmRequest, e.getErrorInformation());
            forwardToForm(pwmRequest, updateAttributesProfile, updateProfileBean);
            return;
        }

        final boolean requireConfirmation = updateAttributesProfile.readSettingAsBoolean(PwmSetting.UPDATE_PROFILE_SHOW_CONFIRMATION);
        if (requireConfirmation && !updateProfileBean.isConfirmationPassed()) {
            forwardToConfirmForm(pwmRequest, updateAttributesProfile, updateProfileBean);
            return;
        }

        final Set<TokenVerificationProgress.TokenChannel> requiredVerifications = determineTokenPhaseRequired(pwmRequest, updateProfileBean, updateAttributesProfile);
        if (requiredVerifications != null) {
            for (final TokenVerificationProgress.TokenChannel tokenChannel : requiredVerifications) {
                if (requiredVerifications.contains(tokenChannel)) {
                    if (!updateProfileBean.getTokenVerificationProgress().getIssuedTokens().contains(tokenChannel)) {
                        initializeToken(pwmRequest, updateProfileBean, tokenChannel);
                    }

                    if (!updateProfileBean.getTokenVerificationProgress().getPassedTokens().contains(tokenChannel)) {
                        updateProfileBean.getTokenVerificationProgress().setPhase(tokenChannel);
                        pwmRequest.forwardToJsp(JspUrl.UPDATE_ATTRIBUTES_ENTER_CODE);
                        return;
                    }
                }
            }
        }

        try {
            // write the form values
            final ChaiUser theUser = pwmSession.getSessionManager().getActor(pwmApplication);
            doProfileUpdate(pwmRequest, updateProfileBean.getFormData(), theUser);
            pwmRequest.getPwmResponse().forwardToSuccessPage(Message.Success_UpdateProfile);
            return;
        } catch (PwmException e) {
            LOGGER.error(pwmSession, e.getMessage());
            setLastError(pwmRequest, e.getErrorInformation());
        } catch (ChaiException e) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UPDATE_ATTRS_FAILURE,e.toString());
            LOGGER.error(pwmSession, errorInformation.toDebugStr());
            setLastError(pwmRequest, errorInformation);
        }

        forwardToForm(pwmRequest, updateAttributesProfile, updateProfileBean);
    }

    @Override
    public ProcessStatus preProcessCheck(final PwmRequest pwmRequest) throws PwmUnrecoverableException, IOException, ServletException {
        if (!pwmRequest.getPwmApplication().getConfig().readSettingAsBoolean(PwmSetting.UPDATE_PROFILE_ENABLE)) {
            pwmRequest.respondWithError(new ErrorInformation(PwmError.ERROR_SERVICE_NOT_AVAILABLE, "Setting " + PwmSetting.UPDATE_PROFILE_ENABLE.toMenuLocationDebug(null,null) + " is not enabled."));
            return ProcessStatus.Halt;
        }

        final UpdateAttributesProfile updateAttributesProfile = getProfile(pwmRequest);
        if (updateAttributesProfile == null) {
            pwmRequest.respondWithError(new ErrorInformation(PwmError.ERROR_NO_PROFILE_ASSIGNED));
            return ProcessStatus.Halt;
        }

        return ProcessStatus.Continue;
    }




    final Map<FormConfiguration,String> readFormParametersFromRequest(
            final PwmRequest pwmRequest,
            final UpdateAttributesProfile updateAttributesProfile,
            final UpdateProfileBean updateProfileBean
    )
            throws PwmUnrecoverableException, PwmDataValidationException, ChaiUnavailableException
    {
        final List<FormConfiguration> formFields = updateAttributesProfile.readSettingAsForm(PwmSetting.UPDATE_PROFILE_FORM);

        //read the values from the request
        final Map<FormConfiguration,String> formValueMap = FormUtility.readFormValuesFromRequest(pwmRequest, formFields, pwmRequest.getLocale());

        return updateBeanFormData(formFields, formValueMap, updateProfileBean);
    }

    static Map<FormConfiguration,String> readFromJsonRequest(
            final PwmRequest pwmRequest,
            final UpdateAttributesProfile updateAttributesProfile,
            final UpdateProfileBean updateProfileBean
    )
            throws PwmDataValidationException, PwmUnrecoverableException, IOException
    {
        final List<FormConfiguration> formFields = updateAttributesProfile.readSettingAsForm(PwmSetting.UPDATE_PROFILE_FORM);

        final Map<FormConfiguration,String> formValueMap = FormUtility.readFormValuesFromMap(pwmRequest.readBodyAsJsonStringMap(), formFields, pwmRequest.getLocale());

        return updateBeanFormData(formFields, formValueMap, updateProfileBean);
    }

    static Map<FormConfiguration,String> updateBeanFormData(
            final List<FormConfiguration> formFields,
            final Map<FormConfiguration,String> formValueMap,
            final UpdateProfileBean updateProfileBean
    ) {
        final LinkedHashMap<FormConfiguration,String> newFormValueMap = new LinkedHashMap<>();
        for (final FormConfiguration formConfiguration : formFields) {
            if (formConfiguration.isReadonly()) {
                final String existingValue = updateProfileBean.getFormData().get(formConfiguration.getName());
                newFormValueMap.put(formConfiguration, existingValue);
            } else {
                newFormValueMap.put(formConfiguration, formValueMap.get(formConfiguration));
            }
        }

        updateProfileBean.getFormData().clear();
        updateProfileBean.getFormData().putAll(FormUtility.asStringMap(newFormValueMap));

        return newFormValueMap;
    }



    public static void doProfileUpdate(
            final PwmRequest pwmRequest,
            final Map<String,String> formValues,
            final ChaiUser theUser
    )
            throws PwmUnrecoverableException, ChaiUnavailableException, PwmOperationalException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final UserInfoBean uiBean = pwmRequest.getPwmSession().getUserInfoBean();
        final UpdateAttributesProfile updateAttributesProfile = pwmRequest.getPwmSession().getSessionManager().getUpdateAttributeProfile(pwmApplication);

        final List<FormConfiguration> formFields = updateAttributesProfile.readSettingAsForm(PwmSetting.UPDATE_PROFILE_FORM);
        final Map<FormConfiguration, String> formMap = FormUtility.readFormValuesFromMap(formValues, formFields, pwmRequest.getLocale());

        // verify form meets the form requirements (may be redundant, but shouldn't hurt)
        verifyFormAttributes(pwmRequest, formMap, false);

        // write values.
        LOGGER.info("updating profile for " + pwmRequest.getPwmSession().getUserInfoBean().getUserIdentity());

        pwmRequest.getPwmSession().getSessionManager().getChaiProvider();

        LdapOperationsHelper.writeFormValuesToLdap(pwmRequest.getPwmApplication(), pwmRequest.getPwmSession(), theUser, formMap, false);

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
            final List<ActionConfiguration> actions = updateAttributesProfile.readSettingAsAction(PwmSetting.UPDATE_PROFILE_WRITE_ATTRIBUTES);
            if (actions != null && !actions.isEmpty()) {
                LOGGER.debug(pwmRequest, "executing configured actions to user " + userIdentity);


                final ActionExecutor actionExecutor = new ActionExecutor.ActionExecutorSettings(pwmApplication, userIdentity)
                        .setExpandPwmMacros(true)
                        .setMacroMachine(pwmSession.getSessionManager().getMacroMachine(pwmApplication))
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
        pwmApplication.getSessionStateService().clearBean(pwmRequest, UpdateProfileBean.class);

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

    private static void forwardToForm(final PwmRequest pwmRequest, final UpdateAttributesProfile updateAttributesProfile, final UpdateProfileBean updateProfileBean)
            throws ServletException, PwmUnrecoverableException, IOException
    {
        final List<FormConfiguration> form = updateAttributesProfile.readSettingAsForm(PwmSetting.UPDATE_PROFILE_FORM);
        final Map<FormConfiguration,String> formValueMap = formMapFromBean(updateAttributesProfile, updateProfileBean);
        pwmRequest.addFormInfoToRequestAttr(form, formValueMap, false, false);
        pwmRequest.forwardToJsp(JspUrl.UPDATE_ATTRIBUTES);
    }

    private static void forwardToConfirmForm(final PwmRequest pwmRequest, final UpdateAttributesProfile updateAttributesProfile, final UpdateProfileBean updateProfileBean)
            throws ServletException, PwmUnrecoverableException, IOException
    {
        final List<FormConfiguration> form = updateAttributesProfile.readSettingAsForm(PwmSetting.UPDATE_PROFILE_FORM);
        final Map<FormConfiguration,String> formValueMap = formMapFromBean(updateAttributesProfile, updateProfileBean);
        pwmRequest.addFormInfoToRequestAttr(form, formValueMap, true, false);
        pwmRequest.forwardToJsp(JspUrl.UPDATE_ATTRIBUTES_CONFIRM);
    }

    private static Map<FormConfiguration, String> formMapFromBean(final UpdateAttributesProfile updateAttributesProfile, final UpdateProfileBean updateProfileBean) throws PwmUnrecoverableException {

        final List<FormConfiguration> form = updateAttributesProfile.readSettingAsForm(PwmSetting.UPDATE_PROFILE_FORM);
        final Map<FormConfiguration, String> formValueMap = new LinkedHashMap<>();
        for (final FormConfiguration formConfiguration : form) {
            formValueMap.put(
                    formConfiguration,
                    updateProfileBean.getFormData().keySet().contains(formConfiguration.getName())
                            ? updateProfileBean.getFormData().get(formConfiguration.getName())
                            : ""
            );
        }
        return formValueMap;
    }

    private static Map<String,String> formDataFromLdap(final PwmRequest pwmRequest, final UpdateAttributesProfile updateAttributesProfile)
            throws PwmUnrecoverableException
    {
        final UserDataReader userDataReader = pwmRequest.getPwmSession().getSessionManager().getUserDataReader(pwmRequest.getPwmApplication());
        final List<FormConfiguration> formFields = updateAttributesProfile.readSettingAsForm(PwmSetting.UPDATE_PROFILE_FORM);
        final Map<FormConfiguration, String> formMap = new LinkedHashMap<>();
        FormUtility.populateFormMapFromLdap(formFields, pwmRequest.getSessionLabel(), formMap, userDataReader);
        return FormUtility.asStringMap(formMap);
    }

    private static Set<TokenVerificationProgress.TokenChannel> determineTokenPhaseRequired(
            final PwmRequest pwmRequest,
            final UpdateProfileBean updateProfileBean,
            final UpdateAttributesProfile updateAttributesProfile

    )
            throws PwmUnrecoverableException
    {
        final Set<TokenVerificationProgress.TokenChannel> returnObj = new HashSet<>();

        final LdapProfile ldapProfile = pwmRequest.getUserInfoIfLoggedIn().getLdapProfile(pwmRequest.getConfig());
        final Map<String,String> userFormData = updateProfileBean.getFormData();
        Map<String,String> ldapData = null;

        if (updateAttributesProfile.readSettingAsBoolean(PwmSetting.UPDATE_PROFILE_EMAIL_VERIFICATION)) {
            final String emailAddressAttribute = ldapProfile.readSettingAsString(PwmSetting.EMAIL_USER_MAIL_ATTRIBUTE);
            if (userFormData.containsKey(emailAddressAttribute)) {
                ldapData = formDataFromLdap(pwmRequest, updateAttributesProfile);
                if (userFormData.get(emailAddressAttribute) != null && !userFormData.get(emailAddressAttribute).equalsIgnoreCase(ldapData.get(emailAddressAttribute))) {
                    returnObj.add(TokenVerificationProgress.TokenChannel.EMAIL);
                }
            } else {
                LOGGER.warn(pwmRequest, "email verification enabled, but email attribute '" + emailAddressAttribute + "' is not in update form");
            }
        }

        if (updateAttributesProfile.readSettingAsBoolean(PwmSetting.UPDATE_PROFILE_SMS_VERIFICATION)) {
            final String phoneNumberAttribute = ldapProfile.readSettingAsString(PwmSetting.SMS_USER_PHONE_ATTRIBUTE);
            if (userFormData.containsKey(phoneNumberAttribute)) {
                if (ldapData == null) {
                    ldapData = formDataFromLdap(pwmRequest, updateAttributesProfile);
                }
                if (userFormData.get(phoneNumberAttribute) != null && !userFormData.get(phoneNumberAttribute).equalsIgnoreCase(ldapData.get(phoneNumberAttribute))) {
                    returnObj.add(TokenVerificationProgress.TokenChannel.SMS);
                }
            } else {
                LOGGER.warn(pwmRequest, "sms verification enabled, but phone attribute '" + phoneNumberAttribute + "' is not in update form");
            }
        }

        return returnObj;
    }

    private void initializeToken(
            final PwmRequest pwmRequest,
            final UpdateProfileBean updateProfileBean,
            final TokenVerificationProgress.TokenChannel tokenType
    )
            throws PwmUnrecoverableException
    {
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();

        if (pwmApplication.getConfig().getTokenStorageMethod() == TokenStorageMethod.STORE_LDAP) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR,null,new String[]{
                    "cannot generate new user tokens when storage type is configured as STORE_LDAP.",
            }));
        }

        final MacroMachine macroMachine = pwmRequest.getPwmSession().getSessionManager().getMacroMachine(pwmApplication);
        final Configuration config = pwmApplication.getConfig();
        final LdapProfile ldapProfile = pwmRequest.getUserInfoIfLoggedIn().getLdapProfile(pwmRequest.getConfig());

        switch (tokenType) {
            case SMS: {
                final String telephoneNumberAttribute = ldapProfile.readSettingAsString(PwmSetting.SMS_USER_PHONE_ATTRIBUTE);
                final String toNum = updateProfileBean.getFormData().get(telephoneNumberAttribute);
                final String tokenKey;
                try {
                    final TokenPayload tokenPayload = pwmApplication.getTokenService().createTokenPayload(
                            TokenType.UPDATE_SMS,
                            Collections.emptyMap(),
                            pwmRequest.getUserInfoIfLoggedIn(),
                            Collections.singleton(toNum)
                    );
                    tokenKey = pwmApplication.getTokenService().generateNewToken(tokenPayload,
                            pwmRequest.getSessionLabel());
                } catch (PwmOperationalException e) {
                    throw new PwmUnrecoverableException(e.getErrorInformation());
                }

                final String message = config.readSettingAsLocalizedString(PwmSetting.SMS_UPDATE_PROFILE_TOKEN_TEXT,
                        pwmSession.getSessionStateBean().getLocale());

                try {
                    TokenService.TokenSender.sendSmsToken(pwmApplication, null, macroMachine, toNum, message, tokenKey);
                } catch (Exception e) {
                    throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_UNKNOWN));
                }

                updateProfileBean.getTokenVerificationProgress().getIssuedTokens().add(TokenVerificationProgress.TokenChannel.SMS);
                updateProfileBean.getTokenVerificationProgress().setTokenDisplayText(toNum);
                updateProfileBean.getTokenVerificationProgress().setPhase(TokenVerificationProgress.TokenChannel.SMS);
            }
            break;

            case EMAIL: {
                final EmailItemBean configuredEmailSetting = config.readSettingAsEmail(
                        PwmSetting.EMAIL_UPDATEPROFILE_VERIFICATION,
                        pwmRequest.getLocale()
                );
                final String emailAddressAttribute = ldapProfile.readSettingAsString(PwmSetting.EMAIL_USER_MAIL_ATTRIBUTE);
                final String toAddress = updateProfileBean.getFormData().get(emailAddressAttribute);

                final String tokenKey;
                try {
                    final TokenPayload tokenPayload = pwmApplication.getTokenService().createTokenPayload(
                            TokenType.UPDATE_EMAIL,
                            Collections.<String,String>emptyMap(),
                            pwmRequest.getUserInfoIfLoggedIn(),
                            Collections.singleton(toAddress)
                    );
                    tokenKey = pwmApplication.getTokenService().generateNewToken(tokenPayload,
                            pwmRequest.getSessionLabel());
                } catch (PwmOperationalException e) {
                    throw new PwmUnrecoverableException(e.getErrorInformation());
                }

                updateProfileBean.getTokenVerificationProgress().getIssuedTokens().add(TokenVerificationProgress.TokenChannel.EMAIL);
                updateProfileBean.getTokenVerificationProgress().setPhase(TokenVerificationProgress.TokenChannel.EMAIL);
                updateProfileBean.getTokenVerificationProgress().setTokenDisplayText(toAddress);

                final EmailItemBean emailItemBean = new EmailItemBean(
                        toAddress,
                        configuredEmailSetting.getFrom(),
                        configuredEmailSetting.getSubject(),
                        configuredEmailSetting.getBodyPlain().replace("%TOKEN%", tokenKey),
                        configuredEmailSetting.getBodyHtml().replace("%TOKEN%", tokenKey));

                try {
                    TokenService.TokenSender.sendEmailToken(pwmApplication, null, macroMachine, emailItemBean,
                            toAddress, tokenKey);
                } catch (Exception e) {
                    throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_UNKNOWN));
                }
            }
            break;

            default:
                LOGGER.error("Unimplemented token purpose: " + tokenType);
                updateProfileBean.getTokenVerificationProgress().setPhase(null);
        }
    }


}

