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

package password.pwm.http.servlet.changepw;

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.AppProperty;
import password.pwm.PwmDomain;
import password.pwm.bean.LocalSessionStateBean;
import password.pwm.bean.LoginInfoBean;
import password.pwm.config.PwmSetting;
import password.pwm.config.profile.ChangePasswordProfile;
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
import password.pwm.ldap.auth.AuthenticationType;
import password.pwm.svc.event.AuditEvent;
import password.pwm.svc.event.AuditRecord;
import password.pwm.svc.event.AuditRecordFactory;
import password.pwm.svc.event.AuditServiceClient;
import password.pwm.svc.intruder.IntruderServiceClient;
import password.pwm.svc.stats.AvgStatistic;
import password.pwm.user.UserInfo;
import password.pwm.util.PasswordData;
import password.pwm.util.form.FormUtility;
import password.pwm.util.java.EnumUtil;
import password.pwm.util.java.PwmUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroRequest;
import password.pwm.util.password.PasswordUtility;
import password.pwm.util.password.PwmPasswordRuleValidator;
import password.pwm.ws.server.RestResultBean;
import password.pwm.ws.server.rest.RestCheckPasswordServer;
import password.pwm.ws.server.rest.RestRandomPasswordServer;

import javax.servlet.ServletException;
import java.io.IOException;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * User interaction servlet for changing (self) passwords.
 *
 * @author Jason D. Rivard.
 */

