/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2010 The PWM Project
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

package password.pwm;

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.cr.*;
import com.novell.ldapchai.exception.*;
import com.novell.ldapchai.provider.ChaiProvider;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.Message;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.util.PwmLogger;
import password.pwm.util.TimeDuration;
import password.pwm.util.db.PwmDB;
import password.pwm.util.db.PwmDBException;
import password.pwm.util.stats.Statistic;
import password.pwm.ws.client.novell.pwdmgt.*;

import javax.xml.rpc.Stub;
import java.io.Serializable;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.*;

/**
 * @author Jason D. Rivard
 */
public class CrUtility {
    private static final PwmLogger LOGGER = PwmLogger.getLogger(CrUtility.class);

    private CrUtility() {
    }

    public static ChallengeSet readUserChallengeSet(
            final PwmSession pwmSession,
            final ChaiUser theUser,
            final PwmPasswordPolicy policy,
            final Locale locale
    ) {
        final long methodStartTime = System.currentTimeMillis();

        ChallengeSet returnSet = null;

        if (pwmSession.getConfig().readSettingAsBoolean(PwmSetting.EDIRECTORY_READ_CHALLENGE_SET)) {
            try {
                if (pwmSession.getContextManager().getProxyChaiProvider().getDirectoryVendor() == ChaiProvider.DIRECTORY_VENDOR.NOVELL_EDIRECTORY) {
                    if (policy != null && policy.getChaiPasswordPolicy() != null) {
                        returnSet = CrFactory.readAssignedChallengeSet(theUser.getChaiProvider(), policy.getChaiPasswordPolicy(), locale);
                    }

                    if (returnSet == null) {
                        returnSet = CrFactory.readAssignedChallengeSet(theUser, locale);
                    }

                    if (returnSet == null) {
                        LOGGER.debug(pwmSession, "no nmas c/r policy found for user " + theUser.getEntryDN());
                    } else {
                        LOGGER.debug(pwmSession, "using nmas c/r policy for user " + theUser.getEntryDN() + ": " + returnSet.toString());
                    }
                }
            } catch (ChaiException e) {
                LOGGER.error(pwmSession, "error reading nmas c/r policy for user " + theUser.getEntryDN() + ": " + e.getMessage());
            }
        }

        // use PWM policies if PWM is configured and either its all that is configured OR the NMAS policy read was not successfull
        if (returnSet == null) {
            returnSet = pwmSession.getContextManager().getConfig().getGlobalChallengeSet(pwmSession.getSessionStateBean().getLocale());
            if (returnSet != null) {
                LOGGER.debug(pwmSession, "using pwm c/r policy for user " + theUser.getEntryDN() + ": " + returnSet.toString());
            }
        }

        if (returnSet == null) {
            LOGGER.warn(pwmSession, "no available c/r policy for user" + theUser.getEntryDN() + ": ");
        }

        LOGGER.trace(pwmSession, "readUserChallengeSet completed in " + TimeDuration.fromCurrent(methodStartTime).asCompactString());

        return returnSet;
    }

