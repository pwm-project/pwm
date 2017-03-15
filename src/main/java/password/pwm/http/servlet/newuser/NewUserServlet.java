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

import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.TokenVerificationProgress;
import password.pwm.bean.UserIdentity;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.Configuration;
import password.pwm.config.FormConfiguration;
import password.pwm.config.FormUtility;
import password.pwm.config.PwmSetting;
import password.pwm.config.profile.NewUserProfile;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmDataValidationException;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpMethod;
import password.pwm.http.JspUrl;
import password.pwm.http.ProcessStatus;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmSession;
import password.pwm.http.PwmURL;
import password.pwm.http.bean.NewUserBean;
import password.pwm.http.servlet.AbstractPwmServlet;
import password.pwm.http.servlet.ControlledPwmServlet;
import password.pwm.http.servlet.PwmServletDefinition;
import password.pwm.i18n.Message;
import password.pwm.svc.token.TokenPayload;
import password.pwm.util.CaptchaUtility;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.Percent;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;
import password.pwm.util.operations.PasswordUtility;
import password.pwm.ws.server.RestResultBean;
import password.pwm.ws.server.rest.RestCheckPasswordServer;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * User interaction servlet for creating new users (self registration)
 *
 * @author Jason D. Rivard
 */
@WebServlet(
        name="NewUserServlet",
        urlPatterns = {
                PwmConstants.URL_PREFIX_PUBLIC + "/newuser",
                PwmConstants.URL_PREFIX_PUBLIC + "/newuser/*",
                PwmConstants.URL_PREFIX_PUBLIC + "/NewUser",
                PwmConstants.URL_PREFIX_PUBLIC + "/NewUser/*",
        }
)
public class NewUserServlet extends ControlledPwmServlet {
    private static final PwmLogger LOGGER = PwmLogger.forClass(NewUserServlet.class);

    static final String FIELD_PASSWORD1 = "password1";
    static final String FIELD_PASSWORD2 = "password2";
    static final String TOKEN_PAYLOAD_ATTR = "_______profileID";

    public enum NewUserAction implements AbstractPwmServlet.ProcessAction {
        profileChoice(HttpMethod.POST),
        checkProgress(HttpMethod.GET),
        complete(HttpMethod.GET),
        processForm(HttpMethod.POST),
        validate(HttpMethod.POST),
        enterCode(HttpMethod.POST, HttpMethod.GET),
        reset(HttpMethod.POST),
        agree(HttpMethod.POST),

        ;

        private final Collection<HttpMethod> method;

        NewUserAction(final HttpMethod... method)
        {
            this.method = Collections.unmodifiableList(Arrays.asList(method));
        }

        public Collection<HttpMethod> permittedMethods()
        {
            return method;
        }
    }

    @Override
    public Class<? extends ProcessAction> getProcessActionsClass() {
        return NewUserAction.class;
    }


    private static NewUserBean getNewUserBean(final PwmRequest pwmRequest) throws PwmUnrecoverableException {
        return pwmRequest.getPwmApplication().getSessionStateService().getBean(pwmRequest, NewUserBean.class);
    }

    @Override
    public ProcessStatus preProcessCheck(final PwmRequest pwmRequest) throws PwmUnrecoverableException, IOException, ServletException {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();

        final Configuration config = pwmApplication.getConfig();

        if (!config.readSettingAsBoolean(PwmSetting.NEWUSER_ENABLE)) {
            throw new PwmUnrecoverableException(PwmError.ERROR_SERVICE_NOT_AVAILABLE);
        }

        final NewUserBean newUserBean = pwmApplication.getSessionStateService().getBean(pwmRequest, NewUserBean.class);

        // convert a url command like /public/newuser/profile/xxx to set profile.
        if (readProfileFromUrl(pwmRequest, newUserBean)) {
            return ProcessStatus.Halt;
        }

        final ProcessAction action = this.readProcessAction(pwmRequest);

        // convert a url command like /public/newuser/12321321 to redirect with a process action.
        if (action == null) {
            if (pwmRequest.convertURLtokenCommand()) {
                return ProcessStatus.Halt;
            }
        } else if (action != NewUserAction.complete && action != NewUserAction.checkProgress ) {
            if (pwmRequest.isAuthenticated()) {
                pwmRequest.respondWithError(PwmError.ERROR_USERAUTHENTICATED.toInfo());
                return ProcessStatus.Halt;
            }
        }

        return ProcessStatus.Continue;
    }

