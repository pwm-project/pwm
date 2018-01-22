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
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.exception.ImpossiblePasswordPolicyException;
import com.novell.ldapchai.provider.ChaiProvider;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.EmailItemBean;
import password.pwm.bean.LocalSessionStateBean;
import password.pwm.bean.LoginInfoBean;
import password.pwm.bean.TokenDestinationItem;
import password.pwm.bean.UserIdentity;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.MessageSendMethod;
import password.pwm.config.profile.LdapProfile;
import password.pwm.config.value.data.ActionConfiguration;
import password.pwm.config.value.data.FormConfiguration;
import password.pwm.config.value.data.UserPermission;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmDataValidationException;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpMethod;
import password.pwm.http.JspUrl;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmSession;
import password.pwm.http.bean.ActivateUserBean;
import password.pwm.i18n.Message;
import password.pwm.ldap.LdapPermissionTester;
import password.pwm.ldap.UserInfo;
import password.pwm.ldap.UserInfoFactory;
import password.pwm.ldap.auth.AuthenticationType;
import password.pwm.ldap.auth.PwmAuthenticationSource;
import password.pwm.ldap.auth.SessionAuthenticator;
import password.pwm.ldap.search.SearchConfiguration;
import password.pwm.ldap.search.UserSearchEngine;
import password.pwm.svc.event.AuditEvent;
import password.pwm.svc.event.AuditRecord;
import password.pwm.svc.event.AuditRecordFactory;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.token.TokenPayload;
import password.pwm.svc.token.TokenService;
import password.pwm.svc.token.TokenType;
import password.pwm.util.CaptchaUtility;
import password.pwm.util.PostChangePasswordAction;
import password.pwm.util.form.FormUtility;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;
import password.pwm.util.operations.ActionExecutor;
import password.pwm.util.operations.PasswordUtility;
import password.pwm.ws.client.rest.RestTokenDataClient;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * User interaction servlet for creating new users (self registration).
 *
 * @author Jason D. Rivard
 */