    public static ResponseSet readUserResponseSet(final PwmSession pwmSession, final ChaiUser theUser)
            throws ChaiUnavailableException, PwmException {
        if (pwmSession.getConfig().readSettingAsBoolean(PwmSetting.RESPONSE_STORAGE_PWMDB)) {
            final String GUIDattr = pwmSession.getConfig().readSettingAsString(PwmSetting.LDAP_GUID_ATTRIBUTE);
            String userGUID = null;
            try {
                userGUID = theUser.readStringAttribute(GUIDattr);
            } catch (ChaiOperationException e) {
                LOGGER.error(pwmSession, " error reading pwmGUID attribute " + GUIDattr + " on user " + theUser.getEntryDN() + ": " + e.getMessage());
            }

            if (userGUID == null || userGUID.length() < 1) {
                LOGGER.error(pwmSession, "user " + theUser.getEntryDN() + " does not have a pwmGUID, skipping search for responses in pwmDB");
            } else {
                final PwmDB pwmDB = pwmSession.getContextManager().getPwmDB();
                try {
                    final String responseStringBlob = pwmDB.get(PwmDB.DB.RESPONSE_STORAGE, userGUID);
                    if (responseStringBlob != null && responseStringBlob.length() > 0) {
                        final ResponseSet userResponseSet = ChaiResponseSet.parseChaiResponseSetXML(responseStringBlob, theUser);
                        LOGGER.debug(pwmSession, "found user responses in pwmDB: " + userResponseSet.toString());
                        return userResponseSet;
                    }
                } catch (PwmDBException e) {
                    LOGGER.error(pwmSession, "unexpected pwmDB error reading responses from pwmDB: " + e.getMessage());
                } catch (ChaiValidationException e) {
                    LOGGER.error(pwmSession, "unexpected chai error reading responses from pwmDB: " + e.getMessage());
                }
            }
        }

        final String novellUserAppWebServiceURL = pwmSession.getConfig().readSettingAsString(PwmSetting.EDIRECTORY_PWD_MGT_WEBSERVICE_URL);
        if (novellUserAppWebServiceURL != null && novellUserAppWebServiceURL.length() > 0) {
            try {
                LOGGER.trace(pwmSession, "establishing connection to web service at " + novellUserAppWebServiceURL);
                final PasswordManagementServiceLocator locater = new PasswordManagementServiceLocator();
                final PasswordManagement service = locater.getPasswordManagementPort(new URL(novellUserAppWebServiceURL));
                ((Stub) service)._setProperty(javax.xml.rpc.Stub.SESSION_MAINTAIN_PROPERTY, Boolean.TRUE);
                final ProcessUserRequest userRequest = new ProcessUserRequest(theUser.getEntryDN());
                final ForgotPasswordWSBean processUserResponse = service.processUser(userRequest);
                if (processUserResponse.isTimeout()) {
                    LOGGER.error(pwmSession, "novell web service reports timeout: " + processUserResponse.getMessage());
                    return null;
                }
                if (processUserResponse.isError()) {
                    LOGGER.error(pwmSession, "novell web service reports error: " + processUserResponse.getMessage());
                    return null;
                }
                return new NovellWSResponseSet(service, processUserResponse, pwmSession);
            } catch (Throwable e) {
                LOGGER.error(pwmSession, "error retrieving novell user responses from web service: " + e.getMessage());
                return null;
            }
        }

        try {
            return theUser.readResponseSet();
        } catch (ChaiOperationException e) {
            LOGGER.debug(pwmSession, "ldap error reading response set: " + e.getMessage());
        }

        return null;
    }

    public static boolean saveResponses(final PwmSession pwmSession, final ResponseSet responses)
            throws PwmException, ChaiUnavailableException {
        int attempts = 0, successes = 0;

        if (pwmSession.getConfig().readSettingAsBoolean(PwmSetting.RESPONSE_STORAGE_PWMDB)) {
            attempts++;
            final String userGUID = pwmSession.getUserInfoBean().getUserGuid();
            if (userGUID == null || userGUID.length() < 1) {
                throw new PwmException(PwmError.ERROR_MISSING_GUID.toInfo(), "cannot save responses to pwmDB without pwmGUID");
            }

            final PwmDB pwmDB = pwmSession.getContextManager().getPwmDB();

            try {
                pwmDB.put(PwmDB.DB.RESPONSE_STORAGE, userGUID, responses.stringValue());
                LOGGER.info(pwmSession, "saved responses for user in local pwmDB");
                successes++;
            } catch (PwmDBException e) {
                LOGGER.error(pwmSession, "unexpected pwmDB error saving responses to pwmDB: " + e.getMessage());
            }
        }


        final String ldapStorageAttribute = pwmSession.getConfig().readSettingAsString(PwmSetting.CHALLENGE_USER_ATTRIBUTE);
        if (ldapStorageAttribute != null && ldapStorageAttribute.length() > 0) {
            try {
                attempts++;
                final boolean storeUsingHash = pwmSession.getConfig().readSettingAsBoolean(PwmSetting.CHALLENGE_STORAGE_HASHED);
                final CrMode writeMode = storeUsingHash ? CrMode.CHAI_SHA1_SALT : CrMode.CHAI_TEXT;
                responses.write(writeMode);
                LOGGER.info(pwmSession, "saved responses for user using method " + writeMode);
                successes++;
            } catch (ChaiOperationException e) {
                if (e.getErrorCode() == ChaiError.NO_ACCESS) {
                    LOGGER.warn(pwmSession, "error writing user's supplied new responses to ldap: " + e.getMessage());
                    LOGGER.warn(pwmSession, "user '" + pwmSession.getUserInfoBean().getUserDN() + "' does not appear to have enough rights to save responses");
                } else {
                    LOGGER.debug(pwmSession, "error writing user's supplied new responses to ldap: " + e.getMessage());
                }
                pwmSession.getSessionStateBean().setSessionError(new ErrorInformation(PwmError.ERROR_UNKNOWN, e.getMessage()));
            }
        }

        if (pwmSession.getConfig().readSettingAsBoolean(PwmSetting.EDIRECTORY_STORE_NMAS_RESPONSES)) {
            try {
                if (pwmSession.getContextManager().getProxyChaiProvider().getDirectoryVendor() == ChaiProvider.DIRECTORY_VENDOR.NOVELL_EDIRECTORY) {
                    attempts++;
                    responses.write(CrMode.NMAS);
                    LOGGER.info(pwmSession, "saved responses for user using method " + CrMode.NMAS);
                    successes++;
                }
            } catch (ChaiOperationException e) {
                LOGGER.debug(pwmSession, "error writing user's supplied new responses to nmas: " + e.getMessage());
                pwmSession.getSessionStateBean().setSessionError(new ErrorInformation(PwmError.ERROR_UNKNOWN, e.getMessage()));
            }
        }

        pwmSession.getContextManager().getStatisticsManager().incrementValue(Statistic.SETUP_RESPONSES);
        pwmSession.getUserInfoBean().setRequiresResponseConfig(false);
        pwmSession.getSessionStateBean().setSessionSuccess(Message.SUCCESS_SETUP_RESPONSES, null);
        UserHistory.updateUserHistory(pwmSession, UserHistory.Record.Event.SET_RESPONSES, null);

        if (attempts == successes) {
            if (attempts == 0) {
                LOGGER.warn(pwmSession, "no response saving methods available or configured");
                return false;
            }
            final UserInfoBean uiBean = pwmSession.getUserInfoBean();
            UserStatusHelper.populateActorUserInfoBean(pwmSession, uiBean.getUserDN(), uiBean.getUserCurrentPassword());
            //pwmSession.getSetupResponseBean().clear();
            return true;
        }

        LOGGER.warn(pwmSession, "response storage only partially successful; attempts=" + attempts + ", successes=" + successes);
        return false;
    }