    @Override
    protected void nextStep(final PwmRequest pwmRequest)
         throws IOException, ServletException, PwmUnrecoverableException, ChaiUnavailableException
    {
        final NewUserBean newUserBean = getNewUserBean(pwmRequest);
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();

        if (newUserBean.getProfileID() == null) {
            final Set<String> newUserProfileIDs = pwmApplication.getConfig().getNewUserProfiles().keySet();
            if (newUserProfileIDs.isEmpty()) {
                pwmRequest.respondWithError(new ErrorInformation(PwmError.ERROR_INVALID_CONFIG,"no new user profiles are defined"));
                return;
            }

            if (newUserProfileIDs.size() == 1) {
                final String singleID =  newUserProfileIDs.iterator().next();
                LOGGER.trace(pwmRequest, "only one new user profile is defined, auto-selecting profile " + singleID);
                newUserBean.setProfileID(singleID);
            } else {
                LOGGER.trace(pwmRequest, "new user profile not yet selected, redirecting to choice page");
                pwmRequest.forwardToJsp(JspUrl.NEW_USER_PROFILE_CHOICE);
                return;
            }
        }

        final NewUserProfile newUserProfile = getNewUserProfile(pwmRequest);

        // try to read the new user policy to make sure it's readable, that way an exception is thrown here instead of by the jsp
        newUserProfile.getNewUserPasswordPolicy(pwmApplication, pwmSession.getSessionStateBean().getLocale());//

        if (newUserBean.getNewUserForm() == null) {
            forwardToFormPage(pwmRequest, newUserBean);
            return;
        }

        final TokenVerificationProgress tokenVerificationProgress = newUserBean.getTokenVerificationProgress();
        if (newUserProfile.readSettingAsBoolean(PwmSetting.NEWUSER_EMAIL_VERIFICATION)) {
            if (!tokenVerificationProgress.getIssuedTokens().contains(TokenVerificationProgress.TokenChannel.EMAIL)) {
                NewUserUtils.initializeToken(pwmRequest, newUserBean, TokenVerificationProgress.TokenChannel.EMAIL);
            }

            if (!tokenVerificationProgress.getPassedTokens().contains(TokenVerificationProgress.TokenChannel.EMAIL)) {
                pwmRequest.forwardToJsp(JspUrl.NEW_USER_ENTER_CODE);
                return;
            }
        }

        if (newUserProfile.readSettingAsBoolean(PwmSetting.NEWUSER_SMS_VERIFICATION)) {
            if (!newUserBean.getTokenVerificationProgress().getIssuedTokens().contains(TokenVerificationProgress.TokenChannel.SMS)) {
                NewUserUtils.initializeToken(pwmRequest, newUserBean, TokenVerificationProgress.TokenChannel.SMS);
            }

            if (!newUserBean.getTokenVerificationProgress().getPassedTokens().contains(TokenVerificationProgress.TokenChannel.SMS)) {
                pwmRequest.forwardToJsp(JspUrl.NEW_USER_ENTER_CODE);
                return;
            }
        }

        final String newUserAgreementText = newUserProfile.readSettingAsLocalizedString(PwmSetting.NEWUSER_AGREEMENT_MESSAGE,
                pwmSession.getSessionStateBean().getLocale());
        if (newUserAgreementText != null && !newUserAgreementText.isEmpty()) {
            if (!newUserBean.isAgreementPassed()) {
                final MacroMachine macroMachine = NewUserUtils.createMacroMachineForNewUser(
                        pwmApplication,
                        pwmRequest.getSessionLabel(),
                        newUserBean.getNewUserForm()
                );
                final String expandedText = macroMachine.expandMacros(newUserAgreementText);
                pwmRequest.setAttribute(PwmRequest.Attribute.AgreementText, expandedText);
                pwmRequest.forwardToJsp(JspUrl.NEW_USER_AGREEMENT);
                return;
            }
        }

        if (!newUserBean.isFormPassed()) {
            forwardToFormPage(pwmRequest, newUserBean);
        }

        // success so create the new user.
        final String newUserDN = NewUserUtils.determineUserDN(pwmRequest, newUserBean.getNewUserForm());

        try {
            NewUserUtils.createUser(newUserBean.getNewUserForm(), pwmRequest, newUserDN);
            newUserBean.setCreateStartTime(new Date());
            pwmRequest.forwardToJsp(JspUrl.NEW_USER_WAIT);
        } catch (PwmOperationalException e) {
            LOGGER.error(pwmRequest, "error during user creation: " + e.getMessage());
            if (newUserProfile.readSettingAsBoolean(PwmSetting.NEWUSER_DELETE_ON_FAIL)) {
                NewUserUtils.deleteUserAccount(newUserDN, pwmRequest);
            }
            LOGGER.error(pwmSession, e.getErrorInformation().toDebugStr());
            pwmRequest.respondWithError(e.getErrorInformation());
        }
    }

