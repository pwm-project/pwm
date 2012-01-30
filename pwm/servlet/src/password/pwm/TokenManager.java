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
import com.google.gson.JsonSyntaxException;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.error.*;
import password.pwm.health.HealthRecord;
import password.pwm.health.HealthStatus;
import password.pwm.util.*;
import password.pwm.util.db.DatabaseAccessor;
import password.pwm.util.pwmdb.PwmDB;

import javax.crypto.SecretKey;
import java.io.Serializable;
import java.util.*;

public class TokenManager implements PwmService {

    private static PwmLogger LOGGER = PwmLogger.getLogger(TokenManager.class);

    private static final long MAX_CLEANER_INTERVAL_MS = 24 * 60 * 60 * 1000; // one day
    private static final long MIN_CLEANER_INTERVAL_MS = 5 * 60 * 1000; // 5 minutes

    private enum StorageMethod {
        STORE_PWMDB,
        STORE_DB,
        STORE_CRYPTO
    }

    private Timer timer;

    private Configuration configuration;
    private PwmDB pwmDB;
    private DatabaseAccessor databaseAccessor;
    private StorageMethod storageMethod;
    private long maxTokenAgeMS;
    private long maxTokenPurgeAgeMS;
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
        final PwmDB pwmDB = pwmApplication.getPwmDB();
        final Configuration configuration = pwmApplication.getConfig();

        LOGGER.trace("opening");
        status = STATUS.OPENING;

        this.configuration = configuration;
        this.pwmDB = pwmDB;
        this.databaseAccessor = pwmApplication.getDatabaseAccessor();
        try {
            storageMethod = StorageMethod.valueOf(configuration.readSettingAsString(PwmSetting.TOKEN_STORAGEMETHOD));
        } catch (Exception e) {
            final String errorMsg = "unknown storage method specified: " + configuration.readSettingAsString(PwmSetting.TOKEN_STORAGEMETHOD);
            errorInformation = new ErrorInformation(PwmError.ERROR_INVALID_CONFIG,errorMsg);
            status = STATUS.CLOSED;
            throw new PwmOperationalException(errorInformation);
        }
        try {
            maxTokenAgeMS = configuration.readSettingAsLong(PwmSetting.TOKEN_LIFETIME) * 1000;
        } catch (Exception e) {
            final String errorMsg = "unable to parse max token age value: " + e.getMessage();
            errorInformation = new ErrorInformation(PwmError.ERROR_INVALID_CONFIG,errorMsg);
            status = STATUS.CLOSED;
            throw new PwmOperationalException(errorInformation);
        }

        switch (storageMethod) {
            case STORE_DB:
            case STORE_PWMDB:
            {
                maxTokenPurgeAgeMS = maxTokenAgeMS + PwmConstants.TOKEN_REMOVAL_DELAY_MS;
                timer = new Timer("pwm-TokenManager",true);
                final TimerTask cleanerTask = new CleanerTask();

                final long cleanerFrequency = (maxTokenAgeMS*0.5) > MAX_CLEANER_INTERVAL_MS ? MAX_CLEANER_INTERVAL_MS : (maxTokenAgeMS*0.5) < MIN_CLEANER_INTERVAL_MS ? MIN_CLEANER_INTERVAL_MS : (long)(maxTokenAgeMS*0.5);
                timer.schedule(cleanerTask, 10000, cleanerFrequency + 731);
                LOGGER.trace("token cleanup will occur every " + TimeDuration.asCompactString(cleanerFrequency));
                break;
            }
            case STORE_CRYPTO:
            {
                try {
                    secretKey = configuration.getSecurityKey();
                } catch (PwmOperationalException e) {
                    errorInformation = e.getErrorInformation();
                    status = STATUS.CLOSED;
                    throw new PwmOperationalException(errorInformation);
                }
            }
            break;
        }

