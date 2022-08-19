/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2021 The PWM Project
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

import password.pwm.PwmConstants;
import password.pwm.PwmDomain;
import password.pwm.bean.EmailItemBean;
import password.pwm.bean.LocalSessionStateBean;
import password.pwm.bean.ProfileID;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.DomainConfig;
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
import password.pwm.ldap.UserInfoFactory;
import password.pwm.ldap.search.SearchConfiguration;
import password.pwm.ldap.search.UserSearchService;
import password.pwm.svc.intruder.IntruderServiceClient;
import password.pwm.svc.sms.SmsQueueService;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsClient;
import password.pwm.user.UserInfo;
import password.pwm.util.CaptchaUtility;
import password.pwm.util.form.FormUtility;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.MiscUtil;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroRequest;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

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

        @Override
        public Collection<HttpMethod> permittedMethods( )
        {
            return Collections.singletonList( HttpMethod.POST );
        }
    }

    @Override
    protected Optional<ForgottenUsernameAction> readProcessAction( final PwmRequest request )
            throws PwmUnrecoverableException
    {
        return JavaHelper.readEnumFromString( ForgottenUsernameAction.class, request.readParameterAsString( PwmConstants.PARAM_ACTION_REQUEST ) );
    }

    @Override
    protected void processAction( final PwmRequest pwmRequest )
            throws ServletException, IOException, PwmUnrecoverableException
    {
        final DomainConfig config = pwmRequest.getDomainConfig();

        if ( !config.readSettingAsBoolean( PwmSetting.FORGOTTEN_USERNAME_ENABLE ) )
        {
            pwmRequest.respondWithError( PwmError.ERROR_SERVICE_NOT_AVAILABLE.toInfo() );
            return;
        }

        final Optional<ForgottenUsernameAction> action = readProcessAction( pwmRequest );

        if ( action.isPresent() )
        {
            pwmRequest.validatePwmFormID();
            switch ( action.get() )
            {
                case search:
                    handleSearchRequest( pwmRequest );
                    return;

                default:
                    MiscUtil.unhandledSwitchStatement( action.get() );
            }
        }

        forwardToFormJsp( pwmRequest );
    }

    public void handleSearchRequest(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException, IOException, ServletException
    {
        final PwmDomain pwmDomain = pwmRequest.getPwmDomain();
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
        final Optional<ProfileID> ldapProfile = pwmDomain.getConfig()
                .ldapProfileForStringId( pwmRequest.readParameterAsString( PwmConstants.PARAM_LDAP_PROFILE ) );

        final List<FormConfiguration> forgottenUsernameForm = pwmDomain.getConfig().readSettingAsForm( PwmSetting.FORGOTTEN_USERNAME_FORM );

        //read the values from the request
        Map<FormConfiguration, String> formValues = new HashMap<>();
        try
        {
            formValues = FormUtility.readFormValuesFromRequest( pwmRequest,
                    forgottenUsernameForm, ssBean.getLocale() );

            // check for intruder search
            IntruderServiceClient.checkAttributes( pwmDomain, formValues );

            // see if the values meet the configured form requirements.
            FormUtility.validateFormValues( pwmRequest.getDomainConfig(), formValues, ssBean.getLocale() );

            final String searchFilter;
            {
                final String configuredSearchFilter = pwmDomain.getConfig().readSettingAsString( PwmSetting.FORGOTTEN_USERNAME_SEARCH_FILTER );
                if ( configuredSearchFilter == null || configuredSearchFilter.isEmpty() )
                {
                    searchFilter = FormUtility.ldapSearchFilterForForm( pwmDomain, forgottenUsernameForm );
                    LOGGER.trace( pwmRequest, () -> "auto generated ldap search filter: " + searchFilter );
                }
                else
                {
                    searchFilter = configuredSearchFilter;
                }
            }

            final UserIdentity userIdentity;
            {
                final UserSearchService userSearchService = pwmDomain.getUserSearchEngine();
                final SearchConfiguration searchConfiguration = SearchConfiguration.builder()
                        .filter( searchFilter )
                        .formValues( formValues )
                        .ldapProfile( ldapProfile.orElse( null ) )
                        .contexts( Collections.singletonList( contextParam ) )
                        .build();
                userIdentity = userSearchService.performSingleUserSearch( searchConfiguration, pwmRequest.getLabel() );
            }

            if ( userIdentity == null )
            {
                IntruderServiceClient.markAddressAndSession( pwmRequest );
                StatisticsClient.incrementStat( pwmRequest, Statistic.FORGOTTEN_USERNAME_FAILURES );
                setLastError( pwmRequest, PwmError.ERROR_CANT_MATCH_USER.toInfo() );
                forwardToFormJsp( pwmRequest );
                return;
            }

            // make sure the user isn't locked.
            IntruderServiceClient.checkUserIdentity( pwmDomain, userIdentity );

            final UserInfo forgottenUserInfo = UserInfoFactory.newUserInfoUsingProxy(
                    pwmRequest.getPwmApplication(),
                    pwmRequest.getLabel(),
                    userIdentity, pwmRequest.getLocale()
            );

            // send username
            sendUsername( pwmDomain, pwmRequest, forgottenUserInfo );

            IntruderServiceClient.clearAddressAndSession( pwmDomain, pwmSession );
            IntruderServiceClient.clearAttributes( pwmDomain, formValues );

            StatisticsClient.incrementStat( pwmRequest, Statistic.FORGOTTEN_USERNAME_SUCCESSES );

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
            IntruderServiceClient.markAddressAndSession( pwmRequest );
            IntruderServiceClient.markAttributes( pwmDomain, formValues, pwmRequest.getLabel() );
        }

        StatisticsClient.incrementStat( pwmRequest, Statistic.FORGOTTEN_USERNAME_FAILURES );
        forwardToFormJsp( pwmRequest );
    }


    private void sendUsername(
            final PwmDomain pwmDomain,
            final PwmRequest pwmRequest,
            final UserInfo forgottenUserInfo
    )
            throws PwmOperationalException, PwmUnrecoverableException
    {
        final Locale userLocale = pwmRequest.getLocale();
        final DomainConfig domainConfig = pwmDomain.getConfig();
        final MessageSendMethod messageSendMethod = domainConfig.readSettingAsEnum( PwmSetting.FORGOTTEN_USERNAME_SEND_USERNAME_METHOD, MessageSendMethod.class );
        final EmailItemBean emailItemBean = domainConfig.readSettingAsEmail( PwmSetting.EMAIL_SEND_USERNAME, userLocale );
        final String smsMessage = domainConfig.readSettingAsLocalizedString( PwmSetting.SMS_FORGOTTEN_USERNAME_TEXT, userLocale );

        if ( messageSendMethod == null || messageSendMethod == MessageSendMethod.NONE )
        {
            return;
        }

        sendMessageViaMethod(
                pwmDomain,
                pwmRequest.getLabel(),
                forgottenUserInfo,
                messageSendMethod,
                emailItemBean,
                smsMessage
        );
    }


    private static void sendMessageViaMethod(
            final PwmDomain pwmDomain,
            final SessionLabel sessionLabel,
            final UserInfo userInfo,
            final MessageSendMethod messageSendMethod,
            final EmailItemBean emailItemBean,
            final String smsMessage
    )
            throws PwmOperationalException, PwmUnrecoverableException
    {
        if ( pwmDomain == null )
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
                error = sendSmsViaMethod( pwmDomain, sessionLabel, userInfo, smsMessage );
                break;
            case EMAILONLY:
            default:
                // Only try email
                error = sendEmailViaMethod( pwmDomain, sessionLabel, userInfo, emailItemBean );
                break;
        }
        if ( error != null )
        {
            throw new PwmOperationalException( error );
        }
    }

    private static ErrorInformation sendSmsViaMethod(
            final PwmDomain pwmDomain,
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

        final MacroRequest macroRequest = MacroRequest.forUser( pwmDomain.getPwmApplication(), sessionLabel, userInfo, null );

        SmsQueueService.sendSmsUsingQueue( pwmDomain.getPwmApplication(), toNumber, smsMessage, sessionLabel, macroRequest );
        return null;
    }

    private static ErrorInformation sendEmailViaMethod(
            final PwmDomain pwmDomain,
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

        final MacroRequest macroRequest = MacroRequest.forUser( pwmDomain.getPwmApplication(), sessionLabel, userInfo, null );

        pwmDomain.getPwmApplication().getEmailQueue().submitEmail( emailItemBean, userInfo, macroRequest );

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
        final String completeMessage = pwmRequest.getDomainConfig().readSettingAsLocalizedString( PwmSetting.FORGOTTEN_USERNAME_MESSAGE, locale );
        final MacroRequest macroRequest = MacroRequest.forUser( pwmRequest.getPwmApplication(), pwmRequest.getLocale(), pwmRequest.getLabel(), userIdentity );
        final String expandedText = macroRequest.expandMacros( completeMessage );
        pwmRequest.setAttribute( PwmRequestAttribute.CompleteText, expandedText );
        pwmRequest.forwardToJsp( JspUrl.FORGOTTEN_USERNAME_COMPLETE );
    }

}
