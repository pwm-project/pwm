/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2014 The PWM Project
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

import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.*;
import password.pwm.bean.*;
import password.pwm.config.Configuration;
import password.pwm.config.FormConfiguration;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.MessageSendMethod;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.i18n.Message;
import password.pwm.ldap.LdapUserDataReader;
import password.pwm.ldap.UserDataReader;
import password.pwm.ldap.UserSearchEngine;
import password.pwm.ldap.UserStatusReader;
import password.pwm.util.PwmLogger;
import password.pwm.util.ServletHelper;
import password.pwm.util.stats.Statistic;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;

public class ForgottenUsernameServlet extends TopServlet {
    private static final PwmLogger LOGGER = PwmLogger.getLogger(ForgottenUsernameServlet.class);

// -------------------------- OTHER METHODS --------------------------

    protected void processRequest(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws ServletException, ChaiUnavailableException, IOException, PwmUnrecoverableException {
        final Configuration config = ContextManager.getPwmApplication(req).getConfig();

        final String actionParam = Validator.readStringFromRequest(req, PwmConstants.PARAM_ACTION_REQUEST);

        if (!config.readSettingAsBoolean(PwmSetting.FORGOTTEN_USERNAME_ENABLE)) {
            PwmSession.getPwmSession(req).getSessionStateBean().setSessionError(PwmError.ERROR_SERVICE_NOT_AVAILABLE.toInfo());
            ServletHelper.forwardToErrorPage(req, resp, this.getServletContext());
            return;
        }

        if (actionParam != null && actionParam.equalsIgnoreCase("search")) {
            handleSearchRequest(req, resp);
            return;
        }

        forwardToJSP(req, resp);
    }

    public void handleSearchRequest(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws PwmUnrecoverableException, ChaiUnavailableException, IOException, ServletException
    {
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();

        Validator.validatePwmFormID(req);

        final String contextParam = Validator.readStringFromRequest(req, PwmConstants.PARAM_CONTEXT);
        final String ldapProfile = Validator.readStringFromRequest(req, PwmConstants.PARAM_LDAP_PROFILE);

        final List<FormConfiguration> forgottenUsernameForm = pwmApplication.getConfig().readSettingAsForm(PwmSetting.FORGOTTEN_USERNAME_FORM);

        //read the values from the request
        Map<FormConfiguration, String> formValues = new HashMap();
        try {
            formValues = Validator.readFormValuesFromRequest(req, forgottenUsernameForm, ssBean.getLocale());

            // check for intruder search
            pwmApplication.getIntruderManager().convenience().checkAttributes(formValues);

            // see if the values meet the configured form requirements.
            Validator.validateParmValuesMeetRequirements(formValues, ssBean.getLocale());

            final UserSearchEngine userSearchEngine = new UserSearchEngine(pwmApplication);
            final UserSearchEngine.SearchConfiguration searchConfiguration = new UserSearchEngine.SearchConfiguration();
            searchConfiguration.setFilter(pwmApplication.getConfig().readSettingAsString(PwmSetting.FORGOTTEN_USERNAME_SEARCH_FILTER));
            searchConfiguration.setFormValues(formValues);
            searchConfiguration.setLdapProfile(ldapProfile);
            searchConfiguration.setContexts(Collections.singletonList(contextParam));
            final UserIdentity userIdentity = userSearchEngine.performSingleUserSearch(pwmSession, searchConfiguration);

            if (userIdentity == null) {
                ssBean.setSessionError(new ErrorInformation(PwmError.ERROR_CANT_MATCH_USER));
                pwmApplication.getIntruderManager().convenience().markAddressAndSession(pwmSession);
                pwmApplication.getStatisticsManager().incrementValue(Statistic.FORGOTTEN_USERNAME_FAILURES);
                forwardToJSP(req, resp);
                return;
            }

            // make sure the user isn't locked.
            pwmApplication.getIntruderManager().convenience().checkUserIdentity(userIdentity);

            // send username
            sendUsername(pwmApplication, pwmSession, userIdentity);

            // redirect user to success page.
            LOGGER.info(pwmSession, "found user " + userIdentity.getUserDN());
            try {
                final String usernameAttribute = pwmApplication.getConfig().readSettingAsString(PwmSetting.FORGOTTEN_USERNAME_USERNAME_ATTRIBUTE);
                final UserDataReader userDataReader = LdapUserDataReader.appProxiedReader(pwmApplication, userIdentity);
                final String username = userDataReader.readStringAttribute(usernameAttribute);
                LOGGER.trace(pwmSession, "read username attribute '" + usernameAttribute + "' value=" + username);
                ssBean.setSessionSuccess(Message.SUCCESS_FORGOTTEN_USERNAME, username);

                pwmApplication.getIntruderManager().convenience().clearAddressAndSession(pwmSession);
                pwmApplication.getIntruderManager().convenience().clearAttributes(formValues);

                pwmApplication.getStatisticsManager().incrementValue(Statistic.FORGOTTEN_USERNAME_SUCCESSES);
                ServletHelper.forwardToSuccessPage(req, resp);
                return;
            } catch (Exception e) {
                LOGGER.error("error reading username value for " + userIdentity + ", " + e.getMessage());
            }

        } catch (PwmOperationalException e) {
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_CANT_MATCH_USER, e.getErrorInformation().getDetailedErrorMsg(), e.getErrorInformation().getFieldValues());
            ssBean.setSessionError(errorInfo);
            pwmApplication.getIntruderManager().convenience().markAddressAndSession(pwmSession);
            pwmApplication.getIntruderManager().convenience().markAttributes(formValues, pwmSession);
        }

        pwmApplication.getStatisticsManager().incrementValue(Statistic.FORGOTTEN_USERNAME_FAILURES);
        forwardToJSP(req, resp);
    }

    private void forwardToJSP(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException {
        this.getServletContext().getRequestDispatcher('/' + PwmConstants.URL_JSP_FORGOTTEN_USERNAME).forward(req, resp);
    }



    private void sendUsername(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final UserIdentity userIdentity
    )
            throws PwmOperationalException, ChaiUnavailableException, PwmUnrecoverableException
    {
        final Locale userLocale = pwmSession.getSessionStateBean().getLocale();
        final Configuration configuration = pwmApplication.getConfig();
        final MessageSendMethod messageSendMethod = configuration.readSettingAsEnum(PwmSetting.FORGOTTEN_USERNAME_SEND_USERNAME_METHOD, MessageSendMethod.class);
        final EmailItemBean emailItemBean = configuration.readSettingAsEmail(PwmSetting.EMAIL_SEND_USERNAME, userLocale);
        final String smsMessage = configuration.readSettingAsLocalizedString(PwmSetting.SMS_FORGOTTEN_USERNAME_TEXT, userLocale);

        if (messageSendMethod == null || messageSendMethod == MessageSendMethod.NONE) {
            return;
        }

        final UserInfoBean forgottenUserInfo = new UserInfoBean();
        final UserStatusReader userStatusReader = new UserStatusReader(pwmApplication);
        userStatusReader.populateUserInfoBean(pwmSession, forgottenUserInfo, userLocale, userIdentity, null);

        sendMessageViaMethod(
                pwmApplication,
                forgottenUserInfo,
                messageSendMethod,
                emailItemBean,
                smsMessage
        );
    }


    private static void sendMessageViaMethod(
            final PwmApplication pwmApplication,
            final UserInfoBean userInfoBean,
            final MessageSendMethod messageSendMethod,
            final EmailItemBean emailItemBean,
            final String smsMessage
    )
            throws PwmOperationalException
    {
        if (pwmApplication == null) {
            throw new IllegalArgumentException("pwmApplication can not be null");
        }

        if (userInfoBean == null) {
            throw new IllegalArgumentException("userInfoBean can not be null");
        }

        ErrorInformation error = null;
        switch (messageSendMethod) {
            case NONE:
                break;

            case BOTH:
                // Send both email and SMS, success if one of both succeeds
                final ErrorInformation err1 = sendEmailViaMethod(pwmApplication, userInfoBean, emailItemBean);
                final ErrorInformation err2 = sendSmsViaMethod(pwmApplication, userInfoBean, smsMessage);
                if (err1 != null) {
                    error = err1;
                } else if (err2 != null) {
                    error = err2;
                }
                break;
            case EMAILFIRST:
                // Send email first, try SMS if email is not available
                error = sendEmailViaMethod(pwmApplication, userInfoBean, emailItemBean);
                if (error != null) {
                    error = sendSmsViaMethod(pwmApplication, userInfoBean, smsMessage);
                }
                break;
            case SMSFIRST:
                // Send SMS first, try email if SMS is not available
                error = sendSmsViaMethod(pwmApplication, userInfoBean, smsMessage);
                if (error != null) {
                    error = sendEmailViaMethod(pwmApplication, userInfoBean, emailItemBean);
                }
                break;
            case SMSONLY:
                // Only try SMS
                error = sendSmsViaMethod(pwmApplication, userInfoBean, smsMessage);
                break;
            case EMAILONLY:
            default:
                // Only try email
                error = sendEmailViaMethod(pwmApplication, userInfoBean, emailItemBean);
                break;
        }
        if (error != null) {
            throw new PwmOperationalException(error);
        }
    }

    private static ErrorInformation sendSmsViaMethod(
            final PwmApplication pwmApplication,
            final UserInfoBean userInfoBean,
            final String smsMessage
    )
            throws PwmOperationalException
    {
        final Configuration config = pwmApplication.getConfig();
        String senderId = config.readSettingAsString(PwmSetting.SMS_SENDER_ID);
        if (senderId == null) { senderId = ""; }

        final String toNumber = userInfoBean.getUserSmsNumber();
        if (toNumber == null || toNumber.length() < 1) {
            final String errorMsg = String.format("unable to send new password email for '%s'; no SMS number available in ldap", userInfoBean.getUserIdentity());
            return new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
        }

        final UserDataReader userDataReader;
        try {
            userDataReader = LdapUserDataReader.appProxiedReader(pwmApplication, userInfoBean.getUserIdentity());
        } catch (ChaiUnavailableException e) {
            return new ErrorInformation(PwmError.forChaiError(e.getErrorCode()));
        } catch (PwmUnrecoverableException e) {
            return e.getErrorInformation();
        }



        final Integer maxlen = ((Long) config.readSettingAsLong(PwmSetting.SMS_MAX_TEXT_LENGTH)).intValue();
        pwmApplication.sendSmsUsingQueue(new SmsItemBean(toNumber, senderId, smsMessage, maxlen), userInfoBean, userDataReader);
        pwmApplication.getStatisticsManager().incrementValue(Statistic.RECOVERY_TOKENS_SENT);

        return null;
    }

    private static ErrorInformation sendEmailViaMethod(
            final PwmApplication pwmApplication,
            final UserInfoBean userInfoBean,
            final EmailItemBean emailItemBean
    )
    {
        if (emailItemBean == null) {
            final String errorMsg = "emailItemBean is null";
            return new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
        }

        final UserDataReader userDataReader;
        try {
            userDataReader = LdapUserDataReader.appProxiedReader(pwmApplication, userInfoBean.getUserIdentity());
        } catch (ChaiUnavailableException e) {
            return new ErrorInformation(PwmError.forChaiError(e.getErrorCode()));
        } catch (PwmUnrecoverableException e) {
            return e.getErrorInformation();
        }

        pwmApplication.getEmailQueue().submit(emailItemBean, userInfoBean, userDataReader);
        pwmApplication.getStatisticsManager().incrementValue(Statistic.RECOVERY_TOKENS_SENT);

        return null;
    }

}
