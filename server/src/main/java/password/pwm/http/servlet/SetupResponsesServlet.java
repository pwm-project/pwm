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
import com.novell.ldapchai.cr.ChaiCrFactory;
import com.novell.ldapchai.cr.ChaiResponseSet;
import com.novell.ldapchai.cr.Challenge;
import com.novell.ldapchai.cr.ChallengeSet;
import com.novell.ldapchai.exception.ChaiError;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.exception.ChaiValidationException;
import com.novell.ldapchai.provider.ChaiProvider;
import lombok.Value;
import password.pwm.Permission;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.LoginInfoBean;
import password.pwm.bean.ResponseInfoBean;
import password.pwm.config.PwmSetting;
import password.pwm.config.profile.ChallengeProfile;
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
import password.pwm.i18n.Message;
import password.pwm.ldap.UserInfo;
import password.pwm.ldap.auth.AuthenticationType;
import password.pwm.svc.event.AuditEvent;
import password.pwm.svc.event.AuditRecordFactory;
import password.pwm.svc.event.UserAuditRecord;
import password.pwm.svc.stats.Statistic;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.ws.server.RestResultBean;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import java.io.IOException;
import java.io.Serializable;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
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

    public enum SetupResponsesAction implements AbstractPwmServlet.ProcessAction
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

    private SetupResponsesBean getSetupResponseBean( final PwmRequest pwmRequest ) throws PwmUnrecoverableException
    {
        final SetupResponsesBean setupResponsesBean = pwmRequest.getPwmApplication().getSessionStateService().getBean( pwmRequest, SetupResponsesBean.class );
        if ( !setupResponsesBean.isInitialized() )
        {
            initializeBean( pwmRequest, setupResponsesBean );
        }
        return setupResponsesBean;

    }

    @Override
    public ProcessStatus preProcessCheck( final PwmRequest pwmRequest ) throws PwmUnrecoverableException, IOException, ServletException
    {
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();

        if ( !pwmSession.isAuthenticated() )
        {
            pwmRequest.respondWithError( PwmError.ERROR_AUTHENTICATION_REQUIRED.toInfo() );
            return ProcessStatus.Halt;
        }

        if ( pwmSession.getLoginInfoBean().getType() == AuthenticationType.AUTH_WITHOUT_PASSWORD )
        {
            throw new PwmUnrecoverableException( PwmError.ERROR_PASSWORD_REQUIRED );
        }

        if ( !pwmApplication.getConfig().readSettingAsBoolean( PwmSetting.CHALLENGE_ENABLE ) )
        {
            throw new PwmUnrecoverableException( PwmError.ERROR_SERVICE_NOT_AVAILABLE );
        }

        // check to see if the user is permitted to setup responses
        if ( !pwmSession.getSessionManager().checkPermission( pwmApplication, Permission.SETUP_RESPONSE ) )
        {
            throw new PwmUnrecoverableException( PwmError.ERROR_UNAUTHORIZED );
        }

        // check if the locale has changed since first seen.
        if ( pwmSession.getSessionStateBean().getLocale() != pwmApplication.getSessionStateService().getBean( pwmRequest, SetupResponsesBean.class ).getUserLocale() )
        {
            pwmRequest.getPwmApplication().getSessionStateService().clearBean( pwmRequest, SetupResponsesBean.class );
            pwmApplication.getSessionStateService().getBean( pwmRequest, SetupResponsesBean.class ).setUserLocale( pwmSession.getSessionStateBean().getLocale() );
        }

        // check to see if the user has any challenges assigned
        final UserInfo uiBean = pwmSession.getUserInfo();
        final SetupResponsesBean setupResponsesBean = getSetupResponseBean( pwmRequest );

        if ( setupResponsesBean.getResponseData().getChallengeSet() == null || setupResponsesBean.getResponseData().getChallengeSet().getChallenges().isEmpty() )
        {
            final String errorMsg = "no challenge sets configured for user " + uiBean.getUserIdentity();
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_NO_CHALLENGES, errorMsg );
            LOGGER.debug( pwmRequest, errorInformation );
            throw new PwmUnrecoverableException( errorInformation );
        }

        return ProcessStatus.Continue;
    }

    @ActionHandler( action = "confirmResponses" )
    private ProcessStatus processConfirmResponses( final PwmRequest pwmRequest ) throws PwmUnrecoverableException
    {
        final SetupResponsesBean setupResponsesBean = getSetupResponseBean( pwmRequest );
        setupResponsesBean.setConfirmed( true );
        return ProcessStatus.Continue;
    }

    @ActionHandler( action = "changeResponses" )
    private ProcessStatus processChangeResponses( final PwmRequest pwmRequest ) throws PwmUnrecoverableException
    {
        final SetupResponsesBean setupResponsesBean = getSetupResponseBean( pwmRequest );
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        pwmApplication.getSessionStateService().clearBean( pwmRequest, SetupResponsesBean.class );
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
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        try
        {
            final String userGUID = pwmSession.getUserInfo().getUserGuid();
            final ChaiUser theUser = pwmSession.getSessionManager().getActor( );
            pwmApplication.getCrService().clearResponses( pwmRequest.getLabel(), pwmRequest.getUserInfoIfLoggedIn(), theUser, userGUID );
            pwmSession.reloadUserInfoBean( pwmRequest );
            pwmRequest.getPwmApplication().getSessionStateService().clearBean( pwmRequest, SetupResponsesBean.class );

            // mark the event log
            final UserAuditRecord auditRecord = new AuditRecordFactory( pwmRequest ).createUserAuditRecord(
                    AuditEvent.CLEAR_RESPONSES,
                    pwmSession.getUserInfo(),
                    pwmSession
            );
            pwmApplication.getAuditManager().submit( auditRecord );

            pwmRequest.sendRedirect( PwmServletDefinition.SetupResponses );
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

        final boolean allowSkip = checkIfAllowSkipCr( pwmRequest );

        if ( allowSkip )
        {
            pwmRequest.getPwmSession().getLoginInfoBean().getLoginFlags().add( LoginInfoBean.LoginFlag.skipSetupCr );
            pwmRequest.sendRedirectToContinue();
            return ProcessStatus.Halt;
        }

        return ProcessStatus.Continue;
    }

    @ActionHandler( action = "validateResponses" )
    private ProcessStatus restValidateResponses(
            final PwmRequest pwmRequest
    )
            throws IOException, ServletException, PwmUnrecoverableException, ChaiUnavailableException
    {
        final SetupResponsesBean setupResponsesBean = getSetupResponseBean( pwmRequest );
        final Instant startTime = Instant.now();
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final String responseModeParam = pwmRequest.readParameterAsString( "responseMode" );
        final SetupResponsesBean.SetupData setupData = "helpdesk".equalsIgnoreCase( responseModeParam )
                ? setupResponsesBean.getHelpdeskResponseData()
                : setupResponsesBean.getResponseData();

        boolean success = true;
        String userMessage = Message.getLocalizedMessage( pwmSession.getSessionStateBean().getLocale(), Message.Success_ResponsesMeetRules, pwmApplication.getConfig() );

        try
        {
            // read in the responses from the request
            final Map<Challenge, String> responseMap = readResponsesFromJsonRequest( pwmRequest, setupData );
            final int minRandomRequiredSetup = setupData.getMinRandomSetup();
            pwmApplication.getCrService().validateResponses( setupData.getChallengeSet(), responseMap, minRandomRequiredSetup );
            generateResponseInfoBean( pwmRequest, setupData.getChallengeSet(), responseMap, Collections.emptyMap() );
        }
        catch ( final PwmDataValidationException e )
        {
            success = false;
            userMessage = e.getErrorInformation().toUserStr( pwmSession, pwmApplication );
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
            throws ChaiUnavailableException, PwmUnrecoverableException, ServletException, IOException
    {
        setupResponses( pwmRequest, true );
        return ProcessStatus.Continue;
    }

    @ActionHandler( action = "setResponses" )
    private ProcessStatus processSetResponses( final PwmRequest pwmRequest )
            throws ChaiUnavailableException, PwmUnrecoverableException, ServletException, IOException
    {
        setupResponses( pwmRequest, false );
        return ProcessStatus.Continue;
    }

    @Override
    protected void nextStep( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException, IOException, ChaiUnavailableException, ServletException
    {
        final SetupResponsesBean setupResponsesBean = getSetupResponseBean( pwmRequest );

        initializeBean( pwmRequest, setupResponsesBean );

        pwmRequest.setAttribute( PwmRequestAttribute.ModuleBean, setupResponsesBean );
        pwmRequest.setAttribute( PwmRequestAttribute.ModuleBean_String, pwmRequest.getPwmApplication().getSecureService().encryptObjectToString( setupResponsesBean ) );
        pwmRequest.setAttribute( PwmRequestAttribute.SetupResponses_ResponseInfo, pwmRequest.getPwmSession().getUserInfo().getResponseInfoBean() );

        if ( setupResponsesBean.isHasExistingResponses() && !pwmRequest.getPwmSession().getUserInfo().isRequiresResponseConfig() )
        {
            pwmRequest.forwardToJsp( JspUrl.SETUP_RESPONSES_EXISTING );
            return;
        }

        if ( !setupResponsesBean.isResponsesSatisfied() )
        {
            final boolean allowskip = checkIfAllowSkipCr( pwmRequest );
            pwmRequest.setAttribute( PwmRequestAttribute.SetupResponses_AllowSkip, allowskip );
            pwmRequest.forwardToJsp( JspUrl.SETUP_RESPONSES );
            return;
        }

        if ( !setupResponsesBean.isHelpdeskResponsesSatisfied() )
        {
            if ( setupResponsesBean.getHelpdeskResponseData().getChallengeSet() == null
                    || setupResponsesBean.getHelpdeskResponseData().getChallengeSet().getChallenges().isEmpty() )
            {
                setupResponsesBean.setHelpdeskResponsesSatisfied( true );
            }
            else
            {
                pwmRequest.forwardToJsp( JspUrl.SETUP_RESPONSES_HELPDESK );
                return;
            }
        }

        if ( pwmRequest.getConfig().readSettingAsBoolean( PwmSetting.CHALLENGE_SHOW_CONFIRMATION ) )
        {
            if ( !setupResponsesBean.isConfirmed() )
            {
                pwmRequest.forwardToJsp( JspUrl.SETUP_RESPONSES_CONFIRM );
                return;
            }
        }

        try
        {
            // everything good, so lets save responses.
            final ResponseInfoBean responses = generateResponseInfoBean(
                    pwmRequest,
                    setupResponsesBean.getResponseData().getChallengeSet(),
                    setupResponsesBean.getResponseData().getResponseMap(),
                    setupResponsesBean.getHelpdeskResponseData().getResponseMap()
            );
            saveResponses( pwmRequest, responses );
            pwmRequest.getPwmApplication().getSessionStateService().clearBean( pwmRequest, SetupResponsesBean.class );
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


    private void setupResponses(
            final PwmRequest pwmRequest,
            final boolean helpdeskMode
    )
            throws PwmUnrecoverableException, IOException, ServletException, ChaiUnavailableException
    {
        final SetupResponsesBean setupResponsesBean = getSetupResponseBean( pwmRequest );
        final SetupResponsesBean.SetupData setupData = helpdeskMode ? setupResponsesBean.getHelpdeskResponseData() : setupResponsesBean.getResponseData();

        final ChallengeSet challengeSet = setupData.getChallengeSet();
        final Map<Challenge, String> responseMap;
        try
        {
            // build a response set based on the user's challenge set and the html form response.
            responseMap = readResponsesFromHttpRequest( pwmRequest, setupData );

            // test the responses.
            final int minRandomRequiredSetup = setupData.getMinRandomSetup();
            pwmRequest.getPwmApplication().getCrService().validateResponses( challengeSet, responseMap, minRandomRequiredSetup );
        }
        catch ( final PwmDataValidationException e )
        {
            LOGGER.debug( pwmRequest, () -> "error with new " + ( helpdeskMode ? "helpdesk" : "user" ) + " responses: " + e.getErrorInformation().toDebugStr() );
            setLastError( pwmRequest, e.getErrorInformation() );
            return;
        }

        LOGGER.trace( pwmRequest, () -> ( helpdeskMode ? "helpdesk" : "user" ) + " responses are acceptable" );
        if ( helpdeskMode )
        {
            setupResponsesBean.getHelpdeskResponseData().setResponseMap( responseMap );
            setupResponsesBean.setHelpdeskResponsesSatisfied( true );
        }
        else
        {
            setupResponsesBean.getResponseData().setResponseMap( responseMap );
            setupResponsesBean.setResponsesSatisfied( true );
        }
    }

    private void saveResponses( final PwmRequest pwmRequest, final ResponseInfoBean responseInfoBean )
            throws PwmUnrecoverableException, ChaiUnavailableException, PwmOperationalException, ChaiValidationException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final ChaiUser theUser = pwmSession.getSessionManager().getActor( );
        final String userGUID = pwmSession.getUserInfo().getUserGuid();
        pwmApplication.getCrService().writeResponses( pwmRequest.getUserInfoIfLoggedIn(), theUser, userGUID, responseInfoBean );
        pwmSession.reloadUserInfoBean( pwmRequest );
        pwmApplication.getStatisticsManager().incrementValue( Statistic.SETUP_RESPONSES );
        pwmApplication.getAuditManager().submit( AuditEvent.SET_RESPONSES, pwmSession.getUserInfo(), pwmSession );
    }

    private static Map<Challenge, String> readResponsesFromHttpRequest(
            final PwmRequest pwmRequest,
            final SetupResponsesBean.SetupData setupData
    )
            throws PwmDataValidationException, PwmUnrecoverableException
    {
        final Map<String, String> inputMap = pwmRequest.readParametersAsMap();
        return paramMapToChallengeMap( inputMap, setupData );
    }

    private static Map<Challenge, String> readResponsesFromJsonRequest(
            final PwmRequest pwmRequest,
            final SetupResponsesBean.SetupData setupData
    )
            throws PwmDataValidationException, PwmUnrecoverableException, IOException
    {
        final Map<String, String> inputMap = pwmRequest.readBodyAsJsonStringMap();
        return paramMapToChallengeMap( inputMap, setupData );
    }

    private static Map<Challenge, String> paramMapToChallengeMap(
            final Map<String, String> inputMap,
            final SetupResponsesBean.SetupData setupData
    )
            throws PwmDataValidationException, PwmUnrecoverableException
    {
        //final SetupResponsesBean responsesBean = pwmSession.getSetupResponseBean();
        final Map<Challenge, String> readResponses = new LinkedHashMap<>();

        {
            // read in the question texts and responses
            for ( final String indexKey : setupData.getIndexedChallenges().keySet() )
            {
                final Challenge loopChallenge = setupData.getIndexedChallenges().get( indexKey );
                if ( loopChallenge.isRequired() || !setupData.isSimpleMode() )
                {

                    if ( !loopChallenge.isAdminDefined() )
                    {
                        final String questionText = inputMap.get( PwmConstants.PARAM_QUESTION_PREFIX + indexKey );
                        loopChallenge.setChallengeText( questionText );
                    }

                    final String answer = inputMap.get( PwmConstants.PARAM_RESPONSE_PREFIX + indexKey );

                    if ( answer != null && answer.length() > 0 )
                    {
                        readResponses.put( loopChallenge, answer );
                    }
                }
            }

            if ( setupData.isSimpleMode() )
            {
                // if in simple mode, read the select-based random challenges
                for ( int i = 0; i < setupData.getIndexedChallenges().size(); i++ )
                {
                    final String questionText = inputMap.get( PwmConstants.PARAM_QUESTION_PREFIX + "Random_" + String.valueOf( i ) );

                    Challenge challenge = null;
                    for ( final Challenge loopC : setupData.getChallengeSet().getRandomChallenges() )
                    {
                        if ( loopC.isAdminDefined() && questionText != null && questionText.equals( loopC.getChallengeText() ) )
                        {
                            challenge = loopC;
                            break;
                        }
                    }

                    final String answer = inputMap.get( PwmConstants.PARAM_RESPONSE_PREFIX + "Random_" + String.valueOf( i ) );
                    if ( answer != null && answer.length() > 0 )
                    {
                        readResponses.put( challenge, answer );
                    }
                }
            }
        }

        return readResponses;
    }


    private static ResponseInfoBean generateResponseInfoBean(
            final PwmRequest pwmRequest,
            final ChallengeSet challengeSet,
            final Map<Challenge, String> readResponses,
            final Map<Challenge, String> helpdeskResponses
    )
            throws ChaiUnavailableException, PwmDataValidationException, PwmUnrecoverableException
    {
        final ChaiProvider provider = pwmRequest.getPwmSession().getSessionManager().getChaiProvider();

        try
        {
            final ResponseInfoBean responseInfoBean = new ResponseInfoBean(
                    readResponses,
                    helpdeskResponses,
                    challengeSet.getLocale(),
                    challengeSet.getMinRandomRequired(),
                    challengeSet.getIdentifier(),
                    null,
                    null
            );

            final ChaiResponseSet responseSet = ChaiCrFactory.newChaiResponseSet(
                    readResponses,
                    challengeSet.getLocale(),
                    challengeSet.getMinRandomRequired(),
                    provider.getChaiConfiguration(),
                    challengeSet.getIdentifier() );

            responseSet.meetsChallengeSetRequirements( challengeSet );

            final SetupResponsesBean setupResponsesBean = pwmRequest.getPwmApplication().getSessionStateService().getBean( pwmRequest, SetupResponsesBean.class );
            final int minRandomRequiredSetup = setupResponsesBean.getResponseData().getMinRandomSetup();
            if ( minRandomRequiredSetup == 0 )
            {
                // if using recover style, then all readResponseSet must be supplied at this point.
                if ( responseSet.getChallengeSet().getRandomChallenges().size() < challengeSet.getRandomChallenges().size() )
                {
                    throw new ChaiValidationException( "too few random responses", ChaiError.CR_TOO_FEW_RANDOM_RESPONSES );
                }
            }

            return responseInfoBean;
        }
        catch ( final ChaiValidationException e )
        {
            final ErrorInformation errorInfo = convertChaiValidationException( e );
            throw new PwmDataValidationException( errorInfo );
        }
    }

    private static ErrorInformation convertChaiValidationException(
            final ChaiValidationException e
    )
    {
        final String[] fieldNames = new String[] {
                e.getFieldName(),
        };

        switch ( e.getErrorCode() )
        {
            case CR_TOO_FEW_CHALLENGES:
                return new ErrorInformation( PwmError.ERROR_MISSING_REQUIRED_RESPONSE, null, fieldNames );

            case CR_TOO_FEW_RANDOM_RESPONSES:
                return new ErrorInformation( PwmError.ERROR_MISSING_RANDOM_RESPONSE, null, fieldNames );

            case CR_MISSING_REQUIRED_CHALLENGE_TEXT:
                return new ErrorInformation( PwmError.ERROR_MISSING_CHALLENGE_TEXT, null, fieldNames );

            case CR_RESPONSE_TOO_LONG:
                return new ErrorInformation( PwmError.ERROR_RESPONSE_TOO_LONG, null, fieldNames );

            case CR_RESPONSE_TOO_SHORT:
            case CR_MISSING_REQUIRED_RESPONSE_TEXT:
                return new ErrorInformation( PwmError.ERROR_RESPONSE_TOO_SHORT, null, fieldNames );

            case CR_DUPLICATE_RESPONSES:
                return new ErrorInformation( PwmError.ERROR_RESPONSE_DUPLICATE, null, fieldNames );

            case CR_TOO_MANY_QUESTION_CHARS:
                return new ErrorInformation( PwmError.ERROR_CHALLENGE_IN_RESPONSE, null, fieldNames );

            default:
                return new ErrorInformation( PwmError.ERROR_INTERNAL );
        }
    }

    private void initializeBean(
            final PwmRequest pwmRequest,
            final SetupResponsesBean setupResponsesBean
    )
            throws PwmUnrecoverableException
    {
        if ( pwmRequest.getPwmSession().getUserInfo().getResponseInfoBean() != null )
        {
            setupResponsesBean.setHasExistingResponses( true );
        }

        final ChallengeProfile challengeProfile = pwmRequest.getPwmSession().getUserInfo().getChallengeProfile();
        if ( setupResponsesBean.getResponseData() == null )
        {
            //setup user challenge data
            final ChallengeSet userChallengeSet = challengeProfile.getChallengeSet();
            final int minRandomSetup = challengeProfile.getMinRandomSetup();
            final SetupResponsesBean.SetupData userSetupData = populateSetupData( userChallengeSet, minRandomSetup );
            setupResponsesBean.setResponseData( userSetupData );
        }
        if ( setupResponsesBean.getHelpdeskResponseData() == null )
        {
            //setup helpdesk challenge data
            final ChallengeSet helpdeskChallengeSet = challengeProfile.getHelpdeskChallengeSet();
            if ( helpdeskChallengeSet == null )
            {
                setupResponsesBean.setHelpdeskResponseData( new SetupResponsesBean.SetupData() );
            }
            else
            {
                final int minRandomHelpdeskSetup = challengeProfile.getMinHelpdeskRandomsSetup();
                final SetupResponsesBean.SetupData helpdeskSetupData = populateSetupData( helpdeskChallengeSet, minRandomHelpdeskSetup );
                setupResponsesBean.setHelpdeskResponseData( helpdeskSetupData );
            }
        }
    }

    private static SetupResponsesBean.SetupData populateSetupData(
            final ChallengeSet challengeSet,
            final int minRandomSetup
    )
    {
        boolean useSimple = true;
        final Map<String, Challenge> indexedChallenges = new LinkedHashMap<>();

        int minRandom = minRandomSetup;

        {
            if ( minRandom != 0 && minRandom < challengeSet.getMinRandomRequired() )
            {
                minRandom = challengeSet.getMinRandomRequired();
            }
            if ( minRandom > challengeSet.getRandomChallenges().size() )
            {
                minRandom = 0;
            }
        }
        {
            {
                if ( minRandom == 0 )
                {
                    useSimple = false;
                }

                for ( final Challenge challenge : challengeSet.getChallenges() )
                {
                    if ( !challenge.isRequired() && !challenge.isAdminDefined() )
                    {
                        useSimple = false;
                    }
                }

                if ( challengeSet.getRandomChallenges().size() == challengeSet.getMinRandomRequired() )
                {
                    useSimple = false;
                }
            }
        }

        {
            int index = 0;
            for ( final Challenge loopChallenge : challengeSet.getChallenges() )
            {
                indexedChallenges.put( String.valueOf( index ), loopChallenge );
                index++;
            }
        }

        final SetupResponsesBean.SetupData setupData = new SetupResponsesBean.SetupData();
        setupData.setChallengeSet( challengeSet );
        setupData.setSimpleMode( useSimple );
        setupData.setIndexedChallenges( indexedChallenges );
        setupData.setMinRandomSetup( minRandom );
        return setupData;
    }

    @Value
    private static class ValidationResponseBean implements Serializable
    {
        private String message;
        private boolean success;
    }

    private static boolean checkIfAllowSkipCr( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException
    {
        if ( pwmRequest.isForcedPageView() )
        {
            final boolean admin = pwmRequest.getPwmSession().getSessionManager().checkPermission( pwmRequest.getPwmApplication(), Permission.PWMADMIN );
            if ( admin )
            {
                if ( pwmRequest.getConfig().readSettingAsBoolean( PwmSetting.ADMIN_ALLOW_SKIP_FORCED_ACTIVITIES ) )
                {
                    LOGGER.trace( pwmRequest, () -> "allowing c/r answer setup skipping due to user being admin and setting "
                            + PwmSetting.ADMIN_ALLOW_SKIP_FORCED_ACTIVITIES.toMenuLocationDebug( null, pwmRequest.getLocale() ) );
                    return true;
                }
            }
        }

        return false;
    }
}