    public static class NovellWSResponseSet implements ResponseSet, Serializable {
        private transient final PasswordManagement service;
        private final String userDN;
        private final ChallengeSet challengeSet;
        private final PwmSession pwmSession;

        public NovellWSResponseSet(
                final PasswordManagement service,
                final ForgotPasswordWSBean wsBean,
                final PwmSession pwmSession
        )
                throws ChaiValidationException {
            this.userDN = wsBean.getUserDN();
            this.service = service;
            this.pwmSession = pwmSession;
            final List<Challenge> challenges = new ArrayList<Challenge>();
            for (final String loopQuestion : wsBean.getChallengeQuestions()) {
                final Challenge loopChallenge = CrFactory.newChallenge(
                        true,
                        loopQuestion,
                        1,
                        255,
                        true
                );
                challenges.add(loopChallenge);
            }
            challengeSet = CrFactory.newChallengeSet(challenges, Locale.getDefault(), 0, "NovellWSResponseSet derived ChallengeSet");
        }

        public ChallengeSet getChallengeSet() {
            return challengeSet;
        }

        public boolean meetsChallengeSetRequirements(final ChallengeSet challengeSet) {
            return challengeSet.getAdminDefinedChallenges().size() > 0 || challengeSet.getMinRandomRequired() > 0;
        }

        public String stringValue() throws UnsupportedOperationException {
            return "NovellWSResponseSet derived ResponseSet";
        }

        public boolean test(final Map<Challenge, String> responseTest) throws ChaiUnavailableException {
            if (service == null) {
                LOGGER.error(pwmSession, "beginning web service 'processChaRes' response test, however service bean is not in session memory, aborting response test...");
                return false;
            }
            LOGGER.trace(pwmSession, "beginning web service 'processChaRes' response test ");
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
                    LOGGER.error(pwmSession, "NovellWSResponseSet: web service reports timeout: " + response.getMessage());
                    return false;
                }
                if (response.isError()) {
                    if ("Account restrictions prevent you from logging in. See your administrator for more details.".equals(response.getMessage())) {
                        //throw PwmException.createPwmException(PwmError.ERROR_INTRUDER_USER);
                    }
                    LOGGER.error(pwmSession, "NovellWSResponseSet: web service reports error: " + response.getMessage());
                    return false;
                }
                LOGGER.debug(pwmSession, "NovellWSResponseSet: web service has validated the users responses");
                return true;
            } catch (RemoteException e) {
                LOGGER.error("NovellWSResponseSet: error processing web service response: " + e.getMessage());
            }

            pwmSession.getContextManager().getIntruderManager().addBadAddressAttempt(pwmSession);
            return false;  //To change body of implemented methods use File | Settings | File Templates.
        }

        public boolean write() throws ChaiUnavailableException, IllegalStateException, ChaiOperationException {
            throw new IllegalStateException("unsupported");
        }

        public boolean write(final CrMode writeMode) throws ChaiUnavailableException, IllegalStateException, ChaiOperationException {
            throw new IllegalStateException("unsupported");
        }

        public Locale getLocale() throws ChaiUnavailableException, IllegalStateException, ChaiOperationException {
            return Locale.getDefault();
        }

        public Date getTimestamp() throws ChaiUnavailableException, IllegalStateException, ChaiOperationException {
            return new Date();
        }
    }
}
