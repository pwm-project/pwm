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

package password.pwm.ws.client.rest.naaf;

import password.pwm.RecoveryVerificationMethod;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpMethod;
import password.pwm.http.client.PwmHttpClientResponse;
import password.pwm.util.JsonUtil;
import password.pwm.util.logging.PwmLogger;

import java.io.Serializable;
import java.util.*;

public class NAAFLoginSequence {
    private static final PwmLogger LOGGER = PwmLogger.forClass(NAAFLoginSequence.class);
    private static int instanceCounter = 0;

    private final NAAFEndPoint naafEndPoint;
    private int instanceID = instanceCounter++;

    private NAAFLoginResponseBean lastResponseBean;
    private ErrorInformation lastError;

    private final List<NAAFLoginMethod> requiredMethods;
    private final List<NAAFLoginMethod> completedMethods = new ArrayList<>();
    private final String username;
    private final Locale locale;

    private CurrentState currentState;

    static class CurrentState {
        private NAAFLoginMethod currentMethod;
        private NAAFMethodHandler currentMethodHandler;
    }

    public NAAFLoginSequence(
            final NAAFEndPoint naafEndPoint,
            final Collection<NAAFLoginMethod> requiredMethods,
            final String username,
            final Locale locale
    )
            throws PwmUnrecoverableException
    {
        logDebug("new instance");
        this.naafEndPoint = naafEndPoint;
        this.requiredMethods = new ArrayList<>(requiredMethods);
        this.username = username;
        this.locale = locale;
        cycleMethods();
    }

    private void cycleMethods()
            throws PwmUnrecoverableException
    {
        if (currentState != null) {
            return;
        }
        for (final NAAFLoginMethod loginMethod : requiredMethods) {
            if (currentState == null) {
                if (!completedMethods.contains(loginMethod)) {
                    try {
                        final CurrentState newState = new CurrentState();
                        newState.currentMethod = loginMethod;
                        newState.currentMethodHandler = loginMethod.getNaafMethodHandler().newInstance();
                        newState.currentMethodHandler.init(this);
                        currentState = newState;
                        beginLogin();
                        logDebug("currentMethod is now: " + loginMethod);
                    } catch (IllegalAccessException | InstantiationException e) {
                        final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN,e.getMessage());
                        LOGGER.error(errorInformation.toDebugStr(),e);
                        throw new PwmUnrecoverableException(errorInformation);
                    }
                }
            }
        }
    }

    private void beginLogin()
            throws PwmUnrecoverableException
    {
        cycleMethods();

        // initial login session establishment;
        final HashMap<String, String> beginLoginParamters = new HashMap<>();
        beginLoginParamters.put("user_name", username);
        beginLoginParamters.put("method_id", currentState.currentMethod.getNaafName());
        beginLoginParamters.put("endpoint_session_id", naafEndPoint.getEndpoint_session_id());
        final PwmHttpClientResponse response = naafEndPoint.makeApiRequest(
                HttpMethod.POST,
                "/logon",
                beginLoginParamters
        );

        checkResponseForError(response.getBody());
        lastResponseBean = JsonUtil.deserialize(response.getBody(), NAAFLoginResponseBean.class);
    }

    public Map<String,String> nextPrompt(final Locale locale) throws PwmUnrecoverableException {
        return currentState.currentMethodHandler.getPrompts(locale);
    }

    public String answerPrompts(final Map<String, String> promptAnswers) throws PwmUnrecoverableException {
        return currentState.currentMethodHandler.answerPrompts(promptAnswers);
    }


    String sendResponse(final Serializable responseData) throws PwmUnrecoverableException {
        final HashMap<String, Object> loginParams = new HashMap<>();
        loginParams.put("login_process_id", lastResponseBean.getLogon_process_id());
        loginParams.put("endpoint_session_id", naafEndPoint.getEndpoint_session_id());
        loginParams.put("response", responseData);

        final PwmHttpClientResponse response = naafEndPoint.makeApiRequest(
                HttpMethod.POST,
                "/logon/" + lastResponseBean.getLogon_process_id() + "/do_logon",
                loginParams
        );

        checkResponseForError(response.getBody());
        lastResponseBean = JsonUtil.deserialize(response.getBody(), NAAFLoginResponseBean.class);
        final String lastMsg = lastResponseBean.getMsg();
        logDebug("response from NAAF for last submit: " + lastMsg);

        if (responsesContainsCompletedMethod(lastResponseBean,currentState.currentMethod)) {
            completedMethods.add(currentState.currentMethod);
            currentState = null;
            cycleMethods();
            return null;
        }

        switch (lastResponseBean.getStatus()) {
            case FAILED:
            case OK:
                currentState = null;
        }
        if (currentState == null) {
            cycleMethods();
        }

        return lastMsg;
    }

    private void checkResponseForError(final String body) throws PwmUnrecoverableException {
        final NAAFErrorResponseBean errorResponseBean = JsonUtil.deserialize(body, NAAFErrorResponseBean.class);
        if (errorResponseBean != null) {
            if ("error".equalsIgnoreCase(errorResponseBean.getStatus())) {
                String errorMsg = "unknown";
                if (errorResponseBean.getErrors() != null && !errorResponseBean.getErrors().isEmpty()) {
                    errorMsg = errorResponseBean.getErrors().iterator().next().getDescription();
                }
                lastError = new ErrorInformation(PwmError.ERROR_REMOTE_ERROR_VALUE, errorMsg);
                throw new PwmUnrecoverableException(lastError);
            }
        }
    }

    public RecoveryVerificationMethod.VerificationState status() {
        if (lastError != null) {
            return RecoveryVerificationMethod.VerificationState.FAILED;
        }
        if (completedMethods.containsAll(requiredMethods)) {
            return RecoveryVerificationMethod.VerificationState.COMPLETE;
        }
        return RecoveryVerificationMethod.VerificationState.INPROGRESS;
    }


    public NAAFLoginResponseBean getLastResponseBean() {
        return lastResponseBean;
    }

    boolean responsesContainsCompletedMethod(final NAAFLoginResponseBean naafLoginResponseBean, final NAAFLoginMethod naafLoginMethod) {
        if (naafLoginMethod == null || naafLoginResponseBean == null) {
            return false;
        }

        if (naafLoginResponseBean.getCompleted_methods() == null) {
            return false;
        }

        if (naafLoginResponseBean.getCompleted_methods().contains(naafLoginMethod.getNaafName())) {
            return true;
        }

        return false;
    }

    private void logDebug(final String message) {
        LOGGER.debug("id="+instanceID + " " + message);
    }

    public Locale getLocale() {
        return locale;
    }

    public NAAFLoginMethod currentMethod() {
        if (currentState != null) {
            return currentState.currentMethod;
        }

        return null;
    }
}