@WebServlet(
        name = "ActivateUserServlet",
        urlPatterns = {
                PwmConstants.URL_PREFIX_PUBLIC + "/activate",
                PwmConstants.URL_PREFIX_PUBLIC + "/activate/*",
                PwmConstants.URL_PREFIX_PUBLIC + "/ActivateUser",
                PwmConstants.URL_PREFIX_PUBLIC + "/ActivateUser/*",
        }
)
public class ActivateUserServlet extends AbstractPwmServlet
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( ActivateUserServlet.class );

    public enum ActivateUserAction implements AbstractPwmServlet.ProcessAction
    {
        activate( HttpMethod.POST ),
        enterCode( HttpMethod.POST, HttpMethod.GET ),
        reset( HttpMethod.POST ),
        agree( HttpMethod.POST ),;

        private final Collection<HttpMethod> method;

        ActivateUserAction( final HttpMethod... method )
        {
            this.method = Collections.unmodifiableList( Arrays.asList( method ) );
        }

        public Collection<HttpMethod> permittedMethods( )
        {
            return method;
        }
    }

    protected ActivateUserAction readProcessAction( final PwmRequest request )
            throws PwmUnrecoverableException
    {
        try
        {
            return ActivateUserAction.valueOf( request.readParameterAsString( PwmConstants.PARAM_ACTION_REQUEST ) );
        }
        catch ( IllegalArgumentException e )
        {
            return null;
        }
    }


    protected void processAction( final PwmRequest pwmRequest )
            throws ServletException, ChaiUnavailableException, IOException, PwmUnrecoverableException
    {
        //Fetch the session state bean.
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();

        final Configuration config = pwmApplication.getConfig();

        final ActivateUserBean activateUserBean = pwmApplication.getSessionStateService().getBean( pwmRequest, ActivateUserBean.class );

        if ( !config.readSettingAsBoolean( PwmSetting.ACTIVATE_USER_ENABLE ) )
        {
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_SERVICE_NOT_AVAILABLE, "activate user is not enabled" );
            pwmRequest.respondWithError( errorInformation );
            return;
        }

        if ( pwmSession.isAuthenticated() )
        {
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_USERAUTHENTICATED );
            pwmRequest.respondWithError( errorInformation );
            return;
        }

        final ActivateUserAction action = readProcessAction( pwmRequest );

        // convert a url command like /pwm/public/NewUserServlet/12321321 to redirect with a process action.
        if ( action == null )
        {
            if ( pwmRequest.convertURLtokenCommand( PwmServletDefinition.ActivateUser, ActivateUserAction.enterCode ) )
            {
                return;
            }
        }
        else
        {
            switch ( action )
            {
                case activate:
                    handleActivationRequest( pwmRequest );
                    break;

                case enterCode:
                    handleEnterTokenCode( pwmRequest );
                    break;

                case reset:
                    pwmApplication.getSessionStateService().clearBean( pwmRequest, ActivateUserBean.class );
                    forwardToActivateUserForm( pwmRequest );
                    return;

                case agree:
                    handleAgreeRequest( pwmRequest, activateUserBean );
                    advanceToNextStage( pwmRequest );
                    break;

                default:
                    JavaHelper.unhandledSwitchStatement( action );

            }
        }

        if ( !pwmRequest.getPwmResponse().isCommitted() )
        {
            this.advanceToNextStage( pwmRequest );
        }
    }

    public void handleActivationRequest( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException, ChaiUnavailableException, IOException, ServletException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final Configuration config = pwmApplication.getConfig();
        final LocalSessionStateBean ssBean = pwmSession.getSessionStateBean();

        if ( CaptchaUtility.captchaEnabledForRequest( pwmRequest ) )
        {
            if ( !CaptchaUtility.verifyReCaptcha( pwmRequest ) )
            {
                final ErrorInformation errorInfo = new ErrorInformation( PwmError.ERROR_BAD_CAPTCHA_RESPONSE );
                LOGGER.debug( pwmRequest, errorInfo );
                setLastError( pwmRequest, errorInfo );
                return;
            }
        }


        pwmApplication.getSessionStateService().clearBean( pwmRequest, ActivateUserBean.class );
        final List<FormConfiguration> configuredActivationForm = config.readSettingAsForm( PwmSetting.ACTIVATE_USER_FORM );

        Map<FormConfiguration, String> formValues = new HashMap<>();
        try
        {
            //read the values from the request
            formValues = FormUtility.readFormValuesFromRequest( pwmRequest, configuredActivationForm,
                    ssBean.getLocale() );

            // check for intruders
            pwmApplication.getIntruderManager().convenience().checkAttributes( formValues );

            // read the context attr
            final String contextParam = pwmRequest.readParameterAsString( PwmConstants.PARAM_CONTEXT );

            // read the profile attr
            final String ldapProfile = pwmRequest.readParameterAsString( PwmConstants.PARAM_LDAP_PROFILE );

            // see if the values meet the configured form requirements.
            FormUtility.validateFormValues( config, formValues, ssBean.getLocale() );

            final String searchFilter = figureLdapSearchFilter( pwmRequest );

            // read an ldap user object based on the params
            final UserIdentity userIdentity;
            {
                final UserSearchEngine userSearchEngine = pwmApplication.getUserSearchEngine();
                final SearchConfiguration searchConfiguration = SearchConfiguration.builder()
                        .contexts( Collections.singletonList( contextParam ) )
                        .filter( searchFilter )
                        .formValues( formValues )
                        .ldapProfile( ldapProfile )
                        .build();

                userIdentity = userSearchEngine.performSingleUserSearch( searchConfiguration, pwmRequest.getSessionLabel() );
            }

            validateParamsAgainstLDAP( pwmRequest, formValues, userIdentity );

            final List<UserPermission> userPermissions = config.readSettingAsUserPermission( PwmSetting.ACTIVATE_USER_QUERY_MATCH );
            if ( !LdapPermissionTester.testUserPermissions( pwmApplication, pwmSession.getLabel(), userIdentity, userPermissions ) )
            {
                final String errorMsg = "user " + userIdentity + " attempted activation, but does not match query string";
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_ACTIVATE_NO_PERMISSION, errorMsg );
                pwmApplication.getIntruderManager().convenience().markUserIdentity( userIdentity, pwmSession );
                pwmApplication.getIntruderManager().convenience().markAddressAndSession( pwmSession );
                throw new PwmUnrecoverableException( errorInformation );
            }

            final ActivateUserBean activateUserBean = pwmApplication.getSessionStateService().getBean( pwmRequest, ActivateUserBean.class );
            activateUserBean.setUserIdentity( userIdentity );
            activateUserBean.setFormValidated( true );
            pwmApplication.getIntruderManager().convenience().clearAttributes( formValues );
            pwmApplication.getIntruderManager().convenience().clearAddressAndSession( pwmSession );
        }
        catch ( PwmOperationalException e )
        {
            pwmApplication.getIntruderManager().convenience().markAttributes( formValues, pwmSession );
            pwmApplication.getIntruderManager().convenience().markAddressAndSession( pwmSession );
            setLastError( pwmRequest, e.getErrorInformation() );
            LOGGER.debug( pwmSession.getLabel(), e.getErrorInformation().toDebugStr() );
        }

        // redirect user to change password screen.
        advanceToNextStage( pwmRequest );
    }

    private void handleAgreeRequest(
            final PwmRequest pwmRequest,
            final ActivateUserBean activateUserBean
    )
            throws ServletException, IOException, PwmUnrecoverableException, ChaiUnavailableException
    {
        LOGGER.debug( pwmRequest, "user accepted agreement" );

        if ( !activateUserBean.isAgreementPassed() )
        {
            activateUserBean.setAgreementPassed( true );
            final AuditRecord auditRecord = new AuditRecordFactory( pwmRequest ).createUserAuditRecord(
                    AuditEvent.AGREEMENT_PASSED,
                    pwmRequest.getUserInfoIfLoggedIn(),
                    pwmRequest.getSessionLabel(),
                    "ActivateUser"
            );
            pwmRequest.getPwmApplication().getAuditManager().submit( auditRecord );
        }
    }


    private void advanceToNextStage( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException, IOException, ServletException, ChaiUnavailableException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final Configuration config = pwmApplication.getConfig();
        final ActivateUserBean activateUserBean = pwmApplication.getSessionStateService().getBean( pwmRequest, ActivateUserBean.class );

        if ( !activateUserBean.isFormValidated() || activateUserBean.getUserIdentity() == null )
        {
            forwardToActivateUserForm( pwmRequest );
            return;
        }

        final boolean tokenRequired = MessageSendMethod.NONE != MessageSendMethod.valueOf( config.readSettingAsString( PwmSetting.ACTIVATE_TOKEN_SEND_METHOD ) );
        if ( tokenRequired )
        {
            if ( !activateUserBean.isTokenIssued() )
            {
                try
                {
                    final Locale locale = pwmSession.getSessionStateBean().getLocale();
                    initializeToken( pwmRequest, locale, activateUserBean.getUserIdentity() );
                }
                catch ( PwmOperationalException e )
                {
                    setLastError( pwmRequest, e.getErrorInformation() );
                    forwardToActivateUserForm( pwmRequest );
                    return;
                }
            }

            if ( !activateUserBean.isTokenPassed() )
            {
                pwmRequest.forwardToJsp( JspUrl.ACTIVATE_USER_ENTER_CODE );
                return;
            }
        }

        final String agreementText = config.readSettingAsLocalizedString(
                PwmSetting.ACTIVATE_AGREEMENT_MESSAGE,
                pwmSession.getSessionStateBean().getLocale()
        );
        if ( agreementText != null && agreementText.length() > 0 && !activateUserBean.isAgreementPassed() )
        {
            if ( activateUserBean.getAgreementText() == null )
            {
                final MacroMachine macroMachine = MacroMachine.forUser( pwmRequest, activateUserBean.getUserIdentity() );
                final String expandedText = macroMachine.expandMacros( agreementText );
                activateUserBean.setAgreementText( expandedText );
            }
            pwmRequest.forwardToJsp( JspUrl.ACTIVATE_USER_AGREEMENT );
            return;
        }

        try
        {
            activateUser( pwmRequest, activateUserBean.getUserIdentity() );
            pwmRequest.getPwmResponse().forwardToSuccessPage( Message.Success_ActivateUser );
        }
        catch ( PwmOperationalException e )
        {
            LOGGER.debug( pwmRequest, e.getErrorInformation() );
            pwmApplication.getIntruderManager().convenience().markUserIdentity( activateUserBean.getUserIdentity(), pwmSession );
            pwmApplication.getIntruderManager().convenience().markAddressAndSession( pwmSession );
            pwmRequest.respondWithError( e.getErrorInformation() );
        }
    }

    public void activateUser(
            final PwmRequest pwmRequest,
            final UserIdentity userIdentity
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, PwmOperationalException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final Configuration config = pwmApplication.getConfig();
        final ChaiUser theUser = pwmApplication.getProxiedChaiUser( userIdentity );
        if ( config.readSettingAsBoolean( PwmSetting.ACTIVATE_USER_UNLOCK ) )
        {
            try
            {
                theUser.unlockPassword();
            }
            catch ( ChaiOperationException e )
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
                LOGGER.debug( pwmSession.getLabel(), "executing configured pre-actions to user " + theUser.getEntryDN() );
                final List<ActionConfiguration> configValues = config.readSettingAsAction( PwmSetting.ACTIVATE_USER_PRE_WRITE_ATTRIBUTES );
                if ( configValues != null && !configValues.isEmpty() )
                {
                    final MacroMachine macroMachine = MacroMachine.forUser( pwmRequest, userIdentity );

                    final ActionExecutor actionExecutor = new ActionExecutor.ActionExecutorSettings( pwmApplication, userIdentity )
                            .setExpandPwmMacros( true )
                            .setMacroMachine( macroMachine )
                            .createActionExecutor();

                    actionExecutor.executeActions( configValues, pwmRequest.getSessionLabel() );
                }
            }

            //authenticate the pwm session
            final SessionAuthenticator sessionAuthenticator = new SessionAuthenticator( pwmApplication, pwmSession, PwmAuthenticationSource.USER_ACTIVATION );
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

            // setup post-change attributes
            final PostChangePasswordAction postAction = new PostChangePasswordAction()
            {

                public String getLabel( )
                {
                    return "ActivateUser write attributes";
                }

                public boolean doAction( final PwmSession pwmSession, final String newPassword )
                        throws PwmUnrecoverableException
                {
                    try
                    {
                        {
                            // execute configured actions
                            LOGGER.debug( pwmSession.getLabel(), "executing post-activate configured actions to user " + userIdentity.toDisplayString() );

                            final MacroMachine macroMachine = pwmSession.getSessionManager().getMacroMachine( pwmApplication );
                            final List<ActionConfiguration> configValues = pwmApplication.getConfig().readSettingAsAction( PwmSetting.ACTIVATE_USER_POST_WRITE_ATTRIBUTES );

                            final ActionExecutor actionExecutor = new ActionExecutor.ActionExecutorSettings( pwmApplication, userIdentity )
                                    .setExpandPwmMacros( true )
                                    .setMacroMachine( macroMachine )
                                    .createActionExecutor();
                            actionExecutor.executeActions( configValues, pwmRequest.getSessionLabel() );
                        }
                    }
                    catch ( PwmOperationalException e )
                    {
                        final ErrorInformation info = new ErrorInformation(
                                PwmError.ERROR_ACTIVATION_FAILURE,
                                e.getErrorInformation().getDetailedErrorMsg(), e.getErrorInformation().getFieldValues()
                        );
                        final PwmUnrecoverableException newException = new PwmUnrecoverableException( info );
                        newException.initCause( e );
                        throw newException;
                    }
                    catch ( ChaiUnavailableException e )
                    {
                        final String errorMsg = "unable to reach ldap server while writing post-activate attributes: " + e.getMessage();
                        final ErrorInformation info = new ErrorInformation( PwmError.ERROR_ACTIVATION_FAILURE, errorMsg );
                        final PwmUnrecoverableException newException = new PwmUnrecoverableException( info );
                        newException.initCause( e );
                        throw newException;
                    }
                    return true;
                }
            };

            pwmSession.getUserSessionDataCacheBean().addPostChangePasswordActions( "activateUserWriteAttributes", postAction );
        }
        catch ( ImpossiblePasswordPolicyException e )
        {
            final ErrorInformation info = new ErrorInformation( PwmError.ERROR_UNKNOWN, "unexpected ImpossiblePasswordPolicyException error while activating user" );
            LOGGER.warn( pwmSession, info, e );
            throw new PwmOperationalException( info );
        }
    }

    protected static void validateParamsAgainstLDAP(
            final PwmRequest pwmRequest,
            final Map<FormConfiguration, String> formValues,
            final UserIdentity userIdentity
    )
            throws ChaiUnavailableException, PwmDataValidationException, PwmUnrecoverableException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final String searchFilter = figureLdapSearchFilter( pwmRequest );
        final ChaiProvider chaiProvider = pwmApplication.getProxyChaiProvider( userIdentity.getLdapProfileID() );
        final ChaiUser chaiUser = chaiProvider.getEntryFactory().newChaiUser( userIdentity.getUserDN() );

        for ( final Map.Entry<FormConfiguration, String> entry : formValues.entrySet() )
        {
            final FormConfiguration formItem = entry.getKey();
            final String attrName = formItem.getName();
            final String tokenizedAttrName = "%" + attrName + "%";
            if ( searchFilter.contains( tokenizedAttrName ) )
            {
                LOGGER.trace( pwmSession, "skipping validation of ldap value for '" + attrName + "' because it is in search filter" );
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
                        LOGGER.debug( pwmSession.getLabel(), errorInfo.toDebugStr() );
                        throw new PwmDataValidationException( errorInfo );
                    }
                    LOGGER.trace( pwmSession.getLabel(), "successful validation of ldap value for '" + attrName + "'" );
                }
                catch ( ChaiOperationException e )
                {
                    LOGGER.error( pwmSession.getLabel(), "error during param validation of '" + attrName + "', error: " + e.getMessage() );
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

    private void sendPostActivationNotice(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException, ChaiUnavailableException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final Configuration config = pwmApplication.getConfig();
        final UserInfo userInfo = pwmSession.getUserInfo();
        final MessageSendMethod pref = MessageSendMethod.valueOf( config.readSettingAsString( PwmSetting.ACTIVATE_TOKEN_SEND_METHOD ) );

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
            LOGGER.warn( pwmSession, "skipping send activation message for '" + userInfo.getUserIdentity() + "' no email or SMS number configured" );
        }
    }

    private boolean sendPostActivationEmail(
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
            LOGGER.debug( pwmSession, "skipping send activation email for '" + userInfo.getUserIdentity() + "' no email configured" );
            return false;
        }

        pwmApplication.getEmailQueue().submitEmail(
                configuredEmailSetting,
                pwmSession.getUserInfo(),
                pwmSession.getSessionManager().getMacroMachine( pwmApplication )
        );
        return true;
    }

    private Boolean sendPostActivationSms( final PwmRequest pwmRequest )
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
        catch ( Exception e )
        {
            LOGGER.debug( pwmSession.getLabel(), "error reading SMS attribute from user '" + pwmSession.getUserInfo().getUserIdentity() + "': " + e.getMessage() );
            return false;
        }

        if ( toSmsNumber == null || toSmsNumber.length() < 1 )
        {
            LOGGER.debug( pwmSession.getLabel(), "skipping send activation SMS for '" + pwmSession.getUserInfo().getUserIdentity() + "' no SMS number configured" );
            return false;
        }

        pwmApplication.sendSmsUsingQueue(
                toSmsNumber,
                message,
                pwmRequest.getSessionLabel(),
                pwmSession.getSessionManager().getMacroMachine( pwmApplication )
        );
        return true;
    }

    private static void initializeToken(
            final PwmRequest pwmRequest,
            final Locale locale,
            final UserIdentity userIdentity

    )
            throws PwmUnrecoverableException, PwmOperationalException, ChaiUnavailableException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final ActivateUserBean activateUserBean = pwmApplication.getSessionStateService().getBean( pwmRequest, ActivateUserBean.class );
        final Configuration config = pwmApplication.getConfig();

        final RestTokenDataClient.TokenDestinationData inputTokenDestData;
        {
            final String toAddress;
            {
                final EmailItemBean emailItemBean = config.readSettingAsEmail( PwmSetting.EMAIL_ACTIVATION_VERIFICATION, locale );
                final MacroMachine macroMachine = MacroMachine.forUser( pwmRequest, userIdentity );
                toAddress = macroMachine.expandMacros( emailItemBean.getTo() );
            }

            final String toSmsNumber;
            try
            {
                final UserInfo userInfo = UserInfoFactory.newUserInfoUsingProxy( pwmApplication, pwmSession.getLabel(), userIdentity, pwmRequest.getLocale() );
                final LdapProfile ldapProfile = userIdentity.getLdapProfile( config );
                toSmsNumber = userInfo.readStringAttribute( ldapProfile.readSettingAsString( PwmSetting.SMS_USER_PHONE_ATTRIBUTE ) );
            }
            catch ( Exception e )
            {
                final String errorMsg = "unable to read user SMS attribute due to ldap error, unable to send token: " + e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_ACTIVATION_FAILURE, errorMsg );
                LOGGER.error( pwmSession.getLabel(), errorInformation );
                throw new PwmOperationalException( errorInformation );
            }
            inputTokenDestData = new RestTokenDataClient.TokenDestinationData( toAddress, toSmsNumber, null );
        }

        final RestTokenDataClient restTokenDataClient = new RestTokenDataClient( pwmApplication );
        final RestTokenDataClient.TokenDestinationData outputDestTokenData = restTokenDataClient.figureDestTokenDisplayString(
                pwmSession.getLabel(),
                inputTokenDestData,
                activateUserBean.getUserIdentity(),
                pwmSession.getSessionStateBean().getLocale() );

        final Set<String> destinationValues = new HashSet<>();
        if ( outputDestTokenData.getEmail() != null )
        {
            destinationValues.add( outputDestTokenData.getEmail() );
        }
        if ( outputDestTokenData.getSms() != null )
        {
            destinationValues.add( outputDestTokenData.getSms() );
        }


        final Map<String, String> tokenMapData = new HashMap<>();

        try
        {
            final Instant userLastPasswordChange = PasswordUtility.determinePwdLastModified(
                    pwmApplication,
                    pwmSession.getLabel(),
                    activateUserBean.getUserIdentity() );
            if ( userLastPasswordChange != null )
            {
                tokenMapData.put( PwmConstants.TOKEN_KEY_PWD_CHG_DATE, JavaHelper.toIsoDate( userLastPasswordChange ) );
            }
        }
        catch ( ChaiUnavailableException e )
        {
            LOGGER.error( pwmSession.getLabel(), "unexpected error reading user's last password change time" );
        }

        final String tokenKey;
        final TokenPayload tokenPayload;
        try
        {
            tokenPayload = pwmApplication.getTokenService().createTokenPayload(
                    TokenType.ACTIVATION,
                    new TimeDuration( config.readSettingAsLong( PwmSetting.TOKEN_LIFETIME ), TimeUnit.SECONDS ),
                    tokenMapData,
                    userIdentity,
                    destinationValues
            );
            tokenKey = pwmApplication.getTokenService().generateNewToken( tokenPayload, pwmRequest.getSessionLabel() );
        }
        catch ( PwmOperationalException e )
        {
            throw new PwmUnrecoverableException( e.getErrorInformation() );
        }

        final String displayValue = sendToken( pwmRequest, userIdentity, locale, outputDestTokenData.getEmail(), outputDestTokenData.getSms(), tokenKey );
        activateUserBean.setTokenDisplayText( displayValue );
        activateUserBean.setTokenIssued( true );
    }

    private void handleEnterTokenCode(
            final PwmRequest pwmRequest
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, IOException, ServletException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final ActivateUserBean activateUserBean = pwmApplication.getSessionStateService().getBean( pwmRequest, ActivateUserBean.class );
        final String userEnteredCode = pwmRequest.readParameterAsString( PwmConstants.PARAM_TOKEN );

        ErrorInformation errorInformation = null;
        try
        {
            final TokenPayload tokenPayload = pwmApplication.getTokenService().processUserEnteredCode(
                    pwmSession,
                    activateUserBean.getUserIdentity(),
                    TokenType.ACTIVATION,
                    userEnteredCode
            );
            if ( tokenPayload != null )
            {
                activateUserBean.setUserIdentity( tokenPayload.getUserIdentity() );
                activateUserBean.setTokenPassed( true );
                activateUserBean.setFormValidated( true );
            }
        }
        catch ( PwmOperationalException e )
        {
            final String errorMsg = "token incorrect: " + e.getMessage();
            errorInformation = new ErrorInformation( PwmError.ERROR_TOKEN_INCORRECT, errorMsg );
        }

        if ( !activateUserBean.isTokenPassed() )
        {
            if ( errorInformation == null )
            {
                errorInformation = new ErrorInformation( PwmError.ERROR_TOKEN_INCORRECT );
            }
            LOGGER.debug( pwmSession.getLabel(), errorInformation.toDebugStr() );
            setLastError( pwmRequest, errorInformation );
        }

        this.advanceToNextStage( pwmRequest );
    }

    private static String sendToken(
            final PwmRequest pwmRequest,
            final UserIdentity userIdentity,
            final Locale userLocale,
            final String toAddress,
            final String toSmsNumber,
            final String tokenKey
    )
            throws PwmUnrecoverableException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final Configuration config = pwmApplication.getConfig();
        final MessageSendMethod pref = MessageSendMethod.valueOf( config.readSettingAsString( PwmSetting.ACTIVATE_TOKEN_SEND_METHOD ) );
        final EmailItemBean emailItemBean = config.readSettingAsEmail( PwmSetting.EMAIL_ACTIVATION_VERIFICATION, userLocale );
        final String smsMessage = config.readSettingAsLocalizedString( PwmSetting.SMS_ACTIVATION_VERIFICATION_TEXT, userLocale );

        final MacroMachine macroMachine = MacroMachine.forUser( pwmRequest, userIdentity );

        final List<TokenDestinationItem.Type> sentTypes = TokenService.TokenSender.sendToken(
                TokenService.TokenSendInfo.builder()
                        .pwmApplication( pwmApplication )
                        .userInfo( null )
                        .macroMachine( macroMachine )
                        .configuredEmailSetting( emailItemBean )
                        .tokenSendMethod( pref )
                        .emailAddress( toAddress )
                        .smsNumber( toSmsNumber )
                        .smsMessage( smsMessage )
                        .tokenKey( tokenKey )
                        .sessionLabel( pwmRequest.getSessionLabel() )
                        .build()
        );

        return TokenService.TokenSender.figureDisplayString(
                pwmApplication.getConfig(),
                sentTypes,
                toAddress,
                toSmsNumber
        );
    }

    private static String figureLdapSearchFilter( final PwmRequest pwmRequest )
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
            LOGGER.trace( pwmRequest, "auto generated search filter based on activation form: " + searchFilter );
        }
        else
        {
            searchFilter = configuredSearchFilter;
        }
        return searchFilter;
    }

    private static void forwardToActivateUserForm( final PwmRequest pwmRequest )
            throws ServletException, PwmUnrecoverableException, IOException
    {
        pwmRequest.addFormInfoToRequestAttr( PwmSetting.ACTIVATE_USER_FORM, false, false );
        pwmRequest.forwardToJsp( JspUrl.ACTIVATE_USER );
    }
}
