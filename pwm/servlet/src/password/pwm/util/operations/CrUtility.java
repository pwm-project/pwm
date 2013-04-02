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

package password.pwm.util.operations;

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.cr.*;
import com.novell.ldapchai.cr.bean.ChallengeBean;
import com.novell.ldapchai.exception.*;
import com.novell.ldapchai.impl.edir.NmasCrFactory;
import com.novell.ldapchai.impl.edir.NmasResponseSet;
import com.novell.ldapchai.provider.ChaiProvider;
import password.pwm.*;
import password.pwm.bean.ResponseInfoBean;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.error.*;
import password.pwm.util.Helper;
import password.pwm.util.PwmLogger;
import password.pwm.util.TimeDuration;
import password.pwm.util.db.DatabaseAccessor;
import password.pwm.util.pwmdb.PwmDB;
import password.pwm.util.pwmdb.PwmDBException;
import password.pwm.wordlist.WordlistManager;
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

    public static void validateResponses(
            final PwmApplication pwmApplication,
            final ChallengeSet challengeSet,
            final Map<Challenge, String> responseMap,
            final int minRandomRequiredSetup

    )
            throws PwmDataValidationException, PwmUnrecoverableException
    {
        //strip null keys from responseMap;
        for (final Iterator<Challenge> iter = responseMap.keySet().iterator(); iter.hasNext();) {
            final Challenge loopChallenge = iter.next();
            if (loopChallenge == null) {
                iter.remove();
            }
        }

        { // check for missing question texts
            for (final Challenge challenge : responseMap.keySet()) {
                if (!challenge.isAdminDefined()) {
                    final String text = challenge.getChallengeText();
                    if (text == null || text.length() < 1) {
                        final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_MISSING_CHALLENGE_TEXT);
                        throw new PwmDataValidationException(errorInformation);
                    }
                }
            }
        }

        final Configuration config = pwmApplication.getConfig();

        { // check that responses are not using the challenge text word.
            final int maxChallengeLengthInResponse = (int)config.readSettingAsLong(PwmSetting.CHALLENGE_MAX_LENGTH_CHALLENGE_IN_RESPONSE);
            if (maxChallengeLengthInResponse > 0) {
                for (final Challenge loopChallenge : responseMap.keySet()) {
                    final String challengeText = loopChallenge.getChallengeText();
                    if (challengeText != null && responseMap.containsKey(loopChallenge)) {
                        final String[] challengeWords = challengeText.split("\\s");
                        for (final String challengeWord :challengeWords) {
                            if (challengeWord.length() > maxChallengeLengthInResponse) {
                                final String responseTextLower = responseMap.get(loopChallenge).toLowerCase();
                                for (int i = 0; i <= challengeWord.length() - (maxChallengeLengthInResponse + 1); i++ ) {
                                    final String wordPart = challengeWord.substring(i, i + (maxChallengeLengthInResponse + 1));
                                    if (responseTextLower.contains(wordPart)) {
                                        final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_CHALLENGE_IN_RESPONSE,"word '" + challengeWord + "' is in response",new String[]{challengeText});
                                        throw new PwmDataValidationException(errorInformation);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        { // check responses against wordlist
            final boolean applyWordlist = pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.CHALLENGE_APPLY_WORDLIST);
            final WordlistManager wordlistManager = pwmApplication.getWordlistManager();
            if (applyWordlist && wordlistManager.status() == PwmService.STATUS.OPEN) {
                for (final Challenge loopChallenge : responseMap.keySet()) {
                    final String answer = responseMap.get(loopChallenge);
                    if (wordlistManager.containsWord(answer)) {
                        final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_RESPONSE_WORDLIST, null, new String[]{loopChallenge.getChallengeText()});
                        throw new PwmDataValidationException(errorInfo);
                    }
                }
            }
        }

        { // check for duplicate questions.  need to check the actual req params because the following dupes wont populate duplicates
            final Set<String> userQuestionTexts = new HashSet<String>();
            for (final Challenge challenge : responseMap.keySet()) {
                final String text = challenge.getChallengeText();
                if (text != null) {
                    if (userQuestionTexts.contains(text.toLowerCase())) {
                        final String errorMsg = "duplicate challenge text: " + text;
                        final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_CHALLENGE_DUPLICATE, errorMsg, new String[]{text});
                        throw new PwmDataValidationException(errorInformation);
                    } else {
                        userQuestionTexts.add(text.toLowerCase());
                    }
                }
            }
        }

        int randomCount = 0;
        for (final Challenge loopChallenge : responseMap.keySet()) {
            if (!loopChallenge.isRequired()) {
                randomCount++;
            }
        }

        if (minRandomRequiredSetup == 0) { // if using recover style, then all readResponses must be supplied at this point.
            if (randomCount < challengeSet.getRandomChallenges().size()) {
                final String errorMsg = "all randoms required, but not all randoms are completed";
                final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_MISSING_RANDOM_RESPONSE, errorMsg);
                throw new PwmDataValidationException(errorInfo);
            }
        }

        if (randomCount < minRandomRequiredSetup) {
            final String errorMsg = minRandomRequiredSetup + " randoms required, but not only " + randomCount + " randoms are completed";
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_MISSING_RANDOM_RESPONSE, errorMsg);
            throw new PwmDataValidationException(errorInfo);
        }

        if (responseMap == null || responseMap.isEmpty()) {
            final String errorMsg = "empty response set";
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_MISSING_PARAMETER, errorMsg);
            throw new PwmDataValidationException(errorInfo);
        }
    }

    public static ResponseSet readUserResponseSet(
            final PwmSession pwmSession,
            final PwmApplication pwmApplication,
            final ChaiUser theUser
    )
            throws ChaiUnavailableException, PwmUnrecoverableException
    {
        final Configuration config = pwmApplication.getConfig();

        LOGGER.trace(pwmSession, "beginning read of user response sequence");

        final String novellUserAppWebServiceURL = config.readSettingAsString(PwmSetting.EDIRECTORY_PWD_MGT_WEBSERVICE_URL);
        if (novellUserAppWebServiceURL != null && novellUserAppWebServiceURL.length() > 0) {
            LOGGER.trace(pwmSession, "attempting read of responses via Novell UserApp SOAP service url: " + novellUserAppWebServiceURL);
            final ResponseSet responseSet = ResponseReaders.readResponsesFromNovellUA(pwmSession,pwmApplication,theUser);
            if (responseSet != null) {
                LOGGER.debug(pwmSession,"returning responses read via Novell UserApp SOAP Service");
                return responseSet;
            } else {
                LOGGER.trace("no responses returned from Novell UserApp SOAP service");
            }
        }

        final List<Configuration.STORAGE_METHOD> readPreferences = config.getResponseStorageLocations(PwmSetting.FORGOTTEN_PASSWORD_READ_PREFERENCE);
        {
            final StringBuilder debugMsg = new StringBuilder("will attempt to read the following storage methods: ");
            for (Iterator<Configuration.STORAGE_METHOD> iterator = readPreferences.iterator(); iterator.hasNext(); ) {
                final Configuration.STORAGE_METHOD loopMethod = iterator.next();
                debugMsg.append(loopMethod);
                if (iterator.hasNext()) {
                    debugMsg.append(", ");
                }
            }
            LOGGER.debug(pwmSession, debugMsg);
        }
        final String userGUID;
        if (readPreferences.contains(Configuration.STORAGE_METHOD.DB) || readPreferences.contains(Configuration.STORAGE_METHOD.PWMDB)) {
            userGUID = Helper.readLdapGuidValue(pwmApplication, theUser.getEntryDN());
        } else {
            userGUID = null;
        }

        for (final Configuration.STORAGE_METHOD storageMethod : readPreferences) {
            final ResponseSet readResponses;

            LOGGER.trace(pwmSession, "attempting read of responses via storage method: " + storageMethod);
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
                LOGGER.debug(pwmSession,"returning responses read via method " + storageMethod);
                return readResponses;
            } else {
                LOGGER.trace(pwmSession, "no responses read using method " + storageMethod);
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
            } catch (ChaiException e) {
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
                } else {
                    LOGGER.trace(pwmSession, "user guid not found in database");
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
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_SERVICE_UNREACHABLE, errorMsg);
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
            throws PwmOperationalException, ChaiUnavailableException, ChaiValidationException
    {
        final ChaiResponseSet chaiResponseSet = ChaiCrFactory.newChaiResponseSet(
                responseInfoBean.getCrMap(),
                responseInfoBean.getHelpdeskCrMap(),
                responseInfoBean.getLocale(),
                responseInfoBean.getMinRandoms(),
                theUser.getChaiProvider().getChaiConfiguration(),
                responseInfoBean.getCsIdentifier()
        );
        writeResponses(pwmSession,pwmApplication,theUser,userGUID,chaiResponseSet);
    }

    public static void writeResponses(
            final PwmSession pwmSession,
            final PwmApplication pwmApplication,
            final ChaiUser theUser,
            final String userGUID,
            final ChaiResponseSet responseSet
    )
            throws PwmOperationalException, ChaiUnavailableException, ChaiValidationException
    {
        int attempts = 0, successes = 0;
        final Configuration config = pwmApplication.getConfig();


        final List<Configuration.STORAGE_METHOD> writeMethods = config.getResponseStorageLocations(PwmSetting.FORGOTTEN_PASSWORD_WRITE_PREFERENCE);
        for (final Configuration.STORAGE_METHOD loopWriteMethod : writeMethods) {
            switch (loopWriteMethod) {
                case LDAP: {
                    final String ldapStorageAttribute = config.readSettingAsString(PwmSetting.CHALLENGE_USER_ATTRIBUTE);
                    if (ldapStorageAttribute == null || ldapStorageAttribute.length() < 1) {
                        final String errorMsg = "ldap storage attribute is not configured, unable to write user responses";
                        final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_INVALID_CONFIG, errorMsg);
                        throw new PwmOperationalException(errorInformation);
                    }
                    try {
                        attempts++;
                        ChaiCrFactory.writeChaiResponseSet(responseSet, theUser);
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
                break;

                case PWMDB: {
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
                        pwmApplication.getPwmDB().put(PwmDB.DB.RESPONSE_STORAGE, userGUID, responseSet.stringValue());
                        LOGGER.info(pwmSession, "saved responses for user in local pwmDB");
                        successes++;
                    } catch (PwmDBException e) {
                        final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_WRITING_RESPONSES, "unexpected pwmDB error saving responses to pwmDB: " + e.getMessage());
                        final PwmOperationalException pwmOE = new PwmOperationalException(errorInfo);
                        pwmOE.initCause(e);
                        throw pwmOE;
                    } catch (ChaiOperationException e) {
                        final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_WRITING_RESPONSES, "unexpected pwmDB error saving responses to pwmDB: " + e.getMessage());
                        final PwmOperationalException pwmOE = new PwmOperationalException(errorInfo);
                        pwmOE.initCause(e);
                        throw pwmOE;
                    }
                }
                break;

                case DB: {
                    attempts++;
                    if (userGUID == null || userGUID.length() < 1) {
                        throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_MISSING_GUID, "cannot save responses to remote database, user does not have a pwmGUID"));
                    }

                    try {
                        final DatabaseAccessor databaseAccessor = pwmApplication.getDatabaseAccessor();
                        databaseAccessor.put(DatabaseAccessor.TABLE.PWM_RESPONSES, userGUID, responseSet.stringValue());
                        LOGGER.info(pwmSession, "saved responses for user in remote database");
                        successes++;
                    } catch (PwmUnrecoverableException e) {
                        final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_WRITING_RESPONSES, "unexpected error saving responses to remote database: " + e.getMessage());
                        final PwmOperationalException pwmOE = new PwmOperationalException(errorInfo);
                        pwmOE.initCause(e);
                        throw pwmOE;
                    } catch (ChaiOperationException e) {
                        final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_WRITING_RESPONSES, "unexpected error saving responses to remote database: " + e.getMessage());
                        final PwmOperationalException pwmOE = new PwmOperationalException(errorInfo);
                        pwmOE.initCause(e);
                        throw pwmOE;
                    }
                }
                break;
            }
        }

        if (config.readSettingAsBoolean(PwmSetting.EDIRECTORY_STORE_NMAS_RESPONSES)) {
            try {
                if (theUser.getChaiProvider().getDirectoryVendor() == ChaiProvider.DIRECTORY_VENDOR.NOVELL_EDIRECTORY) {
                    attempts++;
                    final Map<Challenge,String> crMap = new LinkedHashMap<Challenge,String>();
                    for (final Challenge challenge : responseSet.getChallengeAnswers().keySet()) {
                        String answerString = null;
                        {
                            final Answer answer = responseSet.getChallengeAnswers().get(challenge);
                            answerString = answer.asAnswerBean().getAnswerText();
                        }
                        if (answerString == null) {
                            throw new IllegalArgumentException("cannot save response for '" + challenge.getChallengeText() + "' to NMAS, cleartext answer is not available");
                        }
                        crMap.put(challenge,answerString);
                    }

                    final NmasResponseSet nmasResponseSet = NmasCrFactory.newNmasResponseSet(
                            crMap,
                            responseSet.getLocale(),
                            responseSet.getChallengeSet().getMinRandomRequired(),
                            theUser,
                            responseSet.getChallengeSet().getIdentifier()
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

    public static void clearResponses(
            final PwmSession pwmSession,
            final PwmApplication pwmApplication,
            final ChaiUser theUser,
            final String userGUID

    )
            throws PwmOperationalException, ChaiUnavailableException
    {
        final Configuration config = pwmApplication.getConfig();
        int attempts = 0, successes = 0;

        LOGGER.trace(pwmSession, "beginning clear response operation for user " + theUser.getEntryDN() + " guid=" + userGUID);

        final List<Configuration.STORAGE_METHOD> writeMethods = config.getResponseStorageLocations(PwmSetting.FORGOTTEN_PASSWORD_WRITE_PREFERENCE);
        for (final Configuration.STORAGE_METHOD loopWriteMethod : writeMethods) {
            switch (loopWriteMethod) {
                case LDAP: {
                    final String ldapStorageAttribute = config.readSettingAsString(PwmSetting.CHALLENGE_USER_ATTRIBUTE);
                    if (ldapStorageAttribute == null || ldapStorageAttribute.length() < 1) {
                        final String errorMsg = "ldap storage attribute is not configured, unable to clear user responses";
                        final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_INVALID_CONFIG, errorMsg);
                        throw new PwmOperationalException(errorInformation);

                    }
                    try {
                        attempts++;
                        final String currentValue = theUser.readStringAttribute(ldapStorageAttribute);
                        if (currentValue != null && currentValue.length() > 0) {
                            theUser.deleteAttribute(ldapStorageAttribute, null);
                        }
                        LOGGER.info(pwmSession, "cleared responses for user to chai-ldap format");
                        successes++;
                    } catch (ChaiOperationException e) {
                        final String errorMsg;
                        if (e.getErrorCode() == ChaiError.NO_ACCESS) {
                            errorMsg = "permission error clearing responses to ldap attribute '" + ldapStorageAttribute + "', user does not appear to have correct permissions to clear responses: " + e.getMessage();
                        } else {
                            errorMsg = "error clearing responses to ldap attribute '" + ldapStorageAttribute + "': " + e.getMessage();
                        }
                        final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_WRITING_RESPONSES, errorMsg);
                        final PwmOperationalException pwmOE = new PwmOperationalException(errorInfo);
                        pwmOE.initCause(e);
                        throw pwmOE;
                    }
                }
                break;

                case PWMDB: {
                    attempts++;
                    if (userGUID == null || userGUID.length() < 1) {
                        throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_MISSING_GUID, "cannot clear responses to pwmDB, user does not have a pwmGUID"));
                    }

                    if (pwmApplication.getPwmDB() == null) {
                        final String errorMsg = "pwmDB is not available, unable to write user responses";
                        final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_PWMDB_UNAVAILABLE, errorMsg);
                        throw new PwmOperationalException(errorInformation);
                    }

                    try {
                        pwmApplication.getPwmDB().remove(PwmDB.DB.RESPONSE_STORAGE, userGUID);
                        LOGGER.info(pwmSession, "cleared responses for user in local pwmDB");
                        successes++;
                    } catch (PwmDBException e) {
                        final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_CLEARING_RESPONSES, "unexpected pwmDB error clearing responses to pwmDB: " + e.getMessage());
                        final PwmOperationalException pwmOE = new PwmOperationalException(errorInfo);
                        pwmOE.initCause(e);
                        throw pwmOE;
                    }
                }
                break;

                case DB: {
                    attempts++;
                    if (userGUID == null || userGUID.length() < 1) {
                        throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_MISSING_GUID, "cannot clear responses to remote database, user does not have a pwmGUID"));
                    }

                    try {
                        final DatabaseAccessor databaseAccessor = pwmApplication.getDatabaseAccessor();
                        databaseAccessor.remove(DatabaseAccessor.TABLE.PWM_RESPONSES, userGUID);
                        LOGGER.info(pwmSession, "cleared responses for user " + theUser.getEntryDN() + " in remote database");
                        successes++;
                    } catch (PwmUnrecoverableException e) {
                        final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_CLEARING_RESPONSES, "unexpected error clearing responses to remote database: " + e.getMessage());
                        final PwmOperationalException pwmOE = new PwmOperationalException(errorInfo);
                        pwmOE.initCause(e);
                        throw pwmOE;
                    }
                }
                break;
            }
        }

        if (config.readSettingAsBoolean(PwmSetting.EDIRECTORY_STORE_NMAS_RESPONSES)) {
            try {
                if (theUser.getChaiProvider().getDirectoryVendor() == ChaiProvider.DIRECTORY_VENDOR.NOVELL_EDIRECTORY) {
                    attempts++;
                    NmasCrFactory.clearResponseSet(theUser);
                    LOGGER.info(pwmSession, "cleared responses for user using NMAS method ");
                    successes++;
                }
            } catch (ChaiOperationException e) {
                final String errorMsg = "error writing responses to nmas: " + e.getMessage();
                final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_CLEARING_RESPONSES, errorMsg);
                final PwmOperationalException pwmOE = new PwmOperationalException(errorInfo);
                pwmOE.initCause(e);
                throw pwmOE;
            }
        }

        if (attempts == 0) {
            final String errorMsg = "no response save methods are available or configured";
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_CLEARING_RESPONSES, errorMsg);
            throw new PwmOperationalException(errorInfo);
        }

        if (attempts != successes) { // should be impossible to get here, but just in case.
            final String errorMsg = "response clear partially successful; attempts=" + attempts + ", successes=" + successes;
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_CLEARING_RESPONSES, errorMsg);
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
                LOGGER.debug(pwmSession,"failed meetsChallengeSetRequirements, not enough required challenge");
                return false;
            }

            for (final Challenge loopChallenge : challengeSet.getRequiredChallenges()) {
                if (loopChallenge.isAdminDefined()) {
                    if (!this.getChallengeSet().getChallengeTexts().contains(loopChallenge.getChallengeText())) {
                        LOGGER.debug(pwmSession,"failed meetsChallengeSetRequirements, missing required challenge text: '" + loopChallenge.getChallengeText() + "'");
                        return false;
                    }
                }
            }

            if (challengeSet.getMinRandomRequired() > 0) {
                if (this.getChallengeSet().getChallenges().size() < challengeSet.getMinRandomRequired()) {
                    LOGGER.debug(pwmSession,"failed meetsChallengeSetRequirements, not enough questions to meet minrandom; minRandomRequired=" + challengeSet.getMinRandomRequired() + ", ChallengesInSet=" + this.getChallengeSet().getChallenges().size());
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
                pwmApplication.getIntruderManager().mark(null, null, pwmSession);
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