    private boolean readProfileFromUrl(final PwmRequest pwmRequest, final NewUserBean newUserBean)
            throws PwmUnrecoverableException, ServletException, IOException
    {
        final String PROFILE_URL_SEGMENT = "profile";
        final String urlRemainder = servletUriRemainder(pwmRequest, PROFILE_URL_SEGMENT);

        if (urlRemainder != null && !urlRemainder.isEmpty()) {
            final List<String> urlSegments = PwmURL.splitPathString(urlRemainder);
            if (urlSegments.size() == 2 && PROFILE_URL_SEGMENT.equals(urlSegments.get(0))) {
                final String requestedProfile = urlSegments.get(1);
                final Collection<String> profileIDs = pwmRequest.getConfig().getNewUserProfiles().keySet();
                if (profileIDs.contains(requestedProfile)) {
                    LOGGER.debug(pwmRequest, "detected profile on request uri: " + requestedProfile);
                    newUserBean.setProfileID(requestedProfile);
                    newUserBean.setUrlSpecifiedProfile(true);
                    pwmRequest.sendRedirect(PwmServletDefinition.NewUser);
                    return true;
                } else {
                    final String errorMsg = "unknown requested new user profile";
                    LOGGER.debug(pwmRequest, errorMsg + ": " + requestedProfile);
                    throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_SERVICE_NOT_AVAILABLE));
                }
            }
        }
        return false;
    }

    @ActionHandler(action = "validate")
    private ProcessStatus restValidateForm(
            final PwmRequest pwmRequest
    )
            throws IOException, ServletException, PwmUnrecoverableException, ChaiUnavailableException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final Locale locale = pwmRequest.getLocale();

        try {
            final NewUserBean.NewUserForm newUserForm = NewUserFormUtils.readFromJsonRequest(pwmRequest);
            PasswordUtility.PasswordCheckInfo passwordCheckInfo = verifyForm(pwmRequest, newUserForm, true);
            if (passwordCheckInfo.isPassed() && passwordCheckInfo.getMatch() == PasswordUtility.PasswordCheckInfo.MatchStatus.MATCH) {
                passwordCheckInfo = new PasswordUtility.PasswordCheckInfo(
                        Message.getLocalizedMessage(locale,
                                Message.Success_NewUserForm, pwmApplication.getConfig()),
                        passwordCheckInfo.isPassed(),
                        passwordCheckInfo.getStrength(),
                        passwordCheckInfo.getMatch(),
                        passwordCheckInfo.getErrorCode()
                );
            }
            final RestCheckPasswordServer.JsonData jsonData = RestCheckPasswordServer.JsonData.fromPasswordCheckInfo(
                    passwordCheckInfo);
            final RestResultBean restResultBean = new RestResultBean();
            restResultBean.setData(jsonData);
            pwmRequest.outputJsonResult(restResultBean);
        } catch (PwmOperationalException e) {
            final RestResultBean restResultBean = RestResultBean.fromError(e.getErrorInformation(), pwmRequest);
            LOGGER.debug(pwmRequest, "error while validating new user form: " + e.getMessage());
            pwmRequest.outputJsonResult(restResultBean);
        }

        return ProcessStatus.Halt;
    }

    static PasswordUtility.PasswordCheckInfo verifyForm(
            final PwmRequest pwmRequest,
            final NewUserBean.NewUserForm newUserForm,
            final boolean allowResultCaching
    )
            throws PwmDataValidationException, PwmUnrecoverableException, ChaiUnavailableException
    {
        final Locale locale = pwmRequest.getLocale();
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final NewUserProfile newUserProfile = getNewUserProfile(pwmRequest);
        final List<FormConfiguration> formDefinition = newUserProfile.readSettingAsForm(PwmSetting.NEWUSER_FORM);
        final Map<FormConfiguration,String> formValueData = FormUtility.readFormValuesFromMap(newUserForm.getFormData(), formDefinition, locale);

        FormUtility.validateFormValues(pwmApplication.getConfig(), formValueData, locale);
        FormUtility.validateFormValueUniqueness(
                pwmApplication,
                formValueData,
                locale,
                Collections.<UserIdentity>emptyList(),
                allowResultCaching
        );
        final UserInfoBean uiBean = new UserInfoBean();
        uiBean.setCachedPasswordRuleAttributes(FormUtility.asStringMap(formValueData));
        uiBean.setPasswordPolicy(newUserProfile.getNewUserPasswordPolicy(pwmApplication, locale));
        return PasswordUtility.checkEnteredPassword(
                pwmApplication,
                locale,
                null,
                uiBean,
                null,
                newUserForm.getNewUserPassword(),
                newUserForm.getConfirmPassword()
        );
    }

    @ActionHandler(action = "enterCode")
    private ProcessStatus handleEnterCodeRequest(final PwmRequest pwmRequest)
            throws PwmUnrecoverableException, IOException, ServletException, ChaiUnavailableException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final String userEnteredCode = pwmRequest.readParameterAsString(PwmConstants.PARAM_TOKEN);

        boolean tokenPassed = false;
        ErrorInformation errorInformation = null;
        try {
            final TokenPayload tokenPayload = pwmApplication.getTokenService().processUserEnteredCode(
                    pwmSession,
                    null,
                    null,
                    userEnteredCode
            );
            if (tokenPayload != null) {
                final NewUserBean newUserBean = getNewUserBean(pwmRequest);
                final NewUserTokenData newUserTokenData = NewUserFormUtils.fromTokenPayload(pwmRequest, tokenPayload);
                newUserBean.setProfileID(newUserTokenData.getProfileID());
                final NewUserBean.NewUserForm newUserFormFromToken = newUserTokenData.getFormData();
                if (password.pwm.svc.token.TokenType.NEWUSER_EMAIL.matchesName(tokenPayload.getName())) {
                    LOGGER.debug(pwmRequest, "email token passed");

                    try {
                        verifyForm(pwmRequest, newUserFormFromToken, false);
                    } catch (PwmUnrecoverableException | PwmOperationalException e) {
                        LOGGER.error(pwmRequest,"while reading stored form data in token payload, form validation error occurred: " + e.getMessage());
                        throw e;
                    }

                    newUserBean.setNewUserForm(newUserFormFromToken);
                    newUserBean.setFormPassed(true);
                    newUserBean.getTokenVerificationProgress().getPassedTokens().add(TokenVerificationProgress.TokenChannel.EMAIL);
                    newUserBean.getTokenVerificationProgress().getIssuedTokens().add(TokenVerificationProgress.TokenChannel.EMAIL);
                    newUserBean.getTokenVerificationProgress().setPhase(null);
                    tokenPassed = true;
                } else if (password.pwm.svc.token.TokenType.NEWUSER_SMS.matchesName(tokenPayload.getName())) {
                    if (newUserBean.getNewUserForm() != null && newUserBean.getNewUserForm().isConsistentWith(newUserFormFromToken)) {
                        LOGGER.debug(pwmRequest, "SMS token passed");
                        newUserBean.getTokenVerificationProgress().getPassedTokens().add(TokenVerificationProgress.TokenChannel.SMS);
                        newUserBean.getTokenVerificationProgress().getIssuedTokens().add(TokenVerificationProgress.TokenChannel.SMS);
                        newUserBean.getTokenVerificationProgress().setPhase(null);
                        tokenPassed = true;
                    } else {
                        LOGGER.debug(pwmRequest, "SMS token value is valid, but form data does not match current session form data");
                        final String errorMsg = "sms token does not match current session";
                        errorInformation = new ErrorInformation(PwmError.ERROR_TOKEN_INCORRECT,errorMsg);
                    }
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

    @ActionHandler(action = "profileChoice")
    private ProcessStatus handleProfileChoiceRequest(final PwmRequest pwmRequest)
            throws PwmUnrecoverableException, ChaiUnavailableException, IOException, ServletException
    {
        final Set<String> profileIDs = pwmRequest.getConfig().getNewUserProfiles().keySet();
        final String requestedProfileID = pwmRequest.readParameterAsString("profile");

        final NewUserBean newUserBean = getNewUserBean(pwmRequest);

        if (requestedProfileID == null || requestedProfileID.isEmpty()) {
            newUserBean.setProfileID(null);
        }
        if (profileIDs.contains(requestedProfileID)) {
            newUserBean.setProfileID(requestedProfileID);
        }

        return ProcessStatus.Continue;
    }

    @ActionHandler(action = "processForm")
    private ProcessStatus handleProcessFormRequest(final PwmRequest pwmRequest)
            throws PwmUnrecoverableException, ChaiUnavailableException, IOException, ServletException
    {
        final NewUserBean newUserBean = getNewUserBean(pwmRequest);

        if (!CaptchaUtility.verifyReCaptcha(pwmRequest)) {
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_BAD_CAPTCHA_RESPONSE);
            LOGGER.debug(pwmRequest, errorInfo);
            setLastError(pwmRequest, errorInfo);
            forwardToFormPage(pwmRequest, newUserBean);
            return ProcessStatus.Halt;
        }

        newUserBean.setFormPassed(false);
        newUserBean.setNewUserForm(null);

        try {
            final NewUserBean.NewUserForm newUserForm = NewUserFormUtils.readFromRequest(pwmRequest);
            final PasswordUtility.PasswordCheckInfo passwordCheckInfo = verifyForm(pwmRequest, newUserForm, true);
            NewUserUtils.passwordCheckInfoToException(passwordCheckInfo);
            newUserBean.setNewUserForm(newUserForm);
            newUserBean.setFormPassed(true);
        } catch (PwmOperationalException e) {
            setLastError(pwmRequest, e.getErrorInformation());
            forwardToFormPage(pwmRequest, newUserBean);
            return ProcessStatus.Halt;
        }
        return ProcessStatus.Continue;
    }


    @ActionHandler(action = "checkProgress")
    private ProcessStatus restCheckProgress(
            final PwmRequest pwmRequest
    )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        final NewUserBean newUserBean = getNewUserBean(pwmRequest);
        final Date startTime = newUserBean.getCreateStartTime();
        if (startTime == null) {
            pwmRequest.respondWithError(PwmError.ERROR_INCORRECT_REQ_SEQUENCE.toInfo(), true);
            return ProcessStatus.Halt;
        }

        final NewUserProfile newUserProfile = NewUserServlet.getNewUserProfile(pwmRequest);
        final long minWaitTime = newUserProfile.readSettingAsLong(PwmSetting.NEWUSER_MINIMUM_WAIT_TIME) * 1000L;
        final Date completeTime = new Date(startTime.getTime() + minWaitTime);

        final BigDecimal percentComplete;
        final boolean complete;

        // be sure minimum wait time has passed
        if (new Date().after(completeTime)) {
            percentComplete = new BigDecimal("100");
            complete = true;
        } else {
            final TimeDuration elapsedTime = TimeDuration.fromCurrent(startTime);
            complete = false;
            percentComplete = new Percent(elapsedTime.getTotalMilliseconds(), minWaitTime).asBigDecimal();
        }

        final LinkedHashMap<String, Object> outputMap = new LinkedHashMap<>();
        outputMap.put("percentComplete", percentComplete);
        outputMap.put("complete", complete);

        final RestResultBean restResultBean = new RestResultBean();
        restResultBean.setData(outputMap);

        LOGGER.trace(pwmRequest, "returning result for restCheckProgress: " + JsonUtil.serialize(restResultBean));
        pwmRequest.outputJsonResult(restResultBean);
        return ProcessStatus.Halt;
    }

    @ActionHandler(action = "agree")
    private ProcessStatus handleAgree(
            final PwmRequest pwmRequest
    )
            throws ServletException, IOException, PwmUnrecoverableException, ChaiUnavailableException
    {
        LOGGER.debug(pwmRequest, "user accepted new-user agreement");

        final NewUserBean newUserBean = getNewUserBean(pwmRequest);
        newUserBean.setAgreementPassed(true);

        return ProcessStatus.Continue;
    }

    @ActionHandler(action = "reset")
    private ProcessStatus handleReset(
            final PwmRequest pwmRequest
    )
            throws ServletException, IOException, PwmUnrecoverableException, ChaiUnavailableException
    {
        pwmRequest.getPwmApplication().getSessionStateService().clearBean(pwmRequest, NewUserBean.class);
        pwmRequest.sendRedirectToContinue();

        return ProcessStatus.Continue;
    }

    @ActionHandler(action = "complete")
    private ProcessStatus handleComplete(
            final PwmRequest pwmRequest
    )
            throws ServletException, IOException, PwmUnrecoverableException, ChaiUnavailableException
    {
        final NewUserBean newUserBean = getNewUserBean(pwmRequest);
        final Date startTime = newUserBean.getCreateStartTime();
        if (startTime == null) {
            pwmRequest.respondWithError(PwmError.ERROR_INCORRECT_REQ_SEQUENCE.toInfo(), true);
            return ProcessStatus.Halt;
        }

        final NewUserProfile newUserProfile = NewUserServlet.getNewUserProfile(pwmRequest);
        final long minWaitTime = newUserProfile.readSettingAsLong(PwmSetting.NEWUSER_MINIMUM_WAIT_TIME) * 1000L;
        final Date completeTime = new Date(startTime.getTime() + minWaitTime);

        // be sure minimum wait time has passed
        if (new Date().before(completeTime)) {
            pwmRequest.forwardToJsp(JspUrl.NEW_USER_WAIT);
            return ProcessStatus.Halt;
        }

        pwmRequest.getPwmApplication().getSessionStateService().clearBean(pwmRequest, NewUserBean.class);
        pwmRequest.getPwmResponse().forwardToSuccessPage(Message.Success_CreateUser);
        return ProcessStatus.Halt;
    }


    static List<FormConfiguration> getFormDefinition(final PwmRequest pwmRequest) throws PwmUnrecoverableException {
        final NewUserProfile profile = getNewUserProfile(pwmRequest);
        return profile.readSettingAsForm(PwmSetting.NEWUSER_FORM);
    }

    public static NewUserProfile getNewUserProfile(final PwmRequest pwmRequest) throws PwmUnrecoverableException {
        final String profileID = pwmRequest.getPwmApplication().getSessionStateService().getBean(pwmRequest, NewUserBean.class).getProfileID();
        if (profileID == null) {
            throw new IllegalStateException("can not read new user profile until profile is selected");
        }
        return pwmRequest.getConfig().getNewUserProfiles().get(profileID);
    }

    private void forwardToFormPage(final PwmRequest pwmRequest, final NewUserBean newUserBean)
            throws ServletException, PwmUnrecoverableException, IOException
    {
        final List<FormConfiguration> formConfiguration = getFormDefinition(pwmRequest);
        pwmRequest.addFormInfoToRequestAttr(formConfiguration, null, false, true);

        {
            final boolean showBack = !newUserBean.isUrlSpecifiedProfile()
                    && pwmRequest.getConfig().getNewUserProfiles().keySet().size() > 1;
            pwmRequest.setAttribute(PwmRequest.Attribute.NewUser_FormShowBackButton, showBack);
        }

        pwmRequest.forwardToJsp(JspUrl.NEW_USER);
    }

}