public abstract class ChangePasswordServlet extends ControlledPwmServlet
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( ChangePasswordServlet.class );

    @Override
    protected PwmLogger getLogger()
    {
        return LOGGER;
    }

    public enum ChangePasswordAction implements ProcessAction
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

        @Override
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
    public Optional<Class<? extends ProcessAction>> getProcessActionsClass( )
    {
        return Optional.of( ChangePasswordServlet.ChangePasswordAction.class );
    }

    static ChangePasswordProfile getProfile( final PwmRequest pwmRequest ) throws PwmUnrecoverableException
    {
        return pwmRequest.getChangePasswordProfile( );
    }

    static ChangePasswordBean getBean( final PwmRequest pwmRequest ) throws PwmUnrecoverableException
    {
        return pwmRequest.getPwmDomain().getSessionStateService().getBean( pwmRequest, ChangePasswordBean.class );
    }

    @ActionHandler( action = "reset" )
    public ProcessStatus processResetAction( final PwmRequest pwmRequest ) throws ServletException, PwmUnrecoverableException, IOException
    {

        final PwmSession pwmSession = pwmRequest.getPwmSession();
        if ( pwmSession.getLoginInfoBean().getAuthFlags().contains( AuthenticationType.AUTH_FROM_PUBLIC_MODULE ) )
        {
            // Must have gotten here from the "Forgotten Password" link.  Better clear out the temporary authentication
            pwmSession.unAuthenticateUser( pwmRequest );
        }

        pwmRequest.getPwmDomain().getSessionStateService().clearBean( pwmRequest, ChangePasswordBean.class );
        pwmRequest.getPwmResponse().sendRedirectToContinue();

        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "warnResponse" )
    public ProcessStatus processWarnResponse( final PwmRequest pwmRequest ) throws ServletException, PwmUnrecoverableException, IOException
    {
        final ChangePasswordBean changePasswordBean = getBean( pwmRequest );

        if ( pwmRequest.getPwmSession().getUserInfo().getPasswordStatus().isWarnPeriod() )
        {
            final String warnResponseStr = pwmRequest.readParameterAsString( "warnResponse" );
            final Optional<WarnResponseValue> warnResponse = EnumUtil.readEnumFromString( WarnResponseValue.class, warnResponseStr );
            if ( warnResponse.isPresent() )
            {
                switch ( warnResponse.get() )
                {
                    case skip:
                        pwmRequest.getPwmSession().getLoginInfoBean().setFlag( LoginInfoBean.LoginFlag.skipNewPw );
                        pwmRequest.getPwmResponse().sendRedirectToContinue();
                        return ProcessStatus.Halt;

                    case change:
                        changePasswordBean.setWarnPassed( true );
                        break;

                    default:
                        PwmUtil.unhandledSwitchStatement( warnResponse );
                }
            }
        }

        return ProcessStatus.Continue;
    }

    @ActionHandler( action = "change" )
    public ProcessStatus processChangeAction( final PwmRequest pwmRequest ) throws ServletException, PwmUnrecoverableException, IOException, ChaiUnavailableException
    {
        final ChangePasswordBean changePasswordBean = getBean( pwmRequest );

        final UserInfo userInfo = pwmRequest.getPwmSession().getUserInfo();

        if ( !changePasswordBean.isAllChecksPassed() )
        {
            return ProcessStatus.Continue;
        }

        final PasswordData password1 = pwmRequest.readParameterAsPassword( "password1" )
                .orElseThrow( () -> PwmUnrecoverableException.newException( PwmError.ERROR_FIELD_REQUIRED, "missing password1 field" ) );
        final PasswordData password2 = pwmRequest.readParameterAsPassword( "password2" )
                .orElseThrow( () -> PwmUnrecoverableException.newException( PwmError.ERROR_FIELD_REQUIRED, "missing password2 field" ) );

        // check the password meets the requirements
        try
        {
            final ChaiUser theUser = pwmRequest.getClientConnectionHolder().getActor( );
            final PwmPasswordRuleValidator pwmPasswordRuleValidator = PwmPasswordRuleValidator.create(
                    pwmRequest.getLabel(), pwmRequest.getPwmDomain(), userInfo.getPasswordPolicy() );
            final PasswordData oldPassword = pwmRequest.getPwmSession().getLoginInfoBean().getUserCurrentPassword();
            pwmPasswordRuleValidator.testPassword( pwmRequest.getLabel(), password1, oldPassword, userInfo, theUser );
        }
        catch ( final PwmDataValidationException e )
        {
            setLastError( pwmRequest, e.getErrorInformation() );
            LOGGER.debug( pwmRequest, () -> "failed password validation check: " + e.getErrorInformation().toDebugStr() );
            return ProcessStatus.Continue;
        }

        //make sure the two passwords match
        final boolean caseSensitive = userInfo.getPasswordPolicy().ruleHelper().readBooleanValue(
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
    public ProcessStatus processAgreeAction( final PwmRequest pwmRequest ) throws ServletException, PwmUnrecoverableException, IOException, ChaiUnavailableException
    {
        final ChangePasswordBean changePasswordBean = getBean( pwmRequest );

        LOGGER.debug( pwmRequest, () -> "user accepted password change agreement" );
        if ( !changePasswordBean.isAgreementPassed() )
        {
            changePasswordBean.setAgreementPassed( true );
            final AuditRecord auditRecord = AuditRecordFactory.make( pwmRequest ).createUserAuditRecord(
                    AuditEvent.AGREEMENT_PASSED,
                    pwmRequest.getUserInfoIfLoggedIn(),
                    pwmRequest.getLabel(),
                    "ChangePassword"
            );
            AuditServiceClient.submit( pwmRequest, auditRecord );
        }

        return ProcessStatus.Continue;
    }

    @ActionHandler( action = "form" )
    public ProcessStatus processFormAction( final PwmRequest pwmRequest )
            throws ServletException, PwmUnrecoverableException, IOException, ChaiUnavailableException
    {
        final ChangePasswordBean changePasswordBean = getBean( pwmRequest );
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final LocalSessionStateBean ssBean = pwmSession.getSessionStateBean();
        final UserInfo userInfo = pwmSession.getUserInfo();
        final LoginInfoBean loginBean = pwmSession.getLoginInfoBean();

        final PasswordData currentPassword = pwmRequest.readParameterAsPassword( "currentPassword" )
                .orElseThrow( () -> new NoSuchElementException( "missing currentPassword field" ) );


        // check the current password
        if ( changePasswordBean.isCurrentPasswordRequired() && loginBean.getUserCurrentPassword() != null )
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
                IntruderServiceClient.markUserIdentity( pwmRequest, userInfo.getUserIdentity() );
                LOGGER.debug( pwmRequest, () -> "failed password validation check: currentPassword value is incorrect" );
                setLastError( pwmRequest, new ErrorInformation( PwmError.ERROR_BAD_CURRENT_PASSWORD ) );
                return ProcessStatus.Continue;
            }
            changePasswordBean.setCurrentPasswordPassed( true );
        }

        final List<FormConfiguration> formItem = getProfile( pwmRequest ).readSettingAsForm( PwmSetting.PASSWORD_REQUIRE_FORM );

        try
        {
            //read the values from the request
            final Map<FormConfiguration, String> formValues = FormUtility.readFormValuesFromRequest(
                    pwmRequest, formItem, ssBean.getLocale() );

            ChangePasswordServletUtil.validateParamsAgainstLDAP( formValues, pwmRequest, pwmRequest.getClientConnectionHolder().getActor( ) );

            changePasswordBean.setFormPassed( true );
        }
        catch ( final PwmOperationalException e )
        {
            IntruderServiceClient.markAddressAndSession( pwmRequest );
            IntruderServiceClient.markUserIdentity( pwmRequest, userInfo.getUserIdentity() );
            LOGGER.debug( pwmRequest, e.getErrorInformation() );
            setLastError( pwmRequest, e.getErrorInformation() );
            return ProcessStatus.Continue;
        }

        return ProcessStatus.Continue;
    }

    @ActionHandler( action = "checkProgress" )
    public ProcessStatus processCheckProgressAction( final PwmRequest pwmRequest ) throws ServletException, PwmUnrecoverableException, IOException
    {
        final ChangePasswordBean changePasswordBean = getBean( pwmRequest );

        final PasswordChangeProgressChecker.ProgressTracker progressTracker = changePasswordBean.getChangeProgressTracker();
        final PasswordChangeProgressChecker.PasswordChangeProgress passwordChangeProgress;
        if ( progressTracker == null )
        {
            passwordChangeProgress = PasswordChangeProgressChecker.PasswordChangeProgress.COMPLETE;
        }
        else
        {
            final PasswordChangeProgressChecker checker = new PasswordChangeProgressChecker(
                    pwmRequest.getPwmDomain(),
                    getProfile( pwmRequest ),
                    pwmRequest.getPwmSession().getUserInfo().getUserIdentity(),
                    pwmRequest.getLabel(),
                    pwmRequest.getLocale()
            );
            passwordChangeProgress = checker.figureProgress( progressTracker );
        }
        final RestResultBean<PasswordChangeProgressChecker.PasswordChangeProgress> restResultBean = RestResultBean.withData(
                passwordChangeProgress,
                PasswordChangeProgressChecker.PasswordChangeProgress.class );
        LOGGER.trace( pwmRequest, () -> "returning result for restCheckProgress: " + JsonFactory.get().serialize( restResultBean ) );
        pwmRequest.outputJsonResult( restResultBean );
        return ProcessStatus.Halt;
    }


    @ActionHandler( action = "complete" )
    public ProcessStatus processCompleteAction( final PwmRequest pwmRequest ) throws ServletException, PwmUnrecoverableException, IOException
    {
        final ChangePasswordBean changePasswordBean = getBean( pwmRequest );

        final PasswordChangeProgressChecker.ProgressTracker progressTracker = changePasswordBean.getChangeProgressTracker();
        boolean isComplete = true;
        if ( progressTracker != null )
        {
            final PasswordChangeProgressChecker checker = new PasswordChangeProgressChecker(
                    pwmRequest.getPwmDomain(),
                    getProfile( pwmRequest ),
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
                    pwmRequest.getPwmDomain().getStatisticsService().updateAverageValue( AvgStatistic.AVG_PASSWORD_SYNC_TIME, totalTime.asMillis() );
                    LOGGER.trace( pwmRequest, () -> "password sync process marked completed (" + totalTime.asCompactString() + ")" );
                }
                catch ( final Exception e )
                {
                    LOGGER.error( pwmRequest, () -> "unable to update average password sync time statistic: " + e.getMessage() );
                }
            }
            changePasswordBean.setChangeProgressTracker( null );
            final Locale locale = pwmRequest.getLocale();
            final String completeMessage = getProfile( pwmRequest ).readSettingAsLocalizedString( PwmSetting.PASSWORD_COMPLETE_MESSAGE, locale );

            pwmRequest.getPwmDomain().getSessionStateService().clearBean( pwmRequest, ChangePasswordBean.class );
            if ( completeMessage != null && !completeMessage.isEmpty() )
            {
                final MacroRequest macroRequest = pwmRequest.getMacroMachine( );
                final String expandedText = macroRequest.expandMacros( completeMessage );
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
    public ProcessStatus processCheckPasswordAction( final PwmRequest pwmRequest )
            throws IOException, PwmUnrecoverableException, ChaiUnavailableException
    {
        final RestCheckPasswordServer.JsonInput jsonInput =
                pwmRequest.readBodyAsJsonObject( RestCheckPasswordServer.JsonInput.class );

        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final UserInfo userInfo = pwmSession.getUserInfo();
        final PasswordUtility.PasswordCheckInfo passwordCheckInfo = PasswordUtility.checkEnteredPassword(
                pwmRequest.getPwmRequestContext(),
                pwmRequest.getClientConnectionHolder().getActor(),
                userInfo,
                pwmSession.getLoginInfoBean(),
                PasswordData.forStringValue( jsonInput.getPassword1() ),
                PasswordData.forStringValue( jsonInput.getPassword2() )
        );


        final RestCheckPasswordServer.JsonOutput checkResult = RestCheckPasswordServer.JsonOutput.fromPasswordCheckInfo( passwordCheckInfo );

        final RestResultBean restResultBean = RestResultBean.withData( checkResult, RestCheckPasswordServer.JsonOutput.class );
        pwmRequest.outputJsonResult( restResultBean );

        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "randomPassword" )
    public ProcessStatus processRandomPasswordAction( final PwmRequest pwmRequest ) throws IOException, PwmUnrecoverableException, ChaiUnavailableException
    {
        final PasswordData passwordData = PasswordUtility.generateRandom(
                pwmRequest.getLabel(),
                pwmRequest.getPwmSession().getUserInfo().getPasswordPolicy(),
                pwmRequest.getPwmDomain() );

        final RestRandomPasswordServer.JsonOutput jsonOutput = new RestRandomPasswordServer.JsonOutput();
        jsonOutput.setPassword( passwordData.getStringValue() );
        final RestResultBean<RestRandomPasswordServer.JsonOutput> restResultBean = RestResultBean.withData( jsonOutput, RestRandomPasswordServer.JsonOutput.class );
        pwmRequest.outputJsonResult( restResultBean );
        return ProcessStatus.Halt;
    }

    @Override
    public void nextStep(
            final PwmRequest pwmRequest
    )
            throws IOException, PwmUnrecoverableException, ServletException
    {
        final ChangePasswordBean changePasswordBean = getBean( pwmRequest );
        final ChangePasswordProfile changePasswordProfile = getProfile( pwmRequest );

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

        final String agreementMsg = changePasswordProfile.readSettingAsLocalizedString( PwmSetting.PASSWORD_CHANGE_AGREEMENT_MESSAGE, pwmRequest.getLocale() );
        if ( StringUtil.notEmpty( agreementMsg ) && !changePasswordBean.isAgreementPassed() )
        {
            final MacroRequest macroRequest = pwmRequest.getMacroMachine();
            final String expandedText = macroRequest.expandMacros( agreementMsg );
            pwmRequest.setAttribute( PwmRequestAttribute.AgreementText, expandedText );
            pwmRequest.forwardToJsp( JspUrl.PASSWORD_AGREEMENT );
            return;
        }

        if ( ChangePasswordServletUtil.determineIfCurrentPasswordRequired( pwmRequest ) && !changePasswordBean.isCurrentPasswordPassed() )
        {
            forwardToFormPage( pwmRequest );
            return;
        }

        if ( !changePasswordProfile.readSettingAsForm( PwmSetting.PASSWORD_REQUIRE_FORM ).isEmpty() && !changePasswordBean.isFormPassed() )
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
        final List<FormConfiguration> formConfigurations = getProfile( pwmRequest ).readSettingAsForm( PwmSetting.PASSWORD_REQUIRE_FORM );
        pwmRequest.addFormInfoToRequestAttr( formConfigurations, new LinkedHashMap<>(), false, false );
        pwmRequest.forwardToJsp( JspUrl.PASSWORD_FORM );
    }

    private void forwardToWaitPage( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException, ServletException, IOException
    {
        final ChangePasswordBean changePasswordBean = getBean( pwmRequest );
        final Instant maxCompleteTime = changePasswordBean.getChangePasswordMaxCompletion();
        pwmRequest.setAttribute(
                PwmRequestAttribute.ChangePassword_MaxWaitSeconds,
                maxCompleteTime == null ? 30 : TimeDuration.fromCurrent( maxCompleteTime ).as( TimeDuration.Unit.SECONDS )
        );

        pwmRequest.setAttribute(
                PwmRequestAttribute.ChangePassword_CheckIntervalSeconds,
                Long.parseLong( pwmRequest.getDomainConfig().readAppProperty( AppProperty.CLIENT_AJAX_PW_WAIT_CHECK_SECONDS ) )
        );

        pwmRequest.forwardToJsp( JspUrl.PASSWORD_CHANGE_WAIT );
    }

    @Override
    public ProcessStatus preProcessCheck( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException, IOException, ServletException
    {
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final PwmDomain pwmDomain = pwmRequest.getPwmDomain();
        final ChangePasswordBean changePasswordBean = pwmDomain.getSessionStateService().getBean( pwmRequest, ChangePasswordBean.class );

        if ( !pwmDomain.getConfig().readSettingAsBoolean( PwmSetting.CHANGE_PASSWORD_ENABLE ) )
        {
            pwmRequest.respondWithError( new ErrorInformation(
                    PwmError.ERROR_SERVICE_NOT_AVAILABLE,
                    "Setting " + PwmSetting.CHANGE_PASSWORD_ENABLE.toMenuLocationDebug( null, null ) + " is not enabled." )
            );
            return ProcessStatus.Halt;
        }

        getProfile( pwmRequest );

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

        ChangePasswordServletUtil.checkMinimumLifetime( pwmRequest, changePasswordBean, pwmSession.getUserInfo() );

        return ProcessStatus.Continue;
    }

    private void forwardToChangePage( final PwmRequest pwmRequest ) throws ServletException, PwmUnrecoverableException, IOException
    {
        pwmRequest.forwardToJsp( JspUrl.PASSWORD_CHANGE );
    }
}


