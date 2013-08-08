/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2012 The PWM Project
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

package password.pwm.servlet;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.cr.ChaiCrFactory;
import com.novell.ldapchai.cr.ChaiResponseSet;
import com.novell.ldapchai.cr.Challenge;
import com.novell.ldapchai.cr.ChallengeSet;
import com.novell.ldapchai.exception.ChaiError;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.exception.ChaiValidationException;
import com.novell.ldapchai.provider.ChaiProvider;
import password.pwm.*;
import password.pwm.bean.ResponseInfoBean;
import password.pwm.bean.SessionStateBean;
import password.pwm.bean.servlet.SetupResponsesBean;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.PwmSetting;
import password.pwm.error.*;
import password.pwm.event.AuditEvent;
import password.pwm.i18n.Message;
import password.pwm.util.PwmLogger;
import password.pwm.util.ServletHelper;
import password.pwm.util.operations.UserStatusHelper;
import password.pwm.util.stats.Statistic;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;

/**
 * User interaction servlet for setting up secret question/answer
 *
 * @author Jason D. Rivard
 */
public class SetupResponsesServlet extends TopServlet {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(SetupResponsesServlet.class);

// -------------------------- OTHER METHODS --------------------------

    protected void processRequest(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws ServletException, ChaiUnavailableException, IOException, PwmUnrecoverableException
    {
        // fetch the required beans / managers
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();
        final UserInfoBean uiBean = pwmSession.getUserInfoBean();

        if (!pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.CHALLENGE_ENABLE)) {
            PwmSession.getPwmSession(req).getSessionStateBean().setSessionError(PwmError.ERROR_SERVICE_NOT_AVAILABLE.toInfo());
            ServletHelper.forwardToErrorPage(req, resp, this.getServletContext());
            return;
        }

        // check to see if the user is permitted to setup responses
        if (!Permission.checkPermission(Permission.SETUP_RESPONSE, pwmSession, pwmApplication)) {
            ssBean.setSessionError(new ErrorInformation(PwmError.ERROR_UNAUTHORIZED));
            ServletHelper.forwardToErrorPage(req, resp, this.getServletContext());
            return;
        }

        if (pwmSession.getUserInfoBean().getAuthenticationType() == UserInfoBean.AuthenticationType.AUTH_WITHOUT_PASSWORD) {
            throw new PwmUnrecoverableException(PwmError.ERROR_PASSWORD_REQUIRED);
        }

        // read the action request parameter
        final String actionParam = Validator.readStringFromRequest(req, PwmConstants.PARAM_ACTION_REQUEST);

        SetupResponsesBean setupResponsesBean = (SetupResponsesBean)pwmSession.getSessionBean(SetupResponsesBean.class);

        // check if the locale has changed since first seen.
        if (pwmSession.getSessionStateBean().getLocale() != setupResponsesBean.getUserLocale()) {
            pwmSession.clearUserBean(SetupResponsesBean.class);
            setupResponsesBean = (SetupResponsesBean)pwmSession.getSessionBean(SetupResponsesBean.class);
            setupResponsesBean.setUserLocale(pwmSession.getSessionStateBean().getLocale());
        }
        initializeBean(pwmSession, pwmApplication, setupResponsesBean);

        // check to see if the user has any challenges assigned
        if (setupResponsesBean.getResponseData().getChallengeSet() == null || setupResponsesBean.getResponseData().getChallengeSet().getChallenges().isEmpty()) {
            ssBean.setSessionError(new ErrorInformation(PwmError.ERROR_NO_CHALLENGES));
            LOGGER.debug(pwmSession, "no challenge sets configured for user " + uiBean.getUserDN());
            ServletHelper.forwardToErrorPage(req, resp, this.getServletContext());
            return;
        }

        if (actionParam != null && actionParam.length() > 0) {
            Validator.validatePwmFormID(req);

            // handle the requested action.
            if ("validateResponses".equalsIgnoreCase(actionParam)) {
                handleValidateResponses(req, resp, setupResponsesBean);
                return;
            } else if ("setResponses".equalsIgnoreCase(actionParam)) {
                handleSetupResponses(req, resp, setupResponsesBean, false);
                return;
            } else if ("setHelpdeskResponses".equalsIgnoreCase(actionParam)) {
                handleSetupResponses(req, resp, setupResponsesBean, true);
                return;
            } else if ("confirmResponses".equalsIgnoreCase(actionParam)) {
                setupResponsesBean.setConfirmed(true);
            } else if ("changeResponses".equalsIgnoreCase(actionParam)) {
                pwmSession.clearUserBean(SetupResponsesBean.class);
            }
        }

        this.advanceToNextStage(req, resp, setupResponsesBean);
    }

