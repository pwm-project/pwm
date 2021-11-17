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

package password.pwm.http.servlet.setupresponses;

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.cr.Challenge;
import com.novell.ldapchai.cr.ChallengeSet;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.exception.ChaiValidationException;
import password.pwm.PwmConstants;
import password.pwm.PwmDomain;
import password.pwm.bean.LoginInfoBean;
import password.pwm.bean.ResponseInfoBean;
import password.pwm.config.PwmSetting;
import password.pwm.config.profile.ChallengeProfile;
import password.pwm.config.profile.SetupResponsesProfile;
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
import password.pwm.http.bean.SetupResponsesBean;
import password.pwm.http.servlet.ControlledPwmServlet;
import password.pwm.http.servlet.PwmServletDefinition;
import password.pwm.i18n.Message;
import password.pwm.ldap.UserInfo;
import password.pwm.ldap.auth.AuthenticationType;
import password.pwm.svc.event.AuditEvent;
import password.pwm.svc.event.AuditRecordFactory;
import password.pwm.svc.event.AuditServiceClient;
import password.pwm.svc.event.UserAuditRecord;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsClient;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.ws.server.RestResultBean;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import java.io.IOException;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * User interaction servlet for setting up secret question/answer.
 *
 * @author Jason D. Rivard
 */

