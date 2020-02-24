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

package password.pwm.http.servlet.activation;

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.exception.ImpossiblePasswordPolicyException;
import com.novell.ldapchai.provider.ChaiProvider;
import password.pwm.PwmApplication;
import password.pwm.bean.EmailItemBean;
import password.pwm.bean.LoginInfoBean;
import password.pwm.bean.UserIdentity;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.MessageSendMethod;
import password.pwm.config.profile.ActivateUserProfile;
import password.pwm.config.profile.LdapProfile;
import password.pwm.config.profile.ProfileDefinition;
import password.pwm.config.profile.ProfileUtility;
import password.pwm.config.value.data.ActionConfiguration;
import password.pwm.config.value.data.FormConfiguration;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmDataValidationException;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.JspUrl;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmRequestAttribute;
import password.pwm.http.PwmSession;
import password.pwm.http.bean.ActivateUserBean;
import password.pwm.ldap.UserInfo;
import password.pwm.ldap.auth.AuthenticationType;
import password.pwm.ldap.auth.PwmAuthenticationSource;
import password.pwm.ldap.auth.SessionAuthenticator;
import password.pwm.svc.event.AuditEvent;
import password.pwm.svc.stats.Statistic;
import password.pwm.util.form.FormUtility;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;
import password.pwm.util.operations.ActionExecutor;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

