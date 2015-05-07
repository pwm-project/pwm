/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2015 The PWM Project
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
import com.novell.ldapchai.cr.ChaiCrFactory;
import com.novell.ldapchai.cr.ChaiResponseSet;
import com.novell.ldapchai.cr.Challenge;
import com.novell.ldapchai.cr.ChallengeSet;
import com.novell.ldapchai.exception.ChaiError;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.exception.ChaiValidationException;
import com.novell.ldapchai.provider.ChaiProvider;
import password.pwm.Permission;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.Validator;
import password.pwm.bean.ResponseInfoBean;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.PwmSetting;
import password.pwm.config.profile.ChallengeProfile;
import password.pwm.error.*;
import password.pwm.event.AuditEvent;
import password.pwm.event.UserAuditRecord;
import password.pwm.http.HttpMethod;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmSession;
import password.pwm.http.bean.SetupResponsesBean;
import password.pwm.i18n.Message;
import password.pwm.ldap.UserStatusReader;
import password.pwm.ldap.auth.AuthenticationType;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.stats.Statistic;
import password.pwm.ws.server.RestResultBean;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * User interaction servlet for setting up secret question/answer
 *
 * @author Jason D. Rivard
 */
public class SetupResponsesServlet extends PwmServlet {

    private static final PwmLogger LOGGER = PwmLogger.forClass(SetupResponsesServlet.class);

    public enum SetupResponsesAction implements PwmServlet.ProcessAction {
        validateResponses(HttpMethod.POST),
        setResponses(HttpMethod.POST),
        setHelpdeskResponses(HttpMethod.POST),
        confirmResponses(HttpMethod.POST),
        clearExisting(HttpMethod.POST),
        changeResponses(HttpMethod.POST),

        ;

        private final HttpMethod method;

        SetupResponsesAction(HttpMethod method)
        {
            this.method = method;
        }

        public Collection<HttpMethod> permittedMethods()
        {
            return Collections.singletonList(method);
        }
    }

