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

package password.pwm.token;

import com.google.gson.Gson;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.PwmService;
import password.pwm.PwmSession;
import password.pwm.bean.UserIdentity;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.MessageSendMethod;
import password.pwm.config.option.TokenStorageMethod;
import password.pwm.error.*;
import password.pwm.event.AuditEvent;
import password.pwm.health.HealthRecord;
import password.pwm.health.HealthStatus;
import password.pwm.util.Helper;
import password.pwm.util.PwmLogger;
import password.pwm.util.PwmRandom;
import password.pwm.util.TimeDuration;
import password.pwm.util.localdb.LocalDB;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.util.*;

/**
 * This PWM service is responsible for reading/writing tokens used for forgotten password,
 * new user registration, account activation, and other functions.  Several implementations
 * of the backing storage method are available.
 *
 * @author jrivard@gmail.com
 */
public class TokenService implements PwmService {

    private static final PwmLogger LOGGER = PwmLogger.getLogger(TokenService.class);

    private static final long MAX_CLEANER_INTERVAL_MS = 24 * 60 * 60 * 1000; // one day
    private static final long MIN_CLEANER_INTERVAL_MS = 5 * 60 * 1000; // 5 minutes

    private Timer timer;

    private PwmApplication pwmApplication;
    private Configuration configuration;
    private TokenStorageMethod storageMethod;
    private long maxTokenAgeMS;
    private long maxTokenPurgeAgeMS;
    private TokenMachine tokenMachine;
    private SecretKey secretKey;
    private long counter;


    private STATUS status = STATUS.NEW;

    private ErrorInformation errorInformation = null;

    public TokenService()
            throws PwmOperationalException
    {
    }

    public synchronized TokenPayload createTokenPayload(final String name, final Map<String, String> data, final UserIdentity userIdentity, final Set<String> dest) {
        final long count = counter++;
        final StringBuilder guid = new StringBuilder();
        try {
            guid.append(Helper.md5sum(pwmApplication.getInstanceID() + pwmApplication.getStartupTime().toString()));
            guid.append("-");
            guid.append(count);
        } catch (Exception e) {
            LOGGER.error("error making payload guid: " + e.getMessage(),e);
        }
        return new TokenPayload(name, data, userIdentity, dest, guid.toString());
    }

    public void init(final PwmApplication pwmApplication)
            throws PwmException
    {
        LOGGER.trace("opening");
        status = STATUS.OPENING;

        this.pwmApplication = pwmApplication;
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
                    tokenMachine = new PwmDBTokenMachine(this, pwmApplication.getLocalDB());
                    break;

                case STORE_DB:
                    tokenMachine = new DBTokenMachine(this, pwmApplication.getDatabaseAccessor());
                    break;

                case STORE_CRYPTO:
                    tokenMachine = new CryptoTokenMachine(this);
                    break;

                case STORE_LDAP:
                    tokenMachine = new LdapTokenMachine(this, pwmApplication);
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

        final String counterString = pwmApplication.readAppAttribute(PwmApplication.AppAttribute.TOKEN_COUNTER);
        try {
            counter = Long.parseLong(counterString);
        } catch (Exception e) {
            /* noop */
        }

        status = STATUS.OPEN;
        LOGGER.debug("open");
    }

    public boolean supportsName() {
        return tokenMachine.supportsName();
    }

    public String generateNewToken(final TokenPayload tokenPayload, final PwmSession pwmSession)
            throws PwmUnrecoverableException, PwmOperationalException
    {
        checkStatus();

        final String tokenKey;
        try {
            tokenKey = tokenMachine.generateToken(tokenPayload);
            tokenMachine.storeToken(tokenKey, tokenPayload);
        } catch (PwmException e) {
            final String errorMsg = "unexpected error trying to store token in datastore: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(e.getError(),errorMsg);
            throw new PwmOperationalException(errorInformation);
        }

        pwmApplication.getAuditManager().submit(pwmApplication.getAuditManager().createUserAuditRecord(
                AuditEvent.TOKEN_ISSUED,
                tokenPayload.getUserIdentity(),
                pwmSession,
                Helper.getGson().toJson(tokenPayload)
        ));

        return tokenKey;
    }


