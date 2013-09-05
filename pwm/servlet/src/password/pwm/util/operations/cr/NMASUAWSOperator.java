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

package password.pwm.util.operations.cr;

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.cr.*;
import com.novell.ldapchai.cr.bean.ChallengeBean;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.exception.ChaiValidationException;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.ResponseInfoBean;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.PwmLogger;
import password.pwm.ws.client.novell.pwdmgt.*;

import javax.xml.rpc.Stub;
import java.io.Serializable;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.*;

public class NMASUAWSOperator implements CrOperator {

    private static final PwmLogger LOGGER = PwmLogger.getLogger(NMASUAWSOperator.class);

    final PwmApplication pwmApplication;

    public NMASUAWSOperator(PwmApplication pwmApplication) {
        this.pwmApplication = pwmApplication;
    }

    public void close() {
    }

    @Override
    public ResponseSet readResponseSet(ChaiUser theUser, String userGUID) throws PwmUnrecoverableException {
        return readResponsesFromNovellUA(pwmApplication,theUser);
    }

    @Override
    public ResponseInfoBean readResponseInfo(ChaiUser theUser, String userGUID) throws PwmUnrecoverableException {
        final ResponseSet responseSet = readResponsesFromNovellUA(pwmApplication,theUser);
        if (responseSet == null) {
            return null;
        }

        final Map<Challenge, String> crMap = new LinkedHashMap<Challenge, String>();
        final Map<Challenge, String> helpdeskCrMap = new LinkedHashMap<Challenge, String>();
        try {
            for (final Challenge loopChallenge : responseSet.getChallengeSet().getChallenges()) {
                crMap.put(loopChallenge,"");
            }
            for (final Challenge loopChallenge : responseSet.getHelpdeskResponses().keySet()) {
                helpdeskCrMap.put(loopChallenge,"");
            }

            return new ResponseInfoBean(
                    crMap,
                    helpdeskCrMap,
                    PwmConstants.DEFAULT_LOCALE,
                    responseSet.getChallengeSet().getMinRandomRequired(),
                    responseSet.getChallengeSet().getIdentifier()
            );
        } catch (ChaiValidationException e) {
            LOGGER.error("unexpected error converting NMASUserAppWebService ResponseSet to ResponseInfoBean: " + e.getMessage());
        }

        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void clearResponses(ChaiUser theUser, String userGUID) throws PwmUnrecoverableException {
        throw new UnsupportedOperationException("NMASUserAppWebService C/R implementation does not support clearing responses");
    }

    @Override
    public void writeResponses(ChaiUser theUser, String userGuid, ResponseInfoBean responseInfoBean) throws PwmUnrecoverableException {
        throw new UnsupportedOperationException("NMASUserAppWebService C/R implementation does not support writing responses");
    }

    private static ResponseSet readResponsesFromNovellUA(
            final PwmApplication pwmApplication,
            final ChaiUser theUser
    )
            throws PwmUnrecoverableException
    {
        final String novellUserAppWebServiceURL = pwmApplication.getConfig().readSettingAsString(PwmSetting.EDIRECTORY_PWD_MGT_WEBSERVICE_URL);

        try {
            LOGGER.trace("establishing connection to web service at " + novellUserAppWebServiceURL);
            final PasswordManagementServiceLocator locater = new PasswordManagementServiceLocator();
            final PasswordManagement service = locater.getPasswordManagementPort(new URL(novellUserAppWebServiceURL));
            ((Stub) service)._setProperty(javax.xml.rpc.Stub.SESSION_MAINTAIN_PROPERTY, Boolean.TRUE);
            final ProcessUserRequest userRequest = new ProcessUserRequest(theUser.getEntryDN());
            final ForgotPasswordWSBean processUserResponse = service.processUser(userRequest);
            if (processUserResponse.isTimeout() || processUserResponse.isError()) {
                throw new Exception( "novell web service reports " + (processUserResponse.isTimeout() ? "timeout" : "error") + ": " + processUserResponse.getMessage());
            }
            if (processUserResponse.getChallengeQuestions() != null) {
                return new NovellWSResponseSet(service, processUserResponse);
            }
        } catch (Throwable e) {
            final String errorMsg = "error retrieving novell user responses from web service: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_SERVICE_UNREACHABLE, errorMsg);
            throw new PwmUnrecoverableException(errorInformation);
        }

        return null;
    }

    private static class NovellWSResponseSet implements ResponseSet, Serializable {
        private transient final PasswordManagement service;
        private final String userDN;
        private final ChallengeSet challengeSet;
        private final Locale locale;
        private final String localIdentifer;

        private static int lastLocalIdentifer;

        public NovellWSResponseSet(
                final PasswordManagement service,
                final ForgotPasswordWSBean wsBean
        )
                throws ChaiValidationException {
            this.userDN = wsBean.getUserDN();
            this.service = service;
            this.localIdentifer = "NovellWSResponseSet #" + String.valueOf(lastLocalIdentifer++);
            LOGGER.debug("initialized " + localIdentifer);

            final List<Challenge> challenges = new ArrayList<Challenge>();
            for (final String loopQuestion : wsBean.getChallengeQuestions()) {
                final Challenge loopChallenge = new ChaiChallenge(
                        true,
                        loopQuestion,
                        1,
                        255,
                        true
                );
                challenges.add(loopChallenge);
            }
            locale = PwmConstants.DEFAULT_LOCALE;
            challengeSet = new ChaiChallengeSet(challenges, 0, locale, "NovellWSResponseSet derived ChallengeSet");
        }

        public ChallengeSet getChallengeSet() {
            return challengeSet;
        }

        public ChallengeSet getPresentableChallengeSet() throws ChaiValidationException {
            return challengeSet;
        }

        public boolean meetsChallengeSetRequirements(final ChallengeSet challengeSet) {
            if (challengeSet.getRequiredChallenges().size() > this.getChallengeSet().getRequiredChallenges().size()) {
                LOGGER.debug(localIdentifer + "failed meetsChallengeSetRequirements, not enough required challenge");
                return false;
            }

            for (final Challenge loopChallenge : challengeSet.getRequiredChallenges()) {
                if (loopChallenge.isAdminDefined()) {
                    if (!this.getChallengeSet().getChallengeTexts().contains(loopChallenge.getChallengeText())) {
                        LOGGER.debug(localIdentifer + "failed meetsChallengeSetRequirements, missing required challenge text: '" + loopChallenge.getChallengeText() + "'");
                        return false;
                    }
                }
            }

            if (challengeSet.getMinRandomRequired() > 0) {
                if (this.getChallengeSet().getChallenges().size() < challengeSet.getMinRandomRequired()) {
                    LOGGER.debug(localIdentifer + "failed meetsChallengeSetRequirements, not enough questions to meet minrandom; minRandomRequired=" + challengeSet.getMinRandomRequired() + ", ChallengesInSet=" + this.getChallengeSet().getChallenges().size());
                    return false;
                }
            }

            return true;
        }

        public String stringValue() throws UnsupportedOperationException {
            return "NovellWSResponseSet derived ResponseSet";
        }

        public boolean test(final Map<Challenge, String> responseTest) throws ChaiUnavailableException {
            if (service == null) {
                LOGGER.error(localIdentifer + "beginning web service 'processChaRes' response test, however service bean is not in session memory, aborting response test...");
                return false;
            }
            LOGGER.trace(localIdentifer + "beginning web service 'processChaRes' response test ");
            final String[] responseArray = new String[challengeSet.getAdminDefinedChallenges().size()];
            {
                int i = 0;
                for (final Challenge loopChallenge : challengeSet.getAdminDefinedChallenges()) {
                    final String loopResponse = responseTest.get(loopChallenge);
                    responseArray[i] = loopResponse;
                    i++;
                }
            }
            final ProcessChaResRequest request = new ProcessChaResRequest();
            request.setChaAnswers(responseArray);
            request.setUserDN(userDN);

            try {
                final ForgotPasswordWSBean response = service.processChaRes(request);
                if (response.isTimeout()) {
                    LOGGER.error(localIdentifer + "web service reports timeout: " + response.getMessage());
                    return false;
                }
                if (response.isError()) {
                    if ("Account restrictions prevent you from logging in. See your administrator for more details.".equals(response.getMessage())) {
                        //throw PwmUnrecoverableException.createPwmException(PwmError.ERROR_INTRUDER_USER);
                    }
                    LOGGER.error(localIdentifer + "web service reports error: " + response.getMessage());
                    return false;
                }
                LOGGER.debug(localIdentifer + "web service has validated the users responses");
                return true;
            } catch (RemoteException e) {
                LOGGER.error(localIdentifer + "error processing web service response: " + e.getMessage());
            }

            return false;  //To change body of implemented methods use File | Settings | File Templates.
        }

        public Locale getLocale() throws ChaiUnavailableException, IllegalStateException, ChaiOperationException {
            return locale;
        }

        public Date getTimestamp() throws ChaiUnavailableException, IllegalStateException, ChaiOperationException {
            return new Date();
        }

        @Override
        public Map<Challenge, String> getHelpdeskResponses() {
            return Collections.emptyMap();
        }

        @Override
        public String toString() {
            return "NovellWSResponseSet holding {" + challengeSet.toString() + "}";
        }

        @Override
        public List<ChallengeBean> asChallengeBeans(boolean includeAnswers) {
            return Collections.emptyList();
        }

        @Override
        public List<ChallengeBean> asHelpdeskChallengeBeans(boolean includeAnswers) {
            return Collections.emptyList();
        }
    }

}
