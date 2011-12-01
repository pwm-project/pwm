/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2011 The PWM Project
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

package password.pwm.util.operations;

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.cr.*;
import com.novell.ldapchai.exception.*;
import com.novell.ldapchai.impl.edir.NmasCrFactory;
import com.novell.ldapchai.impl.edir.NmasResponseSet;
import com.novell.ldapchai.provider.ChaiProvider;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.PwmPasswordPolicy;
import password.pwm.PwmSession;
import password.pwm.bean.ResponseInfoBean;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.Helper;
import password.pwm.util.PwmLogger;
import password.pwm.util.TimeDuration;
import password.pwm.util.db.DatabaseAccessor;
import password.pwm.util.pwmdb.PwmDB;
import password.pwm.util.pwmdb.PwmDBException;
import password.pwm.ws.client.novell.pwdmgt.*;

import javax.xml.rpc.Stub;
import java.io.Serializable;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.*;

/**
 * @author Jason D. Rivard
 */
public abstract class CrUtility {
    private static final PwmLogger LOGGER = PwmLogger.getLogger(CrUtility.class);

    public enum STORAGE_METHOD { DB, LDAP, PWMDB }

    private CrUtility() {
    }

    public static ChallengeSet readUserChallengeSet(
            final PwmSession pwmSession,
            final Configuration config,
            final ChaiUser theUser,
            final PwmPasswordPolicy policy,
            final Locale locale
    ) throws PwmUnrecoverableException {
        final long methodStartTime = System.currentTimeMillis();

        ChallengeSet returnSet = null;

        if (config.readSettingAsBoolean(PwmSetting.EDIRECTORY_READ_CHALLENGE_SET)) {
            try {
                if (theUser.getChaiProvider().getDirectoryVendor() == ChaiProvider.DIRECTORY_VENDOR.NOVELL_EDIRECTORY) {
                    if (policy != null && policy.getChaiPasswordPolicy() != null) {
                        returnSet = NmasCrFactory.readAssignedChallengeSet(theUser.getChaiProvider(), policy.getChaiPasswordPolicy(), locale);
                    }

                    if (returnSet == null) {
                        returnSet = NmasCrFactory.readAssignedChallengeSet(theUser, locale);
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
            returnSet = config.getGlobalChallengeSet(locale);
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

    public static ResponseSet readUserResponseSet(
            final PwmSession pwmSession,
            final PwmApplication pwmApplication,
            final ChaiUser theUser
    )
            throws ChaiUnavailableException, PwmUnrecoverableException
    {
        final Configuration config = pwmApplication.getConfig();

        final String novellUserAppWebServiceURL = config.readSettingAsString(PwmSetting.EDIRECTORY_PWD_MGT_WEBSERVICE_URL);
        if (novellUserAppWebServiceURL != null && novellUserAppWebServiceURL.length() > 0) {
            final ResponseSet responseSet = ResponseReaders.readResponsesFromNovellUA(pwmSession,pwmApplication,theUser);
            if (responseSet != null) {
                LOGGER.debug(pwmSession,"returning responses read via Novell UserApp SOAP Service");
                return responseSet;
            }
        }

        final String readRawValue = config.readSettingAsString(PwmSetting.FORGOTTEN_PASSWORD_READ_PREFERENCE);
        final List<STORAGE_METHOD> readPreferences = new ArrayList<STORAGE_METHOD>();
        for (final String rawValue : readRawValue.split("-")) {
            readPreferences.add(STORAGE_METHOD.valueOf(rawValue));
        }

        final String userGUID;
        if (readPreferences.contains(STORAGE_METHOD.DB) || readPreferences.contains(STORAGE_METHOD.PWMDB)) {
            userGUID = Helper.readLdapGuidValue(theUser.getChaiProvider(), config, theUser.getEntryDN());
        } else {
            userGUID = null;
        }

        for (final STORAGE_METHOD storageMethod : readPreferences) {
            final ResponseSet readResponses;

            switch (storageMethod) {
                case DB:
                    final DatabaseAccessor databaseAccessor = pwmApplication.getDatabaseAccessor();
                    readResponses = ResponseReaders.readResponsesFromDatabase(pwmSession, databaseAccessor, theUser, userGUID);
                    break;

                case PWMDB:
                    final PwmDB pwmDB = pwmApplication.getPwmDB();
                    readResponses = ResponseReaders.readResponsesFromPwmDB(pwmSession, pwmDB, theUser, userGUID);
                    break;

                case LDAP:
                    readResponses = ResponseReaders.readResponsesFromLdap(pwmSession, theUser);
                    break;

                default:
                    readResponses = null;
            }

            if (readResponses != null) {
                LOGGER.debug(pwmSession,"returning responses read via " + storageMethod);
                return readResponses;
            }
        }

        return null;
    }

    static class ResponseReaders {

        private static ResponseSet readResponsesFromPwmDB(
                final PwmSession pwmSession,
                final PwmDB pwmDB,
                final ChaiUser theUser,
                final String userGUID
        )
                throws ChaiUnavailableException, PwmUnrecoverableException
        {
            if (userGUID == null || userGUID.length() < 1) {
                final String errorMsg = "user " + theUser.getEntryDN() + " does not have a pwmGUID, unable to search for responses in PwmDB";
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_MISSING_GUID, errorMsg);
                throw new PwmUnrecoverableException(errorInformation);
            }

            if (pwmDB == null) {
                final String errorMsg = "pwmDB is not available, unable to search for user responses";
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_PWMDB_UNAVAILABLE, errorMsg);
                throw new PwmUnrecoverableException(errorInformation);
            }

            try {
                final String responseStringBlob = pwmDB.get(PwmDB.DB.RESPONSE_STORAGE, userGUID);
                if (responseStringBlob != null && responseStringBlob.length() > 0) {
                    final ResponseSet userResponseSet = ChaiResponseSet.parseChaiResponseSetXML(responseStringBlob, theUser);
                    LOGGER.debug(pwmSession, "found user responses in pwmDB: " + userResponseSet.toString());
                    return userResponseSet;
                }
            } catch (PwmDBException e) {
                final String errorMsg = "unexpected pwmDB error reading responses from pwmDB: " + e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
                throw new PwmUnrecoverableException(errorInformation);
            } catch (ChaiValidationException e) {
                final String errorMsg = "unexpected chai error reading responses from pwmDB: " + e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
                throw new PwmUnrecoverableException(errorInformation);
            }
            return null;
        }

        private static ResponseSet readResponsesFromDatabase(
                final PwmSession pwmSession,
                final DatabaseAccessor databaseAccessor,
                final ChaiUser theUser,
                final String userGUID
        )
                throws ChaiUnavailableException, PwmUnrecoverableException
        {
            if (userGUID == null || userGUID.length() < 1) {
                final String errorMsg = "user " + theUser.getEntryDN() + " does not have a pwmGUID, unable to search for responses in Database";
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_MISSING_GUID, errorMsg);
                throw new PwmUnrecoverableException(errorInformation);
            }

            try {
                final String responseStringBlob = databaseAccessor.get(DatabaseAccessor.TABLE.PWM_RESPONSES, userGUID);
                if (responseStringBlob != null && responseStringBlob.length() > 0) {
                    final ResponseSet userResponseSet = ChaiResponseSet.parseChaiResponseSetXML(responseStringBlob, theUser);
                    LOGGER.debug(pwmSession, "found user responses in database: " + userResponseSet.toString());
                    return userResponseSet;
                }
            } catch (ChaiValidationException e) {
                final String errorMsg = "unexpected chai error reading responses from database: " + e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
                throw new PwmUnrecoverableException(errorInformation);
            } catch (PwmOperationalException e) {
                final String errorMsg = "unexpected error reading responses from database: " + e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
                throw new PwmUnrecoverableException(errorInformation);
            }
            return null;
        }

        private static ResponseSet readResponsesFromNovellUA(
                final PwmSession pwmSession,
                final PwmApplication pwmApplication,
                final ChaiUser theUser
        )
                throws PwmUnrecoverableException
        {
            final String novellUserAppWebServiceURL = pwmApplication.getConfig().readSettingAsString(PwmSetting.EDIRECTORY_PWD_MGT_WEBSERVICE_URL);

            try {
                LOGGER.trace(pwmSession, "establishing connection to web service at " + novellUserAppWebServiceURL);
                final PasswordManagementServiceLocator locater = new PasswordManagementServiceLocator();
                final PasswordManagement service = locater.getPasswordManagementPort(new URL(novellUserAppWebServiceURL));
                ((Stub) service)._setProperty(javax.xml.rpc.Stub.SESSION_MAINTAIN_PROPERTY, Boolean.TRUE);
                final ProcessUserRequest userRequest = new ProcessUserRequest(theUser.getEntryDN());
                final ForgotPasswordWSBean processUserResponse = service.processUser(userRequest);
                if (processUserResponse.isTimeout() || processUserResponse.isError()) {
                    throw new Exception( "novell web service reports " + (processUserResponse.isTimeout() ? "timeout" : "error") + ": " + processUserResponse.getMessage());
                }
                if (processUserResponse.getChallengeQuestions() != null) {
                    return new NovellWSResponseSet(service, processUserResponse, pwmSession, pwmApplication);
                }
            } catch (Throwable e) {
                final String errorMsg = "error retrieving novell user responses from web service: " + e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
                throw new PwmUnrecoverableException(errorInformation);
            }

            return null;
        }

        private static ResponseSet readResponsesFromLdap(final PwmSession pwmSession, final ChaiUser theUser)
                throws ChaiUnavailableException
        {
            try {
                return ChaiCrFactory.readChaiResponseSet(theUser);
            } catch (ChaiOperationException e) {
                LOGGER.debug(pwmSession, "ldap error reading response set: " + e.getMessage());
            } catch (ChaiValidationException e) {
                LOGGER.debug(pwmSession, "ldap error reading response set: " + e.getMessage());
            }
            return null;
        }
    }

    public static void writeResponses(
            final PwmSession pwmSession,
            final PwmApplication pwmApplication,
            final ChaiUser theUser,
            final String userGUID,
            final ResponseInfoBean responseInfoBean

    )
            throws PwmOperationalException, ChaiUnavailableException, ChaiValidationException {
        int attempts = 0, successes = 0;
        final Configuration config = pwmApplication.getConfig();

        final ChaiResponseSet chaiResponseSet = ChaiCrFactory.newChaiResponseSet(
                responseInfoBean.getCrMap(),
                responseInfoBean.getLocale(),
                responseInfoBean.getMinRandoms(),
                theUser.getChaiProvider().getChaiConfiguration(),
                responseInfoBean.getCsIdentifier()
        );



        if (config.readSettingAsBoolean(PwmSetting.RESPONSE_STORAGE_DB)) {
            attempts++;
            if (userGUID == null || userGUID.length() < 1) {
                throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_MISSING_GUID, "cannot save responses to remote database, user does not have a pwmGUID"));
            }

            try {
                final DatabaseAccessor databaseAccessor = pwmApplication.getDatabaseAccessor();
                databaseAccessor.put(DatabaseAccessor.TABLE.PWM_RESPONSES, userGUID, chaiResponseSet.stringValue());
                LOGGER.info(pwmSession, "saved responses for user in remote database");
                successes++;
            } catch (PwmUnrecoverableException e) {
                final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_WRITING_RESPONSES, "unexpected error saving responses to remote database: " + e.getMessage());
                final PwmOperationalException pwmOE = new PwmOperationalException(errorInfo);
                pwmOE.initCause(e);
                throw pwmOE;
            }
        }

