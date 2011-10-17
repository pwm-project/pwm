package password.pwm;

import com.google.gson.Gson;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.error.*;
import password.pwm.health.HealthRecord;
import password.pwm.util.PwmLogger;
import password.pwm.util.PwmRandom;
import password.pwm.util.TimeDuration;
import password.pwm.util.db.DatabaseAccessor;
import password.pwm.util.pwmdb.PwmDB;

import java.io.Serializable;
import java.util.*;

public class TokenManager implements PwmService {

    private static PwmLogger LOGGER = PwmLogger.getLogger(TokenManager.class);

    private static long MAX_CLEANER_INTERVAL_MS = 24 * 60 * 60 * 1000; // one day
    private static long MIN_CLEANER_INTERVAL_MS = 5 * 60 * 1000; // 5 minutes

    private enum StorageMethod {
        STORE_PWMDB,
        STORE_DB,
    }

    private final Timer timer;

    private final Configuration configuration;
    private final PwmDB pwmDB;
    private final DatabaseAccessor databaseAccessor;
    private final StorageMethod storageMethod;
    private final long maxTokenAgeMS;

    private STATUS status = STATUS.NEW;

    public TokenManager(Configuration configuration, final PwmDB pwmDB, DatabaseAccessor databaseAccessor)
            throws PwmOperationalException
    {
        LOGGER.trace("opening");
        status = STATUS.OPENING;

        this.configuration = configuration;
        this.pwmDB = pwmDB;
        this.databaseAccessor = databaseAccessor;
        try {
            storageMethod = StorageMethod.valueOf(configuration.readSettingAsString(PwmSetting.TOKEN_STORAGEMETHOD));
        } catch (Exception e) {
            final String errorMsg = "Unknown storage method specified: " + configuration.readSettingAsString(PwmSetting.TOKEN_STORAGEMETHOD);
            throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_INVALID_CONFIG,errorMsg));
        }
        try {
            maxTokenAgeMS = configuration.readSettingAsLong(PwmSetting.TOKEN_LIFETIME) * 1000;
        } catch (Exception e) {
            final String errorMsg = "Unable to parse max token age value";
            throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_INVALID_CONFIG,errorMsg));
        }

        timer = new Timer("pwm-TokenManager",true);
        final TimerTask cleanerTask = new CleanerTask();

        final long cleanerFrequency = (maxTokenAgeMS*0.5) > MAX_CLEANER_INTERVAL_MS ? MAX_CLEANER_INTERVAL_MS : (maxTokenAgeMS*0.5) < MIN_CLEANER_INTERVAL_MS ? MIN_CLEANER_INTERVAL_MS : (long)(maxTokenAgeMS*0.5);
        timer.schedule(cleanerTask, 1000, cleanerFrequency);
        status = STATUS.OPEN;
        LOGGER.debug("open");
    }

    public String generateNewToken(final TokenPayload tokenPayload)
            throws PwmOperationalException
    {
        checkStatus();
        try {
            String tokenKey = null;
            int attempts = 0;
            while (tokenKey == null && attempts < 100) {
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

    public TokenPayload retrieveTokenData(final String tokenKey)
            throws PwmOperationalException
    {
        checkStatus();
        try {
            final TokenPayload storedToken = retrieveStoredToken(tokenKey);
            if (storedToken != null) {
                return storedToken;
            }
        } catch (PwmException e) {
            final String errorMsg = "unexpected error trying to retrieve token from datastore: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(e.getError(),errorMsg);
            throw new PwmOperationalException(errorInformation);
        }
        return null;
    }

    public STATUS status() {
        return status;
    }

    public void close() {
        timer.cancel();
        status = STATUS.CLOSED;
    }

    public List<HealthRecord> healthCheck() {
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
                final Gson gson = new Gson();
                returnToken = gson.fromJson(storedRawValue,TokenPayload.class);
                if (testIfTokenIsExpired(returnToken)) {
                    returnToken = null;
                }
            }
        }

        if (returnToken != null) {
            removeTokenFormStorage(tokenKey);
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

    private void cleanupOutdatedTokens() throws
            PwmUnrecoverableException, PwmOperationalException
    {
        final long startTime = System.currentTimeMillis();
        int cleanedTokens = 0;
        List<String> tempKeyList = new ArrayList<String>();
        tempKeyList.addAll(discoverOutdatedTokenKeys(100));
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

    private List<String> discoverOutdatedTokenKeys(final int maxCount)
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
                    if (testIfTokenIsExpired(loopInfo)) {
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

        public TokenPayload(final String name, final Map<String,String> payloadData) {
            this.issueDate = new Date();
            this.payloadData = payloadData;
            this.name = name;

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
    }

    private class CleanerTask extends TimerTask {
        public void run() {
            try {
                cleanupOutdatedTokens();
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
        } catch (PwmOperationalException e) {
            LOGGER.error("unexpected error reading size of token storage table: " + e.getMessage());
        }

        return -1;
    }
}
