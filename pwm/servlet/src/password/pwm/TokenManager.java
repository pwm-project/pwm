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

package password.pwm;

import com.google.gson.Gson;
import com.novell.ldapchai.ChaiFactory;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiException;
import com.novell.ldapchai.provider.ChaiProvider;
import com.novell.ldapchai.util.SearchHelper;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.error.*;
import password.pwm.health.HealthRecord;
import password.pwm.health.HealthStatus;
import password.pwm.util.Helper;
import password.pwm.util.PwmLogger;
import password.pwm.util.PwmRandom;
import password.pwm.util.TimeDuration;
import password.pwm.util.db.DatabaseAccessor;
import password.pwm.util.operations.UserSearchEngine;
import password.pwm.util.localdb.LocalDB;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.io.Serializable;
import java.util.*;

/**
 * This PWM service is responsible for reading/writing tokens used for forgotten password,
 * new user registration, account activation, and other functions.  Several implementations
 * of the backing storage method are available.
 *
 * @author jrivard@gmail.com
 */
public class TokenManager implements PwmService {

    private static PwmLogger LOGGER = PwmLogger.getLogger(TokenManager.class);

    private static final long MAX_CLEANER_INTERVAL_MS = 24 * 60 * 60 * 1000; // one day
    private static final long MIN_CLEANER_INTERVAL_MS = 5 * 60 * 1000; // 5 minutes

    private Timer timer;

    private Configuration configuration;
    private Configuration.TokenStorageMethod storageMethod;
    private long maxTokenAgeMS;
    private long maxTokenPurgeAgeMS;
    private TokenMachine tokenMachine;
    private SecretKey secretKey;

    private STATUS status = STATUS.NEW;

    private ErrorInformation errorInformation = null;

    public TokenManager()
            throws PwmOperationalException
    {
    }

    public void init(final PwmApplication pwmApplication)
            throws PwmException
    {
        LOGGER.trace("opening");
        status = STATUS.OPENING;

        this.configuration = pwmApplication.getConfig();
        storageMethod = configuration.getTokenStorageMethod();
        if (storageMethod == null) {
            final String errorMsg = "no storage method specified";
            errorInformation = new ErrorInformation(PwmError.ERROR_INVALID_CONFIG,errorMsg);
            status = STATUS.CLOSED;
            throw new PwmOperationalException(errorInformation);
        }
        try {
            maxTokenAgeMS = configuration.readSettingAsLong(PwmSetting.TOKEN_LIFETIME) * 1000;
            maxTokenPurgeAgeMS = maxTokenAgeMS + PwmConstants.TOKEN_REMOVAL_DELAY_MS;
        } catch (Exception e) {
            final String errorMsg = "unable to parse max token age value: " + e.getMessage();
            errorInformation = new ErrorInformation(PwmError.ERROR_INVALID_CONFIG,errorMsg);
            status = STATUS.CLOSED;
            throw new PwmOperationalException(errorInformation);
        }

        try {
            secretKey = configuration.getSecurityKey();

            switch (storageMethod) {
                case STORE_LOCALDB:
                    tokenMachine = new PwmDBTokenMachine(pwmApplication.getLocalDB());
                    break;

                case STORE_DB:
                    tokenMachine = new DBTokenMachine(pwmApplication.getDatabaseAccessor());
                    break;

                case STORE_CRYPTO:
                    tokenMachine = new CryptoTokenMachine();
                    break;

                case STORE_LDAP:
                    tokenMachine = new LdapTokenMachine(pwmApplication);
                    break;
            }
        } catch (PwmException e) {
            final String errorMsg = "unable to start token manager: " + e.getErrorInformation().getDetailedErrorMsg();
            final ErrorInformation newErrorInformation = new ErrorInformation(e.getError(), errorMsg);
            errorInformation = newErrorInformation;
            LOGGER.error(newErrorInformation.toDebugStr());
            status = STATUS.CLOSED;
            return;
        }

        timer = new Timer(PwmConstants.PWM_APP_NAME + "-TokenManager timer",true);
        final TimerTask cleanerTask = new CleanerTask();

        final long cleanerFrequency = (maxTokenAgeMS*0.5) > MAX_CLEANER_INTERVAL_MS ? MAX_CLEANER_INTERVAL_MS : (maxTokenAgeMS*0.5) < MIN_CLEANER_INTERVAL_MS ? MIN_CLEANER_INTERVAL_MS : (long)(maxTokenAgeMS*0.5);
        timer.schedule(cleanerTask, 10000, cleanerFrequency + 731);
        LOGGER.trace("token cleanup will occur every " + TimeDuration.asCompactString(cleanerFrequency));

        status = STATUS.OPEN;
        LOGGER.debug("open");
    }

