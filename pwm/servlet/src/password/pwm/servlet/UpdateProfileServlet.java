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

package password.pwm.servlet;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.novell.ldapchai.ChaiFactory;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiException;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiProvider;
import password.pwm.*;
import password.pwm.bean.EmailItemBean;
import password.pwm.bean.SessionStateBean;
import password.pwm.bean.servlet.UpdateProfileBean;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.ActionConfiguration;
import password.pwm.config.Configuration;
import password.pwm.config.FormConfiguration;
import password.pwm.config.PwmSetting;
import password.pwm.error.*;
import password.pwm.event.AuditEvent;
import password.pwm.i18n.Message;
import password.pwm.util.Helper;
import password.pwm.util.PwmLogger;
import password.pwm.util.ServletHelper;
import password.pwm.util.operations.ActionExecutor;
import password.pwm.util.operations.UserDataReader;
import password.pwm.util.operations.UserStatusHelper;
import password.pwm.util.stats.Statistic;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;

/**
 * User interaction servlet for updating user attributes
 *
 * @author Jason D. Rivard
 */
public class UpdateProfileServlet extends TopServlet {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(UpdateProfileServlet.class);

// -------------------------- OTHER METHODS --------------------------

    protected void processRequest(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws ServletException, ChaiUnavailableException, IOException, PwmUnrecoverableException
    {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();
        final UpdateProfileBean updateProfileBean = pwmSession.getUpdateProfileBean();

        if (!pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.UPDATE_PROFILE_ENABLE)) {
            ssBean.setSessionError(new ErrorInformation(PwmError.ERROR_SERVICE_NOT_AVAILABLE));
            ServletHelper.forwardToErrorPage(req, resp, this.getServletContext());
            return;
        }

        if (!Permission.checkPermission(Permission.PROFILE_UPDATE, pwmSession, pwmApplication)) {
            ssBean.setSessionError(new ErrorInformation(PwmError.ERROR_UNAUTHORIZED));
            ServletHelper.forwardToErrorPage(req, resp, this.getServletContext());
            return;
        }

        final String actionParam = Validator.readStringFromRequest(req, PwmConstants.PARAM_ACTION_REQUEST);
        if (actionParam != null && actionParam.length() > 0) {
            Validator.validatePwmFormID(req);
            if ("updateProfile".equalsIgnoreCase(actionParam)) {
                handleUpdateRequest(pwmApplication,pwmSession,updateProfileBean,req);
            } else if ("agree".equalsIgnoreCase(actionParam)) {         // accept password change agreement
                LOGGER.debug(pwmSession, "user accepted update profile agreement");
                updateProfileBean.setAgreementPassed(true);
            } else if ("confirm".equalsIgnoreCase(actionParam)) {       // confirm data
                LOGGER.debug(pwmSession, "user confirmed profile data");
                updateProfileBean.setConfirmationPassed(true);
            } else if ("unConfirm".equalsIgnoreCase(actionParam)) {       // go back and edit data
                LOGGER.debug(pwmSession, "user requested to 'go back' and re-edit profile data");
                handleUnconfirm(updateProfileBean);
            } else if ("validate".equalsIgnoreCase(actionParam)) {       // go back and edit data
                restValidateForm(pwmApplication, pwmSession, updateProfileBean, req, resp);
                return;
            }
        }

        advanceToNextStep(pwmApplication, pwmSession, updateProfileBean, req, resp);
    }

