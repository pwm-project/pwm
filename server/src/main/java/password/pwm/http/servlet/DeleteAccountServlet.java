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

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.EmailItemBean;
import password.pwm.bean.UserIdentity;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.profile.DeleteAccountProfile;
import password.pwm.config.profile.ProfileDefinition;
import password.pwm.config.value.data.ActionConfiguration;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpMethod;
import password.pwm.http.JspUrl;
import password.pwm.http.ProcessStatus;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmRequestAttribute;
import password.pwm.http.bean.DeleteAccountBean;
import password.pwm.svc.event.AuditEvent;
import password.pwm.svc.event.AuditRecord;
import password.pwm.svc.event.AuditRecordFactory;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;
import password.pwm.util.operations.ActionExecutor;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@WebServlet(
        name = "SelfDeleteServlet",
        urlPatterns = {
                PwmConstants.URL_PREFIX_PRIVATE + "/delete",
                PwmConstants.URL_PREFIX_PRIVATE + "/DeleteAccount"
        }
)
public class DeleteAccountServlet extends ControlledPwmServlet
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( DeleteAccountServlet.class );

    public enum DeleteAccountAction implements AbstractPwmServlet.ProcessAction
    {
        agree( HttpMethod.POST ),
        delete( HttpMethod.POST ),
        reset( HttpMethod.POST ),;

        private final HttpMethod method;

        DeleteAccountAction( final HttpMethod method )
        {
            this.method = method;
        }

        public Collection<HttpMethod> permittedMethods( )
        {
            return Collections.singletonList( method );
        }
    }

    @Override
    public Class<? extends ProcessAction> getProcessActionsClass( )
    {
        return DeleteAccountAction.class;
    }

    private DeleteAccountProfile getProfile( final PwmRequest pwmRequest ) throws PwmUnrecoverableException
    {
        return pwmRequest.getPwmSession().getSessionManager().getSelfDeleteProfile( );
    }

    private DeleteAccountBean getBean( final PwmRequest pwmRequest ) throws PwmUnrecoverableException
    {
        return pwmRequest.getPwmApplication().getSessionStateService().getBean( pwmRequest, DeleteAccountBean.class );
    }

    @Override
    public ProcessStatus preProcessCheck( final PwmRequest pwmRequest ) throws PwmUnrecoverableException, IOException, ServletException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final DeleteAccountProfile deleteAccountProfile = getProfile( pwmRequest );

        if ( !pwmApplication.getConfig().readSettingAsBoolean( PwmSetting.DELETE_ACCOUNT_ENABLE ) )
        {
            throw new PwmUnrecoverableException( new ErrorInformation(
                    PwmError.ERROR_SERVICE_NOT_AVAILABLE,
                    "Setting "
                            + PwmSetting.DELETE_ACCOUNT_ENABLE.toMenuLocationDebug( null, null ) + " is not enabled." )
            );
        }

        if ( deleteAccountProfile == null )
        {
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_NO_PROFILE_ASSIGNED ) );
        }

        return ProcessStatus.Continue;
    }

    @Override
    protected void nextStep( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException, IOException, ChaiUnavailableException, ServletException
    {
        final DeleteAccountProfile profile = getProfile( pwmRequest );
        final DeleteAccountBean bean = getBean( pwmRequest );

        final String selfDeleteAgreementText = profile.readSettingAsLocalizedString(
                PwmSetting.DELETE_ACCOUNT_AGREEMENT,
                pwmRequest.getPwmSession().getSessionStateBean().getLocale()
        );

        if ( selfDeleteAgreementText != null && !selfDeleteAgreementText.trim().isEmpty() )
        {
            if ( !bean.isAgreementPassed() )
            {
                final MacroMachine macroMachine = pwmRequest.getPwmSession().getSessionManager().getMacroMachine( );
                final String expandedText = macroMachine.expandMacros( selfDeleteAgreementText );
                pwmRequest.setAttribute( PwmRequestAttribute.AgreementText, expandedText );
                pwmRequest.forwardToJsp( JspUrl.SELF_DELETE_AGREE );
                return;
            }
        }

        pwmRequest.forwardToJsp( JspUrl.SELF_DELETE_CONFIRM );
    }

    @ActionHandler( action = "reset" )
    private ProcessStatus handleResetRequest(
            final PwmRequest pwmRequest
    )
            throws ServletException, IOException, PwmUnrecoverableException, ChaiUnavailableException
    {
        pwmRequest.getPwmApplication().getSessionStateService().clearBean( pwmRequest, DeleteAccountBean.class );
        pwmRequest.sendRedirectToContinue();
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "agree" )
    private ProcessStatus handleAgreeRequest(
            final PwmRequest pwmRequest
    )
            throws ServletException, IOException, PwmUnrecoverableException, ChaiUnavailableException
    {
        LOGGER.debug( pwmRequest, () -> "user accepted agreement" );

        final DeleteAccountBean deleteAccountBean = getBean( pwmRequest );
        if ( !deleteAccountBean.isAgreementPassed() )
        {
            deleteAccountBean.setAgreementPassed( true );
            final AuditRecord auditRecord = new AuditRecordFactory( pwmRequest ).createUserAuditRecord(
                    AuditEvent.AGREEMENT_PASSED,
                    pwmRequest.getUserInfoIfLoggedIn(),
                    pwmRequest.getLabel(),
                    ProfileDefinition.DeleteAccount.toString()
            );
            pwmRequest.getPwmApplication().getAuditManager().submit( auditRecord );
        }

        return ProcessStatus.Continue;
    }

    @ActionHandler( action = "delete" )
    private ProcessStatus handleDeleteRequest(
            final PwmRequest pwmRequest
    )
            throws ServletException, IOException, PwmUnrecoverableException, ChaiUnavailableException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final DeleteAccountProfile deleteAccountProfile = getProfile( pwmRequest );
        final UserIdentity userIdentity = pwmRequest.getUserInfoIfLoggedIn();


        {
            // execute configured actions
            final List<ActionConfiguration> actions = deleteAccountProfile.readSettingAsAction( PwmSetting.DELETE_ACCOUNT_ACTIONS );
            if ( actions != null && !actions.isEmpty() )
            {
                LOGGER.debug( pwmRequest, () -> "executing configured actions to user " + userIdentity );


                final ActionExecutor actionExecutor = new ActionExecutor.ActionExecutorSettings( pwmApplication, userIdentity )
                        .setExpandPwmMacros( true )
                        .setMacroMachine( pwmRequest.getPwmSession().getSessionManager().getMacroMachine( ) )
                        .createActionExecutor();

                try
                {
                    actionExecutor.executeActions( actions, pwmRequest.getLabel() );
                }
                catch ( final PwmOperationalException e )
                {
                    LOGGER.error( () -> "error during user delete action execution: " + e.getMessage() );
                    throw new PwmUnrecoverableException( e.getErrorInformation(), e.getCause() );
                }
            }
        }

        // send notification
        sendProfileUpdateEmailNotice( pwmRequest );

        // mark the event log
        pwmApplication.getAuditManager().submit( AuditEvent.DELETE_ACCOUNT, pwmRequest.getPwmSession().getUserInfo(), pwmRequest.getPwmSession() );

        final String nextUrl = deleteAccountProfile.readSettingAsString( PwmSetting.DELETE_ACCOUNT_NEXT_URL );
        if ( nextUrl != null && !nextUrl.isEmpty() )
        {
            final MacroMachine macroMachine = pwmRequest.getPwmSession().getSessionManager().getMacroMachine( );
            final String macroedUrl = macroMachine.expandMacros( nextUrl );
            LOGGER.debug( pwmRequest, () -> "setting forward url to post-delete next url: " + macroedUrl );
            pwmRequest.getPwmSession().getSessionStateBean().setForwardURL( macroedUrl );
        }

        // perform ldap entry delete.
        if ( deleteAccountProfile.readSettingAsBoolean( PwmSetting.DELETE_ACCOUNT_DELETE_USER_ENTRY ) )
        {
            final ChaiUser chaiUser = pwmApplication.getProxiedChaiUser( pwmRequest.getUserInfoIfLoggedIn() );
            try
            {
                chaiUser.getChaiProvider().deleteEntry( chaiUser.getEntryDN() );
            }
            catch ( final ChaiException e )
            {
                final PwmUnrecoverableException pwmException = PwmUnrecoverableException.fromChaiException( e );
                LOGGER.error( () -> "error during user delete", pwmException );
                throw pwmException;
            }
        }

        // clear the delete bean
        pwmApplication.getSessionStateService().clearBean( pwmRequest, DeleteAccountBean.class );

        // delete finished, so logout and redirect.
        pwmRequest.getPwmSession().unauthenticateUser( pwmRequest );
        pwmRequest.sendRedirectToContinue();
        return ProcessStatus.Halt;
    }

    private static void sendProfileUpdateEmailNotice(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException, ChaiUnavailableException
    {
        final Configuration config = pwmRequest.getConfig();
        final Locale locale = pwmRequest.getLocale();
        final EmailItemBean configuredEmailSetting = config.readSettingAsEmail( PwmSetting.EMAIL_DELETEACCOUNT, locale );

        if ( configuredEmailSetting == null )
        {
            LOGGER.debug( pwmRequest, () -> "skipping delete account notice email for '" + pwmRequest.getUserInfoIfLoggedIn() + "' no email configured" );
            return;
        }

        pwmRequest.getPwmApplication().getEmailQueue().submitEmail(
                configuredEmailSetting,
                pwmRequest.getPwmSession().getUserInfo(),
                pwmRequest.getPwmSession().getSessionManager().getMacroMachine( )
        );
    }
}
