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

package password.pwm.http.servlet.forgottenpw;

import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.RecoveryVerificationMethod;
import password.pwm.bean.*;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpMethod;
import password.pwm.http.client.PwmHttpClient;
import password.pwm.http.client.PwmHttpClientRequest;
import password.pwm.http.client.PwmHttpClientResponse;
import password.pwm.util.JsonUtil;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmRandom;

import java.util.*;

public class RemoteVerificationMethod implements RecoveryVerificationMethod {

    private static final PwmLogger LOGGER = PwmLogger.forClass(RemoteVerificationMethod.class);


    private String remoteSessionID = PwmRandom.getInstance().randomUUID().toString();

    private RemoteVerificationResponseBean lastResponse;
    private PwmHttpClient pwmHttpClient;
    private PwmApplication pwmApplication;

    private UserInfoBean userInfoBean;
    private SessionLabel sessionLabel;
    private Locale locale;
    private String url;

    @Override
    public List<UserPrompt> getCurrentPrompts() throws PwmUnrecoverableException {
        if (lastResponse == null || lastResponse.getUserPrompts() == null) {
            return null;
        }

        final List<UserPrompt> returnObj = new ArrayList<>();
        for (final UserPromptBean userPromptBean : lastResponse.getUserPrompts()) {
            returnObj.add(userPromptBean);
        }
        return returnObj;
    }

    @Override
    public String getCurrentDisplayInstructions() {
        return lastResponse == null
                ? ""
                : lastResponse.getDisplayInstructions();
    }

    @Override
    public ErrorInformation respondToPrompts(Map<String, String> answers) throws PwmUnrecoverableException {
        sendRemoteRequest(answers);
        if (lastResponse != null) {
            final String errorMsg = lastResponse.getErrorMessage();
            if (errorMsg != null && !errorMsg.isEmpty()) {
                return new ErrorInformation(PwmError.ERROR_REMOTE_ERROR_VALUE, errorMsg);
            }
        }
        return null;
    }

    @Override
    public VerificationState getVerificationState() {
        return lastResponse == null
                ? VerificationState.INPROGRESS
                : lastResponse.getVerificationState();
    }

    @Override
    public void init(PwmApplication pwmApplication, UserInfoBean userInfoBean, SessionLabel sessionLabel, Locale locale) throws PwmUnrecoverableException {
        pwmHttpClient = new PwmHttpClient(pwmApplication, sessionLabel);
        this.userInfoBean = userInfoBean;
        this.sessionLabel = sessionLabel;
        this.locale = locale;
        this.pwmApplication = pwmApplication;
        this.url = pwmApplication.getConfig().readSettingAsString(PwmSetting.EXTERNAL_MACROS_REMOTE_RESPONSES_URL);

        if (url == null || url.isEmpty()) {
            final String errorMsg = PwmSetting.EXTERNAL_MACROS_REMOTE_RESPONSES_URL.toMenuLocationDebug(null, PwmConstants.DEFAULT_LOCALE)
                    + " must be configured for remote responses";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_INVALID_CONFIG, errorMsg);
            LOGGER.error(sessionLabel, errorInformation);
            throw new PwmUnrecoverableException(errorInformation);
        }

        sendRemoteRequest(null);
    }

    private void sendRemoteRequest(final Map<String, String> userResponses) throws PwmUnrecoverableException {
        lastResponse = null;

        final Map<String, String> headers = new LinkedHashMap<>();
        headers.put(PwmConstants.HttpHeader.Content_Type.getHttpName(), PwmConstants.ContentTypeValue.json.getHeaderValue());
        headers.put(PwmConstants.HttpHeader.Accept_Language.getHttpName(), locale.toLanguageTag());

        RemoteVerificationRequestBean remoteVerificationRequestBean = new RemoteVerificationRequestBean();
        remoteVerificationRequestBean.setResponseSessionID(this.remoteSessionID);
        remoteVerificationRequestBean.setUserInfo(PublicUserInfoBean.fromUserInfoBean(userInfoBean, pwmApplication.getConfig(), locale));
        remoteVerificationRequestBean.setUserResponses(userResponses);

        PwmHttpClientRequest pwmHttpClientRequest = new PwmHttpClientRequest(
                HttpMethod.POST,
                url,
                JsonUtil.serialize(remoteVerificationRequestBean),
                headers
        );

        try {
            final PwmHttpClientResponse response = pwmHttpClient.makeRequest(pwmHttpClientRequest);
            final String responseBodyStr = response.getBody();
            this.lastResponse = JsonUtil.deserialize(responseBodyStr, RemoteVerificationResponseBean.class);
        } catch (PwmException e) {
            LOGGER.error(sessionLabel, e.getErrorInformation());
            throw new PwmUnrecoverableException(e.getErrorInformation());
        } catch (Exception e) {
            final String errorMsg = "error reading remote responses web service response: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_SERVICE_NOT_AVAILABLE, errorMsg);
            LOGGER.error(sessionLabel, errorInformation);
            throw new PwmUnrecoverableException(errorInformation);
        }
    }
}