    protected SetupResponsesAction readProcessAction(final PwmRequest request)
            throws PwmUnrecoverableException
    {
        try {
            return SetupResponsesAction.valueOf(request.readParameterAsString(PwmConstants.PARAM_ACTION_REQUEST));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    protected void processAction(final PwmRequest pwmRequest)
            throws ServletException, IOException, ChaiUnavailableException, PwmUnrecoverableException
    {
        // fetch the required beans / managers
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final UserInfoBean uiBean = pwmSession.getUserInfoBean();


        if (!pwmSession.getSessionStateBean().isAuthenticated()) {
            pwmRequest.respondWithError(PwmError.ERROR_AUTHENTICATION_REQUIRED.toInfo());
            return;
        }

        if (pwmSession.getLoginInfoBean().getAuthenticationType() == AuthenticationType.AUTH_WITHOUT_PASSWORD) {
            throw new PwmUnrecoverableException(PwmError.ERROR_PASSWORD_REQUIRED);
        }

        if (!pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.CHALLENGE_ENABLE)) {
            pwmRequest.respondWithError(PwmError.ERROR_SERVICE_NOT_AVAILABLE.toInfo());
            return;
        }

        // check to see if the user is permitted to setup responses
        if (!pwmSession.getSessionManager().checkPermission(pwmApplication, Permission.SETUP_RESPONSE)) {
            pwmRequest.respondWithError(PwmError.ERROR_UNAUTHORIZED.toInfo());
            return;
        }

        // check if the locale has changed since first seen.
        if (pwmSession.getSessionStateBean().getLocale() != pwmSession.getSetupResponseBean().getUserLocale()) {
            pwmSession.clearSessionBean(SetupResponsesBean.class);
            pwmSession.getSetupResponseBean().setUserLocale(pwmSession.getSessionStateBean().getLocale());
        }

        SetupResponsesBean setupResponsesBean = pwmSession.getSessionBean(SetupResponsesBean.class);
        initializeBean(pwmRequest, setupResponsesBean);

        // check to see if the user has any challenges assigned
        if (setupResponsesBean.getResponseData().getChallengeSet() == null || setupResponsesBean.getResponseData().getChallengeSet().getChallenges().isEmpty()) {
            LOGGER.debug(pwmSession, "no challenge sets configured for user " + uiBean.getUserIdentity());
            pwmRequest.respondWithError(PwmError.ERROR_NO_CHALLENGES.toInfo());
            return;
        }

        // read the action request parameter
        final SetupResponsesAction action = readProcessAction(pwmRequest);

        if (action != null) {
            Validator.validatePwmFormID(pwmRequest);

            switch (action) {
                case validateResponses:
                    restValidateResponses(pwmRequest, setupResponsesBean);
                    return;

                case setResponses:
                    handleSetupResponses(pwmRequest, setupResponsesBean, false);
                    break;

                case setHelpdeskResponses:
                    handleSetupResponses(pwmRequest, setupResponsesBean, true);
                    break;

                case confirmResponses:
                    setupResponsesBean.setConfirmed(true);
                    break;

                case clearExisting:
                    handleClearResponses(pwmRequest);
                    return;

                case changeResponses:
                    pwmSession.clearSessionBean(SetupResponsesBean.class);
                    setupResponsesBean = pwmSession.getSessionBean(SetupResponsesBean.class);
                    this.initializeBean(pwmRequest, setupResponsesBean);
                    setupResponsesBean.setUserLocale(pwmSession.getSessionStateBean().getLocale());


            }
        }

        this.advanceToNextStage(pwmRequest, setupResponsesBean);
    }

    private void handleClearResponses(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException, ChaiUnavailableException, IOException {
        LOGGER.trace(pwmRequest, "request for response clear received");
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        try {
            final String userGUID = pwmSession.getUserInfoBean().getUserGuid();
            final ChaiUser theUser = pwmSession.getSessionManager().getActor(pwmApplication);
            pwmApplication.getCrService().clearResponses(pwmSession, theUser, userGUID);
            UserStatusReader userStatusReader = new UserStatusReader(pwmApplication, pwmRequest.getSessionLabel());
            userStatusReader.populateLocaleSpecificUserInfoBean(pwmSession.getUserInfoBean(),pwmRequest.getLocale());
            pwmSession.clearSessionBean(SetupResponsesBean.class);

            // mark the event log
            final UserAuditRecord auditRecord = pwmApplication.getAuditManager().createUserAuditRecord(
                    AuditEvent.CLEAR_RESPONSES,
                    pwmSession.getUserInfoBean(),
                    pwmSession
            );
            pwmApplication.getAuditManager().submit(auditRecord);

            pwmRequest.sendRedirect(pwmRequest.getHttpServletRequest().getContextPath() + "/private/" + PwmConstants.URL_SERVLET_SETUP_RESPONSES);
        } catch (PwmOperationalException e) {
            LOGGER.debug(pwmSession, e.getErrorInformation());
            pwmRequest.setResponseError(e.getErrorInformation());
        }
    }

    private void advanceToNextStage(
            final PwmRequest pwmRequest,
            final SetupResponsesBean setupResponsesBean
    )
            throws PwmUnrecoverableException, IOException, ServletException, ChaiUnavailableException
    {

        if (setupResponsesBean.isHasExistingResponses()) {
            pwmRequest.forwardToJsp(PwmConstants.JSP_URL.SETUP_RESPONSES_EXISTING);
            return;
        }

        if (!setupResponsesBean.isResponsesSatisfied()) {
            pwmRequest.forwardToJsp(PwmConstants.JSP_URL.SETUP_RESPONSES);
            return;
        }

        if (!setupResponsesBean.isHelpdeskResponsesSatisfied()) {
            if (setupResponsesBean.getHelpdeskResponseData().getChallengeSet() == null ||
                    setupResponsesBean.getHelpdeskResponseData().getChallengeSet().getChallenges().isEmpty())
            {
                setupResponsesBean.setHelpdeskResponsesSatisfied(true);
            } else {
                pwmRequest.forwardToJsp(PwmConstants.JSP_URL.SETUP_RESPONSES_HELPDESK);
                return;
            }
        }

        if (pwmRequest.getConfig().readSettingAsBoolean(PwmSetting.CHALLENGE_SHOW_CONFIRMATION)) {
            if (!setupResponsesBean.isConfirmed()) {
                pwmRequest.forwardToJsp(PwmConstants.JSP_URL.SETUP_RESPONSES_CONFIRM);
                return;
            }
        }

        try { // everything good, so lets save responses.
            final ResponseInfoBean responses = generateResponseInfoBean(
                    pwmRequest,
                    setupResponsesBean.getResponseData().getChallengeSet(),
                    setupResponsesBean.getResponseData().getResponseMap(),
                    setupResponsesBean.getHelpdeskResponseData().getResponseMap()
            );
            saveResponses(pwmRequest, responses);
            pwmRequest.getPwmSession().clearSessionBean(SetupResponsesBean.class);
            pwmRequest.forwardToSuccessPage(Message.Success_SetupResponse);
        } catch (PwmOperationalException e) {
            LOGGER.error(pwmRequest.getSessionLabel(), e.getErrorInformation());
            pwmRequest.respondWithError(e.getErrorInformation());
        } catch (ChaiValidationException e) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_MISSING_RANDOM_RESPONSE,e.getMessage());
            LOGGER.error(pwmRequest.getSessionLabel(), errorInformation);
            pwmRequest.respondWithError(errorInformation);
        }
    }