        status = STATUS.OPEN;
        LOGGER.debug("open");
    }

    public String generateNewToken(final TokenPayload tokenPayload)
            throws PwmOperationalException
    {
        checkStatus();
        if (storageMethod == StorageMethod.STORE_CRYPTO) {
            return generateEmbedToken(tokenPayload);
        }

        try {
            String tokenKey = null;
            int attempts = 0;
            while (tokenKey == null && attempts < PwmConstants.TOKEN_MAX_UNIQUE_CREATE_ATTEMPTS) {
                tokenKey = makeRandomCode(configuration);
                if (retrieveStoredToken(tokenKey) != null) {
                    tokenKey = null;
                }
                attempts++;
            }

            if (tokenKey == null) {
                throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_UNKNOWN,"unable to generate a unique token key after " + attempts + " attempts"));
            }

            storeToken(tokenKey, tokenPayload);

            return tokenKey;
        } catch (PwmException e) {
            final String errorMsg = "unexpected error trying to store token in datastore: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(e.getError(),errorMsg);
            throw new PwmOperationalException(errorInformation);
        }
    }

    private String generateEmbedToken(final TokenPayload tokenPayload) throws PwmOperationalException {
        final Gson gson = new Gson();
        final String jsonPayload = gson.toJson(tokenPayload);
        try {
            final String encryptedPaylod = Helper.SimpleTextCrypto.encryptValue(jsonPayload,secretKey);
            return Base64Util.encodeObject(encryptedPaylod, Base64Util.GZIP | Base64Util.URL_SAFE);
        } catch (Exception e) {
            final String errorMsg = "unexpected error generating embeded token: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN,errorMsg);
            throw new PwmOperationalException(errorInformation);
        }
    }

    public TokenPayload retrieveTokenData(final String tokenKey)
            throws PwmOperationalException
    {
        checkStatus();
        if (storageMethod == StorageMethod.STORE_CRYPTO) {
            return retrieveEmbedToken(tokenKey);
        }

        try {
            final TokenPayload storedToken = retrieveStoredToken(tokenKey);
            if (storedToken != null) {

                if (testIfTokenIsExpired(storedToken)) {
                    throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_TOKEN_EXPIRED));
                }

                if (storedToken.isUsed()) {
                    throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_TOKEN_EXPIRED,"token has already been used"));
                } else {
                    storedToken.setUsed(true);
                    storeToken(tokenKey, storedToken);
                }

                return storedToken;
            }
        } catch (PwmException e) {
            final String errorMsg = "unexpected error trying to retrieve token from datastore: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(e.getError(),errorMsg);
            throw new PwmOperationalException(errorInformation);
        }
        return null;
    }

    private TokenPayload retrieveEmbedToken(final String tokenKey) throws PwmOperationalException {
        final String decodedString;
        try {
            decodedString = (String) Base64Util.decodeToObject(tokenKey, Base64Util.GZIP | Base64Util.URL_SAFE, ClassLoader.getSystemClassLoader());
            final String decryptedString = Helper.SimpleTextCrypto.decryptValue(decodedString,secretKey);
            final Gson gson = new Gson();
            final TokenPayload tokenPayload = gson.fromJson(decryptedString,TokenPayload.class);
            if (testIfTokenIsExpired(tokenPayload)) {
                throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_TOKEN_EXPIRED));
            }
            return tokenPayload;
        } catch (Exception e) {
            final String errorMsg = "unexpected error decrypting embed token: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN,errorMsg);
            throw new PwmOperationalException(errorInformation);
        }
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
        if (errorInformation != null) {
            return Collections.singletonList(new HealthRecord(HealthStatus.WARN,"TokenManager",errorInformation.toDebugStr()));
        }
        return null;
    }

    private TokenPayload retrieveStoredToken(final String tokenKey)
            throws PwmOperationalException, PwmUnrecoverableException {
        TokenPayload returnToken = null;

        {
            final String storedRawValue;
            switch (storageMethod) {
                case STORE_PWMDB:
                    storedRawValue = pwmDB.get(PwmDB.DB.TOKENS,tokenKey);
                    break;

                case STORE_DB:
                    storedRawValue = databaseAccessor.get(DatabaseAccessor.TABLE.TOKENS,tokenKey);
                    break;

                default:
                    throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_UNKNOWN,"unknown storage method: " + storageMethod));
            }

            if (storedRawValue != null && storedRawValue.length() > 0 ) {
                try {
                    final Gson gson = new Gson();
                    returnToken = gson.fromJson(storedRawValue,TokenPayload.class);
                } catch (JsonSyntaxException e) {
                    LOGGER.error("unexpected syntax error reading token payload: " + e.getMessage());
                    removeTokenFormStorage(tokenKey);
                }
            }
        }

        return returnToken;
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


    private void storeToken(final String tokenKey, final TokenPayload tokenInformation)
            throws  PwmUnrecoverableException, PwmOperationalException {
        final Gson gson = new Gson();
        final String rawValue = gson.toJson(tokenInformation);

        {
            switch (storageMethod) {
                case STORE_PWMDB:
                    pwmDB.put(PwmDB.DB.TOKENS, tokenKey, rawValue);
                    break;

                case STORE_DB:
                    databaseAccessor.put(DatabaseAccessor.TABLE.TOKENS, tokenKey, rawValue);
                    break;

                default:
                    throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_UNKNOWN,"unknown storage method: " + storageMethod));
            }
        }
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
                removeTokenFormStorage(loopKey);
            }
            cleanedTokens = cleanedTokens + tempKeyList.size();
            tempKeyList.clear();
        }
        if (cleanedTokens > 0) {
            LOGGER.trace("cleaner thread removed " + cleanedTokens + " tokens in " + TimeDuration.fromCurrent(startTime).asCompactString());
        }
    }

    private void removeTokenFormStorage(final String tokenKey)
            throws PwmUnrecoverableException, PwmOperationalException
    {
        switch (storageMethod) {
            case STORE_PWMDB:
                pwmDB.remove(PwmDB.DB.TOKENS,tokenKey);
                break;

            case STORE_DB:
                databaseAccessor.remove(DatabaseAccessor.TABLE.TOKENS,tokenKey);
                break;
        }
    }

    private List<String> discoverPurgeableTokenKeys(final int maxCount)
            throws PwmUnrecoverableException, PwmOperationalException
    {
        final List<String> returnList = new ArrayList<String>();
        Iterator<String> keyIterator = null;

        try {
            switch (storageMethod) {
                case STORE_PWMDB:
                    keyIterator = pwmDB.iterator(PwmDB.DB.TOKENS);
                    break;

                case STORE_DB:
                    keyIterator = databaseAccessor.iterator(DatabaseAccessor.TABLE.TOKENS);
                    break;

            }

            while (status() == STATUS.OPEN && returnList.size() < maxCount && keyIterator.hasNext()) {
                final String loopKey = keyIterator.next();
                final TokenPayload loopInfo = retrieveStoredToken(loopKey);
                if (loopInfo != null) {
                    if (testIfTokenIsPurgable(loopInfo)) {
                        returnList.add(loopKey);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("unexpected error while cleaning expired stored tokens: " + e.getMessage());
        } finally {
            if (keyIterator != null && storageMethod == StorageMethod.STORE_PWMDB) {
                try {pwmDB.returnIterator(PwmDB.DB.TOKENS); } catch (Exception e) {LOGGER.error("unexpected error returning pwmDB token DB iterator: " + pwmDB);}
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
        private boolean used;

        public TokenPayload(final String name, final Map<String,String> payloadData) {
            this.issueDate = new Date();
            this.payloadData = payloadData;
            this.name = name;
            this.used = false;
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

        public boolean isUsed() {
            return used;
        }

        public void setUsed(final boolean used) {
            this.used = used;
        }
    }

    private class CleanerTask extends TimerTask {
        public void run() {
            try {
                purgeOutdatedTokens();
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
            switch (storageMethod) {
                case STORE_PWMDB:
                    return pwmDB.size(PwmDB.DB.TOKENS);

                case STORE_DB:
                    return databaseAccessor.size(PwmDB.DB.TOKENS);
            }
        } catch (Exception e) {
            LOGGER.error("unexpected error reading size of token storage table: " + e.getMessage());
        }

        return -1;
    }
}
