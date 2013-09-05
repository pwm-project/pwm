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

import com.google.gson.Gson;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.cr.*;
import com.novell.ldapchai.exception.*;
import com.novell.ldapchai.impl.edir.NmasCrFactory;
import com.novell.ldapchai.provider.*;
import password.pwm.*;
import password.pwm.bean.ResponseInfoBean;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.error.*;
import password.pwm.health.HealthRecord;
import password.pwm.util.Helper;
import password.pwm.util.PwmLogger;
import password.pwm.util.TimeDuration;
import password.pwm.util.operations.cr.*;
import password.pwm.wordlist.WordlistManager;

import java.net.URL;
import java.util.*;

/**
 * @author Jason D. Rivard
 */
public class CrService implements PwmService {
    private static final PwmLogger LOGGER = PwmLogger.getLogger(CrService.class);

    private final Map<Configuration.STORAGE_METHOD,CrOperator> operatorMap = new HashMap<Configuration.STORAGE_METHOD,CrOperator>();
    private PwmApplication pwmApplication;

    public CrService() {
    }

    @Override
    public STATUS status() {
        return STATUS.OPEN;
    }

    @Override
    public void init(PwmApplication pwmApplication) throws PwmException {
        this.pwmApplication = pwmApplication;
        operatorMap.put(Configuration.STORAGE_METHOD.DB, new DbCrOperator(pwmApplication));
        operatorMap.put(Configuration.STORAGE_METHOD.LDAP, new LdapCrOperator(pwmApplication.getConfig()));
        operatorMap.put(Configuration.STORAGE_METHOD.LOCALDB, new LocalDbCrOperator(pwmApplication.getLocalDB()));
        operatorMap.put(Configuration.STORAGE_METHOD.NMAS, new NMASCrOperator(pwmApplication));
        operatorMap.put(Configuration.STORAGE_METHOD.NMASUAWS, new NMASUAWSOperator(pwmApplication));
    }

    @Override
    public void close() {
        for (final CrOperator operator : operatorMap.values()) {
            operator.close();
        }
        operatorMap.clear();
    }

    @Override
    public List<HealthRecord> healthCheck() {
        return Collections.emptyList();
    }

