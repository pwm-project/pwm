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

package password.pwm.token;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.PwmService;
import password.pwm.bean.*;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.config.option.MessageSendMethod;
import password.pwm.config.option.TokenStorageMethod;
import password.pwm.config.profile.ForgottenPasswordProfile;
import password.pwm.config.profile.NewUserProfile;
import password.pwm.error.*;
import password.pwm.event.AuditEvent;
import password.pwm.health.HealthMessage;
import password.pwm.health.HealthRecord;
import password.pwm.http.PwmSession;
import password.pwm.ldap.auth.SessionAuthenticator;
import password.pwm.util.*;
import password.pwm.util.intruder.RecordType;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;
import password.pwm.util.operations.PasswordUtility;
import password.pwm.util.stats.Statistic;
import password.pwm.util.stats.StatisticsManager;

import javax.crypto.SecretKey;
import java.util.*;

/**
 * This PWM service is responsible for reading/writing tokens used for forgotten password,
 * new user registration, account activation, and other functions.  Several implementations
 * of the backing storage method are available.
 *
 * @author jrivard@gmail.com
 */
public class TokenService implements PwmService {

    private static final PwmLogger LOGGER = PwmLogger.forClass(TokenService.class);

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

    private ServiceInfo serviceInfo = new ServiceInfo(Collections.<DataStorageMethod>emptyList());
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
            guid.append(SecureHelper.md5sum(pwmApplication.getInstanceID() + pwmApplication.getStartupTime().toString()));
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
            maxTokenPurgeAgeMS = maxTokenAgeMS + Long.parseLong(configuration.readAppProperty(AppProperty.TOKEN_REMOVAL_DELAY_MS));
        } catch (Exception e) {
            final String errorMsg = "unable to parse max token age value: " + e.getMessage();
            errorInformation = new ErrorInformation(PwmError.ERROR_INVALID_CONFIG,errorMsg);
            status = STATUS.CLOSED;
            throw new PwmOperationalException(errorInformation);
        }

        try {
            secretKey = configuration.getSecurityKey();
            DataStorageMethod usedStorageMethod = null;
            switch (storageMethod) {
                case STORE_LOCALDB:
                    tokenMachine = new LocalDBTokenMachine(this, pwmApplication.getLocalDB());
                    usedStorageMethod = DataStorageMethod.LOCALDB;
                    break;

                case STORE_DB:
                    tokenMachine = new DBTokenMachine(this, pwmApplication.getDatabaseAccessor());
                    usedStorageMethod = DataStorageMethod.DB;
                    break;

                case STORE_CRYPTO:
                    tokenMachine = new CryptoTokenMachine(this);
                    usedStorageMethod = DataStorageMethod.CRYPTO;
                    break;

                case STORE_LDAP:
                    tokenMachine = new LdapTokenMachine(this, pwmApplication);
                    usedStorageMethod = DataStorageMethod.LDAP;
                    break;
            }
            serviceInfo = new ServiceInfo(Collections.singletonList(usedStorageMethod));
        } catch (PwmException e) {
            final String errorMsg = "unable to start token manager: " + e.getErrorInformation().getDetailedErrorMsg();
            final ErrorInformation newErrorInformation = new ErrorInformation(e.getError(), errorMsg);
            errorInformation = newErrorInformation;
            LOGGER.error(newErrorInformation.toDebugStr());
            status = STATUS.CLOSED;
            return;
        }

        final String threadName = Helper.makeThreadName(pwmApplication,this.getClass()) + " timer";
        timer = new Timer(threadName, true);
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

    public String generateNewToken(final TokenPayload tokenPayload, final SessionLabel sessionLabel)
            throws PwmUnrecoverableException, PwmOperationalException
    {
        checkStatus();

        final String tokenKey;
        try {
            tokenKey = tokenMachine.generateToken(sessionLabel, tokenPayload);
            tokenMachine.storeToken(tokenKey, tokenPayload);
        } catch (PwmException e) {
            final String errorMsg = "unexpected error trying to store token in datastore: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(e.getError(),errorMsg);
            throw new PwmOperationalException(errorInformation);
        }

        pwmApplication.getAuditManager().submit(pwmApplication.getAuditManager().createUserAuditRecord(
                AuditEvent.TOKEN_ISSUED,
                tokenPayload.getUserIdentity(),
                sessionLabel,
                JsonUtil.serialize(tokenPayload)
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
                pwmSession.getLabel(),
                JsonUtil.serialize(tokenPayload)
        ));

        StatisticsManager.incrementStat(pwmApplication, Statistic.TOKENS_PASSSED);
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
            if (e.getError() == PwmError.ERROR_TOKEN_EXPIRED || e.getError() == PwmError.ERROR_TOKEN_INCORRECT || e.getError() == PwmError.ERROR_TOKEN_MISSING_CONTACT) {
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
        final List<HealthRecord> returnRecords = new ArrayList<>();

        if (tokensAreUsedInConfig(configuration)) {
            if (errorInformation != null) {
                returnRecords.add(HealthRecord.forMessage(HealthMessage.CryptoTokenWithNewUserVerification,errorInformation.toDebugStr()));
            }
        }

        if (storageMethod == TokenStorageMethod.STORE_LDAP) {
            if (configuration.readSettingAsBoolean(PwmSetting.NEWUSER_ENABLE)) {
                if (configuration.readSettingAsBoolean(PwmSetting.NEWUSER_EMAIL_VERIFICATION)) {
                    returnRecords.add(HealthRecord.forMessage(HealthMessage.CryptoTokenWithNewUserVerification));
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
            LOGGER.error("retrieved token has no issueDate, marking as expired: " + JsonUtil.serialize(theToken));
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
            LOGGER.error("retrieved token has no issueDate, marking as purgable: " + JsonUtil.serialize(theToken));
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
        List<String> tempKeyList = new ArrayList<>();
        final int purgeBatchSize = Integer.parseInt(pwmApplication.getConfig().readAppProperty(AppProperty.TOKEN_PURGE_BATCH_SIZE));
        tempKeyList.addAll(discoverPurgeableTokenKeys(purgeBatchSize));
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
        final List<String> returnList = new ArrayList<>();
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

        return RANDOM.alphaNumericString(RANDOM_CHARS, CODE_LENGTH);
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

    String makeUniqueTokenForMachine(final SessionLabel sessionLabel, final TokenMachine machine)
            throws PwmUnrecoverableException, PwmOperationalException
    {
        String tokenKey = null;
        int attempts = 0;
        final int maxUniqueCreateAttempts = Integer.parseInt(pwmApplication.getConfig().readAppProperty(AppProperty.TOKEN_MAX_UNIQUE_CREATE_ATTEMPTS));
        while (tokenKey == null && attempts < maxUniqueCreateAttempts) {
            tokenKey = makeRandomCode(configuration);
            LOGGER.trace(sessionLabel, "generated new token random code, checking for uniqueness");
            if (machine.retrieveToken(tokenKey) != null) {
                tokenKey = null;
            }
            attempts++;
        }

        if (tokenKey == null) {
            throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_UNKNOWN,"unable to generate a unique token key after " + attempts + " attempts"));
        }

        LOGGER.trace(sessionLabel, "found new, unique, random token value");
        return tokenKey;
    }

    static String makeTokenHash(final String tokenKey) throws PwmUnrecoverableException {
        return SecureHelper.md5sum(tokenKey) + "-hash";
    }

    private static boolean tokensAreUsedInConfig(final Configuration configuration) {
        if (configuration.readSettingAsBoolean(PwmSetting.NEWUSER_ENABLE)) {
            for (final NewUserProfile newUserProfile : configuration.getNewUserProfiles().values()) {
                if (newUserProfile.readSettingAsBoolean(PwmSetting.NEWUSER_EMAIL_VERIFICATION)) {
                    return true;
                }
            }
            return true;
        }

        if (configuration.readSettingAsBoolean(PwmSetting.ACTIVATE_USER_ENABLE) &&
                MessageSendMethod.NONE != configuration.readSettingAsTokenSendMethod(PwmSetting.ACTIVATE_TOKEN_SEND_METHOD)) {
            return true;
        }

        if (configuration.readSettingAsBoolean(PwmSetting.CHALLENGE_ENABLE)) {
            for (final ForgottenPasswordProfile forgottenPasswordProfile : configuration.getForgottenPasswordProfiles().values()) {
                final MessageSendMethod messageSendMethod = forgottenPasswordProfile.readSettingAsEnum(PwmSetting.RECOVERY_TOKEN_SEND_METHOD, MessageSendMethod.class);
                if (messageSendMethod != null && messageSendMethod != MessageSendMethod.NONE) {
                    return true;
                }
            }
        }
        return false;
    }

    String toEncryptedString(final TokenPayload tokenPayload)
            throws PwmUnrecoverableException, PwmOperationalException
    {
        final String jsonPayload = JsonUtil.serialize(tokenPayload);
        return SecureHelper.encryptToString(jsonPayload, secretKey, true);
    }

    TokenPayload fromEncryptedString(final String inputString)
            throws PwmOperationalException, PwmUnrecoverableException
    {
        final String deWhiteSpacedToken = inputString.replaceAll("\\s","");
        try {
            final String decryptedString = SecureHelper.decryptStringValue(deWhiteSpacedToken, secretKey, true);
            return JsonUtil.deserialize(decryptedString, TokenPayload.class);
        } catch (PwmUnrecoverableException e) {
            final String errorMsg = "unable to decrypt user supplied token value: " + e.getErrorInformation().toDebugStr();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_TOKEN_INCORRECT,errorMsg);
            throw new PwmOperationalException(errorInformation);
        }
    }

    public ServiceInfo serviceInfo()
    {
        return serviceInfo;
    }

    public TokenPayload processUserEnteredCode(
            final PwmSession pwmSession,
            final UserIdentity sessionUserIdentity,
            final String tokenName,
            final String userEnteredCode
    )
            throws PwmOperationalException, PwmUnrecoverableException
    {
        try {
            final TokenPayload tokenPayload = processUserEnteredCodeImpl(
                    pwmApplication,
                    pwmSession,
                    sessionUserIdentity,
                    tokenName,
                    userEnteredCode
            );
            if (tokenPayload.getDest() != null) {
                for (final String dest : tokenPayload.getDest()) {
                    pwmApplication.getIntruderManager().clear(RecordType.TOKEN_DEST, dest);
                }
            }
            pwmApplication.getTokenService().markTokenAsClaimed(userEnteredCode, pwmSession);
            return tokenPayload;
        } catch (Exception e) {
            final ErrorInformation errorInformation;
            if (e instanceof PwmException) {
                errorInformation = ((PwmException) e).getErrorInformation();
            } else {
                errorInformation = new ErrorInformation(PwmError.ERROR_TOKEN_INCORRECT, e.getMessage());
            }

            LOGGER.debug(pwmSession, errorInformation.toDebugStr());

            if (sessionUserIdentity != null) {
                SessionAuthenticator sessionAuthenticator = new SessionAuthenticator(pwmApplication, pwmSession);
                sessionAuthenticator.simulateBadPassword(sessionUserIdentity);
                pwmApplication.getIntruderManager().convenience().markUserIdentity(sessionUserIdentity, pwmSession);
            }
            pwmApplication.getIntruderManager().convenience().markAddressAndSession(pwmSession);
            pwmApplication.getStatisticsManager().incrementValue(Statistic.RECOVERY_FAILURES);
            throw new PwmOperationalException(errorInformation);
        }
    }

    private static TokenPayload processUserEnteredCodeImpl(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final UserIdentity sessionUserIdentity,
            final String tokenName,
            final String userEnteredCode
    )
            throws PwmOperationalException
    {
        TokenPayload tokenPayload;
        try {
            tokenPayload = pwmApplication.getTokenService().retrieveTokenData(userEnteredCode);
        } catch (PwmOperationalException e) {
            final String errorMsg = "unexpected error attempting to read token from storage: " + e.getErrorInformation().toDebugStr();
            throw new PwmOperationalException(PwmError.ERROR_TOKEN_INCORRECT,errorMsg);
        }

        if (tokenPayload == null) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_TOKEN_INCORRECT,"token not found");
            throw new PwmOperationalException(errorInformation);
        }

        LOGGER.trace(pwmSession, "retrieved tokenPayload: " + JsonUtil.serialize(tokenPayload));

        if (tokenName != null && pwmApplication.getTokenService().supportsName()) {
            if (!tokenName.equals(tokenPayload.getName()) ) {
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_TOKEN_INCORRECT,"incorrect token/name format");
                throw new PwmOperationalException(errorInformation);
            }
        }

        // check current session identity
        if (tokenPayload.getUserIdentity() != null && sessionUserIdentity != null) {
            if (!tokenPayload.getUserIdentity().equals(sessionUserIdentity)) {
                final String errorMsg = "user in session '" + sessionUserIdentity + "' entered code for user '" + tokenPayload.getUserIdentity()+ "', counting as invalid attempt";
                throw new PwmOperationalException(PwmError.ERROR_TOKEN_INCORRECT,errorMsg);
            }
        }

        // check if password-last-modified is same as when tried to read it before.
        if (tokenPayload.getUserIdentity() != null && tokenPayload.getData() != null && tokenPayload.getData().containsKey(PwmConstants.TOKEN_KEY_PWD_CHG_DATE)) {
            try {
                final Date userLastPasswordChange = PasswordUtility.determinePwdLastModified(
                        pwmApplication,
                        pwmSession.getLabel(),
                        tokenPayload.getUserIdentity());
                final String dateStringInToken = tokenPayload.getData().get(PwmConstants.TOKEN_KEY_PWD_CHG_DATE);
                if (userLastPasswordChange != null && dateStringInToken != null) {
                    final String userChangeString = PwmConstants.DEFAULT_DATETIME_FORMAT.format(userLastPasswordChange);
                    if (!dateStringInToken.equalsIgnoreCase(userChangeString)) {
                        final String errorString = "user password has changed since token issued, token rejected";
                        final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_TOKEN_EXPIRED, errorString);
                        throw new PwmOperationalException(errorInformation);
                    }
                }
            } catch (ChaiUnavailableException | PwmUnrecoverableException e) {
                final String errorMsg = "unexpected error reading user's last password change time while validating token: " + e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_TOKEN_INCORRECT, errorMsg);
                throw new PwmOperationalException(errorInformation);
            }
        }

        LOGGER.debug(pwmSession, "token validation has been passed");
        return tokenPayload;
    }

    public static class TokenSender {
        public static void sendToken(
                final PwmApplication pwmApplication,
                final UserInfoBean userInfoBean,
                final MacroMachine macroMachine,
                final EmailItemBean configuredEmailSetting,
                final MessageSendMethod tokenSendMethod,
                final String emailAddress,
                final String smsNumber,
                final String smsMessage,
                final String tokenKey
        )
                throws PwmUnrecoverableException
        {
            final boolean success;

            try {
                switch (tokenSendMethod) {
                    case NONE:
                        // should never read here
                        LOGGER.error("attempt to send token to destination type 'NONE'");
                        throw new PwmUnrecoverableException(PwmError.ERROR_UNKNOWN);
                    case BOTH:
                        // Send both email and SMS, success if one of both succeeds
                        final boolean suc1 = sendEmailToken(pwmApplication, userInfoBean, macroMachine, configuredEmailSetting, emailAddress, tokenKey);
                        final boolean suc2 = sendSmsToken(pwmApplication, userInfoBean, macroMachine, smsNumber, smsMessage, tokenKey);
                        success = suc1 || suc2;
                        break;
                    case EMAILFIRST:
                        // Send email first, try SMS if email is not available
                        success = sendEmailToken(pwmApplication, userInfoBean, macroMachine, configuredEmailSetting, emailAddress, tokenKey) ||
                                sendSmsToken(pwmApplication, userInfoBean, macroMachine, smsNumber, smsMessage, tokenKey);
                        break;
                    case SMSFIRST:
                        // Send SMS first, try email if SMS is not available
                        success = sendSmsToken(pwmApplication, userInfoBean, macroMachine, smsNumber, smsMessage, tokenKey) ||
                                sendEmailToken(pwmApplication, userInfoBean, macroMachine, configuredEmailSetting, emailAddress, tokenKey);
                        break;
                    case SMSONLY:
                        // Only try SMS
                        success = sendSmsToken(pwmApplication, userInfoBean, macroMachine, smsNumber, smsMessage, tokenKey);
                        break;
                    case EMAILONLY:
                    default:
                        // Only try email
                        success = sendEmailToken(pwmApplication, userInfoBean, macroMachine, configuredEmailSetting, emailAddress, tokenKey);
                        break;
                }
            } catch (ChaiUnavailableException e) {
                throw new PwmUnrecoverableException(PwmError.forChaiError(e.getErrorCode()));
            }

            if (!success) {
                throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_TOKEN_MISSING_CONTACT));
            }
            pwmApplication.getStatisticsManager().incrementValue(Statistic.TOKENS_SENT);
        }

        public static boolean sendEmailToken(
                final PwmApplication pwmApplication,
                final UserInfoBean userInfoBean,
                final MacroMachine macroMachine,
                final EmailItemBean configuredEmailSetting,
                final String toAddress,
                final String tokenKey
        )
                throws PwmUnrecoverableException, ChaiUnavailableException
        {
            if (toAddress == null || toAddress.length() < 1) {
                return false;
            }

            pwmApplication.getIntruderManager().mark(RecordType.TOKEN_DEST, toAddress, null);

            pwmApplication.getEmailQueue().submitEmail(new EmailItemBean(
                    toAddress,
                    configuredEmailSetting.getFrom(),
                    configuredEmailSetting.getSubject(),
                    configuredEmailSetting.getBodyPlain().replace("%TOKEN%", tokenKey),
                    configuredEmailSetting.getBodyHtml().replace("%TOKEN%", tokenKey)
            ), userInfoBean, macroMachine);
            LOGGER.debug("token email added to send queue for " + toAddress);
            return true;
        }

        public static boolean sendSmsToken(
                final PwmApplication pwmApplication,
                final UserInfoBean userInfoBean,
                final MacroMachine macroMachine,
                final String smsNumber,
                final String smsMessage,
                final String tokenKey
        )
                throws PwmUnrecoverableException, ChaiUnavailableException
        {
            final Configuration config = pwmApplication.getConfig();

            if (smsNumber == null || smsNumber.length() < 1) {
                return false;
            }

            final String modifiedMessage = smsMessage.replaceAll("%TOKEN%", tokenKey);

            pwmApplication.getIntruderManager().mark(RecordType.TOKEN_DEST, smsNumber, null);

            pwmApplication.sendSmsUsingQueue(new SmsItemBean(smsNumber, modifiedMessage), macroMachine);
            LOGGER.debug("token SMS added to send queue for " + smsNumber);
            return true;
        }
    }
}
