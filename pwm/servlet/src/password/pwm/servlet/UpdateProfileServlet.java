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
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiProvider;
import password.pwm.*;
import password.pwm.bean.EmailItemBean;
import password.pwm.bean.SessionStateBean;
import password.pwm.bean.UpdateProfileBean;
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
                handleUpdateRequest(req);
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
                handleValidateForm(req,resp);
                return;
            }
        }

        advanceToNextStep(pwmApplication, pwmSession, updateProfileBean, req, resp);
    }

    protected static void handleValidateForm(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException, PwmUnrecoverableException, ChaiUnavailableException
    {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);

        boolean success = true;
        String userMessage = Message.getLocalizedMessage(pwmSession.getSessionStateBean().getLocale(), Message.SUCCESS_UPDATE_FORM, pwmApplication.getConfig());

        try {
            // read in the responses from the request
            final Map<FormConfiguration, String> formMap = readFromJsonRequest(req, pwmApplication, pwmSession);

            // see if the values meet requirements.
            Validator.validateParmValuesMeetRequirements(formMap, pwmSession.getSessionStateBean().getLocale());

        } catch (PwmDataValidationException e) {
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
        if (newUserAgreementText != null && newUserAgreementText.length() > 0) {
            if (!updateProfileBean.isAgreementPassed()) {
                this.forwardToAgreementJSP(req,resp);
                return;
            }
        }

        if (!updateProfileBean.isFormSubmitted()) {
            populateFormFromLdap(req);
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
            Validator.validateParmValuesMeetRequirements(updateProfileBean.getFormData(), pwmSession.getSessionStateBean().getLocale());
        } catch (PwmDataValidationException e) {
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
            doProfileUpdate(pwmApplication, pwmSession, updateProfileBean, req, resp);
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


    private void populateFormFromLdap(final HttpServletRequest req)
            throws PwmUnrecoverableException, ChaiUnavailableException, IOException, ServletException
    {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);

        final List<FormConfiguration> formFields = pwmApplication.getConfig().readSettingAsForm(PwmSetting.UPDATE_PROFILE_FORM);
        final Properties formProps = pwmSession.getSessionStateBean().getLastParameterValues();
        final Map<String,String> currentUserAttributes = pwmSession.getUserInfoBean().getAllUserAttributes();

        for (final FormConfiguration formItem : formFields) {
            final String attrName = formItem.getName();
            if (!formProps.containsKey(attrName)) {
                final String userCurrentValue = currentUserAttributes.get(attrName);
                if (userCurrentValue != null) {
                    formProps.setProperty(attrName, userCurrentValue);
                }
            }
        }
    }

    private Map<FormConfiguration,String> readFormParametersFromRequest(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final HttpServletRequest req
    )
            throws PwmUnrecoverableException, PwmDataValidationException, ChaiUnavailableException
    {
        final List<FormConfiguration> formFields = pwmApplication.getConfig().readSettingAsForm(PwmSetting.UPDATE_PROFILE_FORM);

        // remove read-only fields
        for (Iterator<FormConfiguration> iterator = formFields.iterator(); iterator.hasNext(); ) {
            FormConfiguration loopFormConfig = iterator.next();
            if (loopFormConfig.isReadonly()) {
                iterator.remove();
            }
        }

        //read the values from the request
        return Validator.readFormValuesFromRequest(req, formFields, pwmSession.getSessionStateBean().getLocale());
    }

    private static Map<FormConfiguration, String> readFromJsonRequest(
            final HttpServletRequest req,
            final PwmApplication pwmApplication,
            final PwmSession pwmSession
    )
            throws PwmDataValidationException, PwmUnrecoverableException, IOException
    {
        final List<FormConfiguration> formFields = pwmApplication.getConfig().readSettingAsForm(PwmSetting.UPDATE_PROFILE_FORM);

        // remove read-only fields
        for (Iterator<FormConfiguration> iterator = formFields.iterator(); iterator.hasNext(); ) {
            FormConfiguration loopFormConfig = iterator.next();
            if (loopFormConfig.isReadonly()) {
                iterator.remove();
            }
        }

        final String bodyString = ServletHelper.readRequestBody(req);
        final Map<String, String> clientValues = new Gson().fromJson(bodyString, new TypeToken<Map<String, String>>() {
        }.getType());
        if (clientValues == null) {
            return null;
        }

        return Validator.readFormValuesFromMap(clientValues, formFields,  pwmSession.getSessionStateBean().getLocale());
    }


    private void handleUpdateRequest(final HttpServletRequest req)
            throws PwmUnrecoverableException, ChaiUnavailableException, IOException, ServletException
    {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
        final UpdateProfileBean updateProfileBean = pwmSession.getUpdateProfileBean();
        updateProfileBean.setFormData(null);

        try {
            final Map<FormConfiguration,String> formData = readFormParametersFromRequest(pwmApplication, pwmSession, req);
            updateProfileBean.setFormData(formData);
        } catch (PwmOperationalException e) {
            LOGGER.error(pwmSession, e.getMessage());
            pwmSession.getSessionStateBean().setSessionError(e.getErrorInformation());
        }

        updateProfileBean.setFormSubmitted(true);
    }

    private void doProfileUpdate(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final UpdateProfileBean updateProfileBean,
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws PwmUnrecoverableException, ChaiUnavailableException, IOException, ServletException, PwmOperationalException
    {
        final UserInfoBean uiBean = pwmSession.getUserInfoBean();
        final Map<FormConfiguration, String> formValues = updateProfileBean.getFormData();

        // write values.
        LOGGER.info("updating profile for " + pwmSession.getUserInfoBean().getUserDN());

        // write the form values
        final ChaiProvider provider = pwmSession.getSessionManager().getChaiProvider();
        final ChaiUser actor = ChaiFactory.createChaiUser(pwmSession.getUserInfoBean().getUserDN(), provider);
        Helper.writeFormValuesToLdap(pwmApplication, pwmSession, actor, formValues, false);

        // re-populate the uiBean because we have changed some values.
        UserStatusHelper.populateActorUserInfoBean(pwmSession, pwmApplication, uiBean.getUserDN(), uiBean.getUserCurrentPassword());

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
        sendProfileUpdateEmailNotice(pwmSession, pwmApplication);

        // mark the event log
        pwmApplication.getAuditManager().submitAuditRecord(AuditEvent.UPDATE_PROFILE, pwmSession.getUserInfoBean(), pwmSession);

        // mark the uiBean so we user isn't recycled to the update profile page by the CommandServlet
        uiBean.setRequiresUpdateProfile(false);

        // clear out the updateProfileBean
        pwmSession.clearUpdateProfileBean();

        // success, so forward to success page
        pwmApplication.getStatisticsManager().incrementValue(Statistic.UPDATE_ATTRIBUTES);
        pwmSession.getSessionStateBean().setSessionSuccess(Message.SUCCESS_UPDATE_ATTRIBUTES, null);
        ServletHelper.forwardToSuccessPage(req, resp);
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

    private static void sendProfileUpdateEmailNotice(final PwmSession pwmSession, final PwmApplication pwmApplication)
            throws PwmUnrecoverableException
    {
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
        ), pwmSession.getUserInfoBean());
    }

}