    /**
     * Handle requests for ajax feedback of user supplied responses.
     */
    protected static void restValidateResponses(
            final PwmRequest pwmRequest,
            final SetupResponsesBean setupResponsesBean
    )
            throws IOException, ServletException, PwmUnrecoverableException, ChaiUnavailableException
    {
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final String responseModeParam = pwmRequest.readParameterAsString("responseMode");
        final SetupResponsesBean.SetupData setupData = "helpdesk".equalsIgnoreCase(responseModeParam)
                ? setupResponsesBean.getHelpdeskResponseData()
                : setupResponsesBean.getResponseData();

        boolean success = true;
        String userMessage = Message.getLocalizedMessage(pwmSession.getSessionStateBean().getLocale(), Message.Success_ResponsesMeetRules, pwmApplication.getConfig());

        try {
            // read in the responses from the request
            final Map<Challenge, String> responseMap = readResponsesFromJsonRequest(pwmRequest, setupData);
            final int minRandomRequiredSetup = setupData.getMinRandomSetup();
            pwmApplication.getCrService().validateResponses(setupData.getChallengeSet(), responseMap, minRandomRequiredSetup);
            generateResponseInfoBean(pwmRequest, setupData.getChallengeSet(), responseMap, Collections.<Challenge,String>emptyMap());
        } catch (PwmDataValidationException e) {
            success = false;
            userMessage = e.getErrorInformation().toUserStr(pwmSession, pwmApplication);
        }

        final ValidationResponseBean validationResponseBean = new ValidationResponseBean(userMessage,success);
        pwmRequest.outputJsonResult(new RestResultBean(validationResponseBean));
    }

    private void handleSetupResponses(
            final PwmRequest pwmRequest,
            final SetupResponsesBean setupResponsesBean,
            final boolean helpdeskMode
    )
            throws PwmUnrecoverableException, IOException, ServletException, ChaiUnavailableException
    {
        final SetupResponsesBean.SetupData setupData = helpdeskMode ? setupResponsesBean.getHelpdeskResponseData() : setupResponsesBean.getResponseData();

        final ChallengeSet challengeSet = setupData.getChallengeSet();
        final Map<Challenge, String> responseMap;
        try {
            // build a response set based on the user's challenge set and the html form response.
            responseMap = readResponsesFromHttpRequest(pwmRequest, setupData);

            // test the responses.
            final int minRandomRequiredSetup = setupData.getMinRandomSetup();
            pwmRequest.getPwmApplication().getCrService().validateResponses(challengeSet, responseMap, minRandomRequiredSetup);
        } catch (PwmDataValidationException e) {
            LOGGER.debug(pwmRequest, "error with new " + (helpdeskMode ? "helpdesk" : "user") + " responses: " + e.getErrorInformation().toDebugStr());
            pwmRequest.setResponseError(e.getErrorInformation());
            return;
        }

        LOGGER.trace(pwmRequest, (helpdeskMode ? "helpdesk" : "user") + " responses are acceptable");
        if (helpdeskMode) {
            setupResponsesBean.getHelpdeskResponseData().setResponseMap(responseMap);
            setupResponsesBean.setHelpdeskResponsesSatisfied(true);
        } else {
            setupResponsesBean.getResponseData().setResponseMap(responseMap);
            setupResponsesBean.setResponsesSatisfied(true);
        }
    }