    public void markTokenAsClaimed(final String tokenKey, final PwmSession pwmSession) throws PwmUnrecoverableException {

        final TokenPayload tokenPayload;
        try {
            tokenPayload = retrieveTokenData(tokenKey);
        } catch (PwmOperationalException e) {
            return;
        }

        if (tokenPayload == null || tokenPayload.getUserIdentity() == null) {
            return;
        }

        pwmApplication.getAuditManager().submit(pwmApplication.getAuditManager().createUserAuditRecord(
                AuditEvent.TOKEN_CLAIMED,
                tokenPayload.getUserIdentity(),
                pwmSession,
                Helper.getGson().toJson(tokenPayload)
        ));
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
            if (e.getError() == PwmError.ERROR_TOKEN_EXPIRED) {
                throw new PwmOperationalException(e.getErrorInformation());
            }
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

        if (storageMethod == TokenStorageMethod.STORE_LDAP) {
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
        final Date issueDate = theToken.getDate();
        if (issueDate == null) {
            LOGGER.error("retrieved token has no issueDate, marking as expired: " + Helper.getGson().toJson(theToken));
            return true;
        }
        final TimeDuration duration = new TimeDuration(issueDate,new Date());
        return duration.isLongerThan(maxTokenAgeMS);
    }

    private boolean testIfTokenIsPurgable(final TokenPayload theToken) {
        if (theToken == null) {
            return false;
        }
        final Date issueDate = theToken.getDate();
        if (issueDate == null) {
            LOGGER.error("retrieved token has no issueDate, marking as purgable: " + Helper.getGson().toJson(theToken));
            return true;
        }
        final TimeDuration duration = new TimeDuration(issueDate,new Date());
        return duration.isLongerThan(maxTokenPurgeAgeMS);
    }


    void purgeOutdatedTokens() throws
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
            if (keyIterator != null && storageMethod == TokenStorageMethod.STORE_LOCALDB) {
                try {((LocalDB.LocalDBIterator)keyIterator).close(); } catch (Exception e) {LOGGER.error("unexpected error returning LocalDB token DB iterator: " + e.getMessage());}
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

    String makeUniqueTokenForMachine(final TokenMachine machine)
            throws PwmUnrecoverableException, PwmOperationalException {
        String tokenKey = null;
        int attempts = 0;
        while (tokenKey == null && attempts < PwmConstants.TOKEN_MAX_UNIQUE_CREATE_ATTEMPTS) {
            tokenKey = makeRandomCode(configuration);
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

    static String makeTokenHash(final String tokenKey) throws PwmUnrecoverableException {
        try {
            return Helper.md5sum(tokenKey) + "-hash";
        } catch (IOException e) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_UNKNOWN,"unexpected IOException generating md5sum of tokenKey: " + e.getMessage()));
        }
    }

    private static boolean tokensAreUsedInConfig(final Configuration configuration) {
        if (configuration.readSettingAsBoolean(PwmSetting.NEWUSER_EMAIL_VERIFICATION) &&
                configuration.readSettingAsBoolean(PwmSetting.NEWUSER_ENABLE)) {
            return true;
        }

        if (configuration.readSettingAsBoolean(PwmSetting.ACTIVATE_USER_ENABLE) &&
                MessageSendMethod.NONE != configuration.readSettingAsTokenSendMethod(PwmSetting.ACTIVATE_TOKEN_SEND_METHOD)) {
            return true;
        }

        if (configuration.readSettingAsBoolean(PwmSetting.CHALLENGE_ENABLE) &&
                MessageSendMethod.NONE != configuration.readSettingAsTokenSendMethod(PwmSetting.CHALLENGE_TOKEN_SEND_METHOD)) {
            return true;
        }

        return false;
    }

    String toEncryptedString(final TokenPayload tokenPayload)
            throws PwmUnrecoverableException, PwmOperationalException
    {
        final Gson gson = Helper.getGson();
        final String jsonPayload = gson.toJson(tokenPayload);
        return Helper.SimpleTextCrypto.encryptValue(jsonPayload, secretKey, true);
    }

    TokenPayload fromEncryptedString(final String inputString)
            throws PwmOperationalException, PwmUnrecoverableException
    {
        final String deWhiteSpacedToken = inputString.replaceAll("\\s","");
        final String decryptedString = Helper.SimpleTextCrypto.decryptValue(deWhiteSpacedToken, secretKey, true);
        final Gson gson = Helper.getGson();
        return gson.fromJson(decryptedString,TokenPayload.class);
    }

}
