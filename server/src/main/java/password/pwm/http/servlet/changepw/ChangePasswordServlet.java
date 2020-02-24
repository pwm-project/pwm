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

package password.pwm.http.servlet.changepw;

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.AppProperty;
import password.pwm.Permission;
import password.pwm.PwmApplication;
import password.pwm.bean.LocalSessionStateBean;
import password.pwm.bean.LoginInfoBean;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.profile.PwmPasswordRule;
import password.pwm.config.value.data.FormConfiguration;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmDataValidationException;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpMethod;
import password.pwm.http.JspUrl;
import password.pwm.http.ProcessStatus;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmRequestAttribute;
import password.pwm.http.PwmSession;
import password.pwm.http.bean.ChangePasswordBean;
import password.pwm.http.servlet.ControlledPwmServlet;
import password.pwm.i18n.Message;
import password.pwm.ldap.PasswordChangeProgressChecker;
import password.pwm.ldap.UserInfo;
import password.pwm.ldap.auth.AuthenticationType;
import password.pwm.svc.event.AuditEvent;
import password.pwm.svc.event.AuditRecord;
import password.pwm.svc.event.AuditRecordFactory;
import password.pwm.svc.stats.AvgStatistic;
import password.pwm.util.PasswordData;
import password.pwm.util.form.FormUtility;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;
import password.pwm.util.password.PasswordUtility;
import password.pwm.util.password.PwmPasswordRuleValidator;
import password.pwm.util.password.RandomPasswordGenerator;
import password.pwm.ws.server.RestResultBean;
import password.pwm.ws.server.rest.RestCheckPasswordServer;
import password.pwm.ws.server.rest.RestRandomPasswordServer;

import javax.servlet.ServletException;
import java.io.IOException;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * User interaction servlet for changing (self) passwords.
 *
 * @author Jason D. Rivard.
 */