    private void saveResponses(final PwmRequest pwmRequest, final ResponseInfoBean responseInfoBean)
            throws PwmUnrecoverableException, ChaiUnavailableException, PwmOperationalException, ChaiValidationException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final ChaiUser theUser = pwmSession.getSessionManager().getActor(pwmApplication);
        final String userGUID = pwmSession.getUserInfoBean().getUserGuid();
        pwmApplication.getCrService().writeResponses(theUser, userGUID, responseInfoBean);
        final UserInfoBean uiBean = pwmSession.getUserInfoBean();
        final UserStatusReader userStatusReader = new UserStatusReader(pwmApplication, pwmSession.getLabel());
        userStatusReader.populateActorUserInfoBean(pwmSession, uiBean.getUserIdentity());
        pwmApplication.getStatisticsManager().incrementValue(Statistic.SETUP_RESPONSES);
        pwmSession.getUserInfoBean().setRequiresResponseConfig(false);
        pwmSession.getSessionStateBean().setSessionSuccess(Message.Success_SetupResponse, null);
        pwmApplication.getAuditManager().submit(AuditEvent.SET_RESPONSES, pwmSession.getUserInfoBean(), pwmSession);
    }

    private static Map<Challenge, String> readResponsesFromHttpRequest(
            final PwmRequest pwmRequest,
            final SetupResponsesBean.SetupData setupData
    )
            throws PwmDataValidationException, PwmUnrecoverableException
    {
        final Map<String, String> inputMap = pwmRequest.readParametersAsMap();
        return paramMapToChallengeMap(inputMap, setupData);
    }

    private static Map<Challenge, String> readResponsesFromJsonRequest(
            final PwmRequest pwmRequest,
            final SetupResponsesBean.SetupData setupData
    )
            throws PwmDataValidationException, PwmUnrecoverableException, IOException
    {
        final Map<String, String> inputMap = pwmRequest.readBodyAsJsonStringMap();
        return paramMapToChallengeMap(inputMap, setupData);
    }

    private static Map<Challenge, String> paramMapToChallengeMap(
            final Map<String, String> inputMap,
            final SetupResponsesBean.SetupData setupData
    )
            throws PwmDataValidationException, PwmUnrecoverableException
    {
        final Map<Challenge, String> readResponses = new LinkedHashMap<>();
        //final SetupResponsesBean responsesBean = pwmSession.getSetupResponseBean();

        { // read in the question texts and responses
            for (final String indexKey : setupData.getIndexedChallenges().keySet()) {
                final Challenge loopChallenge = setupData.getIndexedChallenges().get(indexKey);
                if (loopChallenge.isRequired() || !setupData.isSimpleMode()) {

                    if (!loopChallenge.isAdminDefined()) {
                        final String questionText = inputMap.get(PwmConstants.PARAM_QUESTION_PREFIX + indexKey);
                        loopChallenge.setChallengeText(questionText);
                    }

                    final String answer = inputMap.get(PwmConstants.PARAM_RESPONSE_PREFIX + indexKey);

                    if (answer != null && answer.length() > 0) {
                        readResponses.put(loopChallenge, answer);
                    }
                }
            }

            if (setupData.isSimpleMode()) { // if in simple mode, read the select-based random challenges
                for (int i = 0; i < setupData.getIndexedChallenges().size(); i++) {
                    final String questionText = inputMap.get(PwmConstants.PARAM_QUESTION_PREFIX + "Random_" + String.valueOf(i));

                    Challenge challenge = null;
                    for (final Challenge loopC : setupData.getChallengeSet().getRandomChallenges()) {
                        if (loopC.isAdminDefined() && questionText != null && questionText.equals(loopC.getChallengeText())) {
                            challenge = loopC;
                            break;
                        }
                    }

                    final String answer = inputMap.get(PwmConstants.PARAM_RESPONSE_PREFIX + "Random_" + String.valueOf(i));
                    if (answer != null && answer.length() > 0) {
                        readResponses.put(challenge, answer);
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

        try {
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
                    challengeSet.getIdentifier());

            responseSet.meetsChallengeSetRequirements(challengeSet);

            final int minRandomRequiredSetup = pwmRequest.getPwmSession().getSetupResponseBean().getResponseData().getMinRandomSetup();
            if (minRandomRequiredSetup == 0) { // if using recover style, then all readResponseSet must be supplied at this point.
                if (responseSet.getChallengeSet().getRandomChallenges().size() < challengeSet.getRandomChallenges().size()) {
                    throw new ChaiValidationException("too few random responses", ChaiError.CR_TOO_FEW_RANDOM_RESPONSES);
                }
            }

            return responseInfoBean;
        } catch (ChaiValidationException e) {
            final ErrorInformation errorInfo = convertChaiValidationException(e);
            throw new PwmDataValidationException(errorInfo);
        }
    }

    private static ErrorInformation convertChaiValidationException(
            final ChaiValidationException e
    ) {
        switch (e.getErrorCode()) {
            case CR_TOO_FEW_CHALLENGES:
                return new ErrorInformation(PwmError.ERROR_MISSING_REQUIRED_RESPONSE, null, new String[]{e.getFieldName()});

            case CR_TOO_FEW_RANDOM_RESPONSES:
                return new ErrorInformation(PwmError.ERROR_MISSING_RANDOM_RESPONSE, null, new String[]{e.getFieldName()});

            case CR_MISSING_REQUIRED_CHALLENGE_TEXT:
                return new ErrorInformation(PwmError.ERROR_MISSING_CHALLENGE_TEXT, null, new String[]{e.getFieldName()});

            case CR_RESPONSE_TOO_LONG:
                return new ErrorInformation(PwmError.ERROR_RESPONSE_TOO_LONG, null, new String[]{e.getFieldName()});

            case CR_RESPONSE_TOO_SHORT:
            case CR_MISSING_REQUIRED_RESPONSE_TEXT:
                return new ErrorInformation(PwmError.ERROR_RESPONSE_TOO_SHORT, null, new String[]{e.getFieldName()});

            case CR_DUPLICATE_RESPONSES:
                return new ErrorInformation(PwmError.ERROR_RESPONSE_DUPLICATE, null, new String[]{e.getFieldName()});

            case CR_TOO_MANY_QUESTION_CHARS:
                return new ErrorInformation(PwmError.ERROR_CHALLENGE_IN_RESPONSE, null, new String[]{e.getFieldName()});

            default:
                return new ErrorInformation(PwmError.ERROR_UNKNOWN);
        }
    }

    private void initializeBean(
            final PwmRequest pwmRequest,
            final SetupResponsesBean setupResponsesBean
    )
    {
        if (pwmRequest.getPwmSession().getUserInfoBean().getResponseInfoBean() != null) {
            setupResponsesBean.setHasExistingResponses(true);

        }

        final ChallengeProfile challengeProfile = pwmRequest.getPwmSession().getUserInfoBean().getChallengeProfile();
        if (setupResponsesBean.getResponseData() == null) { //setup user challenge data
            final ChallengeSet userChallengeSet = challengeProfile.getChallengeSet();
            final int minRandomSetup = challengeProfile.getMinRandomSetup();
            final SetupResponsesBean.SetupData userSetupData = populateSetupData(userChallengeSet,minRandomSetup);
            setupResponsesBean.setResponseData(userSetupData);
        }
        if (setupResponsesBean.getHelpdeskResponseData() == null) { //setup helpdesk challenge data
            final ChallengeSet helpdeskChallengeSet = challengeProfile.getHelpdeskChallengeSet();
            if (helpdeskChallengeSet == null) {
                setupResponsesBean.setHelpdeskResponseData(new SetupResponsesBean.SetupData());
            } else {
                final int minRandomHelpdeskSetup = challengeProfile.getMinHelpdeskRandomsSetup();
                final SetupResponsesBean.SetupData helpdeskSetupData = populateSetupData(helpdeskChallengeSet,minRandomHelpdeskSetup);
                setupResponsesBean.setHelpdeskResponseData(helpdeskSetupData);
            }
        }
    }

    private static SetupResponsesBean.SetupData populateSetupData(
            final ChallengeSet challengeSet,
            int minRandomSetup
    )
    {
        boolean useSimple = true;
        final Map<String, Challenge> indexedChallenges = new LinkedHashMap<>();

        {
            if (minRandomSetup != 0 && minRandomSetup < challengeSet.getMinRandomRequired()) {
                minRandomSetup = challengeSet.getMinRandomRequired();
            }
            if (minRandomSetup > challengeSet.getRandomChallenges().size()) {
                minRandomSetup = 0;
            }
        }
        {
            {
                if (minRandomSetup == 0) {
                    useSimple = false;
                }

                for (final Challenge challenge : challengeSet.getChallenges()) {
                    if (!challenge.isRequired() && !challenge.isAdminDefined()) {
                        useSimple = false;
                    }
                }

                if (challengeSet.getRandomChallenges().size() == challengeSet.getMinRandomRequired()) {
                    useSimple = false;
                }
            }
        }

        {
            int i = 0;
            for (final Challenge loopChallenge : challengeSet.getChallenges()) {
                indexedChallenges.put(String.valueOf(i), loopChallenge);
                i++;
            }
        }

        SetupResponsesBean.SetupData setupData = new SetupResponsesBean.SetupData();
        setupData.setChallengeSet(challengeSet);
        setupData.setSimpleMode(useSimple);
        setupData.setIndexedChallenges(indexedChallenges);
        setupData.setMinRandomSetup(minRandomSetup);
        return setupData;
    }

    private static class ValidationResponseBean implements Serializable {
        final private int version = 1;
        final private String message;
        final private boolean success;

        private ValidationResponseBean(
                final String message,
                final boolean success
        ) {
            this.message = message;
            this.success = success;
        }

        public int getVersion() {
            return version;
        }

        public String getMessage() {
            return message;
        }

        public boolean isSuccess() {
            return success;
        }
    }
}