    private void advanceToNextStage(
            final HttpServletRequest req,
            final HttpServletResponse resp,
            final SetupResponsesBean setupResponsesBean
    )
            throws PwmUnrecoverableException, IOException, ServletException, ChaiUnavailableException
    {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);

        if (!setupResponsesBean.isResponsesSatisfied()) {
            this.forwardToSetupJSP(req,resp);
            return;
        }

        if (!setupResponsesBean.isHelpdeskResponsesSatisfied()) {
            if (setupResponsesBean.getHelpdeskResponseData().getChallengeSet() == null ||
                    setupResponsesBean.getHelpdeskResponseData().getChallengeSet().getChallenges().isEmpty())
            {
                setupResponsesBean.setHelpdeskResponsesSatisfied(true);
            } else {
                this.forwardToSetupHelpdeskJSP(req, resp);
                return;
            }
        }

        if (pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.CHALLENGE_SHOW_CONFIRMATION)) {
            if (!setupResponsesBean.isConfirmed()) {
                this.forwardToConfirmJSP(req,resp);
                return;
            }
        }

        try { // everything good, so lets save responses.
            final ResponseInfoBean responses = generateResponseInfoBean(
                    pwmSession,
                    setupResponsesBean.getResponseData().getChallengeSet(),
                    setupResponsesBean.getResponseData().getResponseMap(),
                    setupResponsesBean.getHelpdeskResponseData().getResponseMap()
            );
            saveResponses(pwmSession, pwmApplication, responses);
            pwmSession.clearUserBean(SetupResponsesBean.class);
            ServletHelper.forwardToSuccessPage(req, resp);
        } catch (PwmOperationalException e) {
            LOGGER.error(pwmSession, e.getErrorInformation().toDebugStr());
            pwmSession.getSessionStateBean().setSessionError(e.getErrorInformation());
            ServletHelper.forwardToErrorPage(req, resp, false);
        } catch (ChaiValidationException e) {
            LOGGER.error(pwmSession, e.getMessage());
            pwmSession.getSessionStateBean().setSessionError(new ErrorInformation(PwmError.ERROR_MISSING_RANDOM_RESPONSE,e.getMessage()));
            ServletHelper.forwardToErrorPage(req, resp, false);
        }
    }

    /**
     * Handle requests for ajax feedback of user supplied responses.
     */
    protected static void handleValidateResponses(
            final HttpServletRequest req,
            final HttpServletResponse resp,
            final SetupResponsesBean setupResponsesBean
    )
            throws IOException, ServletException, PwmUnrecoverableException, ChaiUnavailableException
    {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
        final String responseModeParam = Validator.readStringFromRequest(req,"responseMode");
        final SetupResponsesBean.SetupData setupData = "helpdesk".equalsIgnoreCase(responseModeParam) ? setupResponsesBean.getHelpdeskResponseData() : setupResponsesBean.getResponseData();

        boolean success = true;
        String userMessage = Message.getLocalizedMessage(pwmSession.getSessionStateBean().getLocale(), Message.SUCCESS_RESPONSES_MEET_RULES, pwmApplication.getConfig());

        try {
            // read in the responses from the request
            final Map<Challenge, String> responseMap = readResponsesFromJsonRequest(req, pwmApplication, setupData);
            final int minRandomRequiredSetup = setupData.getMinRandomSetup();
            pwmApplication.getCrService().validateResponses(setupData.getChallengeSet(), responseMap, minRandomRequiredSetup);
            generateResponseInfoBean(pwmSession, setupData.getChallengeSet(), responseMap, Collections.<Challenge,String>emptyMap());
        } catch (PwmDataValidationException e) {
            success = false;
            userMessage = e.getErrorInformation().toUserStr(pwmSession, pwmApplication);
        }

        final AjaxValidationBean ajaxValidationBean = new AjaxValidationBean(userMessage,success);
        final String output = new Gson().toJson(ajaxValidationBean);

        resp.setContentType("text/plain;charset=utf-8");
        resp.getWriter().print(output);

        LOGGER.trace(pwmSession, "ajax validate responses: " + output);
    }

    private void handleSetupResponses(
            final HttpServletRequest req,
            final HttpServletResponse resp,
            final SetupResponsesBean setupResponsesBean,
            final boolean helpdeskMode
    )
            throws PwmUnrecoverableException, IOException, ServletException, ChaiUnavailableException
    {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();
        final SetupResponsesBean.SetupData setupData = helpdeskMode ? setupResponsesBean.getHelpdeskResponseData() : setupResponsesBean.getResponseData();

        final ChallengeSet challengeSet = setupData.getChallengeSet();
        final Map<Challenge, String> responseMap;
        try {
            // build a response set based on the user's challenge set and the html form response.
            responseMap = readResponsesFromHttpRequest(req, setupData);

            // test the responses.
            final int minRandomRequiredSetup = setupData.getMinRandomSetup();
            pwmApplication.getCrService().validateResponses(challengeSet, responseMap, minRandomRequiredSetup);
        } catch (PwmDataValidationException e) {
            LOGGER.debug(pwmSession, "error with new " + (helpdeskMode ? "helpdesk" : "user") + " responses: " + e.getErrorInformation().toDebugStr());
            ssBean.setSessionError(e.getErrorInformation());
            this.advanceToNextStage(req,resp,setupResponsesBean);
            return;
        }

        LOGGER.trace(pwmSession, "new " + (helpdeskMode ? "helpdesk" : "user") + " responses are acceptable");
        if (helpdeskMode) {
            setupResponsesBean.getHelpdeskResponseData().setResponseMap(responseMap);
            setupResponsesBean.setHelpdeskResponsesSatisfied(true);
        } else {
            setupResponsesBean.getResponseData().setResponseMap(responseMap);
            setupResponsesBean.setResponsesSatisfied(true);
        }
        this.advanceToNextStage(req, resp, setupResponsesBean);
    }

    private void saveResponses(final PwmSession pwmSession, final PwmApplication pwmApplication, final ResponseInfoBean responseInfoBean)
            throws PwmUnrecoverableException, ChaiUnavailableException, PwmOperationalException, ChaiValidationException
    {
        final ChaiUser theUser = pwmSession.getSessionManager().getActor();
        final String userGUID = pwmSession.getUserInfoBean().getUserGuid();
        pwmApplication.getCrService().writeResponses(theUser, userGUID, responseInfoBean);
        final UserInfoBean uiBean = pwmSession.getUserInfoBean();
        UserStatusHelper.populateActorUserInfoBean(pwmSession, pwmApplication, uiBean.getUserDN(), uiBean.getUserCurrentPassword());
        pwmApplication.getStatisticsManager().incrementValue(Statistic.SETUP_RESPONSES);
        pwmSession.getUserInfoBean().setRequiresResponseConfig(false);
        pwmSession.getSessionStateBean().setSessionSuccess(Message.SUCCESS_SETUP_RESPONSES, null);
        pwmApplication.getAuditManager().submitAuditRecord(AuditEvent.SET_RESPONSES, pwmSession.getUserInfoBean(), pwmSession);
    }

    private static Map<Challenge, String> readResponsesFromHttpRequest(
            final HttpServletRequest req,
            final SetupResponsesBean.SetupData setupData
    )
            throws PwmDataValidationException, PwmUnrecoverableException
    {
        final Map<String, String> inputMap = new HashMap<String, String>();

        for (Enumeration nameEnum = req.getParameterNames(); nameEnum.hasMoreElements();) {
            final String paramName = nameEnum.nextElement().toString();
            final String paramValue = Validator.readStringFromRequest(req, paramName);
            inputMap.put(paramName, paramValue);
        }

        return paramMapToChallengeMap(inputMap, setupData);
    }

    private static Map<Challenge, String> readResponsesFromJsonRequest(
            final HttpServletRequest req,
            final PwmApplication pwmApplication,
            final SetupResponsesBean.SetupData setupData
    )
            throws PwmDataValidationException, PwmUnrecoverableException, IOException
    {
        final Map<String, String> inputMap = new HashMap<String, String>();

        final String bodyString = ServletHelper.readRequestBody(req);

        final Gson gson = new Gson();
        final Map<String, String> srcMap = gson.fromJson(bodyString, new TypeToken<Map<String, String>>() {
        }.getType());

        if (srcMap == null) {
            return null;
        }

        for (final String key : srcMap.keySet()) {
            if (key != null) {
                final String paramValue = Validator.sanatizeInputValue(pwmApplication.getConfig(),srcMap.get(key),0);
                if (paramValue != null && paramValue.length() > 0) {
                    inputMap.put(key, paramValue);
                }
            }
        }

        return paramMapToChallengeMap(inputMap, setupData);
    }

    private static Map<Challenge, String> paramMapToChallengeMap(
            final Map<String, String> inputMap,
            final SetupResponsesBean.SetupData setupData
    )
            throws PwmDataValidationException, PwmUnrecoverableException
    {
        final Map<Challenge, String> readResponses = new LinkedHashMap<Challenge, String>();
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
            final PwmSession pwmSession,
            final ChallengeSet challengeSet,
            final Map<Challenge, String> readResponses,
            final Map<Challenge, String> helpdeskResponses
    )
            throws ChaiUnavailableException, PwmDataValidationException, PwmUnrecoverableException {
        final ChaiProvider provider = pwmSession.getSessionManager().getChaiProvider();

        try {
            final ResponseInfoBean responseInfoBean = new ResponseInfoBean(
                    readResponses,
                    helpdeskResponses,
                    challengeSet.getLocale(),
                    challengeSet.getMinRandomRequired(),
                    challengeSet.getIdentifier()
            );

            final ChaiResponseSet responseSet = ChaiCrFactory.newChaiResponseSet(
                    readResponses,
                    challengeSet.getLocale(),
                    challengeSet.getMinRandomRequired(),
                    provider.getChaiConfiguration(),
                    challengeSet.getIdentifier());

            responseSet.meetsChallengeSetRequirements(challengeSet);

            final int minRandomRequiredSetup = pwmSession.getSetupResponseBean().getResponseData().getMinRandomSetup();
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

    private void forwardToSetupJSP(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException {
        this.getServletContext().getRequestDispatcher('/' + PwmConstants.URL_JSP_SETUP_RESPONSES).forward(req, resp);
    }

    private void forwardToSetupHelpdeskJSP(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException {
        this.getServletContext().getRequestDispatcher('/' + PwmConstants.URL_JSP_SETUP_HELPDESK_RESPONSES).forward(req, resp);
    }

    private void forwardToConfirmJSP(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException {
        this.getServletContext().getRequestDispatcher('/' + PwmConstants.URL_JSP_CONFIRM_RESPONSES).forward(req, resp);
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

            default:
                return new ErrorInformation(PwmError.ERROR_UNKNOWN);
        }
    }

    private void initializeBean(final PwmSession pwmSession, final PwmApplication pwmApplication, final SetupResponsesBean setupResponsesBean)
    {
        if (setupResponsesBean.getResponseData() == null) { //setup user challenge data
            final ChallengeSet userChallengeSet = pwmSession.getUserInfoBean().getChallengeSet();
            final int minRandomSetup = (int)pwmApplication.getConfig().readSettingAsLong(PwmSetting.CHALLENGE_MIN_RANDOM_SETUP);
            final SetupResponsesBean.SetupData userSetupData = populateSetupData(userChallengeSet,minRandomSetup);
            setupResponsesBean.setResponseData(userSetupData);
        }
        if (setupResponsesBean.getHelpdeskResponseData() == null) { //setup helpdesk challenge data
            final ChallengeSet helpdeskChallengeSet = pwmApplication.getConfig().getHelpdeskChallengeSet(pwmSession.getSessionStateBean().getLocale());
            if (helpdeskChallengeSet == null) {
                setupResponsesBean.setHelpdeskResponseData(new SetupResponsesBean.SetupData());
            } else {
                final int minRandomHelpdeskSetup = (int)pwmApplication.getConfig().readSettingAsLong(PwmSetting.CHALLENGE_HELPDESK_MIN_RANDOM_SETUP);
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
        final Map<String, Challenge> indexedChallenges = new LinkedHashMap<String, Challenge>();

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

    private static class AjaxValidationBean {
        final private int version = 1;
        private String message;
        private boolean success;

        private AjaxValidationBean(String message, boolean success) {
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