public abstract class ChangePasswordServlet extends ControlledPwmServlet
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( ChangePasswordServlet.class );

    public enum ChangePasswordAction implements ControlledPwmServlet.ProcessAction
    {
        checkProgress( HttpMethod.POST ),
        complete( HttpMethod.GET ),
        change( HttpMethod.POST ),
        form( HttpMethod.POST ),
        agree( HttpMethod.POST ),
        warnResponse( HttpMethod.POST ),
        reset( HttpMethod.POST ),
        checkPassword( HttpMethod.POST ),
        randomPassword( HttpMethod.POST ),;

        private final HttpMethod method;


        ChangePasswordAction( final HttpMethod method )
        {
            this.method = method;
        }

        public Collection<HttpMethod> permittedMethods( )
        {
            return Collections.singletonList( method );
        }
    }

    enum WarnResponseValue
    {
        skip,
        change,
    }

    @Override
    public Class<? extends ProcessAction> getProcessActionsClass( )
    {
        return ChangePasswordServlet.ChangePasswordAction.class;
    }


    @ActionHandler( action = "reset" )
    ProcessStatus processResetAction( final PwmRequest pwmRequest ) throws ServletException, PwmUnrecoverableException, IOException
    {

        if ( pwmRequest.getPwmSession().getLoginInfoBean().getAuthFlags().contains( AuthenticationType.AUTH_FROM_PUBLIC_MODULE ) )
        {
            // Must have gotten here from the "Forgotten Password" link.  Better clear out the temporary authentication
            pwmRequest.getPwmSession().unauthenticateUser( pwmRequest );
        }

        pwmRequest.getPwmApplication().getSessionStateService().clearBean( pwmRequest, ChangePasswordBean.class );
        pwmRequest.sendRedirectToContinue();

        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "warnResponse" )
    public ProcessStatus processWarnResponse( final PwmRequest pwmRequest ) throws ServletException, PwmUnrecoverableException, IOException
    {
        final ChangePasswordBean changePasswordBean = pwmRequest.getPwmApplication().getSessionStateService().getBean( pwmRequest, ChangePasswordBean.class );

        if ( pwmRequest.getPwmSession().getUserInfo().getPasswordStatus().isWarnPeriod() )
        {
            final String warnResponseStr = pwmRequest.readParameterAsString( "warnResponse" );
            final WarnResponseValue warnResponse = JavaHelper.readEnumFromString( WarnResponseValue.class, null, warnResponseStr );
            if ( warnResponse != null )
            {
                switch ( warnResponse )
                {
                    case skip:
                        pwmRequest.getPwmSession().getLoginInfoBean().setFlag( LoginInfoBean.LoginFlag.skipNewPw );
                        pwmRequest.sendRedirectToContinue();
                        return ProcessStatus.Halt;

                    case change:
                        changePasswordBean.setWarnPassed( true );
                        break;

                    default:
                        JavaHelper.unhandledSwitchStatement( warnResponse );
                }
            }
        }

        return ProcessStatus.Continue;
    }

    @ActionHandler( action = "change" )
    ProcessStatus processChangeAction( final PwmRequest pwmRequest ) throws ServletException, PwmUnrecoverableException, IOException, ChaiUnavailableException
    {
        final ChangePasswordBean changePasswordBean = pwmRequest.getPwmApplication().getSessionStateService().getBean( pwmRequest, ChangePasswordBean.class );

        final UserInfo userInfo = pwmRequest.getPwmSession().getUserInfo();

        if ( !changePasswordBean.isAllChecksPassed() )
        {
            return ProcessStatus.Continue;
        }

        final PasswordData password1 = pwmRequest.readParameterAsPassword( "password1" );
        final PasswordData password2 = pwmRequest.readParameterAsPassword( "password2" );

        // check the password meets the requirements
        try
        {
            final ChaiUser theUser = pwmRequest.getPwmSession().getSessionManager().getActor( );
            final PwmPasswordRuleValidator pwmPasswordRuleValidator = new PwmPasswordRuleValidator( pwmRequest.getPwmApplication(), userInfo.getPasswordPolicy() );
            final PasswordData oldPassword = pwmRequest.getPwmSession().getLoginInfoBean().getUserCurrentPassword();
            pwmPasswordRuleValidator.testPassword( password1, oldPassword, userInfo, theUser );
        }
        catch ( final PwmDataValidationException e )
        {
            setLastError( pwmRequest, e.getErrorInformation() );
            LOGGER.debug( pwmRequest, () -> "failed password validation check: " + e.getErrorInformation().toDebugStr() );
            return ProcessStatus.Continue;
        }

        //make sure the two passwords match
        final boolean caseSensitive = userInfo.getPasswordPolicy().getRuleHelper().readBooleanValue(
                PwmPasswordRule.CaseSensitive );
        if ( PasswordUtility.PasswordCheckInfo.MatchStatus.MATCH != PasswordUtility.figureMatchStatus( caseSensitive,
                password1, password2 ) )
        {
            setLastError( pwmRequest, PwmError.PASSWORD_DOESNOTMATCH.toInfo() );
            forwardToChangePage( pwmRequest );
            return ProcessStatus.Continue;
        }

        try
        {
            ChangePasswordServletUtil.executeChangePassword( pwmRequest, password1 );
        }
        catch ( final PwmOperationalException e )
        {
            LOGGER.debug( () -> e.getErrorInformation().toDebugStr() );
            setLastError( pwmRequest, e.getErrorInformation() );
        }

        return ProcessStatus.Continue;
    }

    @ActionHandler( action = "agree" )
    ProcessStatus processAgreeAction( final PwmRequest pwmRequest ) throws ServletException, PwmUnrecoverableException, IOException, ChaiUnavailableException
    {
        final ChangePasswordBean changePasswordBean = pwmRequest.getPwmApplication().getSessionStateService().getBean( pwmRequest, ChangePasswordBean.class );

        LOGGER.debug( pwmRequest, () -> "user accepted password change agreement" );
        if ( !changePasswordBean.isAgreementPassed() )
        {
            changePasswordBean.setAgreementPassed( true );
            final AuditRecord auditRecord = new AuditRecordFactory( pwmRequest ).createUserAuditRecord(
                    AuditEvent.AGREEMENT_PASSED,
                    pwmRequest.getUserInfoIfLoggedIn(),
                    pwmRequest.getLabel(),
                    "ChangePassword"
            );
            pwmRequest.getPwmApplication().getAuditManager().submit( auditRecord );
        }

        return ProcessStatus.Continue;
    }

    @ActionHandler( action = "form" )
    ProcessStatus processFormAction( final PwmRequest pwmRequest )
            throws ServletException, PwmUnrecoverableException, IOException, ChaiUnavailableException
    {
        final ChangePasswordBean cpb = pwmRequest.getPwmApplication().getSessionStateService().getBean( pwmRequest, ChangePasswordBean.class );
        final LocalSessionStateBean ssBean = pwmRequest.getPwmSession().getSessionStateBean();
        final UserInfo userInfo = pwmRequest.getPwmSession().getUserInfo();
        final LoginInfoBean loginBean = pwmRequest.getPwmSession().getLoginInfoBean();

        final PasswordData currentPassword = pwmRequest.readParameterAsPassword( "currentPassword" );

        // check the current password
        if ( cpb.isCurrentPasswordRequired() && loginBean.getUserCurrentPassword() != null )
        {
            if ( currentPassword == null )
            {
                LOGGER.debug( pwmRequest, () -> "failed password validation check: currentPassword value is missing" );
                setLastError( pwmRequest, new ErrorInformation( PwmError.ERROR_MISSING_PARAMETER ) );
                return ProcessStatus.Continue;
            }

            final boolean passed;
            {
                final boolean caseSensitive = Boolean.parseBoolean(
                        userInfo.getPasswordPolicy().getValue( PwmPasswordRule.CaseSensitive ) );
                final PasswordData storedPassword = loginBean.getUserCurrentPassword();
                passed = caseSensitive ? storedPassword.equals( currentPassword ) : storedPassword.equalsIgnoreCase( currentPassword );
            }

            if ( !passed )
            {
                pwmRequest.getPwmApplication().getIntruderManager().convenience().markUserIdentity(
                        userInfo.getUserIdentity(), pwmRequest.getLabel() );
                LOGGER.debug( pwmRequest, () -> "failed password validation check: currentPassword value is incorrect" );
                setLastError( pwmRequest, new ErrorInformation( PwmError.ERROR_BAD_CURRENT_PASSWORD ) );
                return ProcessStatus.Continue;
            }
            cpb.setCurrentPasswordPassed( true );
        }

        final List<FormConfiguration> formItem = pwmRequest.getConfig().readSettingAsForm( PwmSetting.PASSWORD_REQUIRE_FORM );

        try
        {
            //read the values from the request
            final Map<FormConfiguration, String> formValues = FormUtility.readFormValuesFromRequest(
                    pwmRequest, formItem, ssBean.getLocale() );

            ChangePasswordServletUtil.validateParamsAgainstLDAP( formValues, pwmRequest,
                    pwmRequest.getPwmSession().getSessionManager().getActor( ) );

            cpb.setFormPassed( true );
        }
        catch ( final PwmOperationalException e )
        {
            pwmRequest.getPwmApplication().getIntruderManager().convenience().markAddressAndSession( pwmRequest );
            pwmRequest.getPwmApplication().getIntruderManager().convenience().markUserIdentity( userInfo.getUserIdentity(), pwmRequest.getLabel() );
            LOGGER.debug( pwmRequest, e.getErrorInformation() );
            setLastError( pwmRequest, e.getErrorInformation() );
            return ProcessStatus.Continue;
        }

        return ProcessStatus.Continue;
    }

    @ActionHandler( action = "checkProgress" )
    ProcessStatus processCheckProgressAction( final PwmRequest pwmRequest ) throws ServletException, PwmUnrecoverableException, IOException
    {
        final ChangePasswordBean changePasswordBean = pwmRequest.getPwmApplication().getSessionStateService().getBean( pwmRequest, ChangePasswordBean.class );
        final PasswordChangeProgressChecker.ProgressTracker progressTracker = changePasswordBean.getChangeProgressTracker();
        final PasswordChangeProgressChecker.PasswordChangeProgress passwordChangeProgress;
        if ( progressTracker == null )
        {
            passwordChangeProgress = PasswordChangeProgressChecker.PasswordChangeProgress.COMPLETE;
        }
        else
        {
            final PasswordChangeProgressChecker checker = new PasswordChangeProgressChecker(
                    pwmRequest.getPwmApplication(),
                    pwmRequest.getPwmSession().getUserInfo().getUserIdentity(),
                    pwmRequest.getLabel(),
                    pwmRequest.getLocale()
            );
            passwordChangeProgress = checker.figureProgress( progressTracker );
        }
        final RestResultBean restResultBean = RestResultBean.withData( passwordChangeProgress );
        LOGGER.trace( pwmRequest, () -> "returning result for restCheckProgress: " + JsonUtil.serialize( restResultBean ) );
        pwmRequest.outputJsonResult( restResultBean );
        return ProcessStatus.Halt;
    }


    @ActionHandler( action = "complete" )
    public ProcessStatus processCompleteAction( final PwmRequest pwmRequest ) throws ServletException, PwmUnrecoverableException, IOException
    {
        final ChangePasswordBean cpb = pwmRequest.getPwmApplication().getSessionStateService().getBean( pwmRequest, ChangePasswordBean.class );
        final PasswordChangeProgressChecker.ProgressTracker progressTracker = cpb.getChangeProgressTracker();
        boolean isComplete = true;
        if ( progressTracker != null )
        {
            final PasswordChangeProgressChecker checker = new PasswordChangeProgressChecker(
                    pwmRequest.getPwmApplication(),
                    pwmRequest.getPwmSession().getUserInfo().getUserIdentity(),
                    pwmRequest.getLabel(),
                    pwmRequest.getLocale()
            );
            final PasswordChangeProgressChecker.PasswordChangeProgress passwordChangeProgress = checker.figureProgress( progressTracker );
            isComplete = passwordChangeProgress.isComplete();
        }

        if ( isComplete )
        {
            if ( progressTracker != null )
            {
                final TimeDuration totalTime = TimeDuration.fromCurrent( progressTracker.getBeginTime() );
                try
                {
                    pwmRequest.getPwmApplication().getStatisticsManager().updateAverageValue( AvgStatistic.AVG_PASSWORD_SYNC_TIME, totalTime.asMillis() );
                    LOGGER.trace( pwmRequest, () -> "password sync process marked completed (" + totalTime.asCompactString() + ")" );
                }
                catch ( final Exception e )
                {
                    LOGGER.error( pwmRequest, () -> "unable to update average password sync time statistic: " + e.getMessage() );
                }
            }
            cpb.setChangeProgressTracker( null );
            final Locale locale = pwmRequest.getLocale();
            final String completeMessage = pwmRequest.getConfig().readSettingAsLocalizedString( PwmSetting.PASSWORD_COMPLETE_MESSAGE, locale );

            pwmRequest.getPwmApplication().getSessionStateService().clearBean( pwmRequest, ChangePasswordBean.class );
            if ( completeMessage != null && !completeMessage.isEmpty() )
            {
                final MacroMachine macroMachine = pwmRequest.getPwmSession().getSessionManager().getMacroMachine( );
                final String expandedText = macroMachine.expandMacros( completeMessage );
                pwmRequest.setAttribute( PwmRequestAttribute.CompleteText, expandedText );
                pwmRequest.forwardToJsp( JspUrl.PASSWORD_COMPLETE );
            }
            else
            {
                pwmRequest.getPwmResponse().forwardToSuccessPage( Message.Success_PasswordChange );
            }
        }
        else
        {
            forwardToWaitPage( pwmRequest );
        }
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "checkPassword" )
    private ProcessStatus processCheckPasswordAction( final PwmRequest pwmRequest ) throws IOException, PwmUnrecoverableException, ChaiUnavailableException
    {
        final RestCheckPasswordServer.JsonInput jsonInput = JsonUtil.deserialize(
                pwmRequest.readRequestBodyAsString(),
                RestCheckPasswordServer.JsonInput.class
        );

        final UserInfo userInfo = pwmRequest.getPwmSession().getUserInfo();
        final PasswordUtility.PasswordCheckInfo passwordCheckInfo = PasswordUtility.checkEnteredPassword(
                pwmRequest.getPwmApplication(),
                pwmRequest.getLocale(),
                pwmRequest.getPwmSession().getSessionManager().getActor(),
                userInfo,
                pwmRequest.getPwmSession().getLoginInfoBean(),
                PasswordData.forStringValue( jsonInput.getPassword1() ),
                PasswordData.forStringValue( jsonInput.getPassword2() )
        );


        final RestCheckPasswordServer.JsonOutput checkResult = RestCheckPasswordServer.JsonOutput.fromPasswordCheckInfo( passwordCheckInfo );

        final RestResultBean restResultBean = RestResultBean.withData( checkResult );
        pwmRequest.outputJsonResult( restResultBean );

        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "randomPassword" )
    private ProcessStatus processRandomPasswordAction( final PwmRequest pwmRequest ) throws IOException, PwmUnrecoverableException, ChaiUnavailableException
    {
        final PasswordData passwordData = RandomPasswordGenerator.createRandomPassword(
                pwmRequest.getLabel(),
                pwmRequest.getPwmSession().getUserInfo().getPasswordPolicy(),
                pwmRequest.getPwmApplication() );

        final RestRandomPasswordServer.JsonOutput jsonOutput = new RestRandomPasswordServer.JsonOutput();
        jsonOutput.setPassword( passwordData.getStringValue() );
        final RestResultBean restResultBean = RestResultBean.withData( jsonOutput );
        pwmRequest.outputJsonResult( restResultBean );
        return ProcessStatus.Halt;
    }

    public void nextStep(
            final PwmRequest pwmRequest
    )
            throws IOException, PwmUnrecoverableException, ServletException
    {
        final ChangePasswordBean changePasswordBean = pwmRequest.getPwmApplication().getSessionStateService().getBean( pwmRequest, ChangePasswordBean.class );

        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final Configuration config = pwmApplication.getConfig();

        if ( changePasswordBean.getChangeProgressTracker() != null )
        {
            forwardToWaitPage( pwmRequest );
            return;
        }

        if ( ChangePasswordServletUtil.warnPageShouldBeShown( pwmRequest, changePasswordBean ) )
        {
            LOGGER.trace( pwmRequest, () -> "password expiration is within password warn period, forwarding user to warning page" );
            pwmRequest.forwardToJsp( JspUrl.PASSWORD_WARN );
            return;
        }

        final String agreementMsg = pwmApplication.getConfig().readSettingAsLocalizedString( PwmSetting.PASSWORD_CHANGE_AGREEMENT_MESSAGE, pwmRequest.getLocale() );
        if ( agreementMsg != null && agreementMsg.length() > 0 && !changePasswordBean.isAgreementPassed() )
        {
            final MacroMachine macroMachine = pwmSession.getSessionManager().getMacroMachine();
            final String expandedText = macroMachine.expandMacros( agreementMsg );
            pwmRequest.setAttribute( PwmRequestAttribute.AgreementText, expandedText );
            pwmRequest.forwardToJsp( JspUrl.PASSWORD_AGREEMENT );
            return;
        }

        if ( ChangePasswordServletUtil.determineIfCurrentPasswordRequired( pwmRequest ) && !changePasswordBean.isCurrentPasswordPassed() )
        {
            forwardToFormPage( pwmRequest );
            return;
        }

        if ( !config.readSettingAsForm( PwmSetting.PASSWORD_REQUIRE_FORM ).isEmpty() && !changePasswordBean.isFormPassed() )
        {
            forwardToFormPage( pwmRequest );
            return;
        }

        changePasswordBean.setAllChecksPassed( true );
        forwardToChangePage( pwmRequest );
    }


    private void forwardToFormPage( final PwmRequest pwmRequest )
            throws ServletException, PwmUnrecoverableException, IOException
    {
        pwmRequest.addFormInfoToRequestAttr( PwmSetting.PASSWORD_REQUIRE_FORM, false, false );
        pwmRequest.forwardToJsp( JspUrl.PASSWORD_FORM );
    }

    private void forwardToWaitPage( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException, ServletException, IOException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final ChangePasswordBean changePasswordBean = pwmApplication.getSessionStateService().getBean( pwmRequest, ChangePasswordBean.class );
        final Instant maxCompleteTime = changePasswordBean.getChangePasswordMaxCompletion();
        pwmRequest.setAttribute(
                PwmRequestAttribute.ChangePassword_MaxWaitSeconds,
                maxCompleteTime == null ? 30 : TimeDuration.fromCurrent( maxCompleteTime ).as( TimeDuration.Unit.SECONDS )
        );

        pwmRequest.setAttribute(
                PwmRequestAttribute.ChangePassword_CheckIntervalSeconds,
                Long.parseLong( pwmRequest.getConfig().readAppProperty( AppProperty.CLIENT_AJAX_PW_WAIT_CHECK_SECONDS ) )
        );

        pwmRequest.forwardToJsp( JspUrl.PASSWORD_CHANGE_WAIT );
    }

    @Override
    public ProcessStatus preProcessCheck( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException, IOException, ServletException
    {
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final ChangePasswordBean changePasswordBean = pwmApplication.getSessionStateService().getBean( pwmRequest, ChangePasswordBean.class );

        if ( pwmSession.getLoginInfoBean().getType() == AuthenticationType.AUTH_WITHOUT_PASSWORD )
        {
            throw new PwmUnrecoverableException( PwmError.ERROR_PASSWORD_REQUIRED );
        }

        if ( !pwmRequest.isAuthenticated() )
        {
            pwmRequest.respondWithError( PwmError.ERROR_AUTHENTICATION_REQUIRED.toInfo() );
            LOGGER.debug( pwmRequest, () -> "rejecting action request for unauthenticated session" );
            return ProcessStatus.Halt;

        }

        if ( ChangePasswordServletUtil.determineIfCurrentPasswordRequired( pwmRequest ) )
        {
            changePasswordBean.setCurrentPasswordRequired( true );
        }

        if ( !pwmSession.getSessionManager().checkPermission( pwmApplication, Permission.CHANGE_PASSWORD ) )
        {
            pwmRequest.respondWithError( PwmError.ERROR_UNAUTHORIZED.toInfo() );
            return ProcessStatus.Halt;
        }

        ChangePasswordServletUtil.checkMinimumLifetime( pwmRequest, changePasswordBean, pwmSession.getUserInfo() );

        return ProcessStatus.Continue;
    }

    private void forwardToChangePage( final PwmRequest pwmRequest ) throws ServletException, PwmUnrecoverableException, IOException
    {
        final String passwordPolicyChangeMessage = pwmRequest.getPwmSession().getUserInfo().getPasswordPolicy().getRuleHelper().getChangeMessage();
        if ( passwordPolicyChangeMessage.length() > 1 )
        {
            final MacroMachine macroMachine = pwmRequest.getPwmSession().getSessionManager().getMacroMachine( );
            macroMachine.expandMacros( passwordPolicyChangeMessage );
            pwmRequest.setAttribute( PwmRequestAttribute.ChangePassword_PasswordPolicyChangeMessage, passwordPolicyChangeMessage );
        }

        pwmRequest.forwardToJsp( JspUrl.PASSWORD_CHANGE );
    }
}