        if (config.readSettingAsBoolean(PwmSetting.RESPONSE_STORAGE_PWMDB)) {
            attempts++;
            if (userGUID == null || userGUID.length() < 1) {
                throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_MISSING_GUID, "cannot save responses to pwmDB, user does not have a pwmGUID"));
            }

            if (pwmApplication.getPwmDB() == null) {
                final String errorMsg = "pwmDB is not available, unable to write user responses";
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_PWMDB_UNAVAILABLE, errorMsg);
                throw new PwmOperationalException(errorInformation);
            }

            try {
                pwmApplication.getPwmDB().put(PwmDB.DB.RESPONSE_STORAGE, userGUID, chaiResponseSet.stringValue());
                LOGGER.info(pwmSession, "saved responses for user in local pwmDB");
                successes++;
            } catch (PwmDBException e) {
                final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_WRITING_RESPONSES, "unexpected pwmDB error saving responses to pwmDB: " + e.getMessage());
                final PwmOperationalException pwmOE = new PwmOperationalException(errorInfo);
                pwmOE.initCause(e);
                throw pwmOE;
            }
        }

        final String ldapStorageAttribute = config.readSettingAsString(PwmSetting.CHALLENGE_USER_ATTRIBUTE);
        if (ldapStorageAttribute != null && ldapStorageAttribute.length() > 0) {
            try {
                attempts++;
                ChaiCrFactory.writeChaiResponseSet(chaiResponseSet, theUser);
                LOGGER.info(pwmSession, "saved responses for user to chai-ldap format");
                successes++;
            } catch (ChaiOperationException e) {
                final String errorMsg;
                if (e.getErrorCode() == ChaiError.NO_ACCESS) {
                    errorMsg = "permission error writing user responses to ldap attribute '" + ldapStorageAttribute + "', user does not appear to have correct permissions to save responses: " + e.getMessage();
                } else {
                    errorMsg = "error writing user responses to ldap attribute '" + ldapStorageAttribute + "': " + e.getMessage();
                }
                final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_WRITING_RESPONSES, errorMsg);
                final PwmOperationalException pwmOE = new PwmOperationalException(errorInfo);
                pwmOE.initCause(e);
                throw pwmOE;
            }
        }

        if (config.readSettingAsBoolean(PwmSetting.EDIRECTORY_STORE_NMAS_RESPONSES)) {
            try {
                if (theUser.getChaiProvider().getDirectoryVendor() == ChaiProvider.DIRECTORY_VENDOR.NOVELL_EDIRECTORY) {
                    attempts++;
                    final NmasResponseSet nmasResponseSet = NmasCrFactory.newNmasResponseSet(
                            responseInfoBean.getCrMap(),
                            responseInfoBean.getLocale(),
                            responseInfoBean.getMinRandoms(),
                            theUser,
                            responseInfoBean.getCsIdentifier()
                    );
                    NmasCrFactory.writeResponseSet(nmasResponseSet);
                    LOGGER.info(pwmSession, "saved responses for user using NMAS method ");
                    successes++;
                }
            } catch (ChaiOperationException e) {
                final String errorMsg = "error writing responses to nmas: " + e.getMessage();
                final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_WRITING_RESPONSES, errorMsg);
                final PwmOperationalException pwmOE = new PwmOperationalException(errorInfo);
                pwmOE.initCause(e);
                throw pwmOE;
            }
        }

        if (attempts == 0) {
            final String errorMsg = "no response save methods are available or configured";
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_WRITING_RESPONSES, errorMsg);
            throw new PwmOperationalException(errorInfo);
        }

        if (attempts != successes) { // should be impossible to get here, but just in case.
            final String errorMsg = "response storage only partially successful; attempts=" + attempts + ", successes=" + successes;
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_WRITING_RESPONSES, errorMsg);
            throw new PwmOperationalException(errorInfo);
        }
    }

    public static class NovellWSResponseSet implements ResponseSet, Serializable {
        private transient final PasswordManagement service;
        private final String userDN;
        private final ChallengeSet challengeSet;
        private final PwmSession pwmSession;
        private final Locale locale;
        private final PwmApplication pwmApplication;

        public NovellWSResponseSet(
                final PasswordManagement service,
                final ForgotPasswordWSBean wsBean,
                final PwmSession pwmSession,
                final PwmApplication pwmApplication
        )
                throws ChaiValidationException {
            this.userDN = wsBean.getUserDN();
            this.service = service;
            this.pwmSession = pwmSession;
            this.pwmApplication = pwmApplication;

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
                LOGGER.debug("not enough required challenge");
                return false;
            }

            for (final Challenge loopChallenge : challengeSet.getRequiredChallenges()) {
                if (loopChallenge.isAdminDefined()) {
                    if (!this.getChallengeSet().getChallengeTexts().contains(loopChallenge.getChallengeText())) {
                        LOGGER.debug("missing required challenge text: '" + loopChallenge.getChallengeText() + "'");
                        return false;
                    }
                }
            }

            if (challengeSet.getMinRandomRequired() > 0) {
                if (this.getChallengeSet().getRandomChallenges().size() < challengeSet.getMinRandomRequired()) {
                    LOGGER.debug("not enough random questions");
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
                        //throw PwmUnrecoverableException.createPwmException(PwmError.ERROR_INTRUDER_USER);
                    }
                    LOGGER.error(pwmSession, "NovellWSResponseSet: web service reports error: " + response.getMessage());
                    return false;
                }
                LOGGER.debug(pwmSession, "NovellWSResponseSet: web service has validated the users responses");
                return true;
            } catch (RemoteException e) {
                LOGGER.error("NovellWSResponseSet: error processing web service response: " + e.getMessage());
            }

            try {
                pwmApplication.getIntruderManager().addBadAddressAttempt(pwmSession);
            } catch (PwmUnrecoverableException e) {
                // nothing to be done
            }
            return false;  //To change body of implemented methods use File | Settings | File Templates.
        }

        public Locale getLocale() throws ChaiUnavailableException, IllegalStateException, ChaiOperationException {
            return locale;
        }

        public Date getTimestamp() throws ChaiUnavailableException, IllegalStateException, ChaiOperationException {
            return new Date();
        }
    }

    public static boolean checkIfResponseConfigNeeded(
            final PwmSession pwmSession,
            final PwmApplication pwmApplication,
            final ChaiUser theUser,
            final ChallengeSet challengeSet)
            throws ChaiUnavailableException, PwmUnrecoverableException
    {
        LOGGER.trace(pwmSession, "beginning check to determine if responses need to be configured for user");

        final String userDN = theUser.getEntryDN();

        final ChaiProvider provider = pwmApplication.getProxyChaiProvider();
        final Configuration config = pwmApplication.getConfig();

        if (!Helper.testUserMatchQueryString(provider, userDN, config.readSettingAsString(PwmSetting.QUERY_MATCH_CHECK_RESPONSES))) {
            LOGGER.debug(pwmSession, "checkIfResponseConfigNeeded: " + userDN + " is not eligible for checkIfResponseConfigNeeded due to query match");
            return false;
        }

        // check to be sure there are actually challenges in the challenge set
        if (challengeSet == null || challengeSet.getChallenges().isEmpty()) {
            LOGGER.debug(pwmSession, "checkIfResponseConfigNeeded: no challenge sets configured for user " + userDN);
            return false;
        }

        // read the user's response
        final ResponseSet usersResponses = readUserResponseSet(pwmSession, pwmApplication, theUser);

        try {
            // check if responses exist
            if (usersResponses == null) {
                throw new Exception("no responses configured");
            }

            // check if responses meet the challenge set policy for the user
            usersResponses.meetsChallengeSetRequirements(challengeSet);

            LOGGER.debug(pwmSession, "checkIfResponseConfigNeeded: " + userDN + " has good responses");
            return false;
        } catch (Exception e) {
            LOGGER.debug(pwmSession, "checkIfResponseConfigNeeded: " + userDN + " does not have good responses: " + e.getMessage());
            return true;
        }
    }

}