    protected static void restValidateForm(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final UpdateProfileBean updateProfileBean,
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException, PwmUnrecoverableException, ChaiUnavailableException
    {
        boolean success = true;
        String userMessage = Message.getLocalizedMessage(pwmSession.getSessionStateBean().getLocale(), Message.SUCCESS_UPDATE_FORM, pwmApplication.getConfig());
        final Locale userLocale = pwmSession.getSessionStateBean().getLocale();
        final Map<FormConfiguration, String> formValues = updateProfileBean.getFormData();

        try {
            // read in the responses from the request
            readFromJsonRequest(pwmApplication, pwmSession, updateProfileBean, req);

            // verify form meets the form requirements
            verifyFormAttributes(
                    formValues,
                    pwmSession,
                    pwmApplication
            );
        } catch (PwmDataValidationException e) {
            success = false;
            userMessage = e.getErrorInformation().toUserStr(pwmSession, pwmApplication);
        } catch (PwmOperationalException e) {
            success = false;
            userMessage = e.getErrorInformation().toUserStr(pwmSession, pwmApplication);
	}

        final Map<String, String> outputMap = new HashMap<String, String>();
        outputMap.put("version", "1");
        outputMap.put("message", userMessage);
        outputMap.put("success", String.valueOf(success));

        final String output = new Gson().toJson(outputMap);

        resp.setContentType("text/plain;charset=utf-8");
        resp.getWriter().print(output);

        LOGGER.trace(pwmSession, "ajax validate responses: " + output);
    }

    private void handleUnconfirm(
            final UpdateProfileBean updateProfileBean
    ) {
        updateProfileBean.setFormSubmitted(false);
        updateProfileBean.setConfirmationPassed(false);
    }

    private void advanceToNextStep(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final UpdateProfileBean updateProfileBean,
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException, PwmUnrecoverableException, ChaiUnavailableException
    {
        final String newUserAgreementText = pwmApplication.getConfig().readSettingAsLocalizedString(PwmSetting.UPDATE_PROFILE_AGREEMENT_MESSAGE, pwmSession.getSessionStateBean().getLocale());
        final Locale userLocale = pwmSession.getSessionStateBean().getLocale();
        final Map<FormConfiguration, String> formValues = updateProfileBean.getFormData();

        if (newUserAgreementText != null && newUserAgreementText.length() > 0) {
            if (!updateProfileBean.isAgreementPassed()) {
                this.forwardToAgreementJSP(req,resp);
                return;
            }
        }

        if (!updateProfileBean.isFormSubmitted()) {
            final Map<FormConfiguration,String> formMap = updateProfileBean.getFormData();
            final List<FormConfiguration> formFields = pwmApplication.getConfig().readSettingAsForm(PwmSetting.UPDATE_PROFILE_FORM);
            populateFormFromLdap(formFields, pwmSession, formMap, pwmSession.getSessionManager().getUserDataReader());
            forwardToJSP(req,resp);
            return;
        }

        //make sure there is form data in the bean.
        if (updateProfileBean.getFormData() == null) {
            forwardToJSP(req,resp);
            return;
        }

        // validate the form data.
        try {
            // verify form meets the form requirements
            verifyFormAttributes(
                    formValues,
                    pwmSession,
                    pwmApplication
            );
        } catch (PwmDataValidationException e) {
            LOGGER.error(pwmSession, e.getMessage());
            pwmSession.getSessionStateBean().setSessionError(e.getErrorInformation());
            this.forwardToJSP(req,resp);
            return;
        } catch (PwmOperationalException e) {
            LOGGER.error(pwmSession, e.getMessage());
            pwmSession.getSessionStateBean().setSessionError(e.getErrorInformation());
            this.forwardToJSP(req,resp);
            return;
        }

        final boolean requireConfirmation = pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.UPDATE_PROFILE_SHOW_CONFIRMATION);
        if (requireConfirmation && !updateProfileBean.isConfirmationPassed()) {
            this.forwardToConfirmationJSP(req,resp);
            return;
        }

        try {
            doProfileUpdate(pwmApplication, pwmSession, formValues);
            pwmSession.getSessionStateBean().setSessionSuccess(Message.SUCCESS_UPDATE_ATTRIBUTES, null);
            ServletHelper.forwardToSuccessPage(req, resp);
            return;
        } catch (PwmException e) {
            LOGGER.error(pwmSession, e.getMessage());
            pwmSession.getSessionStateBean().setSessionError(e.getErrorInformation());
        } catch (ChaiException e) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UPDATE_ATTRS_FAILURE,e.toString());
            LOGGER.error(pwmSession, errorInformation.toDebugStr());
            pwmSession.getSessionStateBean().setSessionError(errorInformation);
        }

