/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2023 The PWM Project
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

package password.pwm.http.servlet.forgottenpw;

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.cr.Challenge;
import com.novell.ldapchai.cr.ResponseSet;
import com.novell.ldapchai.cr.bean.ChallengeBean;
import com.novell.ldapchai.cr.bean.ChallengeSetBean;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.TokenDestinationItem;
import password.pwm.bean.UserIdentity;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.IdentityVerificationMethod;
import password.pwm.config.option.RecoveryAction;
import password.pwm.config.option.SelectableContextMode;
import password.pwm.config.profile.LdapProfile;
import password.pwm.config.profile.PwmPasswordPolicy;
import password.pwm.config.profile.PwmPasswordRule;
import password.pwm.config.value.data.FormConfiguration;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmDataValidationException;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequestContext;
import password.pwm.http.PwmRequestAttribute;
import password.pwm.http.bean.ForgottenPasswordBean;
import password.pwm.http.bean.ForgottenPasswordStage;
import password.pwm.http.tag.PasswordRequirementsTag;
import password.pwm.i18n.Display;
import password.pwm.i18n.Message;
import password.pwm.ldap.UserInfo;
import password.pwm.ldap.UserInfoFactory;
import password.pwm.ldap.auth.AuthenticationUtility;
import password.pwm.ldap.auth.SessionAuthenticator;
import password.pwm.ldap.search.SearchConfiguration;
import password.pwm.ldap.search.UserSearchEngine;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsManager;
import password.pwm.svc.token.TokenPayload;
import password.pwm.svc.token.TokenService;
import password.pwm.svc.token.TokenType;
import password.pwm.svc.token.TokenUtil;
import password.pwm.util.PasswordData;
import password.pwm.util.form.FormUtility;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroRequest;
import password.pwm.util.password.PasswordUtility;
import password.pwm.util.operations.otp.OTPUserRecord;
import password.pwm.ws.server.PresentableForm;
import password.pwm.ws.server.PresentableFormRow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ForgottenPasswordStateMachine
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( ForgottenPasswordStateMachine.class );

    private static final Map<ForgottenPasswordStage, StageHandler> STAGE_HANDLERS;

    private static final String PARAM_PASSWORD = "password1";
    private static final String PARAM_PASSWORD_CONFIRM = "password2";

    static
    {
        final Map<ForgottenPasswordStage, StageHandler> stageStateHandlerMap = new HashMap<>();
        stageStateHandlerMap.put( ForgottenPasswordStage.IDENTIFICATION, new IdentificationStageHandler() );
        stageStateHandlerMap.put( ForgottenPasswordStage.METHOD_CHOICE, new MethodChoiceStageHandler() );
        stageStateHandlerMap.put( ForgottenPasswordStage.TOKEN_CHOICE, new TokenChoiceStageHandler() );
        stageStateHandlerMap.put( ForgottenPasswordStage.VERIFICATION, new VerificationStageHandler() );
        stageStateHandlerMap.put( ForgottenPasswordStage.ACTION_CHOICE, new ActionChoiceStageHandler() );
        stageStateHandlerMap.put( ForgottenPasswordStage.NEW_PASSWORD, new PasswordChangeStageHandler() );
        stageStateHandlerMap.put( ForgottenPasswordStage.COMPLETE, new CompletedStageHandler() );
        STAGE_HANDLERS = Collections.unmodifiableMap( stageStateHandlerMap );
    }

    interface StageHandler
    {
        void applyForm( ForgottenPasswordStateMachine forgottenPasswordStateMachine, Map<String, String> formValues ) throws PwmUnrecoverableException;

        PresentableForm generateForm( ForgottenPasswordStateMachine forgottenPasswordStateMachine )
                throws PwmUnrecoverableException;
    }

    private ForgottenPasswordBean forgottenPasswordBean;
    private final PwmRequestContext pwmRequestContext;
    private final Map<PwmRequestAttribute, String> requestFlags = new ConcurrentHashMap<>();

    public ForgottenPasswordStateMachine(
            final PwmRequestContext pwmRequestContext,
            final ForgottenPasswordBean forgottenPasswordBean
    )
            throws PwmUnrecoverableException
    {
        this.pwmRequestContext = pwmRequestContext;
        updateBean( forgottenPasswordBean );
        nextStage();
    }

    private void updateBean( final ForgottenPasswordBean forgottenPasswordBean )
    {
        this.forgottenPasswordBean = forgottenPasswordBean == null
                ? new ForgottenPasswordBean()
                : JsonUtil.cloneUsingJson( forgottenPasswordBean, ForgottenPasswordBean.class );
    }

    public ForgottenPasswordBean getForgottenPasswordBean()
    {
        return forgottenPasswordBean;
    }

    PwmRequestContext getCommonValues()
    {
        return pwmRequestContext;
    }

    static Set<IdentityVerificationMethod> supportedVerificationMethods()
    {
        return Collections.unmodifiableSet( VerificationStageHandler.VERIFICATION_HANDLERS.keySet() );
    }

    public Map<PwmRequestAttribute, String> getRequestFlags()
    {
        return requestFlags;
    }


    public ForgottenPasswordStage nextStage()
            throws PwmUnrecoverableException
    {
        return ForgottenPasswordStageProcessor.nextStage( this );
    }


    void clear()
    {
        forgottenPasswordBean = new ForgottenPasswordBean();
        requestFlags.clear();
    }

    public void applyFormValues( final Map<String, String> values ) throws PwmUnrecoverableException
    {
        final ForgottenPasswordStage stage = nextStage();

        final StageHandler handler = STAGE_HANDLERS.get( stage );

        if ( handler != null )
        {
            handler.applyForm( this, values );
        }
        else
        {
            throw new IllegalStateException( "unhandled stage for apply form: " + stage.name() );
        }

        nextStage();
    }

    public PresentableForm nextForm()
            throws PwmUnrecoverableException
    {
        final ForgottenPasswordStage stage = nextStage();

        final StageHandler handler = STAGE_HANDLERS.get( stage );

        if ( handler != null )
        {
            return handler.generateForm( this );
        }
        else
        {
            throw new IllegalStateException( "unhandled stage for next form: " + stage.name() );
        }
    }

    static class CompletedStageHandler implements StageHandler
    {
        @Override
        public void applyForm( final ForgottenPasswordStateMachine forgottenPasswordStateMachine, final Map<String, String> formValues )
        {
            // action is complete, nothing further to execute
        }

        @Override
        public PresentableForm generateForm( final ForgottenPasswordStateMachine forgottenPasswordStateMachine )
        {
            final PwmRequestContext pwmRequestContext = forgottenPasswordStateMachine.getCommonValues();
            return PresentableForm.builder()
                    .label( LocaleHelper.getLocalizedMessage( pwmRequestContext.getLocale(), Display.Title_ChangePassword, pwmRequestContext.getConfig() ) )
                    .message( LocaleHelper.getLocalizedMessage( pwmRequestContext.getLocale(), Message.Success_PasswordChange, pwmRequestContext.getConfig() ) )
                    .build();
        }
    }

    static class PasswordChangeStageHandler implements StageHandler
    {
        private static final String PARAM_VERIFICATION_CHOICE = "verificationOnly";

        @Override
        public void applyForm( final ForgottenPasswordStateMachine forgottenPasswordStateMachine, final Map<String, String> formValues )
                throws PwmUnrecoverableException
        {
            final PwmRequestContext pwmRequestContext = forgottenPasswordStateMachine.getCommonValues();
            final PasswordData password1 = PasswordData.forStringValue( formValues.get( PARAM_PASSWORD ) );
            final PasswordData password2 = PasswordData.forStringValue( formValues.get( PARAM_PASSWORD_CONFIRM ) );

            final UserInfo userInfo = UserInfoFactory.newUserInfoUsingProxy( pwmRequestContext, forgottenPasswordStateMachine.getForgottenPasswordBean().getUserIdentity() );
            final boolean caseSensitive = userInfo.getPasswordPolicy().getRuleHelper().readBooleanValue(
                    PwmPasswordRule.CaseSensitive );
            if ( PasswordUtility.PasswordCheckInfo.MatchStatus.MATCH != PasswordUtility.figureMatchStatus( caseSensitive,
                    password1, password2 ) )
            {
                throw PwmUnrecoverableException.newException( PwmError.PASSWORD_DOESNOTMATCH, null );
            }

            final boolean verifyOnly = Boolean.parseBoolean( formValues.get( PARAM_VERIFICATION_CHOICE ) );

            try
            {
                if ( verifyOnly )
                {
                    final PasswordUtility.PasswordCheckInfo passwordCheckInfo = PasswordUtility.checkEnteredPassword(
                            pwmRequestContext.getPwmApplication(),
                            pwmRequestContext.getLocale(),
                            pwmRequestContext.getPwmApplication().getProxiedChaiUser( userInfo.getUserIdentity() ),
                            userInfo,
                            null,
                            password1,
                            password2
                    );

                    if ( !passwordCheckInfo.isPassed() )
                    {
                        final PwmError pwmError = PwmError.forErrorNumber( passwordCheckInfo.getErrorCode() );
                        throw PwmUnrecoverableException.newException( pwmError, passwordCheckInfo.getMessage() );
                    }
                }
                else
                {
                    PasswordUtility.setPassword(
                            forgottenPasswordStateMachine.getCommonValues().getPwmApplication(),
                            forgottenPasswordStateMachine.getCommonValues().getSessionLabel(),
                            forgottenPasswordStateMachine.getCommonValues().getPwmApplication().getProxyChaiProvider( userInfo.getUserIdentity().getLdapProfileID() ),
                            userInfo,
                            null,
                            password1 );
                }
            }
            catch ( final PwmOperationalException e )
            {
                throw new PwmUnrecoverableException( e.getErrorInformation() );
            }
            catch ( final ChaiUnavailableException e )
            {
                throw PwmUnrecoverableException.fromChaiException( e );
            }

            forgottenPasswordStateMachine.getForgottenPasswordBean().getProgress().setExecutedRecoveryAction( RecoveryAction.RESETPW );
        }

        @Override
        public PresentableForm generateForm( final ForgottenPasswordStateMachine forgottenPasswordStateMachine )
                throws PwmUnrecoverableException
        {
            final PwmRequestContext pwmRequestContext = forgottenPasswordStateMachine.getCommonValues();
            final Configuration config = forgottenPasswordStateMachine.getCommonValues().getConfig();
            final Locale locale = forgottenPasswordStateMachine.getCommonValues().getLocale();
            final UserIdentity userIdentity = forgottenPasswordStateMachine.getForgottenPasswordBean().getUserIdentity();
            final UserInfo userInfo = UserInfoFactory.newUserInfoUsingProxy( pwmRequestContext, userIdentity );
            final MacroRequest macroRequest = MacroRequest.forUser( pwmRequestContext, userIdentity );
            final PwmPasswordPolicy pwmPasswordPolicy = userInfo.getPasswordPolicy();

            final boolean valueMasking = pwmRequestContext.getConfig().readSettingAsBoolean( PwmSetting.DISPLAY_MASK_PASSWORD_FIELDS );
            final FormConfiguration.Type formType = valueMasking
                    ? FormConfiguration.Type.password
                    : FormConfiguration.Type.text;

            final List<PresentableFormRow> formRows = new ArrayList<>();
            formRows.add( PresentableFormRow.builder()
                    .name( PARAM_PASSWORD )
                    .type( formType )
                    .label( LocaleHelper.getLocalizedMessage( locale, Display.Field_Password, config ) )
                    .required( true )
                    .build() );
            formRows.add( PresentableFormRow.builder()
                    .name( PARAM_PASSWORD_CONFIRM )
                    .type( formType )
                    .label( LocaleHelper.getLocalizedMessage( locale, Display.Field_ConfirmPassword, config ) )
                    .required( true )
                    .build() );

            final List<String> passwordRequirementsList = PasswordRequirementsTag.getPasswordRequirementsStrings(
                    pwmPasswordPolicy,
                    pwmRequestContext.getConfig(),
                    pwmRequestContext.getLocale(),
                    macroRequest );

            final String ruleDelimiter = pwmRequestContext.getConfig().readAppProperty( AppProperty.REST_SERVER_FORGOTTEN_PW_RULE_DELIMITER );
            final String ruleText = StringUtil.collectionToString( passwordRequirementsList, ruleDelimiter );

            return PresentableForm.builder()
                    .label( LocaleHelper.getLocalizedMessage( pwmRequestContext.getLocale(), Display.Title_ChangePassword, pwmRequestContext.getConfig() ) )
                    .message( LocaleHelper.getLocalizedMessage( pwmRequestContext.getLocale(), Display.Display_ChangePassword, pwmRequestContext.getConfig() ) )
                    .messageDetail( ruleText )
                    .formRows( formRows )
                    .build();
        }
    }

    static class ActionChoiceStageHandler implements StageHandler
    {
        @Override
        public void applyForm( final ForgottenPasswordStateMachine forgottenPasswordStateMachine, final Map<String, String> formValues )
        {

        }

        @Override
        public PresentableForm generateForm( final ForgottenPasswordStateMachine forgottenPasswordStateMachine )
        {
            return PresentableForm.builder()
                    .message( "you win!" )
                    .build();
        }
    }

    static class TokenChoiceStageHandler implements StageHandler
    {
        @Override
        public void applyForm( final ForgottenPasswordStateMachine forgottenPasswordStateMachine, final Map<String, String> formValues )
                throws PwmUnrecoverableException
        {
            final List<TokenDestinationItem> tokenDestinationItems = ForgottenPasswordUtil.figureAvailableTokenDestinations(
                    forgottenPasswordStateMachine.getCommonValues(),
                    forgottenPasswordStateMachine.getForgottenPasswordBean() );

            final Optional<TokenDestinationItem> selectedItem = TokenDestinationItem.tokenDestinationItemForID( tokenDestinationItems, formValues.get( PwmConstants.PARAM_TOKEN ) );
            if ( selectedItem.isPresent() )
            {
                forgottenPasswordStateMachine.getForgottenPasswordBean().getProgress().setTokenDestination( selectedItem.get() );

                final UserInfo userInfo = ForgottenPasswordUtil.readUserInfo(
                        forgottenPasswordStateMachine.getCommonValues(),
                        forgottenPasswordStateMachine.getForgottenPasswordBean() );
                ForgottenPasswordUtil.initializeAndSendToken( forgottenPasswordStateMachine.getCommonValues(), userInfo, selectedItem.get() );
                forgottenPasswordStateMachine.getForgottenPasswordBean().getProgress().setTokenSent( true );
            }

        }

        @Override
        public PresentableForm generateForm( final ForgottenPasswordStateMachine forgottenPasswordStateMachine )
                throws PwmUnrecoverableException
        {
            final PwmRequestContext pwmRequestContext = forgottenPasswordStateMachine.getCommonValues();
            final List<TokenDestinationItem> tokenDestinationItems = ForgottenPasswordUtil.figureAvailableTokenDestinations(
                    forgottenPasswordStateMachine.getCommonValues(),
                    forgottenPasswordStateMachine.getForgottenPasswordBean() );

            final Map<String, String> selectOptions = new LinkedHashMap<>();

            for ( final TokenDestinationItem item : tokenDestinationItems )
            {
                selectOptions.put( item.getId(), item.longDisplay( pwmRequestContext.getLocale(), pwmRequestContext.getConfig() ) );
            }

            final PresentableFormRow formRow = PresentableFormRow.builder()
                    .name( PwmConstants.PARAM_TOKEN )
                    .type( FormConfiguration.Type.select )
                    .label( LocaleHelper.getLocalizedMessage( pwmRequestContext.getLocale(), Display.Button_Select, pwmRequestContext.getConfig() ) )
                    .selectOptions( selectOptions )
                    .required( true )
                    .build();

            return PresentableForm.builder()
                    .label( LocaleHelper.getLocalizedMessage( pwmRequestContext.getLocale(), Display.Title_ForgottenPassword, pwmRequestContext.getConfig() ) )
                    .message( LocaleHelper.getLocalizedMessage( pwmRequestContext.getLocale(), Display.Display_RecoverTokenSendChoices, pwmRequestContext.getConfig() ) )
                    .formRow( formRow )
                    .build();
        }
    }

    static class VerificationStageHandler implements StageHandler
    {
        private static final Map<IdentityVerificationMethod, StageHandler> VERIFICATION_HANDLERS;

        static
        {
            final Map<IdentityVerificationMethod, StageHandler> stageStateHandlerMap = new HashMap<>();
            stageStateHandlerMap.put( IdentityVerificationMethod.CHALLENGE_RESPONSES, new ChallengeResponseHandler() );
            stageStateHandlerMap.put( IdentityVerificationMethod.ATTRIBUTES, new AttributeVerificationHandler() );
            stageStateHandlerMap.put( IdentityVerificationMethod.TOKEN, new TokenVerificationHandler() );
            stageStateHandlerMap.put( IdentityVerificationMethod.OTP, new OTPVerificationHandler() );
            VERIFICATION_HANDLERS = Collections.unmodifiableMap( stageStateHandlerMap );
        }

        @Override
        public void applyForm( final ForgottenPasswordStateMachine forgottenPasswordStateMachine, final Map<String, String> formValues )
                throws PwmUnrecoverableException
        {
            final IdentityVerificationMethod method = forgottenPasswordStateMachine.getForgottenPasswordBean().getProgress().getInProgressVerificationMethod();

            final StageHandler handler = VERIFICATION_HANDLERS.get( method );

            if ( handler != null )
            {
                handler.applyForm( forgottenPasswordStateMachine, formValues );
            }
            else
            {
                throw new IllegalStateException( "unhandled method for apply form: " + method.name() );
            }
        }

        @Override
        public PresentableForm generateForm( final ForgottenPasswordStateMachine forgottenPasswordStateMachine )
                throws PwmUnrecoverableException
        {
            final IdentityVerificationMethod method = forgottenPasswordStateMachine.getForgottenPasswordBean().getProgress().getInProgressVerificationMethod();

            if ( method == null )
            {
                throw new IllegalStateException( "no verification methods in progress" );
            }

            final StageHandler handler = VERIFICATION_HANDLERS.get( method );

            if ( handler != null )
            {
                return handler.generateForm( forgottenPasswordStateMachine );
            }
            else
            {
                throw new IllegalStateException( "unhandled method for next form: " + method.name() );
            }
        }

        static class OTPVerificationHandler implements StageHandler
        {
            @Override
            public void applyForm( final ForgottenPasswordStateMachine forgottenPasswordStateMachine, final Map<String, String> formValues )
                    throws PwmUnrecoverableException
            {
                final PwmRequestContext pwmRequestContext = forgottenPasswordStateMachine.getCommonValues();
                final String userEnteredCode = formValues.get( PwmConstants.PARAM_OTP_TOKEN );

                final UserInfo userInfo = ForgottenPasswordUtil.readUserInfo( pwmRequestContext, forgottenPasswordStateMachine.getForgottenPasswordBean() );
                final OTPUserRecord otpUserRecord = userInfo.getOtpUserRecord();

                ErrorInformation errorInformation = null;

                boolean otpPassed = false;
                if ( otpUserRecord != null && !StringUtil.isEmpty( userEnteredCode ) )
                {
                    LOGGER.trace( pwmRequestContext.getSessionLabel(), () -> "checking entered OTP for user " + userInfo.getUserIdentity().toDisplayString() );
                    try
                    {
                        // forces service to use proxy account to update (write) updated otp record if necessary.
                        otpPassed = pwmRequestContext.getPwmApplication().getOtpService().validateToken(
                                null,
                                userInfo.getUserIdentity(),
                                otpUserRecord,
                                userEnteredCode,
                                true
                        );
                    }
                    catch ( final PwmOperationalException e )
                    {
                        errorInformation = new ErrorInformation( PwmError.ERROR_INCORRECT_OTP_TOKEN, e.getErrorInformation().toDebugStr() );
                    }
                }

                final String passedStr = otpPassed ? "passed" : "failed";
                LOGGER.trace( pwmRequestContext.getSessionLabel(), () -> "one time password validation has " + passedStr + " for user "
                        + userInfo.getUserIdentity().toDisplayString() );

                if ( otpPassed )
                {
                    pwmRequestContext.getPwmApplication().getStatisticsManager().incrementValue( Statistic.RECOVERY_OTP_PASSED );
                    forgottenPasswordStateMachine.getForgottenPasswordBean().getProgress().getSatisfiedMethods().add( IdentityVerificationMethod.OTP );
                }
                else
                {
                    errorInformation = errorInformation == null
                            ? new ErrorInformation( PwmError.ERROR_INCORRECT_OTP_TOKEN )
                            : errorInformation;
                    pwmRequestContext.getPwmApplication().getStatisticsManager().incrementValue( Statistic.RECOVERY_OTP_FAILED );
                    handleUserVerificationBadAttempt( pwmRequestContext, forgottenPasswordStateMachine.getForgottenPasswordBean(), errorInformation );
                    throw new PwmUnrecoverableException( errorInformation );
                }
            }

            @Override
            public PresentableForm generateForm( final ForgottenPasswordStateMachine forgottenPasswordStateMachine ) throws PwmUnrecoverableException
            {
                final PwmRequestContext pwmRequestContext = forgottenPasswordStateMachine.getCommonValues();

                final UserInfo userInfo = ForgottenPasswordUtil.readUserInfo(
                        forgottenPasswordStateMachine.getCommonValues(),
                        forgottenPasswordStateMachine.getForgottenPasswordBean() );

                final OTPUserRecord otpUserRecord = userInfo == null ? null : userInfo.getOtpUserRecord();

                final String identifier = otpUserRecord == null
                        ? null
                        : otpUserRecord.getIdentifier();

                final String message;
                if ( StringUtil.isEmpty( identifier ) )
                {
                    message = LocaleHelper.getLocalizedMessage( pwmRequestContext.getLocale(), Display.Display_RecoverOTP, pwmRequestContext.getConfig() );
                }
                else
                {
                    message = LocaleHelper.getLocalizedMessage( pwmRequestContext.getLocale(), Display.Display_RecoverOTPIdentified, pwmRequestContext.getConfig(), new String[]
                            {
                                    identifier,
                            }
                    );
                }

                final PresentableFormRow formRow = PresentableFormRow.builder()
                        .name( PwmConstants.PARAM_OTP_TOKEN )
                        .type( FormConfiguration.Type.text )
                        .label( LocaleHelper.getLocalizedMessage( pwmRequestContext.getLocale(), Display.Field_Code, pwmRequestContext.getConfig() ) )
                        .required( true )
                        .build();

                return PresentableForm.builder()
                        .label( LocaleHelper.getLocalizedMessage( pwmRequestContext.getLocale(), Display.Title_ForgottenPassword, pwmRequestContext.getConfig() ) )
                        .message( message )
                        .formRow( formRow )
                        .build();
            }
        }

        static class TokenVerificationHandler implements StageHandler
        {
            @Override
            public void applyForm( final ForgottenPasswordStateMachine forgottenPasswordStateMachine, final Map<String, String> formValues ) throws PwmUnrecoverableException
            {
                final PwmRequestContext pwmRequestContext = forgottenPasswordStateMachine.getCommonValues();
                final TokenDestinationItem tokenDestinationItem = forgottenPasswordStateMachine.getForgottenPasswordBean().getProgress().getTokenDestination();
                final String userEnteredCode = formValues.get( PwmConstants.PARAM_TOKEN );

                ErrorInformation errorInformation = null;
                try
                {
                    final TokenPayload tokenPayload = TokenUtil.checkEnteredCode(
                            pwmRequestContext,
                            userEnteredCode,
                            tokenDestinationItem,
                            forgottenPasswordStateMachine.getForgottenPasswordBean().getUserIdentity(),
                            TokenType.FORGOTTEN_PW,
                            TokenService.TokenEntryType.unauthenticated
                    );

                    if ( tokenPayload == null )
                    {
                        throw PwmUnrecoverableException.newException( PwmError.ERROR_TOKEN_INCORRECT, "token incorrect" );
                    }

                    forgottenPasswordStateMachine.getForgottenPasswordBean().getProgress().getSatisfiedMethods().add( IdentityVerificationMethod.TOKEN );
                    StatisticsManager.incrementStat( pwmRequestContext.getPwmApplication(), Statistic.RECOVERY_TOKENS_PASSED );

                    if ( pwmRequestContext.getConfig().readSettingAsBoolean( PwmSetting.DISPLAY_TOKEN_SUCCESS_BUTTON ) )
                    {
                        return;
                    }
                }
                catch ( final PwmUnrecoverableException e )
                {
                    LOGGER.debug( pwmRequestContext.getSessionLabel(), () -> "error while checking entered token: " );
                    errorInformation = e.getErrorInformation();
                }

                if ( !forgottenPasswordStateMachine.getForgottenPasswordBean().getProgress().getSatisfiedMethods().contains( IdentityVerificationMethod.TOKEN ) )
                {
                    if ( errorInformation == null )
                    {
                        errorInformation = new ErrorInformation( PwmError.ERROR_TOKEN_INCORRECT );
                    }
                    handleUserVerificationBadAttempt( pwmRequestContext, forgottenPasswordStateMachine.getForgottenPasswordBean(), errorInformation );
                }

                if ( errorInformation != null )
                {
                    throw new PwmUnrecoverableException( errorInformation );
                }
            }

            @Override
            public PresentableForm generateForm( final ForgottenPasswordStateMachine forgottenPasswordStateMachine )
            {
                final PwmRequestContext pwmRequestContext = forgottenPasswordStateMachine.getCommonValues();
                final boolean valueMasking = pwmRequestContext.getConfig().readSettingAsBoolean( PwmSetting.TOKEN_ENABLE_VALUE_MASKING );
                final FormConfiguration.Type formType = valueMasking
                        ? FormConfiguration.Type.password
                        : FormConfiguration.Type.text;

                final String tokenDisplay = forgottenPasswordStateMachine.getForgottenPasswordBean().getProgress().getTokenDestination().getDisplay();
                final String message = LocaleHelper.getLocalizedMessage(
                        pwmRequestContext.getLocale(),
                        Display.Display_RecoverEnterCode,
                        pwmRequestContext.getConfig(),
                        new String[]
                        {
                                tokenDisplay,
                        }
                );

                final PresentableFormRow formRow = PresentableFormRow.builder()
                        .name( PwmConstants.PARAM_TOKEN )
                        .type( formType )
                        .label( LocaleHelper.getLocalizedMessage( pwmRequestContext.getLocale(), Display.Field_VerificationMethodToken, pwmRequestContext.getConfig() ) )
                        .required( true )
                        .build();

                return PresentableForm.builder()
                        .label( LocaleHelper.getLocalizedMessage( pwmRequestContext.getLocale(), Display.Title_ForgottenPassword, pwmRequestContext.getConfig() ) )
                        .message( message )
                        .formRow( formRow )
                        .build();
            }
        }

        static class ChallengeResponseHandler implements StageHandler
        {

            @Override
            public void applyForm( final ForgottenPasswordStateMachine forgottenPasswordStateMachine, final Map<String, String> formValues )
                    throws PwmUnrecoverableException
            {
                final PwmRequestContext pwmRequestContext = forgottenPasswordStateMachine.getCommonValues();
                final ResponseSet responseSet = ForgottenPasswordUtil.readResponseSet( pwmRequestContext, forgottenPasswordStateMachine.getForgottenPasswordBean() );
                if ( responseSet == null )
                {
                    final String errorMsg = "attempt to check responses, but responses are not loaded into session bean";
                    final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INTERNAL, errorMsg );
                    throw new PwmUnrecoverableException( errorInformation );
                }

                // read the supplied responses from the user
                final Map<Challenge, String> crMap = ForgottenPasswordUtil.readResponsesFromMap(
                        forgottenPasswordStateMachine.getForgottenPasswordBean().getPresentableChallengeSet(),
                        formValues
                );

                final boolean responsesPassed;
                try
                {
                    responsesPassed = responseSet.test( crMap );
                }
                catch ( final ChaiUnavailableException e )
                {
                    throw PwmUnrecoverableException.fromChaiException( e );
                }

                if ( responsesPassed )
                {
                    final UserIdentity userIdentity = forgottenPasswordStateMachine.getForgottenPasswordBean().getUserIdentity();
                    LOGGER.debug( pwmRequestContext.getSessionLabel(), () -> "user '" + userIdentity + "' has supplied correct responses" );
                    forgottenPasswordStateMachine.getForgottenPasswordBean().getProgress().getSatisfiedMethods().add( IdentityVerificationMethod.CHALLENGE_RESPONSES );
                }
                else
                {
                    final String errorMsg = "incorrect response to one or more challenges";
                    final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INCORRECT_RESPONSE, errorMsg );
                    handleUserVerificationBadAttempt( pwmRequestContext, forgottenPasswordStateMachine.getForgottenPasswordBean(), errorInformation );
                    throw new PwmUnrecoverableException( errorInformation );
                }
            }

            @Override
            public PresentableForm generateForm( final ForgottenPasswordStateMachine forgottenPasswordStateMachine )
            {
                final PwmRequestContext pwmRequestContext = forgottenPasswordStateMachine.getCommonValues();
                final ChallengeSetBean challengeSetBean = forgottenPasswordStateMachine.getForgottenPasswordBean().getPresentableChallengeSet();
                final List<PresentableFormRow> formRows = new ArrayList<>();

                int loopCounter = 0;
                for ( final ChallengeBean challengeBean : challengeSetBean.getChallenges() )
                {
                    loopCounter++;
                    formRows.add( PresentableFormRow.builder()
                            .name( PwmConstants.PARAM_RESPONSE_PREFIX + loopCounter )
                            .type( FormConfiguration.Type.password )
                            .label( challengeBean.getChallengeText() )
                            .required( true )
                            .build()
                    );
                }
                return PresentableForm.builder()
                        .label( LocaleHelper.getLocalizedMessage( pwmRequestContext.getLocale(), Display.Title_ForgottenPassword, pwmRequestContext.getConfig() ) )
                        .message( LocaleHelper.getLocalizedMessage( pwmRequestContext.getLocale(), Display.Display_RecoverPassword, pwmRequestContext.getConfig() ) )
                        .formRows( formRows )
                        .build();
            }

        }

        static class AttributeVerificationHandler implements StageHandler
        {
            @Override
            public void applyForm( final ForgottenPasswordStateMachine forgottenPasswordStateMachine, final Map<String, String> formData )
                    throws PwmUnrecoverableException
            {
                final PwmApplication pwmApplication = forgottenPasswordStateMachine.getCommonValues().getPwmApplication();
                final Locale locale = forgottenPasswordStateMachine.getCommonValues().getLocale();
                final SessionLabel sessionLabel = forgottenPasswordStateMachine.getCommonValues().getSessionLabel();
                final ForgottenPasswordBean forgottenPasswordBean = forgottenPasswordStateMachine.getForgottenPasswordBean();

                if ( forgottenPasswordBean.isBogusUser() )
                {
                    final FormConfiguration formConfiguration = forgottenPasswordBean.getAttributeForm().iterator().next();

                    if ( forgottenPasswordBean.getUserSearchValues() != null )
                    {
                        final List<FormConfiguration> formConfigurations = pwmApplication.getConfig().readSettingAsForm( PwmSetting.FORGOTTEN_PASSWORD_SEARCH_FORM );
                        final Map<FormConfiguration, String> formMap = FormUtility.asFormConfigurationMap( formConfigurations, forgottenPasswordBean.getUserSearchValues() );
                        pwmApplication.getIntruderManager().convenience().markAttributes( formMap, forgottenPasswordStateMachine.getCommonValues().getSessionLabel() );
                    }

                    final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INCORRECT_RESPONSE,
                            "incorrect value for attribute '" + formConfiguration.getName() + "'", new String[]
                            {
                                    formConfiguration.getLabel( locale ),
                            }
                    );

                    throw new PwmUnrecoverableException( errorInformation );
                }

                if ( forgottenPasswordBean.getUserIdentity() == null )
                {
                    return;
                }
                final UserIdentity userIdentity = forgottenPasswordBean.getUserIdentity();

                try
                {
                    // check attributes
                    final ChaiUser theUser = pwmApplication.getProxiedChaiUser( userIdentity );

                    final List<FormConfiguration> requiredAttributesForm = forgottenPasswordBean.getAttributeForm();

                    if ( requiredAttributesForm.isEmpty() )
                    {
                        return;
                    }

                    final Map<FormConfiguration, String> formValues = FormUtility.readFormValuesFromMap(
                            formData, requiredAttributesForm, locale );

                    for ( final Map.Entry<FormConfiguration, String> entry : formValues.entrySet() )
                    {
                        final FormConfiguration formConfiguration = entry.getKey();
                        final String attrName = formConfiguration.getName();

                        try
                        {
                            if ( theUser.compareStringAttribute( attrName, entry.getValue() ) )
                            {
                                LOGGER.trace( sessionLabel, () -> "successful validation of ldap attribute value for '" + attrName + "'" );
                            }
                            else
                            {
                                throw new PwmDataValidationException( new ErrorInformation( PwmError.ERROR_INCORRECT_RESPONSE,
                                        "incorrect value for '" + attrName + "'", new String[]
                                        {
                                                formConfiguration.getLabel( locale ),
                                        }
                                ) );
                            }
                        }
                        catch ( final ChaiOperationException e )
                        {
                            LOGGER.error( sessionLabel, () -> "error during param validation of '" + attrName + "', error: " + e.getMessage() );
                            throw new PwmDataValidationException( new ErrorInformation(
                                    PwmError.ERROR_INCORRECT_RESPONSE, "ldap error testing value for '" + attrName + "'", new String[]
                                    {
                                            formConfiguration.getLabel( locale ),
                                    }
                            ) );
                        }
                        catch ( final ChaiUnavailableException e )
                        {
                            throw PwmUnrecoverableException.fromChaiException( e );
                        }
                    }

                    forgottenPasswordBean.getProgress().getSatisfiedMethods().add( IdentityVerificationMethod.ATTRIBUTES );
                }
                catch ( final PwmDataValidationException e )
                {
                    handleUserVerificationBadAttempt(
                            forgottenPasswordStateMachine.getCommonValues(),
                            forgottenPasswordBean,
                            new ErrorInformation( PwmError.ERROR_INCORRECT_RESPONSE, e.getErrorInformation().toDebugStr() ) );
                }
            }

            @Override
            public PresentableForm generateForm( final ForgottenPasswordStateMachine forgottenPasswordStateMachine )
            {
                final PwmRequestContext pwmRequestContext = forgottenPasswordStateMachine.getCommonValues();
                final List<FormConfiguration> formConfigurations = forgottenPasswordStateMachine.getForgottenPasswordBean().getAttributeForm();
                return PresentableForm.builder()
                        .label( LocaleHelper.getLocalizedMessage( pwmRequestContext.getLocale(), Display.Title_ForgottenPassword, pwmRequestContext.getConfig() ) )
                        .message( LocaleHelper.getLocalizedMessage( pwmRequestContext.getLocale(), Display.Display_RecoverPassword, pwmRequestContext.getConfig() ) )
                        .formRows( PresentableFormRow.fromFormConfigurations( formConfigurations, forgottenPasswordStateMachine.getCommonValues().getLocale() ) )
                        .build();
            }
        }


    }

    static class MethodChoiceStageHandler implements StageHandler
    {
        @Override
        public void applyForm( final ForgottenPasswordStateMachine forgottenPasswordStateMachine, final Map<String, String> formValues )
                throws PwmUnrecoverableException
        {
            final PwmRequestContext pwmRequestContext = forgottenPasswordStateMachine.getCommonValues();
            final ForgottenPasswordBean forgottenPasswordBean = forgottenPasswordStateMachine.getForgottenPasswordBean();
            final LinkedHashSet<IdentityVerificationMethod> remainingAvailableOptionalMethods = new LinkedHashSet<>(
                    ForgottenPasswordUtil.figureRemainingAvailableOptionalAuthMethods( pwmRequestContext, forgottenPasswordBean )
            );

            final IdentityVerificationMethod requestedChoice = JavaHelper.readEnumFromString(
                    IdentityVerificationMethod.class,
                    null,
                    formValues.get( PwmConstants.PARAM_METHOD_CHOICE ) );
            if ( requestedChoice == null )
            {
                final String errorMsg = "unknown verification method requested";
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_MISSING_PARAMETER, errorMsg );
                throw new PwmUnrecoverableException( errorInformation );
            }

            if ( remainingAvailableOptionalMethods.contains( requestedChoice ) )
            {
                forgottenPasswordBean.getProgress().setInProgressVerificationMethod( requestedChoice );
            }
        }

        @Override
        public PresentableForm generateForm( final ForgottenPasswordStateMachine forgottenPasswordStateMachine )
        {
            final PwmRequestContext pwmRequestContext = forgottenPasswordStateMachine.getCommonValues();
            final LinkedHashSet<IdentityVerificationMethod> remainingAvailableOptionalMethods = new LinkedHashSet<>(
                    ForgottenPasswordUtil.figureRemainingAvailableOptionalAuthMethods( pwmRequestContext, forgottenPasswordStateMachine.getForgottenPasswordBean() )
            );

            final Map<String, String> selectOptions = new LinkedHashMap<>();
            for ( final IdentityVerificationMethod method : remainingAvailableOptionalMethods )
            {
                if ( method.isUserSelectable() )
                {
                    selectOptions.put( method.name(), method.getLabel( pwmRequestContext.getConfig(), pwmRequestContext.getLocale() ) );
                }
            }

            final Map<String, String> locales = Collections.singletonMap(
                    "",
                    LocaleHelper.getLocalizedMessage( pwmRequestContext.getLocale(), Display.Button_Select, pwmRequestContext.getConfig() ) );

            final FormConfiguration formConfiguration = FormConfiguration.builder()
                    .type( FormConfiguration.Type.select )
                    .required( true )
                    .selectOptions( selectOptions )
                    .labels( locales )
                    .name( PwmConstants.PARAM_METHOD_CHOICE )
                    .build();

            return PresentableForm.builder()
                    .label( LocaleHelper.getLocalizedMessage( pwmRequestContext.getLocale(), Display.Title_ForgottenPassword, pwmRequestContext.getConfig() ) )
                    .message( LocaleHelper.getLocalizedMessage( pwmRequestContext.getLocale(), Display.Display_RecoverVerificationChoice, pwmRequestContext.getConfig() ) )
                    .formRow( PresentableFormRow.fromFormConfiguration( formConfiguration, pwmRequestContext.getLocale() ) )
                    .build();
        }
    }

    static class IdentificationStageHandler implements StageHandler
    {
        @Override
        public PresentableForm generateForm( final ForgottenPasswordStateMachine forgottenPasswordStateMachine )
                throws PwmUnrecoverableException
        {
            final PwmRequestContext pwmRequestContext = forgottenPasswordStateMachine.getCommonValues();
            final String profile = forgottenPasswordStateMachine.getForgottenPasswordBean().getProfile();
            final List<FormConfiguration> formFields = new ArrayList<>( makeSelectableContextValues( pwmRequestContext, profile ) );
            formFields.addAll( pwmRequestContext.getConfig().readSettingAsForm( PwmSetting.FORGOTTEN_PASSWORD_SEARCH_FORM ) );

            return PresentableForm.builder()
                    .label( LocaleHelper.getLocalizedMessage( pwmRequestContext.getLocale(), Display.Title_ForgottenPassword, pwmRequestContext.getConfig() ) )
                    .message( LocaleHelper.getLocalizedMessage( pwmRequestContext.getLocale(), Display.Display_ForgottenPassword, pwmRequestContext.getConfig() ) )
                    .formRows( PresentableFormRow.fromFormConfigurations( formFields, pwmRequestContext.getLocale() ) )
                    .build();
        }

        @Override
        public void applyForm( final ForgottenPasswordStateMachine forgottenPasswordStateMachine, final Map<String, String> values )
                throws PwmUnrecoverableException
        {
            final PwmRequestContext pwmRequestContext = forgottenPasswordStateMachine.getCommonValues();

            if ( forgottenPasswordStateMachine.nextStage() != ForgottenPasswordStage.IDENTIFICATION )
            {
                forgottenPasswordStateMachine.clear();
            }

            if ( values == null )
            {
                return;
            }

            // process input profile
            {
                final String inputProfile = values.get( PwmConstants.PARAM_LDAP_PROFILE );
                if ( !StringUtil.isEmpty( inputProfile ) && pwmRequestContext.getConfig().getLdapProfiles().containsKey( inputProfile ) )
                {
                    forgottenPasswordStateMachine.getForgottenPasswordBean().setProfile( inputProfile );
                }
            }

            final LdapProfile ldapProfile = pwmRequestContext.getConfig().getLdapProfiles().getOrDefault(
                    forgottenPasswordStateMachine.getForgottenPasswordBean().getProfile(),
                    pwmRequestContext.getConfig().getDefaultLdapProfile() );

            final String contextParam = values.get( PwmConstants.PARAM_CONTEXT );

            final List<FormConfiguration> forgottenPasswordForm = pwmRequestContext.getConfig().readSettingAsForm( PwmSetting.FORGOTTEN_PASSWORD_SEARCH_FORM );

            final boolean bogusUserModeEnabled = pwmRequestContext.getConfig().readSettingAsBoolean( PwmSetting.RECOVERY_BOGUS_USER_ENABLE );

            Map<FormConfiguration, String> formValues = new LinkedHashMap<>();

            try
            {
                //read the values from the request
                formValues = FormUtility.readFormValuesFromMap( values, forgottenPasswordForm, pwmRequestContext.getLocale() );

                // check for intruder search values
                pwmRequestContext.getPwmApplication().getIntruderManager().convenience().checkAttributes( formValues );

                // see if the values meet the configured form requirements.
                FormUtility.validateFormValues( pwmRequestContext.getConfig(), formValues, pwmRequestContext.getLocale() );

                final String searchFilter;
                {
                    final String configuredSearchFilter = pwmRequestContext.getConfig().readSettingAsString( PwmSetting.FORGOTTEN_PASSWORD_SEARCH_FILTER );
                    if ( configuredSearchFilter == null || configuredSearchFilter.isEmpty() )
                    {
                        searchFilter = FormUtility.ldapSearchFilterForForm( pwmRequestContext.getPwmApplication(), forgottenPasswordForm );
                        LOGGER.trace( pwmRequestContext.getSessionLabel(), () -> "auto generated ldap search filter: " + searchFilter );
                    }
                    else
                    {
                        searchFilter = configuredSearchFilter;
                    }
                }
                final UserIdentity userIdentity;

                // convert the username field to an identity
                {
                    final UserSearchEngine userSearchEngine = pwmRequestContext.getPwmApplication().getUserSearchEngine();
                    final SearchConfiguration searchConfiguration = SearchConfiguration.builder()
                            .filter( searchFilter )
                            .formValues( formValues )
                            .contexts( Collections.singletonList( contextParam ) )
                            .ldapProfile( ldapProfile.getIdentifier() )
                            .build();

                    userIdentity = userSearchEngine.performSingleUserSearch( searchConfiguration, pwmRequestContext.getSessionLabel() );
                }

                if ( userIdentity == null )
                {
                    throw new PwmOperationalException( new ErrorInformation( PwmError.ERROR_CANT_MATCH_USER ) );
                }

                AuthenticationUtility.checkIfUserEligibleToAuthentication( pwmRequestContext.getPwmApplication(), userIdentity );

                ForgottenPasswordUtil.initForgottenPasswordBean( pwmRequestContext, userIdentity, forgottenPasswordStateMachine.getForgottenPasswordBean() );

                // clear intruder search values
                pwmRequestContext.getPwmApplication().getIntruderManager().convenience().clearAttributes( formValues );

                return;
            }
            catch ( final PwmOperationalException e )
            {
                if ( e.getError() != PwmError.ERROR_CANT_MATCH_USER || !bogusUserModeEnabled )
                {
                    final ErrorInformation errorInfo = new ErrorInformation(
                            PwmError.ERROR_RESPONSES_NORESPONSES,
                            e.getErrorInformation().getDetailedErrorMsg(), e.getErrorInformation().getFieldValues()
                    );
                    pwmRequestContext.getPwmApplication().getStatisticsManager().incrementValue( Statistic.RECOVERY_FAILURES );

                    pwmRequestContext.getPwmApplication().getIntruderManager().convenience().markAttributes( formValues, pwmRequestContext.getSessionLabel() );

                    LOGGER.debug( pwmRequestContext.getSessionLabel(), errorInfo );
                    forgottenPasswordStateMachine.clear();
                    throw new PwmUnrecoverableException( errorInfo );
                }
            }

            // only reachable if user not matched and bogus mode is enabled
            ForgottenPasswordUtil.initBogusForgottenPasswordBean( pwmRequestContext, forgottenPasswordStateMachine.getForgottenPasswordBean() );
            forgottenPasswordStateMachine.getForgottenPasswordBean().setUserSearchValues( FormUtility.asStringMap( formValues ) );
        }

        private List<FormConfiguration> makeSelectableContextValues( final PwmRequestContext pwmRequestContext, final String profile )
                throws PwmUnrecoverableException
        {
            final SelectableContextMode selectableContextMode = pwmRequestContext.getConfig().readSettingAsEnum(
                    PwmSetting.LDAP_SELECTABLE_CONTEXT_MODE,
                    SelectableContextMode.class );

            if ( selectableContextMode == null || selectableContextMode == SelectableContextMode.NONE )
            {
                return Collections.emptyList();
            }

            final List<FormConfiguration> returnList = new ArrayList<>();

            if ( selectableContextMode == SelectableContextMode.SHOW_PROFILE && pwmRequestContext.getConfig().getLdapProfiles().size() > 1 )
            {
                final Map<String, String> profileSelectValues = new LinkedHashMap<>();
                for ( final LdapProfile ldapProfile : pwmRequestContext.getConfig().getLdapProfiles().values() )
                {
                    profileSelectValues.put( ldapProfile.getIdentifier(), ldapProfile.getDisplayName( pwmRequestContext.getLocale() ) );
                }
                final Map<String, String> labelLocaleMap = LocaleHelper.localeMapToStringMap(
                        LocaleHelper.getUniqueLocalizations( pwmRequestContext.getConfig(), Display.class, "Field_Profile", pwmRequestContext.getLocale() ) );
                final FormConfiguration formConfiguration = FormConfiguration.builder()
                        .name( PwmConstants.PARAM_LDAP_PROFILE )
                        .labels( labelLocaleMap )
                        .type( FormConfiguration.Type.select )
                        .selectOptions( profileSelectValues )
                        .required( true )
                        .build();
                returnList.add( formConfiguration );
            }

            final LdapProfile selectedProfile = pwmRequestContext.getConfig().getLdapProfiles().getOrDefault( profile, pwmRequestContext.getConfig().getDefaultLdapProfile() );
            final Map<String, String> selectableContexts = selectedProfile.getSelectableContexts( pwmRequestContext.getPwmApplication() );
            if ( selectableContexts != null && selectableContexts.size() > 1 )
            {
                final Map<String, String> labelLocaleMap = LocaleHelper.localeMapToStringMap(
                        LocaleHelper.getUniqueLocalizations( pwmRequestContext.getConfig(), Display.class, "Field_Context", pwmRequestContext.getLocale() ) );
                final FormConfiguration formConfiguration = FormConfiguration.builder()
                        .name( PwmConstants.PARAM_CONTEXT )
                        .labels( labelLocaleMap )
                        .type( FormConfiguration.Type.select )
                        .selectOptions( selectableContexts )
                        .required( true )
                        .build();
                returnList.add( formConfiguration );
            }

            return Collections.unmodifiableList( returnList );
        }
    }

    private static void handleUserVerificationBadAttempt(
            final PwmRequestContext pwmRequestContext,
            final ForgottenPasswordBean forgottenPasswordBean,
            final ErrorInformation errorInformation
    )
            throws PwmUnrecoverableException
    {
        LOGGER.debug( pwmRequestContext.getSessionLabel(), errorInformation );

        final UserIdentity userIdentity = forgottenPasswordBean == null
                ? null
                : forgottenPasswordBean.getUserIdentity();


        // add a bit of jitter to pretend like we're checking a data source
        final long jitterMs = 300L + pwmRequestContext.getPwmApplication().getSecureService().pwmRandom().nextInt( 700 );
        TimeDuration.of( jitterMs, TimeDuration.Unit.MILLISECONDS ).pause();

        if ( userIdentity != null )
        {
            SessionAuthenticator.simulateBadPassword( pwmRequestContext, userIdentity );


            pwmRequestContext.getPwmApplication().getIntruderManager().convenience().markUserIdentity( userIdentity,
                    pwmRequestContext.getSessionLabel() );
        }

        StatisticsManager.incrementStat( pwmRequestContext.getPwmApplication(), Statistic.RECOVERY_FAILURES );
    }
}