class ActivateUserUtils
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( ActivateUserUtils.class );

    private ActivateUserUtils()
    {
    }

    static void activateUser(
            final PwmRequest pwmRequest,
            final UserIdentity userIdentity
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, PwmOperationalException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final ChaiUser theUser = pwmApplication.getProxiedChaiUser( userIdentity );

        final ActivateUserProfile activateUserProfile = ActivateUserServlet.activateUserProfile( pwmRequest );

        if ( activateUserProfile.readSettingAsBoolean( PwmSetting.ACTIVATE_USER_UNLOCK ) )
        {
            try
            {
                theUser.unlockPassword();
            }
            catch ( final ChaiOperationException e )
            {
                final String errorMsg = "error unlocking user " + userIdentity + ": " + e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_ACTIVATION_FAILURE, errorMsg );
                throw new PwmOperationalException( errorInformation );
            }
        }

        try
        {
            {
                // execute configured actions
                LOGGER.debug( pwmRequest, () -> "executing configured pre-actions to user " + theUser.getEntryDN() );
                final List<ActionConfiguration> configValues = activateUserProfile.readSettingAsAction( PwmSetting.ACTIVATE_USER_PRE_WRITE_ATTRIBUTES );
                if ( !JavaHelper.isEmpty( configValues ) )
                {
                    final MacroMachine macroMachine = MacroMachine.forUser( pwmRequest, userIdentity );

                    final ActionExecutor actionExecutor = new ActionExecutor.ActionExecutorSettings( pwmApplication, userIdentity )
                            .setExpandPwmMacros( true )
                            .setMacroMachine( macroMachine )
                            .createActionExecutor();

                    actionExecutor.executeActions( configValues, pwmRequest.getLabel() );
                }
            }

            //authenticate the pwm session
            final SessionAuthenticator sessionAuthenticator = new SessionAuthenticator( pwmApplication, pwmRequest, PwmAuthenticationSource.USER_ACTIVATION );
            sessionAuthenticator.authUserWithUnknownPassword( userIdentity, AuthenticationType.AUTH_FROM_PUBLIC_MODULE );

            //ensure a change password is triggered
            pwmSession.getLoginInfoBean().setType( AuthenticationType.AUTH_FROM_PUBLIC_MODULE );
            pwmSession.getLoginInfoBean().getAuthFlags().add( AuthenticationType.AUTH_FROM_PUBLIC_MODULE );
            pwmSession.getLoginInfoBean().getLoginFlags().add( LoginInfoBean.LoginFlag.forcePwChange );


            // mark the event log
            pwmApplication.getAuditManager().submit( AuditEvent.ACTIVATE_USER, pwmSession.getUserInfo(), pwmSession );

            // update the stats bean
            pwmApplication.getStatisticsManager().incrementValue( Statistic.ACTIVATED_USERS );

            // send email or sms
            sendPostActivationNotice( pwmRequest );
        }
        catch ( final ImpossiblePasswordPolicyException e )
        {
            final ErrorInformation info = new ErrorInformation( PwmError.ERROR_INTERNAL, "unexpected ImpossiblePasswordPolicyException error while activating user" );
            LOGGER.warn( pwmRequest.getLabel(), info, e );
            throw new PwmOperationalException( info );
        }
    }

    static void validateParamsAgainstLDAP(
            final PwmRequest pwmRequest,
            final Map<FormConfiguration, String> formValues,
            final UserIdentity userIdentity
    )
            throws ChaiUnavailableException, PwmDataValidationException, PwmUnrecoverableException
    {
        final String searchFilter = figureLdapSearchFilter( pwmRequest );
        final ChaiProvider chaiProvider = pwmRequest.getPwmApplication().getProxyChaiProvider( userIdentity.getLdapProfileID() );
        final ChaiUser chaiUser = chaiProvider.getEntryFactory().newChaiUser( userIdentity.getUserDN() );

        for ( final Map.Entry<FormConfiguration, String> entry : formValues.entrySet() )
        {
            final FormConfiguration formItem = entry.getKey();
            final String attrName = formItem.getName();
            final String tokenizedAttrName = "%" + attrName + "%";
            if ( searchFilter.contains( tokenizedAttrName ) )
            {
                LOGGER.trace( pwmRequest, () -> "skipping validation of ldap value for '" + attrName + "' because it is in search filter" );
            }
            else
            {
                final String value = entry.getValue();
                try
                {
                    if ( !chaiUser.compareStringAttribute( attrName, value ) )
                    {
                        final String errorMsg = "incorrect value for '" + attrName + "'";
                        final ErrorInformation errorInfo = new ErrorInformation( PwmError.ERROR_ACTIVATION_VALIDATIONFAIL, errorMsg, new String[]
                                {
                                        attrName,
                                }
                        );
                        LOGGER.debug( pwmRequest, errorInfo );
                        throw new PwmDataValidationException( errorInfo );
                    }
                    LOGGER.trace( pwmRequest, () -> "successful validation of ldap value for '" + attrName + "'" );
                }
                catch ( final ChaiOperationException e )
                {
                    LOGGER.error( pwmRequest, () -> "error during param validation of '" + attrName + "', error: " + e.getMessage() );
                    throw new PwmDataValidationException( new ErrorInformation(
                            PwmError.ERROR_ACTIVATION_VALIDATIONFAIL,
                            "ldap error testing value for '" + attrName + "'", new String[]
                            {
                                    attrName,
                            }
                    ) );
                }
            }
        }
    }

    static void sendPostActivationNotice(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException, ChaiUnavailableException
    {
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final ActivateUserProfile activateUserProfile = ActivateUserServlet.activateUserProfile( pwmRequest );
        final UserInfo userInfo = pwmSession.getUserInfo();
        final MessageSendMethod pref = activateUserProfile.readSettingAsEnum( PwmSetting.ACTIVATE_TOKEN_SEND_METHOD, MessageSendMethod.class );

        final boolean success;
        switch ( pref )
        {
            case SMSONLY:
                // Only try SMS
                success = sendPostActivationSms( pwmRequest );
                break;
            case EMAILONLY:
            default:
                // Only try email
                success = sendPostActivationEmail( pwmRequest );
                break;
        }
        if ( !success )
        {
            LOGGER.warn( pwmRequest, () -> "skipping send activation message for '" + userInfo.getUserIdentity() + "' no email or SMS number configured" );
        }
    }

    static boolean sendPostActivationEmail(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException, ChaiUnavailableException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final UserInfo userInfo = pwmSession.getUserInfo();
        final Configuration config = pwmApplication.getConfig();
        final Locale locale = pwmSession.getSessionStateBean().getLocale();
        final EmailItemBean configuredEmailSetting = config.readSettingAsEmail( PwmSetting.EMAIL_ACTIVATION, locale );

        if ( configuredEmailSetting == null )
        {
            LOGGER.debug( pwmRequest, () -> "skipping send activation email for '" + userInfo.getUserIdentity() + "' no email configured" );
            return false;
        }

        pwmApplication.getEmailQueue().submitEmail(
                configuredEmailSetting,
                pwmSession.getUserInfo(),
                pwmSession.getSessionManager().getMacroMachine( )
        );
        return true;
    }

    static boolean sendPostActivationSms( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException, ChaiUnavailableException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final Configuration config = pwmApplication.getConfig();
        final UserInfo userInfo = pwmSession.getUserInfo();
        final Locale locale = pwmSession.getSessionStateBean().getLocale();
        final LdapProfile ldapProfile = userInfo.getUserIdentity().getLdapProfile( config );

        final String message = config.readSettingAsLocalizedString( PwmSetting.SMS_ACTIVATION_TEXT, locale );

        final String toSmsNumber;
        try
        {
            toSmsNumber = userInfo.readStringAttribute( ldapProfile.readSettingAsString( PwmSetting.SMS_USER_PHONE_ATTRIBUTE ) );
        }
        catch ( final Exception e )
        {
            LOGGER.debug( pwmRequest, () -> "error reading SMS attribute from user '" + pwmSession.getUserInfo().getUserIdentity() + "': " + e.getMessage() );
            return false;
        }

        if ( toSmsNumber == null || toSmsNumber.length() < 1 )
        {
            LOGGER.debug( pwmRequest, () -> "skipping send activation SMS for '" + pwmSession.getUserInfo().getUserIdentity() + "' no SMS number configured" );
            return false;
        }

        pwmApplication.sendSmsUsingQueue(
                toSmsNumber,
                message,
                pwmRequest.getLabel(),
                pwmSession.getSessionManager().getMacroMachine( )
        );
        return true;
    }

    static String figureLdapSearchFilter( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final Configuration config = pwmApplication.getConfig();
        final List<FormConfiguration> configuredActivationForm = config.readSettingAsForm( PwmSetting.ACTIVATE_USER_FORM );

        final String configuredSearchFilter = config.readSettingAsString( PwmSetting.ACTIVATE_USER_SEARCH_FILTER );
        final String searchFilter;
        if ( configuredSearchFilter == null || configuredSearchFilter.isEmpty() )
        {
            searchFilter = FormUtility.ldapSearchFilterForForm( pwmApplication, configuredActivationForm );
            LOGGER.trace( pwmRequest, () -> "auto generated search filter based on activation form: " + searchFilter );
        }
        else
        {
            searchFilter = configuredSearchFilter;
        }
        return searchFilter;
    }

    static void forwardToAgreementPage( final PwmRequest pwmRequest )
            throws ServletException, PwmUnrecoverableException, IOException
    {
        final ActivateUserProfile activateUserProfile = ActivateUserServlet.activateUserProfile( pwmRequest );
        final String agreementText = activateUserProfile.readSettingAsLocalizedString(
                PwmSetting.ACTIVATE_AGREEMENT_MESSAGE,
                pwmRequest.getLocale()
        );

        final MacroMachine macroMachine = MacroMachine.forUser( pwmRequest, ActivateUserServlet.userInfo( pwmRequest ).getUserIdentity() );
        final String expandedText = macroMachine.expandMacros( agreementText );
        pwmRequest.setAttribute( PwmRequestAttribute.AgreementText, expandedText );
        pwmRequest.forwardToJsp( JspUrl.ACTIVATE_USER_AGREEMENT );
    }

    static void forwardToSearchUserForm( final PwmRequest pwmRequest )
            throws ServletException, PwmUnrecoverableException, IOException
    {
        pwmRequest.addFormInfoToRequestAttr( PwmSetting.ACTIVATE_USER_FORM, false, false );
        pwmRequest.forwardToJsp( JspUrl.ACTIVATE_USER_SEARCH );
    }

    static void initUserActivationBean( final PwmRequest pwmRequest, final UserIdentity userIdentity )
            throws PwmUnrecoverableException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final ActivateUserBean activateUserBean = pwmApplication.getSessionStateService().getBean( pwmRequest, ActivateUserBean.class );

        final Optional<String> profileID = ProfileUtility.discoverProfileIDforUser( pwmRequest.commonValues(), userIdentity, ProfileDefinition.ActivateUser );

        if ( !profileID.isPresent() || !pwmApplication.getConfig().getUserActivationProfiles().containsKey( profileID.get() ) )
        {
            throw PwmUnrecoverableException.newException( PwmError.ERROR_ACTIVATE_NO_PERMISSION, "no matching user activation profile for user" );
        }

        activateUserBean.setUserIdentity( userIdentity );
        activateUserBean.setFormValidated( true );
        activateUserBean.setProfileID( profileID.get() );
    }
}