    public ChallengeSet readUserChallengeSet(
            final ChaiUser theUser,
            final PwmPasswordPolicy policy,
            final Locale locale
    )
            throws PwmUnrecoverableException
    {
        final Configuration config = pwmApplication.getConfig();
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
                        LOGGER.debug( "no nmas c/r policy found for user " + theUser.getEntryDN());
                    } else {
                        LOGGER.debug("using nmas c/r policy for user " + theUser.getEntryDN() + ": " + returnSet.toString());
                    }
                }
            } catch (ChaiException e) {
                LOGGER.error("error reading nmas c/r policy for user " + theUser.getEntryDN() + ": " + e.getMessage());
            }
        }

        // use PWM policies if PWM is configured and either its all that is configured OR the NMAS policy read was not successfull
        if (returnSet == null) {
            returnSet = config.getGlobalChallengeSet(locale);
            if (returnSet != null) {
                LOGGER.debug("using ldap c/r policy for user " + theUser.getEntryDN() + ": " + returnSet.toString());
            }
        }

        if (returnSet == null) {
            LOGGER.warn("no available c/r policy for user" + theUser.getEntryDN() + ": ");
        }

        LOGGER.trace("readUserChallengeSet completed in " + TimeDuration.fromCurrent(methodStartTime).asCompactString());

        return returnSet;
    }

    public void validateResponses(
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

        if (minRandomRequiredSetup == 0) { // if using recover style, then all readResponseSet must be supplied at this point.
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

    public ResponseInfoBean readUserResponseInfo(
            final PwmSession pwmSession,
            final ChaiUser theUser
    )
            throws ChaiUnavailableException, PwmUnrecoverableException
    {
        final Configuration config = pwmApplication.getConfig();

        LOGGER.trace(pwmSession, "beginning read of user response sequence");

        final List<Configuration.STORAGE_METHOD> readPreferences = config.getResponseStorageLocations(PwmSetting.FORGOTTEN_PASSWORD_READ_PREFERENCE);
        {
            final String wsURL = pwmApplication.getConfig().readSettingAsString(PwmSetting.EDIRECTORY_PWD_MGT_WEBSERVICE_URL);
            if (wsURL != null && wsURL.length() > 0) {
                readPreferences.add(Configuration.STORAGE_METHOD.NMASUAWS);
            }
        }
        if (pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.EDIRECTORY_USE_NMAS_RESPONSES)) {
            readPreferences.add(Configuration.STORAGE_METHOD.NMAS);
        }

        final String debugMsg = "will attempt to read the following storage methods: " + new Gson().toJson(readPreferences) + " for response info for user " + theUser.getEntryDN();
        LOGGER.debug(pwmSession, debugMsg);

        final String userGUID;
        if (readPreferences.contains(Configuration.STORAGE_METHOD.DB) || readPreferences.contains(Configuration.STORAGE_METHOD.LOCALDB)) {
            userGUID = Helper.readLdapGuidValue(pwmApplication, theUser.getEntryDN());
        } else {
            userGUID = null;
        }

        for (final Configuration.STORAGE_METHOD storageMethod : readPreferences) {
            final ResponseInfoBean readResponses;

            LOGGER.trace(pwmSession, "attempting read of response info via storage method: " + storageMethod);
            readResponses = operatorMap.get(storageMethod).readResponseInfo(theUser, userGUID);

            if (readResponses != null) {
                LOGGER.debug(pwmSession,"returning response info read via method " + storageMethod + " for user " + theUser.getEntryDN());
                return readResponses;
            } else {
                LOGGER.trace(pwmSession, "no responses info read using method " + storageMethod);
            }
        }
        LOGGER.debug(pwmSession,"no response info found for user " + theUser.getEntryDN());
        return null;
    }



    public ResponseSet readUserResponseSet(
            final PwmSession pwmSession,
            final ChaiUser theUser
    )
            throws ChaiUnavailableException, PwmUnrecoverableException
    {
        final Configuration config = pwmApplication.getConfig();

        LOGGER.trace(pwmSession, "beginning read of user response sequence");

        final List<Configuration.STORAGE_METHOD> readPreferences = config.getResponseStorageLocations(PwmSetting.FORGOTTEN_PASSWORD_READ_PREFERENCE);

        {
            final String wsURL = pwmApplication.getConfig().readSettingAsString(PwmSetting.EDIRECTORY_PWD_MGT_WEBSERVICE_URL);
            if (wsURL != null && wsURL.length() > 0) {
                readPreferences.add(Configuration.STORAGE_METHOD.NMASUAWS);
            }
        }
        if (pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.EDIRECTORY_USE_NMAS_RESPONSES)) {
            readPreferences.add(Configuration.STORAGE_METHOD.NMAS);
        }
        final String debugMsg = "will attempt to read the following storage methods: " + new Gson().toJson(readPreferences) + " for user " + theUser.getEntryDN();
        LOGGER.debug(pwmSession, debugMsg);

        final String userGUID;
        if (readPreferences.contains(Configuration.STORAGE_METHOD.DB) || readPreferences.contains(Configuration.STORAGE_METHOD.LOCALDB)) {
            userGUID = Helper.readLdapGuidValue(pwmApplication, theUser.getEntryDN());
        } else {
            userGUID = null;
        }

        for (final Configuration.STORAGE_METHOD storageMethod : readPreferences) {
            final ResponseSet readResponses;

            LOGGER.trace(pwmSession, "attempting read of responses via storage method: " + storageMethod);
            readResponses = operatorMap.get(storageMethod).readResponseSet(theUser, userGUID);

            if (readResponses != null) {
                LOGGER.debug(pwmSession,"returning responses read via method " + storageMethod + " for user " + theUser.getEntryDN());
                return readResponses;
            } else {
                LOGGER.trace(pwmSession, "no responses read using method " + storageMethod);
            }
        }
        LOGGER.debug(pwmSession,"no responses found for user " + theUser.getEntryDN());
        return null;
    }



    public void writeResponses(
            final ChaiUser theUser,
            final String userGUID,
            final ResponseInfoBean responseInfoBean
    )
            throws PwmOperationalException, ChaiUnavailableException, ChaiValidationException
    {

        int attempts = 0, successes = 0;
        final Configuration config = pwmApplication.getConfig();

        final List<Configuration.STORAGE_METHOD> writeMethods = config.getResponseStorageLocations(PwmSetting.FORGOTTEN_PASSWORD_WRITE_PREFERENCE);
        if (pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.EDIRECTORY_STORE_NMAS_RESPONSES)) {
            writeMethods.add(Configuration.STORAGE_METHOD.NMAS);
        }

        for (final Configuration.STORAGE_METHOD loopWriteMethod : writeMethods) {
            try {
                attempts++;
                operatorMap.get(loopWriteMethod).writeResponses(theUser,userGUID,responseInfoBean);
                LOGGER.debug("saved responses using storage method " + loopWriteMethod + " for user " + theUser.getEntryDN());
                successes++;
            } catch (PwmUnrecoverableException e) {
                final String errorMsg = "unexpected error saving responses via " + loopWriteMethod + ", error: " + e.getMessage();
                LOGGER.error(errorMsg);
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


    public void clearResponses(
            final PwmSession pwmSession,
            final ChaiUser theUser,
            final String userGUID

    )
            throws PwmOperationalException, ChaiUnavailableException
    {
        final Configuration config = pwmApplication.getConfig();
        int attempts = 0, successes = 0;

        LOGGER.trace(pwmSession, "beginning clear response operation for user " + theUser.getEntryDN() + " guid=" + userGUID);

        final List<Configuration.STORAGE_METHOD> writeMethods = config.getResponseStorageLocations(PwmSetting.FORGOTTEN_PASSWORD_WRITE_PREFERENCE);
        if (pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.EDIRECTORY_STORE_NMAS_RESPONSES)) {
            writeMethods.add(Configuration.STORAGE_METHOD.NMAS);
        }

        for (final Configuration.STORAGE_METHOD loopWriteMethod : writeMethods) {
            try {
                attempts++;
                operatorMap.get(loopWriteMethod).clearResponses(theUser, userGUID);
                successes++;
            } catch (PwmUnrecoverableException e) {
                LOGGER.error(pwmSession,"error clearing responses via " + loopWriteMethod + ", error: " + e.getMessage());
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




    public boolean checkIfResponseConfigNeeded(
            final PwmSession pwmSession,
            final ChaiUser theUser,
            final ChallengeSet challengeSet,
            final ResponseInfoBean responseInfoBean
    )
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

        try {
            // check if responses exist
            if (responseInfoBean == null) {
                throw new Exception("no responses configured");
            }

            // check if responses meet the challenge set policy for the user
            //usersResponses.meetsChallengeSetRequirements(challengeSet);

            LOGGER.debug(pwmSession, "checkIfResponseConfigNeeded: " + userDN + " has good responses");
            return false;
        } catch (Exception e) {
            LOGGER.debug(pwmSession, "checkIfResponseConfigNeeded: " + userDN + " does not have good responses: " + e.getMessage());
            return true;
        }
    }

}