    public boolean supportsName() {
        switch (storageMethod) {
            case STORE_LDAP:
                return false;

            default:
                return true;
        }
    }

    public String generateNewToken(final TokenPayload tokenPayload)
            throws PwmOperationalException
    {
        checkStatus();

        try {
            final String tokenKey = tokenMachine.generateToken(tokenPayload);
            tokenMachine.storeToken(tokenKey, tokenPayload);
            return tokenKey;
        } catch (PwmException e) {
            final String errorMsg = "unexpected error trying to store token in datastore: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(e.getError(),errorMsg);
            throw new PwmOperationalException(errorInformation);
        }
    }


    public TokenPayload retrieveTokenData(final String tokenKey)
            throws PwmOperationalException
    {
        checkStatus();

        try {
            final TokenPayload storedToken = tokenMachine.retrieveToken(tokenKey);
            if (storedToken != null) {

                if (testIfTokenIsExpired(storedToken)) {
                    throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_TOKEN_EXPIRED));
                }

                if (testIfTokenIsPurgable(storedToken)) {
                    tokenMachine.removeToken(tokenKey);
                }

                return storedToken;
            }
        } catch (PwmException e) {
            final String errorMsg = "error trying to retrieve token from datastore: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_TOKEN_INCORRECT,errorMsg);
            throw new PwmOperationalException(errorInformation);
        }
        return null;
    }

    public STATUS status() {
        return status;
    }

    public void close() {
        if (timer != null) {
            timer.cancel();
        }
        status = STATUS.CLOSED;
    }

    public List<HealthRecord> healthCheck() {
        final List<HealthRecord> returnRecords = new ArrayList<HealthRecord>();

        if (tokensAreUsedInConfig(configuration)) {
            if (errorInformation != null) {
                returnRecords.add(new HealthRecord(HealthStatus.WARN,"TokenManager",errorInformation.toDebugStr()));
            }
        }

        if (storageMethod == Configuration.TokenStorageMethod.STORE_LDAP) {
            if (configuration.readSettingAsBoolean(PwmSetting.NEWUSER_ENABLE)) {
                if (configuration.readSettingAsBoolean(PwmSetting.NEWUSER_EMAIL_VERIFICATION)) {
                    returnRecords.add(new HealthRecord(HealthStatus.CAUTION,"TokenManager","New User Email Verification is enabled and the token storage method is set to STORE_LDAP, this configuration is not supported."));
                }
            }
        }

        return returnRecords;
    }


    private boolean testIfTokenIsExpired(final TokenPayload theToken) {
        if (theToken == null) {
            return false;
        }
        final Date issueDate = theToken.getIssueDate();
        final TimeDuration duration = new TimeDuration(issueDate,new Date());
        return duration.isLongerThan(maxTokenAgeMS);
    }

    private boolean testIfTokenIsPurgable(final TokenPayload theToken) {
        if (theToken == null) {
            return false;
        }
        final Date issueDate = theToken.getIssueDate();
        final TimeDuration duration = new TimeDuration(issueDate,new Date());
        return duration.isLongerThan(maxTokenPurgeAgeMS);
    }


    private void purgeOutdatedTokens() throws
            PwmUnrecoverableException, PwmOperationalException
    {
        final long startTime = System.currentTimeMillis();
        int cleanedTokens = 0;
        List<String> tempKeyList = new ArrayList<String>();
        tempKeyList.addAll(discoverPurgeableTokenKeys(PwmConstants.TOKEN_PURGE_BATCH_SIZE));
        while (status() == STATUS.OPEN && !tempKeyList.isEmpty()) {
            for (final String loopKey : tempKeyList) {
                tokenMachine.removeToken(loopKey);
            }
            cleanedTokens = cleanedTokens + tempKeyList.size();
            tempKeyList.clear();
        }
        if (cleanedTokens > 0) {
            LOGGER.trace("cleaner thread removed " + cleanedTokens + " tokens in " + TimeDuration.fromCurrent(startTime).asCompactString());
        }
    }

    private List<String> discoverPurgeableTokenKeys(final int maxCount)
            throws PwmUnrecoverableException, PwmOperationalException
    {
        final List<String> returnList = new ArrayList<String>();
        Iterator<String> keyIterator = null;

        try {
            keyIterator = tokenMachine.keyIterator();

            while (status() == STATUS.OPEN && returnList.size() < maxCount && keyIterator.hasNext()) {
                final String loopKey = keyIterator.next();
                final TokenPayload loopInfo = tokenMachine.retrieveToken(loopKey);
                if (loopInfo != null) {
                    if (testIfTokenIsPurgable(loopInfo)) {
                        returnList.add(loopKey);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("unexpected error while cleaning expired stored tokens: " + e.getMessage());
        } finally {
            if (keyIterator != null && storageMethod == Configuration.TokenStorageMethod.STORE_LOCALDB) {
                try {((LocalDB.PwmDBIterator)keyIterator).close(); } catch (Exception e) {LOGGER.error("unexpected error returning LocalDB token DB iterator: " + e.getMessage());}
            }
        }

        return returnList;
    }


    private static String makeRandomCode(final Configuration config) {
        final String RANDOM_CHARS = config.readSettingAsString(PwmSetting.TOKEN_CHARACTERS);
        final int CODE_LENGTH = (int) config.readSettingAsLong(PwmSetting.TOKEN_LENGTH);
        final PwmRandom RANDOM = PwmRandom.getInstance();

        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(RANDOM_CHARS.charAt(RANDOM.nextInt(RANDOM_CHARS.length())));
        }

        return sb.toString();
    }

    public static class TokenPayload implements Serializable {
        private final java.util.Date issueDate;
        private final String name;
        private final Map<String,String> payloadData;
        private final String userDN;

        public TokenPayload(final String name, final Map<String,String> payloadData, final String userDN) {
            this.issueDate = new Date();
            this.payloadData = payloadData;
            this.name = name;
            this.userDN = userDN;
        }

        public Date getIssueDate() {
            return issueDate;
        }

        public String getName() {
            return name;
        }

        public Map<String, String> getPayloadData() {
            return payloadData;
        }

        public String getUserDN() {
            return userDN;
        }

        public String toEncryptedString(final TokenManager tokenManager)
                throws PwmUnrecoverableException, PwmOperationalException
        {
            final Gson gson = new Gson();
            final String jsonPayload = gson.toJson(this);
            return Helper.SimpleTextCrypto.encryptValue(jsonPayload,tokenManager.secretKey, true);
        }

        private static TokenPayload fromEncryptedString(final TokenManager tokenManager, final String inputString)
                throws PwmOperationalException, PwmUnrecoverableException
        {
            final String deWhiteSpacedToken = inputString.replaceAll("\\s","");
            final String decryptedString = Helper.SimpleTextCrypto.decryptValue(deWhiteSpacedToken,tokenManager.secretKey, true);
            final Gson gson = new Gson();
            return gson.fromJson(decryptedString,TokenPayload.class);
        }
    }

    private class CleanerTask extends TimerTask {
        public void run() {
            try {
                tokenMachine.cleanup();
            } catch (Exception e) {
                LOGGER.warn("unexpected error while cleaning expired stored tokens: " + e.getMessage(),e);
            }
        }
    }

    private void checkStatus() throws PwmOperationalException {
        if (status != STATUS.OPEN) {
            throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_SERVICE_NOT_AVAILABLE,"token manager is not open"));
        }
    }

    public int size() throws PwmUnrecoverableException {
        if (status != STATUS.OPEN) {
            return -1;
        }

        try {
            return tokenMachine.size();
        } catch (Exception e) {
            LOGGER.error("unexpected error reading size of token storage table: " + e.getMessage());
        }

        return -1;
    }

    private static String makeUniqueTokenForMachine(final TokenMachine machine, final TokenPayload payload, final Configuration config)
            throws PwmUnrecoverableException, PwmOperationalException {
        String tokenKey = null;
        int attempts = 0;
        while (tokenKey == null && attempts < PwmConstants.TOKEN_MAX_UNIQUE_CREATE_ATTEMPTS) {
            tokenKey = makeRandomCode(config);
            if (machine.retrieveToken(tokenKey) != null) {
                tokenKey = null;
            }
            attempts++;
        }

        if (tokenKey == null) {
            throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_UNKNOWN,"unable to generate a unique token key after " + attempts + " attempts"));
        }
        return tokenKey;
    }

    private static String makeTokenHash(final String tokenKey) throws PwmUnrecoverableException {
        try {
            return Helper.md5sum(tokenKey) + "-hash";
        } catch (IOException e) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_UNKNOWN,"unexpected IOException generating md5sum of tokenKey: " + e.getMessage()));
        }
    }

    private interface TokenMachine {
        String generateToken(final TokenPayload tokenPayload) throws PwmUnrecoverableException, PwmOperationalException;

        TokenPayload retrieveToken(final String tokenKey) throws PwmOperationalException, PwmUnrecoverableException;

        void storeToken(final String tokenKey, final TokenPayload tokenPayload) throws PwmOperationalException, PwmUnrecoverableException;

        void removeToken(final String tokenKey) throws PwmOperationalException, PwmUnrecoverableException;

        int size() throws PwmOperationalException, PwmUnrecoverableException;

        Iterator<String> keyIterator() throws PwmOperationalException, PwmUnrecoverableException;

        void cleanup() throws PwmUnrecoverableException, PwmOperationalException;
    }

    private class PwmDBTokenMachine implements TokenMachine {
        private LocalDB localDB;

        private PwmDBTokenMachine(LocalDB localDB) {
            this.localDB = localDB;
        }

        public String generateToken(TokenPayload tokenPayload) throws PwmUnrecoverableException, PwmOperationalException {
            return makeUniqueTokenForMachine(this, tokenPayload, configuration);
        }

        public TokenPayload retrieveToken(String tokenKey)
                throws PwmOperationalException, PwmUnrecoverableException
        {
            final String md5sumToken = makeTokenHash(tokenKey);
            final String storedRawValue = localDB.get(LocalDB.DB.TOKENS, md5sumToken);

            if (storedRawValue != null && storedRawValue.length() > 0 ) {
                return TokenPayload.fromEncryptedString(TokenManager.this,storedRawValue);
            }

            return null;
        }

        public void storeToken(String tokenKey, TokenPayload tokenPayload) throws PwmOperationalException, PwmUnrecoverableException {
            final String rawValue = tokenPayload.toEncryptedString(TokenManager.this);
            final String md5sumToken = makeTokenHash(tokenKey);
            localDB.put(LocalDB.DB.TOKENS, md5sumToken, rawValue);
        }

        public void removeToken(String tokenKey)
                throws PwmOperationalException, PwmUnrecoverableException
        {
            final String md5sumToken = makeTokenHash(tokenKey);
            localDB.remove(LocalDB.DB.TOKENS, tokenKey);
            localDB.remove(LocalDB.DB.TOKENS, md5sumToken);
        }

        public int size() throws PwmOperationalException {
            return localDB.size(LocalDB.DB.TOKENS);
        }

        public LocalDB.PwmDBIterator<String> keyIterator() throws PwmOperationalException {
            return localDB.iterator(LocalDB.DB.TOKENS);
        }

        public void cleanup() throws PwmUnrecoverableException, PwmOperationalException {
            purgeOutdatedTokens();
        }
    }

    private class DBTokenMachine implements TokenMachine {
        private DatabaseAccessor databaseAccessor;

        private DBTokenMachine(DatabaseAccessor databaseAccessor) {
            this.databaseAccessor = databaseAccessor;
        }

        public String generateToken(TokenPayload tokenPayload) throws PwmUnrecoverableException, PwmOperationalException {
            return makeUniqueTokenForMachine(this, tokenPayload, configuration);
        }

        public TokenPayload retrieveToken(String tokenKey)
                throws PwmOperationalException, PwmUnrecoverableException
        {
            final String md5sumToken = makeTokenHash(tokenKey);
            final String storedRawValue = databaseAccessor.get(DatabaseAccessor.TABLE.TOKENS,md5sumToken);

            if (storedRawValue != null && storedRawValue.length() > 0 ) {
                return TokenPayload.fromEncryptedString(TokenManager.this, storedRawValue);
            }

            return null;
        }

        public void storeToken(String tokenKey, TokenPayload tokenPayload) throws PwmOperationalException, PwmUnrecoverableException {
            final String rawValue = tokenPayload.toEncryptedString(TokenManager.this);
            final String md5sumToken = makeTokenHash(tokenKey);
            databaseAccessor.put(DatabaseAccessor.TABLE.TOKENS, md5sumToken, rawValue);
        }

        public void removeToken(String tokenKey) throws PwmOperationalException, PwmUnrecoverableException {
            final String md5sumToken = makeTokenHash(tokenKey);
            databaseAccessor.remove(DatabaseAccessor.TABLE.TOKENS,tokenKey);
            databaseAccessor.remove(DatabaseAccessor.TABLE.TOKENS,md5sumToken);
        }

        public int size() throws PwmOperationalException, PwmUnrecoverableException {
            return databaseAccessor.size(DatabaseAccessor.TABLE.TOKENS);
        }

        public Iterator<String> keyIterator() throws PwmOperationalException, PwmUnrecoverableException {
            return databaseAccessor.iterator(DatabaseAccessor.TABLE.TOKENS);
        }

        public void cleanup() throws PwmUnrecoverableException, PwmOperationalException {
            purgeOutdatedTokens();
        }
    }

    private class CryptoTokenMachine implements TokenMachine {

        private CryptoTokenMachine()
                throws PwmOperationalException
        {
        }

        public String generateToken(TokenPayload tokenPayload) throws PwmUnrecoverableException, PwmOperationalException {
            final int WRAP_LENGTH = 60;
            final StringBuilder returnString = new StringBuilder(tokenPayload.toEncryptedString(TokenManager.this));
            for (int i = WRAP_LENGTH - 1; i < returnString.length(); i += WRAP_LENGTH) {
                returnString.insert(i,"\n");
            }
            return returnString.toString();
        }

        public TokenPayload retrieveToken(String tokenKey)
                throws PwmOperationalException, PwmUnrecoverableException
        {
            if (tokenKey == null || tokenKey.length() < 1) {
                return null;
            }
            return TokenPayload.fromEncryptedString(TokenManager.this, tokenKey);
        }

        public void storeToken(String tokenKey, TokenPayload tokenPayload) throws PwmOperationalException, PwmUnrecoverableException {
        }

        public void removeToken(String tokenKey) throws PwmOperationalException, PwmUnrecoverableException {
        }

        public int size() throws PwmOperationalException, PwmUnrecoverableException {
            return 0;
        }

        public Iterator<String> keyIterator() throws PwmOperationalException, PwmUnrecoverableException {
            return Collections.<String>emptyList().iterator();
        }

        public void cleanup() {
        }
    }

    private class LdapTokenMachine implements TokenMachine {
        private PwmApplication pwmApplication;
        private String tokenAttribute;
        private final String KEY_VALUE_DELIMITER = " ";

        private LdapTokenMachine(PwmApplication pwmApplication)
                throws PwmOperationalException
        {
            this.pwmApplication = pwmApplication;
            this.tokenAttribute = pwmApplication.getConfig().readSettingAsString(PwmSetting.TOKEN_LDAP_ATTRIBUTE);
            secretKey = configuration.getSecurityKey();
        }

        public String generateToken(TokenPayload tokenPayload) throws PwmUnrecoverableException, PwmOperationalException {
            return makeUniqueTokenForMachine(this, tokenPayload, configuration);
        }

        public TokenPayload retrieveToken(String tokenKey)
                throws PwmOperationalException, PwmUnrecoverableException
        {
            final String searchFilter;
            {
                final String md5sumToken = makeTokenHash(tokenKey);
                final SearchHelper tempSearchHelper = new SearchHelper();
                final Map<String,String> filterAttributes = new HashMap<String,String>();
                for (final String loopStr : pwmApplication.getConfig().readSettingAsStringArray(PwmSetting.DEFAULT_OBJECT_CLASSES)) {
                    filterAttributes.put("objectClass", loopStr);
                }
                filterAttributes.put(tokenAttribute,md5sumToken + "*");
                tempSearchHelper.setFilterAnd(filterAttributes);
                searchFilter = tempSearchHelper.getFilter();
            }

            try {
                final UserSearchEngine userSearchEngine = new UserSearchEngine(pwmApplication);
                final UserSearchEngine.SearchConfiguration searchConfiguration = new UserSearchEngine.SearchConfiguration();
                searchConfiguration.setFilter(searchFilter);
                final ChaiUser user = userSearchEngine.performSingleUserSearch(null, searchConfiguration);
                if (user == null) {
                    return null;
                }
                final String tokenAttributeValue = user.readStringAttribute(tokenAttribute);
                if (tokenAttribute != null && tokenAttributeValue.length() > 0) {
                    final String splitString[] = tokenAttributeValue.split(KEY_VALUE_DELIMITER);
                    if (splitString.length != 2) {
                        final String errorMsg = "error parsing ldap stored token, not enough delimited values";
                        final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_TOKEN_INCORRECT,errorMsg);
                        throw new PwmOperationalException(errorInformation);
                    }
                    return TokenPayload.fromEncryptedString(TokenManager.this,splitString[1]);
                }
            } catch (PwmOperationalException e) {
                if (e.getError() == PwmError.ERROR_CANT_MATCH_USER) {
                    return null;
                }
                throw e;
            } catch (ChaiException e) {
                final String errorMsg = "unexpected ldap error searching for token: " + e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_TOKEN_INCORRECT,errorMsg);
                throw new PwmOperationalException(errorInformation);
            }
            return null;
        }

        public void storeToken(String tokenKey, TokenPayload tokenPayload)
                throws PwmOperationalException, PwmUnrecoverableException
        {
            try {
                final String md5sumToken = makeTokenHash(tokenKey);
                final String encodedTokenPayload = tokenPayload.toEncryptedString(TokenManager.this);

                final ChaiProvider chaiProvider = pwmApplication.getProxyChaiProvider();
                final ChaiUser chaiUser = ChaiFactory.createChaiUser(tokenPayload.getUserDN(),chaiProvider);
                chaiUser.writeStringAttribute(tokenAttribute, md5sumToken + KEY_VALUE_DELIMITER + encodedTokenPayload);
            } catch (ChaiException e) {
                final String errorMsg = "unexpected ldap error saving token: " + e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN,errorMsg);
                throw new PwmOperationalException(errorInformation);
            }
        }

        public void removeToken(String tokenKey)
                throws PwmOperationalException, PwmUnrecoverableException
        {
            final TokenPayload payload = retrieveToken(tokenKey);
            if (payload != null) {
                final String userDN = payload.getUserDN();
                try {
                    final ChaiProvider chaiProvider = pwmApplication.getProxyChaiProvider();
                    final ChaiUser chaiUser = ChaiFactory.createChaiUser(userDN,chaiProvider);
                    chaiUser.deleteAttribute(tokenAttribute, null);
                } catch (ChaiException e) {
                    final String errorMsg = "unexpected ldap error removing token: " + e.getMessage();
                    final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN,errorMsg);
                    throw new PwmOperationalException(errorInformation);
                }
            }
        }

        public int size() throws PwmOperationalException {
            return -1;
        }

        public Iterator<String> keyIterator() throws PwmOperationalException {
            return Collections.<String>emptyList().iterator();
        }

        public void cleanup() throws PwmUnrecoverableException, PwmOperationalException {
        }
    }

    private static boolean tokensAreUsedInConfig(final Configuration configuration) {
        if (configuration.readSettingAsBoolean(PwmSetting.NEWUSER_EMAIL_VERIFICATION) &&
                configuration.readSettingAsBoolean(PwmSetting.NEWUSER_ENABLE)) {
            return true;
        }

        if (configuration.readSettingAsBoolean(PwmSetting.ACTIVATE_USER_ENABLE) &&
                PwmSetting.MessageSendMethod.NONE != configuration.readSettingAsTokenSendMethod(PwmSetting.ACTIVATE_TOKEN_SEND_METHOD)) {
            return true;
        }

        if (configuration.readSettingAsBoolean(PwmSetting.CHALLENGE_ENABLE) &&
                PwmSetting.MessageSendMethod.NONE != configuration.readSettingAsTokenSendMethod(PwmSetting.CHALLENGE_TOKEN_SEND_METHOD)) {
            return true;
        }

        return false;
    }
}