        this.forwardToJSP(req, resp);
    }


    public static void populateFormFromLdap(
            final List<FormConfiguration> formFields,
            final PwmSession pwmSession,
            final Map<FormConfiguration, String> formMap,
            final UserDataReader userDataReader
    )
            throws PwmUnrecoverableException, ChaiUnavailableException, IOException, ServletException
    {
        final Map<String,String> userData = new LinkedHashMap<String,String>();
        try {
            userData.putAll(userDataReader.readStringAttributes(FormConfiguration.convertToListOfNames(formFields)));
        } catch (ChaiOperationException e) {
            LOGGER.error(pwmSession, "unexpected error reading profile data attributes: " + e.getMessage());
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
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final UpdateProfileBean updateProfileBean,
            final HttpServletRequest req
    )
            throws PwmUnrecoverableException, PwmDataValidationException, ChaiUnavailableException
    {
        final List<FormConfiguration> formFields = pwmApplication.getConfig().readSettingAsForm(PwmSetting.UPDATE_PROFILE_FORM);

        final Map<FormConfiguration,String> existingForm = updateProfileBean.getFormData();

        //read the values from the request
        existingForm.putAll(Validator.readFormValuesFromRequest(req, formFields, pwmSession.getSessionStateBean().getLocale()));
    }

    private static void readFromJsonRequest(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final UpdateProfileBean updateProfileBean,
            final HttpServletRequest req
    )
            throws PwmDataValidationException, PwmUnrecoverableException, IOException
    {
        final List<FormConfiguration> formFields = pwmApplication.getConfig().readSettingAsForm(PwmSetting.UPDATE_PROFILE_FORM);
        final Map<FormConfiguration,String> existingForm = updateProfileBean.getFormData();

        final String bodyString = ServletHelper.readRequestBody(req);
        final Map<String, String> clientValues = new Gson().fromJson(bodyString, new TypeToken<Map<String, String>>() {
        }.getType());

        if (clientValues != null) {
            existingForm.putAll(Validator.readFormValuesFromMap(clientValues, formFields,  pwmSession.getSessionStateBean().getLocale()));
        }
    }


    private void handleUpdateRequest(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final UpdateProfileBean updateProfileBean,
            final HttpServletRequest req
    )
            throws PwmUnrecoverableException, ChaiUnavailableException, IOException, ServletException
    {

        try {
            readFormParametersFromRequest(pwmApplication, pwmSession, updateProfileBean, req);
        } catch (PwmOperationalException e) {
            LOGGER.error(pwmSession, e.getMessage());
            pwmSession.getSessionStateBean().setSessionError(e.getErrorInformation());
        }

        updateProfileBean.setFormSubmitted(true);
    }

    public static void doProfileUpdate(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final Map<FormConfiguration, String> formValues
    )
            throws PwmUnrecoverableException, ChaiUnavailableException, IOException, ServletException, PwmOperationalException
    {
        final UserInfoBean uiBean = pwmSession.getUserInfoBean();
        final Locale userLocale = pwmSession.getSessionStateBean().getLocale();

        // verify form meets the form requirements (may be redundant, but shouldn't hurt)
        verifyFormAttributes(
                formValues,
                pwmSession,
                pwmApplication
        );

        // write values.
        LOGGER.info("updating profile for " + pwmSession.getUserInfoBean().getUserDN());

        // write the form values
        final ChaiProvider provider = pwmSession.getSessionManager().getChaiProvider();
        final ChaiUser actor = ChaiFactory.createChaiUser(pwmSession.getUserInfoBean().getUserDN(), provider);
        Helper.writeFormValuesToLdap(pwmApplication, pwmSession, actor, formValues, false);

        // re-populate the uiBean because we have changed some values.
        UserStatusHelper.populateActorUserInfoBean(pwmSession, pwmApplication, uiBean.getUserDN(), uiBean.getUserCurrentPassword());

        // clear cached read attributes.
        pwmSession.getSessionManager().clearUserDataReader();

        {  // execute configured actions
            final ChaiUser proxiedUser = ChaiFactory.createChaiUser(actor.getEntryDN(), pwmApplication.getProxyChaiProvider());
            LOGGER.debug(pwmSession, "executing configured actions to user " + proxiedUser.getEntryDN());
            final List<ActionConfiguration> configValues = pwmApplication.getConfig().readSettingAsAction(PwmSetting.UPDATE_PROFILE_WRITE_ATTRIBUTES);
            final ActionExecutor.ActionExecutorSettings settings = new ActionExecutor.ActionExecutorSettings();
            settings.setExpandPwmMacros(true);
            settings.setUserInfoBean(pwmSession.getUserInfoBean());
            settings.setUser(proxiedUser);
            final ActionExecutor actionExecutor = new ActionExecutor(pwmApplication);
            actionExecutor.executeActions(configValues, settings, pwmSession);
        }

        // send email
        sendProfileUpdateEmailNotice(new UserDataReader(pwmSession.getSessionManager().getProxiedActor()), pwmSession, pwmApplication);

        // mark the event log
        pwmApplication.getAuditManager().submitAuditRecord(AuditEvent.UPDATE_PROFILE, pwmSession.getUserInfoBean(), pwmSession);

        // mark the uiBean so we user isn't recycled to the update profile page by the CommandServlet
        uiBean.setRequiresUpdateProfile(false);

        // clear out the updateProfileBean
        pwmSession.clearUpdateProfileBean();

        // success, so forward to success page
        pwmApplication.getStatisticsManager().incrementValue(Statistic.UPDATE_ATTRIBUTES);
    }

    private static void verifyFormAttributes(
            final Map<FormConfiguration, String> formValues,
            final PwmSession pwmSession,
            final PwmApplication pwmApplication
    )
            throws PwmOperationalException, PwmUnrecoverableException, ChaiUnavailableException
    {
        final Locale userLocale = pwmSession.getSessionStateBean().getLocale();

        //read current values from user.
        final ChaiProvider provider = pwmSession.getSessionManager().getChaiProvider();

        // see if the values meet form requirements.
        Validator.validateParmValuesMeetRequirements(formValues, userLocale);

        // check unique fields against ldap
        try {
            Validator.validateAttributeUniqueness(
                    pwmApplication,
                    pwmApplication.getProxyChaiProvider(),
                    formValues,
                    userLocale,
                    pwmSession.getSessionManager(),
                    Collections.singletonList(pwmSession.getUserInfoBean().getUserDN())
            );
        } catch (ChaiOperationException e) {
            final String userMessage = "unexpected ldap error checking attributes value uniqueness: " + e.getMessage();
            throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_UPDATE_ATTRS_FAILURE,userMessage));
        }
    }

    private void forwardToJSP(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException, PwmUnrecoverableException, ChaiUnavailableException
    {
        this.getServletContext().getRequestDispatcher('/' + PwmConstants.URL_JSP_UPDATE_ATTRIBUTES).forward(req, resp);
    }

    private void forwardToAgreementJSP(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException {
        this.getServletContext().getRequestDispatcher('/' + PwmConstants.URL_JSP_UPDATE_ATTRIBUTES_AGREEMENT).forward(req, resp);
    }

    private void forwardToConfirmationJSP(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException
    {
        this.getServletContext().getRequestDispatcher('/' + PwmConstants.URL_JSP_UPDATE_ATTRIBUTES_CONFIRM).forward(req, resp);
    }

    private static void sendProfileUpdateEmailNotice(
            final UserDataReader userDataReader,
            final PwmSession pwmSession,
            final PwmApplication pwmApplication
    )
            throws PwmUnrecoverableException, ChaiUnavailableException {
        final Configuration config = pwmApplication.getConfig();
        final Locale locale = pwmSession.getSessionStateBean().getLocale();
        final EmailItemBean configuredEmailSetting = config.readSettingAsEmail(PwmSetting.EMAIL_UPDATEPROFILE, locale);

        final String toAddress = pwmSession.getUserInfoBean().getUserEmailAddress();
        if (toAddress == null || toAddress.length() < 1) {
            LOGGER.debug(pwmSession, "unable to send profile update email for '" + pwmSession.getUserInfoBean().getUserDN() + "' no ' user email address available");
            return;
        }

        pwmApplication.sendEmailUsingQueue(new EmailItemBean(
                toAddress,
                configuredEmailSetting.getFrom(),
                configuredEmailSetting.getSubject(),
                configuredEmailSetting.getBodyPlain(),
                configuredEmailSetting.getBodyHtml()
        ), pwmSession.getUserInfoBean(),userDataReader);
    }

}