@WebServlet(
        name = "SetupResponsesServlet",
        urlPatterns = {
                PwmConstants.URL_PREFIX_PRIVATE + "/setup-responses",
                PwmConstants.URL_PREFIX_PRIVATE + "/SetupResponses",
        }
)
public class SetupResponsesServlet extends ControlledPwmServlet
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( SetupResponsesServlet.class );

    public static final String PARAM_RESPONSE_MODE = "responseMode";

    public enum SetupResponsesAction implements ProcessAction
    {
        validateResponses( HttpMethod.POST ),
        setResponses( HttpMethod.POST ),
        setHelpdeskResponses( HttpMethod.POST ),
        confirmResponses( HttpMethod.POST ),
        clearExisting( HttpMethod.POST ),
        changeResponses( HttpMethod.POST ),
        skip ( HttpMethod.POST ),;

        private final HttpMethod method;

        SetupResponsesAction( final HttpMethod method )
        {
            this.method = method;
        }

        @Override
        public Collection<HttpMethod> permittedMethods( )
        {
            return Collections.singletonList( method );
        }
    }

    @Override
    public Class<? extends ProcessAction> getProcessActionsClass( )
    {
        return SetupResponsesAction.class;
    }

    private SetupResponsesBean getSetupResponseBean( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException
    {
        return pwmRequest.getPwmDomain().getSessionStateService().getBean( pwmRequest, SetupResponsesBean.class );
    }


    static SetupResponsesProfile getSetupProfile( final PwmRequest pwmRequest ) throws PwmUnrecoverableException
    {
        return pwmRequest.getSetupResponsesProfile( );
    }

    static ChallengeProfile getChallengeProfile( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException
    {
        return pwmRequest.getPwmSession().getUserInfo().getChallengeProfile();
    }

    static ChallengeSet getChallengeSet( final PwmRequest pwmRequest, final ResponseMode responseMode )
            throws PwmUnrecoverableException
    {
        final ChallengeProfile challengeProfile = getChallengeProfile( pwmRequest );
        if ( responseMode == ResponseMode.helpdesk )
        {
            return challengeProfile.getHelpdeskChallengeSet().orElseThrow();
        }
        return challengeProfile.getChallengeSet().orElseThrow();
    }


    @Override
    public ProcessStatus preProcessCheck( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException, IOException, ServletException
    {
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final PwmDomain pwmDomain = pwmRequest.getPwmDomain();

        if ( !pwmSession.isAuthenticated() )
        {
            pwmRequest.respondWithError( PwmError.ERROR_AUTHENTICATION_REQUIRED.toInfo() );
            return ProcessStatus.Halt;
        }

        if ( pwmSession.getLoginInfoBean().getType() == AuthenticationType.AUTH_WITHOUT_PASSWORD )
        {
            throw new PwmUnrecoverableException( PwmError.ERROR_PASSWORD_REQUIRED );
        }

        if ( !pwmDomain.getConfig().readSettingAsBoolean( PwmSetting.SETUP_RESPONSE_ENABLE ) )
        {
            throw new PwmUnrecoverableException( PwmError.ERROR_SERVICE_NOT_AVAILABLE );
        }

        // check if the locale has changed since first seen.
        if ( pwmSession.getSessionStateBean().getLocale() != pwmDomain.getSessionStateService().getBean( pwmRequest, SetupResponsesBean.class ).getUserLocale() )
        {
            pwmRequest.getPwmDomain().getSessionStateService().clearBean( pwmRequest, SetupResponsesBean.class );
            pwmDomain.getSessionStateService().getBean( pwmRequest, SetupResponsesBean.class ).setUserLocale( pwmSession.getSessionStateBean().getLocale() );
        }

        final SetupResponsesBean setupResponsesBean = getSetupResponseBean( pwmRequest );
        if ( !setupResponsesBean.isInitialized() )
        {
            initializeBean( pwmRequest, setupResponsesBean );
        }

        // check to see if the user has any challenges assigned
        final UserInfo uiBean = pwmSession.getUserInfo();

        if ( !SetupResponsesUtil.hasChallenges( pwmRequest, ResponseMode.user )
                && !SetupResponsesUtil.hasChallenges( pwmRequest, ResponseMode.helpdesk ) )
        {
            final String errorMsg = "no challenge sets configured for user " + uiBean.getUserIdentity();
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_NO_CHALLENGES, errorMsg );
            LOGGER.debug( pwmRequest, errorInformation );
            throw new PwmUnrecoverableException( errorInformation );
        }

        return ProcessStatus.Continue;
    }

    @ActionHandler( action = "confirmResponses" )
    private ProcessStatus processConfirmResponses( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException
    {
        final SetupResponsesBean setupResponsesBean = getSetupResponseBean( pwmRequest );
        setupResponsesBean.setConfirmed( true );
        return ProcessStatus.Continue;
    }

    @ActionHandler( action = "changeResponses" )
    private ProcessStatus processChangeResponses( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException
    {
        final SetupResponsesBean setupResponsesBean = getSetupResponseBean( pwmRequest );
        final PwmDomain pwmDomain = pwmRequest.getPwmDomain();
        pwmDomain.getSessionStateService().clearBean( pwmRequest, SetupResponsesBean.class );
        this.initializeBean( pwmRequest, setupResponsesBean );
        setupResponsesBean.setUserLocale( pwmRequest.getLocale() );
        return ProcessStatus.Continue;
    }

    @ActionHandler( action = "clearExisting" )
    private ProcessStatus handleClearExisting(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException, ChaiUnavailableException, IOException
    {
        LOGGER.trace( pwmRequest, () -> "request for response clear received" );
        final PwmDomain pwmDomain = pwmRequest.getPwmDomain();
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        try
        {
            final String userGUID = pwmSession.getUserInfo().getUserGuid();
            final ChaiUser theUser = pwmSession.getSessionManager().getActor( );
            pwmDomain.getCrService().clearResponses( pwmRequest.getLabel(), pwmRequest.getUserInfoIfLoggedIn(), theUser, userGUID );
            pwmSession.reloadUserInfoBean( pwmRequest );
            pwmRequest.getPwmDomain().getSessionStateService().clearBean( pwmRequest, SetupResponsesBean.class );

            // mark the event log
            final UserAuditRecord auditRecord = AuditRecordFactory.make( pwmRequest ).createUserAuditRecord(
                    AuditEvent.CLEAR_RESPONSES,
                    pwmSession.getUserInfo(),
                    pwmSession
            );

            AuditServiceClient.submit( pwmRequest, auditRecord );

            pwmRequest.getPwmResponse().sendRedirect( PwmServletDefinition.SetupResponses );
        }
        catch ( final PwmOperationalException e )
        {
            LOGGER.debug( pwmRequest, e.getErrorInformation() );
            setLastError( pwmRequest, e.getErrorInformation() );
        }
        return ProcessStatus.Continue;
    }

    @ActionHandler( action = "skip" )
    private ProcessStatus handleSkip(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException, ChaiUnavailableException, IOException
    {
        LOGGER.trace( pwmRequest, () -> "request for skip received" );

        final boolean allowSkip = SetupResponsesUtil.checkIfAllowSkipCr( pwmRequest );

        if ( allowSkip )
        {
            pwmRequest.getPwmSession().getLoginInfoBean().getLoginFlags().add( LoginInfoBean.LoginFlag.skipSetupCr );
            pwmRequest.getPwmResponse().sendRedirectToContinue();
            return ProcessStatus.Halt;
        }

        return ProcessStatus.Continue;
    }

    @ActionHandler( action = "validateResponses" )
    private ProcessStatus restValidateResponses(
            final PwmRequest pwmRequest
    )
            throws IOException, PwmUnrecoverableException
    {
        final Instant startTime = Instant.now();

        final ResponseMode responseMode = pwmRequest.readParameterAsEnum( PARAM_RESPONSE_MODE, ResponseMode.class ).orElseThrow();
        final SetupResponsesBean setupResponsesBean = getSetupResponseBean( pwmRequest );

        final SetupResponsesBean.SetupData setupData = setupResponsesBean.getChallengeData().get( responseMode );
        final ChallengeSet challengeSet = getChallengeSet( pwmRequest, responseMode );

        boolean success = false;
        String userMessage = Message.getLocalizedMessage( pwmRequest.getLocale(), Message.Success_ResponsesMeetRules, pwmRequest.getDomainConfig() );

        try
        {
            // read in the responses from the request
            final Map<Challenge, String> responseMap = readResponsesFromJsonRequest( pwmRequest, setupData );
            final int minRandomRequiredSetup = setupData.getMinRandomSetup();
            pwmRequest.getPwmDomain().getCrService().validateResponses( challengeSet, responseMap, minRandomRequiredSetup );
            SetupResponsesUtil.generateResponseInfoBean( pwmRequest, challengeSet, responseMap, Collections.emptyMap() );
            success = true;
        }
        catch ( final PwmDataValidationException e )
        {
            userMessage = e.getErrorInformation().toUserStr( pwmRequest.getPwmSession(), pwmRequest.getAppConfig() );
        }

        final ValidationResponseBean validationResponseBean = new ValidationResponseBean( userMessage, success );
        final RestResultBean restResultBean = RestResultBean.withData( validationResponseBean );
        LOGGER.trace( pwmRequest, () -> "completed rest validate response in "
                + TimeDuration.compactFromCurrent( startTime )
                + ", result=" + JsonUtil.serialize( restResultBean ) );
        pwmRequest.outputJsonResult( restResultBean );
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "setHelpdeskResponses" )
    private ProcessStatus processSetHelpdeskResponses( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException
    {
        setupResponses( pwmRequest, ResponseMode.helpdesk );
        return ProcessStatus.Continue;
    }

    @ActionHandler( action = "setResponses" )
    private ProcessStatus processSetResponses( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException
    {
        setupResponses( pwmRequest, ResponseMode.user );
        return ProcessStatus.Continue;
    }

    @Override
    protected void nextStep( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException, IOException, ChaiUnavailableException, ServletException
    {
        final SetupResponsesBean setupResponsesBean = getSetupResponseBean( pwmRequest );

        if ( setupResponsesBean.isHasExistingResponses() && !pwmRequest.getPwmSession().getUserInfo().isRequiresResponseConfig() )
        {
            forwardToJsp( pwmRequest, JspUrl.SETUP_RESPONSES_EXISTING );
            return;
        }

        for ( final ResponseMode responseMode : ResponseMode.values() )
        {
            if ( !setupResponsesBean.getResponsesSatisfied().contains( responseMode )
                    && SetupResponsesUtil.hasChallenges( pwmRequest, responseMode ) )
            {
                pwmRequest.setAttribute( PwmRequestAttribute.SetupResponses_ChallengeSet, getChallengeSet( pwmRequest, responseMode ) );
                pwmRequest.setAttribute( PwmRequestAttribute.SetupResponses_AllowSkip, SetupResponsesUtil.checkIfAllowSkipCr( pwmRequest ) );
                pwmRequest.setAttribute( PwmRequestAttribute.SetupResponses_SetupData, setupResponsesBean.getChallengeData().get( responseMode ) );

                forwardToJsp( pwmRequest, responseMode == ResponseMode.helpdesk
                        ? JspUrl.SETUP_RESPONSES_HELPDESK
                        : JspUrl.SETUP_RESPONSES );
                return;
            }
        }

        final SetupResponsesProfile setupResponsesProfile = getSetupProfile( pwmRequest );
        if ( setupResponsesProfile.readSettingAsBoolean( PwmSetting.SETUP_RESPONSES_SHOW_CONFIRMATION ) )
        {
            if ( !setupResponsesBean.isConfirmed() )
            {
                forwardToJsp( pwmRequest, JspUrl.SETUP_RESPONSES_CONFIRM );
                return;
            }
        }

        try
        {
            // everything good, so lets save responses.
            final ResponseInfoBean responses = SetupResponsesUtil.generateResponseInfoBean(
                    pwmRequest,
                    getChallengeSet( pwmRequest, ResponseMode.user ),
                    setupResponsesBean.getChallengeData().get( ResponseMode.user ).getResponseMap(),
                    setupResponsesBean.getChallengeData().get( ResponseMode.helpdesk ).getResponseMap()
            );
            saveResponses( pwmRequest, responses );
            pwmRequest.getPwmDomain().getSessionStateService().clearBean( pwmRequest, SetupResponsesBean.class );
            pwmRequest.getPwmResponse().forwardToSuccessPage( Message.Success_SetupResponse );
        }
        catch ( final PwmOperationalException e )
        {
            LOGGER.error( pwmRequest.getLabel(), e.getErrorInformation() );
            pwmRequest.respondWithError( e.getErrorInformation() );
        }
        catch ( final ChaiValidationException e )
        {
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_MISSING_RANDOM_RESPONSE, e.getMessage() );
            LOGGER.error( pwmRequest.getLabel(), errorInformation );
            pwmRequest.respondWithError( errorInformation );
        }
    }

    private void forwardToJsp( final PwmRequest pwmRequest, final JspUrl jspUrl )
            throws ServletException, PwmUnrecoverableException, IOException
    {
        final SetupResponsesBean setupResponsesBean = getSetupResponseBean( pwmRequest );

        pwmRequest.setAttribute( PwmRequestAttribute.ModuleBean, setupResponsesBean );
        pwmRequest.setAttribute( PwmRequestAttribute.ModuleBean_String, pwmRequest.getPwmDomain().getSecureService().encryptObjectToString( setupResponsesBean ) );
        pwmRequest.setAttribute( PwmRequestAttribute.SetupResponses_ResponseInfo, pwmRequest.getPwmSession().getUserInfo().getResponseInfoBean() );

        pwmRequest.forwardToJsp( jspUrl );
    }


    private void setupResponses(
            final PwmRequest pwmRequest,
            final ResponseMode responseMode
    )
            throws PwmUnrecoverableException
    {
        final SetupResponsesBean setupResponsesBean = getSetupResponseBean( pwmRequest );
        final SetupResponsesBean.SetupData setupData = setupResponsesBean.getChallengeData().get( responseMode );

        final ChallengeSet challengeSet = getChallengeProfile( pwmRequest ).getChallengeSet().orElseThrow();
        final Map<Challenge, String> responseMap;
        try
        {
            // build a response set based on the user's challenge set and the html form response.
            responseMap = readResponsesFromHttpRequest( pwmRequest, setupData );

            // test the responses.
            final int minRandomRequiredSetup = setupData.getMinRandomSetup();
            pwmRequest.getPwmDomain().getCrService().validateResponses( challengeSet, responseMap, minRandomRequiredSetup );
        }
        catch ( final PwmDataValidationException e )
        {
            LOGGER.debug( pwmRequest, () -> "error with new " + responseMode.name()  + " responses: " + e.getErrorInformation().toDebugStr() );
            setLastError( pwmRequest, e.getErrorInformation() );
            return;
        }

        LOGGER.trace( pwmRequest, () -> responseMode.name() + " responses are acceptable" );
        setupData.setResponseMap( responseMap );
        setupResponsesBean.getResponsesSatisfied().add( responseMode );
    }

    private void saveResponses( final PwmRequest pwmRequest, final ResponseInfoBean responseInfoBean )
            throws PwmUnrecoverableException, ChaiUnavailableException, PwmOperationalException, ChaiValidationException
    {
        final PwmDomain pwmDomain = pwmRequest.getPwmDomain();
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final ChaiUser theUser = pwmSession.getSessionManager().getActor( );
        final String userGUID = pwmSession.getUserInfo().getUserGuid();
        pwmDomain.getCrService().writeResponses( pwmRequest.getLabel(), pwmRequest.getUserInfoIfLoggedIn(), theUser, userGUID, responseInfoBean );
        pwmSession.reloadUserInfoBean( pwmRequest );

        StatisticsClient.incrementStat( pwmRequest, Statistic.SETUP_RESPONSES );
        AuditServiceClient.submitUserEvent( pwmRequest, AuditEvent.SET_RESPONSES, pwmSession.getUserInfo() );
    }

    private static Map<Challenge, String> readResponsesFromHttpRequest(
            final PwmRequest pwmRequest,
            final SetupResponsesBean.SetupData setupData
    )
            throws PwmDataValidationException, PwmUnrecoverableException
    {
        final Map<String, String> inputMap = pwmRequest.readParametersAsMap();
        final ChallengeSet challengeSet = getChallengeProfile( pwmRequest ).getChallengeSet().orElseThrow();
        return SetupResponsesUtil.paramMapToChallengeMap( challengeSet, inputMap, setupData );
    }

    private static Map<Challenge, String> readResponsesFromJsonRequest(
            final PwmRequest pwmRequest,
            final SetupResponsesBean.SetupData setupData
    )
            throws PwmDataValidationException, PwmUnrecoverableException, IOException
    {
        final Map<String, String> inputMap = pwmRequest.readBodyAsJsonStringMap();
        final ChallengeSet challengeSet = getChallengeProfile( pwmRequest ).getChallengeSet().orElseThrow();
        return SetupResponsesUtil.paramMapToChallengeMap( challengeSet, inputMap, setupData );
    }


    private void initializeBean(
            final PwmRequest pwmRequest,
            final SetupResponsesBean setupResponsesBean
    )
            throws PwmUnrecoverableException
    {
        if ( setupResponsesBean.isInitialized() )
        {
            return;
        }

        if ( pwmRequest.getPwmSession().getUserInfo().getResponseInfoBean() != null )
        {
            setupResponsesBean.setHasExistingResponses( true );
        }

        final ChallengeProfile challengeProfile = pwmRequest.getPwmSession().getUserInfo().getChallengeProfile();

        {
            final SetupResponsesBean.SetupData userSetupData = challengeProfile.getChallengeSet()
                    .map( challengeSet -> SetupResponsesUtil.populateSetupData( challengeSet, challengeProfile.getMinRandomSetup() ) )
                    .orElse( new SetupResponsesBean.SetupData() );

            setupResponsesBean.getChallengeData().put( ResponseMode.user, userSetupData );
        }

        {
            final SetupResponsesBean.SetupData helpdeskSetupData = challengeProfile.getHelpdeskChallengeSet()
                    .map( challengeSet -> SetupResponsesUtil.populateSetupData( challengeSet, challengeProfile.getMinHelpdeskRandomsSetup() ) )
                    .orElse( new SetupResponsesBean.SetupData() );
            setupResponsesBean.getChallengeData().put( ResponseMode.helpdesk, helpdeskSetupData );
        }

        setupResponsesBean.setInitialized( true );
    }

}

