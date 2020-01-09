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

package password.pwm.http.servlet;

import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.EmailItemBean;
import password.pwm.bean.LocalSessionStateBean;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.MessageSendMethod;
import password.pwm.config.value.data.FormConfiguration;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpMethod;
import password.pwm.http.JspUrl;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmRequestAttribute;
import password.pwm.http.PwmSession;
import password.pwm.ldap.UserInfo;
import password.pwm.ldap.UserInfoFactory;
import password.pwm.ldap.search.SearchConfiguration;
import password.pwm.ldap.search.UserSearchEngine;
import password.pwm.svc.stats.Statistic;
import password.pwm.util.CaptchaUtility;
import password.pwm.util.form.FormUtility;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@WebServlet(
        name = "ForgottenUsernameServlet",
        urlPatterns = {
                PwmConstants.URL_PREFIX_PUBLIC + "/forgottenusername",
                PwmConstants.URL_PREFIX_PUBLIC + "/ForgottenUsername",

        }
)
public class ForgottenUsernameServlet extends AbstractPwmServlet
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( ForgottenUsernameServlet.class );

    public enum ForgottenUsernameAction implements AbstractPwmServlet.ProcessAction
    {
        search,;

        public Collection<HttpMethod> permittedMethods( )
        {
            return Collections.singletonList( HttpMethod.POST );
        }
    }

    protected ForgottenUsernameAction readProcessAction( final PwmRequest request )
            throws PwmUnrecoverableException
    {
        try
        {
            return ForgottenUsernameAction.valueOf( request.readParameterAsString( PwmConstants.PARAM_ACTION_REQUEST ) );
        }
        catch ( final IllegalArgumentException e )
        {
            return null;
        }
    }

    protected void processAction( final PwmRequest pwmRequest )
            throws ServletException, IOException, PwmUnrecoverableException
    {
        final Configuration config = pwmRequest.getConfig();

        if ( !config.readSettingAsBoolean( PwmSetting.FORGOTTEN_USERNAME_ENABLE ) )
        {
            pwmRequest.respondWithError( PwmError.ERROR_SERVICE_NOT_AVAILABLE.toInfo() );
            return;
        }

        final ForgottenUsernameAction action = readProcessAction( pwmRequest );

        if ( action != null )
        {
            pwmRequest.validatePwmFormID();
            switch ( action )
            {
                case search:
                    handleSearchRequest( pwmRequest );
                    return;

                default:
                    JavaHelper.unhandledSwitchStatement( action );
            }
        }

        forwardToFormJsp( pwmRequest );
    }

    public void handleSearchRequest(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException, IOException, ServletException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final LocalSessionStateBean ssBean = pwmSession.getSessionStateBean();

        if ( CaptchaUtility.captchaEnabledForRequest( pwmRequest ) )
        {
            if ( !CaptchaUtility.verifyReCaptcha( pwmRequest ) )
            {
                final ErrorInformation errorInfo = new ErrorInformation( PwmError.ERROR_BAD_CAPTCHA_RESPONSE );
                LOGGER.debug( pwmRequest, errorInfo );
                setLastError( pwmRequest, errorInfo );
                forwardToFormJsp( pwmRequest );
                return;
            }
        }

        final String contextParam = pwmRequest.readParameterAsString( PwmConstants.PARAM_CONTEXT );
        final String ldapProfile = pwmRequest.readParameterAsString( PwmConstants.PARAM_LDAP_PROFILE );

        final List<FormConfiguration> forgottenUsernameForm = pwmApplication.getConfig().readSettingAsForm( PwmSetting.FORGOTTEN_USERNAME_FORM );

        //read the values from the request
        Map<FormConfiguration, String> formValues = new HashMap<>();
        try
        {
            formValues = FormUtility.readFormValuesFromRequest( pwmRequest,
                    forgottenUsernameForm, ssBean.getLocale() );

            // check for intruder search
            pwmApplication.getIntruderManager().convenience().checkAttributes( formValues );

            // see if the values meet the configured form requirements.
            FormUtility.validateFormValues( pwmRequest.getConfig(), formValues, ssBean.getLocale() );

            final String searchFilter;
            {
                final String configuredSearchFilter = pwmApplication.getConfig().readSettingAsString( PwmSetting.FORGOTTEN_USERNAME_SEARCH_FILTER );
                if ( configuredSearchFilter == null || configuredSearchFilter.isEmpty() )
                {
                    searchFilter = FormUtility.ldapSearchFilterForForm( pwmApplication, forgottenUsernameForm );
                    LOGGER.trace( pwmRequest, () -> "auto generated ldap search filter: " + searchFilter );
                }
                else
                {
                    searchFilter = configuredSearchFilter;
                }
            }

            final UserIdentity userIdentity;
            {
                final UserSearchEngine userSearchEngine = pwmApplication.getUserSearchEngine();
                final SearchConfiguration searchConfiguration = SearchConfiguration.builder()
                        .filter( searchFilter )
                        .formValues( formValues )
                        .ldapProfile( ldapProfile )
                        .contexts( Collections.singletonList( contextParam ) )
                        .build();
                userIdentity = userSearchEngine.performSingleUserSearch( searchConfiguration, pwmRequest.getLabel() );
            }

            if ( userIdentity == null )
            {
                pwmApplication.getIntruderManager().convenience().markAddressAndSession( pwmRequest );
                pwmApplication.getStatisticsManager().incrementValue( Statistic.FORGOTTEN_USERNAME_FAILURES );
                setLastError( pwmRequest, PwmError.ERROR_CANT_MATCH_USER.toInfo() );
                forwardToFormJsp( pwmRequest );
                return;
            }

            // make sure the user isn't locked.
            pwmApplication.getIntruderManager().convenience().checkUserIdentity( userIdentity );

            final UserInfo forgottenUserInfo = UserInfoFactory.newUserInfoUsingProxy(
                    pwmApplication,
                    pwmRequest.getLabel(),
                    userIdentity, pwmRequest.getLocale()
            );

            // send username
            sendUsername( pwmApplication, pwmRequest, forgottenUserInfo );

            pwmApplication.getIntruderManager().convenience().clearAddressAndSession( pwmSession );
            pwmApplication.getIntruderManager().convenience().clearAttributes( formValues );

            pwmApplication.getStatisticsManager().incrementValue( Statistic.FORGOTTEN_USERNAME_SUCCESSES );

            // redirect user to success page.
            forwardToCompletePage( pwmRequest, userIdentity );
            return;

        }
        catch ( final PwmOperationalException e )
        {
            final ErrorInformation errorInfo;
            errorInfo = e.getError() == PwmError.ERROR_INTERNAL
                    ? new ErrorInformation( PwmError.ERROR_CANT_MATCH_USER, e.getErrorInformation().getDetailedErrorMsg(),
                    e.getErrorInformation().getFieldValues() )
                    : e.getErrorInformation();
            setLastError( pwmRequest, errorInfo );
            pwmApplication.getIntruderManager().convenience().markAddressAndSession( pwmRequest );
            pwmApplication.getIntruderManager().convenience().markAttributes( formValues, pwmRequest.getLabel() );
        }

        pwmApplication.getStatisticsManager().incrementValue( Statistic.FORGOTTEN_USERNAME_FAILURES );
        forwardToFormJsp( pwmRequest );
    }


    private void sendUsername(
            final PwmApplication pwmApplication,
            final PwmRequest pwmRequest,
            final UserInfo forgottenUserInfo
    )
            throws PwmOperationalException, PwmUnrecoverableException
    {
        final Locale userLocale = pwmRequest.getLocale();
        final Configuration configuration = pwmApplication.getConfig();
        final MessageSendMethod messageSendMethod = configuration.readSettingAsEnum( PwmSetting.FORGOTTEN_USERNAME_SEND_USERNAME_METHOD, MessageSendMethod.class );
        final EmailItemBean emailItemBean = configuration.readSettingAsEmail( PwmSetting.EMAIL_SEND_USERNAME, userLocale );
        final String smsMessage = configuration.readSettingAsLocalizedString( PwmSetting.SMS_FORGOTTEN_USERNAME_TEXT, userLocale );

        if ( messageSendMethod == null || messageSendMethod == MessageSendMethod.NONE )
        {
            return;
        }

        sendMessageViaMethod(
                pwmApplication,
                pwmRequest.getLabel(),
                forgottenUserInfo,
                messageSendMethod,
                emailItemBean,
                smsMessage
        );
    }


    private static void sendMessageViaMethod(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final UserInfo userInfo,
            final MessageSendMethod messageSendMethod,
            final EmailItemBean emailItemBean,
            final String smsMessage
    )
            throws PwmOperationalException, PwmUnrecoverableException
    {
        if ( pwmApplication == null )
        {
            throw new IllegalArgumentException( "pwmApplication can not be null" );
        }

        if ( userInfo == null )
        {
            throw new IllegalArgumentException( "userInfoBean can not be null" );
        }

        ErrorInformation error = null;
        switch ( messageSendMethod )
        {
            case NONE:
                break;

            case SMSONLY:
                // Only try SMS
                error = sendSmsViaMethod( pwmApplication, sessionLabel, userInfo, smsMessage );
                break;
            case EMAILONLY:
            default:
                // Only try email
                error = sendEmailViaMethod( pwmApplication, sessionLabel, userInfo, emailItemBean );
                break;
        }
        if ( error != null )
        {
            throw new PwmOperationalException( error );
        }
    }

    private static ErrorInformation sendSmsViaMethod(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final UserInfo userInfo,
            final String smsMessage
    )
            throws PwmOperationalException, PwmUnrecoverableException
    {
        final String toNumber = userInfo.getUserSmsNumber();
        if ( toNumber == null || toNumber.length() < 1 )
        {
            final String errorMsg = String.format( "unable to send new password email for '%s'; no SMS number available in ldap", userInfo.getUserIdentity() );
            return new ErrorInformation( PwmError.ERROR_INTERNAL, errorMsg );
        }

        final MacroMachine macroMachine = MacroMachine.forUser( pwmApplication, sessionLabel, userInfo, null );

        pwmApplication.sendSmsUsingQueue( toNumber, smsMessage, sessionLabel, macroMachine );
        return null;
    }

    private static ErrorInformation sendEmailViaMethod(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final UserInfo userInfo,
            final EmailItemBean emailItemBean
    )
            throws PwmUnrecoverableException
    {
        if ( emailItemBean == null )
        {
            final String errorMsg = "emailItemBean is null";
            return new ErrorInformation( PwmError.ERROR_INTERNAL, errorMsg );
        }

        final MacroMachine macroMachine = MacroMachine.forUser( pwmApplication, sessionLabel, userInfo, null );

        pwmApplication.getEmailQueue().submitEmail( emailItemBean, userInfo, macroMachine );

        return null;
    }

    private void forwardToFormJsp( final PwmRequest pwmRequest )
            throws ServletException, PwmUnrecoverableException, IOException
    {
        pwmRequest.addFormInfoToRequestAttr( PwmSetting.FORGOTTEN_USERNAME_FORM, false, false );
        pwmRequest.forwardToJsp( JspUrl.FORGOTTEN_USERNAME );
    }

    private static void forwardToCompletePage( final PwmRequest pwmRequest, final UserIdentity userIdentity )
            throws PwmUnrecoverableException, ServletException, IOException
    {
        final Locale locale = pwmRequest.getLocale();
        final String completeMessage = pwmRequest.getConfig().readSettingAsLocalizedString( PwmSetting.FORGOTTEN_USERNAME_MESSAGE, locale );
        final MacroMachine macroMachine = MacroMachine.forUser( pwmRequest.getPwmApplication(), pwmRequest.getLocale(), pwmRequest.getLabel(), userIdentity );
        final String expandedText = macroMachine.expandMacros( completeMessage );
        pwmRequest.setAttribute( PwmRequestAttribute.CompleteText, expandedText );
        pwmRequest.forwardToJsp( JspUrl.FORGOTTEN_USERNAME_COMPLETE );
    }

}
