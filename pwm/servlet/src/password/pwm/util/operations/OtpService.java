/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2014 The PWM Project
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

import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.util.internal.Base64Util;
import org.apache.commons.codec.binary.Base32;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.PwmService;
import password.pwm.PwmSession;
import password.pwm.bean.UserIdentity;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.config.option.OTPStorageFormat;
import password.pwm.error.*;
import password.pwm.health.HealthRecord;
import password.pwm.ldap.LdapOperationsHelper;
import password.pwm.util.PwmLogger;
import password.pwm.util.PwmRandom;
import password.pwm.util.TimeDuration;
import password.pwm.util.operations.otp.DbOtpOperator;
import password.pwm.util.operations.otp.LdapOtpOperator;
import password.pwm.util.operations.otp.LocalDbOtpOperator;
import password.pwm.util.operations.otp.OtpOperator;
import password.pwm.util.otp.OTPUserRecord;
import password.pwm.util.otp.PasscodeGenerator;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * @author Menno Pieters, Jason D. Rivard
 */
public class OtpService implements PwmService {

    private static final PwmLogger LOGGER = PwmLogger.getLogger(OtpService.class);

    private final Map<DataStorageMethod, OtpOperator> operatorMap = new EnumMap<DataStorageMethod, OtpOperator>(DataStorageMethod.class);
    private PwmApplication pwmApplication;

    public OtpService() {
    }

    public boolean validateToken(
            final PwmSession pwmSession,
            final UserIdentity userIdentity,
            final OTPUserRecord otpUserRecord,
            final String userInput,
            final boolean allowRecoveryCodes
    )
            throws PwmOperationalException, ChaiUnavailableException, PwmUnrecoverableException
    {
        boolean otpCorrect = false;
        try {
            final Base32 base32 = new Base32();
            final byte[] rawSecret = base32.decode(otpUserRecord.getSecret());
            final Mac mac = Mac.getInstance("HMACSHA1");
            mac.init(new SecretKeySpec(rawSecret, ""));
            final PasscodeGenerator generator = new PasscodeGenerator(mac, PwmConstants.OTP_TOKEN_LENGTH, PwmConstants.TOTP_INTERVAL);
            switch (otpUserRecord.getType()) {
                case TOTP:
                    otpCorrect = generator.verifyTimeoutCode(userInput, PwmConstants.TOTP_PAST_INTERVALS, PwmConstants.TOTP_FUTURE_INTERVALS);
                case HOTP:
                    /* Not yet implemented */
                    break;
            }
        } catch (Exception e) {
            LOGGER.error("error checking otp secret: " + e.getMessage());
        }

        if (!otpCorrect && allowRecoveryCodes && otpUserRecord.getRecoveryCodes() != null && otpUserRecord.getRecoveryInfo() != null) {
            final OTPUserRecord.RecoveryInfo recoveryInfo = otpUserRecord.getRecoveryInfo();
            final String userHashedInput = doRecoveryHash(userInput, recoveryInfo);
            for (final OTPUserRecord.RecoveryCode code : otpUserRecord.getRecoveryCodes()) {
                if (code.getHashCode().equals(userInput) || code.getHashCode().equals(userHashedInput)) {
                    if (code.isUsed()) {
                        throw new PwmOperationalException(PwmError.ERROR_OTP_RECOVERY_USED,
                                "recovery code has been previously used");
                    }

                    code.setUsed(true);
                    pwmApplication.getOtpService().writeOTPUserConfiguration(null, userIdentity, otpUserRecord);
                    otpCorrect = true;
                }
            }
        }

        return otpCorrect;
    }

    public static List<String> createRawRecoveryCodes(int numRecoveryCodes) throws NoSuchAlgorithmException, InvalidKeyException {
        final List<String> recoveryCodes = new ArrayList<String>();
        int length = PwmConstants.OTP_RECOVERY_TOKEN_LENGTH;
        for (int n = 0; n < numRecoveryCodes; n++) {
            String code = PwmRandom.getInstance().alphaNumericString("0123456789", length);
            recoveryCodes.add(code);
        }
        return recoveryCodes;
    }

    public static List<String> initializeUserRecord(
            final OTPUserRecord otpUserRecord,
            String identifier,
            OTPUserRecord.Type otpType,
            OTPStorageFormat storageFormat,
            int recoveryCodeCount
    )
            throws NoSuchAlgorithmException, InvalidKeyException
    {
        otpUserRecord.setIdentifier(identifier);

        final Base32 base32 = new Base32();
        final byte[] rawSecret = generateSecret();
        otpUserRecord.setSecret(new String(base32.encode(rawSecret)));
        switch (otpType) {
            case HOTP:
                otpUserRecord.setAttemptCount(PwmRandom.getInstance().nextLong());
                otpUserRecord.setType(OTPUserRecord.Type.HOTP);
                break;

            case TOTP:
                otpUserRecord.setType(OTPUserRecord.Type.TOTP);
        }
        final List<String> rawRecoveryCodes;
        if (storageFormat.supportsRecoveryCodes()) {
            rawRecoveryCodes = createRawRecoveryCodes(recoveryCodeCount);
            final List<OTPUserRecord.RecoveryCode> recoveryCodeList = new ArrayList<OTPUserRecord.RecoveryCode>();
            final OTPUserRecord.RecoveryInfo recoveryInfo = new OTPUserRecord.RecoveryInfo();
            if (storageFormat.supportsHashedRecoveryCodes()) {
                LOGGER.debug("Hashing the recovery codes");
                recoveryInfo.setSalt(PwmRandom.getInstance().alphaNumericString(32));
                recoveryInfo.setHashCount(PwmConstants.OTP_RECOVERY_HASH_COUNT);
                recoveryInfo.setHashMethod(PwmConstants.OTP_RECOVERY_HASH_METHOD);
            } else {
                LOGGER.debug("Not hashing the recovery codes");
                recoveryInfo.setSalt(null);
                recoveryInfo.setHashCount(0);
                recoveryInfo.setHashMethod(null);
            }
            otpUserRecord.setRecoveryInfo(recoveryInfo);
            for (final String rawCode : rawRecoveryCodes) {
                final String hashedCode;
                if (storageFormat.supportsHashedRecoveryCodes()) {
                    hashedCode = doRecoveryHash(rawCode, recoveryInfo);
                } else {
                    hashedCode = rawCode;
                }
                final OTPUserRecord.RecoveryCode recoveryCode = new OTPUserRecord.RecoveryCode();
                recoveryCode.setHashCode(hashedCode);
                recoveryCode.setUsed(false);
                recoveryCodeList.add(recoveryCode);
            }
            otpUserRecord.setRecoveryCodes(recoveryCodeList);
        } else {
            rawRecoveryCodes = new ArrayList<String>();
        }
        return rawRecoveryCodes;
    }

    private static byte[] generateSecret() {
        byte[] secArray = new byte[10];
        PwmRandom.getInstance().nextBytes(secArray);
        return secArray;
    }

    public static String doRecoveryHash(
            final String input,
            final OTPUserRecord.RecoveryInfo recoveryInfo
    )
            throws IllegalStateException
    {
        final String algorithm = PwmConstants.OTP_RECOVERY_HASH_METHOD;
        final MessageDigest md;
        try {
            md = MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("unable to load " + algorithm + " message digest algorithm: " + e.getMessage());
        }

        final String raw = recoveryInfo.getSalt() == null
                ? input.trim()
                : recoveryInfo.getSalt().trim() + input.trim();

        final int hashCount = recoveryInfo.getHashCount();
        byte[] hashedBytes = raw.getBytes();
        for (int i = 0; i < hashCount; i++) {
            hashedBytes = md.digest(hashedBytes);
        }
        return Base64Util.encodeBytes(hashedBytes);
    }

    @Override
    public STATUS status() {
        return STATUS.OPEN;
    }

    @Override
    public void init(PwmApplication pwmApplication) throws PwmException {
        this.pwmApplication = pwmApplication;
        operatorMap.put(DataStorageMethod.LDAP, new LdapOtpOperator(pwmApplication));
        operatorMap.put(DataStorageMethod.LOCALDB, new LocalDbOtpOperator(pwmApplication.getLocalDB(), pwmApplication.getConfig()));
        operatorMap.put(DataStorageMethod.DB, new DbOtpOperator(pwmApplication));
    }

    @Override
    public void close() {
        for (final OtpOperator operator : operatorMap.values()) {
            operator.close();
        }
        operatorMap.clear();
    }

    @Override
    public List<HealthRecord> healthCheck() {
        return Collections.emptyList();
    }

    public OTPUserRecord readOTPUserConfiguration(final UserIdentity userIdentity)
            throws PwmUnrecoverableException, ChaiUnavailableException
    {
        OTPUserRecord otpConfig = null;
        final Configuration config = pwmApplication.getConfig();
        final Date methodStartTime = new Date();

        final List<DataStorageMethod> otpSecretStorageLocations = config.getOtpSecretStorageLocations(
                PwmSetting.OTP_SECRET_READ_PREFERENCE);

        if (otpSecretStorageLocations != null) {
            final String userGUID = readGuidIfNeeded(pwmApplication, otpSecretStorageLocations, userIdentity);
            final Iterator<DataStorageMethod> locationIterator = otpSecretStorageLocations.iterator();
            while (otpConfig == null && locationIterator.hasNext()) {
                final DataStorageMethod location = locationIterator.next();
                final OtpOperator operator = operatorMap.get(location);
                if (operator != null) {
                    try {
                        otpConfig = operator.readOtpUserConfiguration(userIdentity, userGUID);
                    } catch (Exception e) {
                        LOGGER.error("unexpected error reading stored otp configuration from " + location + " for user " + userIdentity + ", error: " + e.getMessage());
                    }
                } else {
                    LOGGER.warn(String.format("Storage location %s not implemented", location.toString()));
                }
            }
        }

        LOGGER.trace("readOTPUserConfiguration completed in " + TimeDuration.fromCurrent(
                methodStartTime).asCompactString());
        return otpConfig;
    }

    public void writeOTPUserConfiguration(
            final PwmSession pwmSession,
            final UserIdentity userIdentity,
            final OTPUserRecord otp
    )
            throws PwmOperationalException, ChaiUnavailableException, PwmUnrecoverableException
    {
        int attempts = 0, successes = 0;
        final Configuration config = pwmApplication.getConfig();
        final List<DataStorageMethod> otpSecretStorageLocations = config.getOtpSecretStorageLocations(
                PwmSetting.OTP_SECRET_READ_PREFERENCE);
        final String userGUID = readGuidIfNeeded(pwmApplication, otpSecretStorageLocations, userIdentity);

        final StringBuilder errorMsgs = new StringBuilder();
        if (otpSecretStorageLocations != null) {
            for (DataStorageMethod otpSecretStorageLocation : otpSecretStorageLocations) {
                attempts++;
                final OtpOperator operator = operatorMap.get(otpSecretStorageLocation);
                if (operator != null) {
                    try {
                        operator.writeOtpUserConfiguration(pwmSession, userIdentity, userGUID, otp);
                        successes++;
                    } catch (PwmUnrecoverableException e) {
                        LOGGER.error(pwmSession, "error writing to " + otpSecretStorageLocation + ", error: " + e.getMessage());
                        errorMsgs.append(otpSecretStorageLocation).append(" error: ").append(e.getMessage());
                    }
                } else {
                    LOGGER.warn(pwmSession, String.format("storage location %s not implemented", otpSecretStorageLocation.toString()));
                }
            }
        }

        if (attempts == 0) {
            final String errorMsg = "no OTP secret save methods are available or configured";
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_WRITING_OTP_SECRET, errorMsg);
            throw new PwmOperationalException(errorInfo);
        }

        if (attempts != successes) { // should be impossible to get here, but just in case.
            final String errorMsg = "OTP secret write only partially successful; attempts=" + attempts + ", successes=" + successes + ", errors: " + errorMsgs.toString();
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_WRITING_OTP_SECRET, errorMsg);
            throw new PwmOperationalException(errorInfo);
        }
    }

    public void clearOTPUserConfiguration(
            final PwmSession pwmSession,
            final UserIdentity userIdentity
    )
            throws PwmOperationalException, ChaiUnavailableException, PwmUnrecoverableException
    {
        LOGGER.trace(pwmSession, "beginning clear otp user configuration");

        int attempts = 0, successes = 0;
        final Configuration config = pwmApplication.getConfig();
        final List<DataStorageMethod> otpSecretStorageLocations = config.getOtpSecretStorageLocations(PwmSetting.OTP_SECRET_READ_PREFERENCE);

        final String userGUID = readGuidIfNeeded(pwmApplication, otpSecretStorageLocations, userIdentity);

        final StringBuilder errorMsgs = new StringBuilder();
        if (otpSecretStorageLocations != null) {
            for (DataStorageMethod otpSecretStorageLocation : otpSecretStorageLocations) {
                attempts++;
                final OtpOperator operator = operatorMap.get(otpSecretStorageLocation);
                if (operator != null) {
                    try {
                        operator.clearOtpUserConfiguration(pwmSession, userIdentity, userGUID);
                        successes++;
                    } catch (PwmUnrecoverableException e) {
                        LOGGER.error(pwmSession, "error clearing " + otpSecretStorageLocation + ", error: " + e.getMessage());
                        errorMsgs.append(otpSecretStorageLocation).append(" error: ").append(e.getMessage());
                    }
                } else {
                    LOGGER.warn(pwmSession, String.format("Storage location %s not implemented", otpSecretStorageLocation.toString()));
                }
            }
        }

        if (attempts == 0) {
            final String errorMsg = "no OTP secret clear methods are available or configured";
            /* TODO: replace error message */
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_WRITING_OTP_SECRET, errorMsg);
            throw new PwmOperationalException(errorInfo);
        }

        if (attempts != successes) { // should be impossible to get here, but just in case.
            final String errorMsg = "OTP secret clearing only partially successful; attempts=" + attempts + ", successes=" + successes + ", error: " + errorMsgs.toString();
            /* TODO: replace error message */
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_WRITING_OTP_SECRET, errorMsg);
            throw new PwmOperationalException(errorInfo);
        }
    }

    /**
     * Check whether the user needs to setup an OTP secret.
     * @param pwmSession
     * @param theUser
     * @param otpConfig
     * @return true if the record is null or does not contain a shared secret; otherwise false.
     * @throws ChaiUnavailableException
     * @throws PwmUnrecoverableException
     */
    public boolean checkIfOtpSetupNeeded(
            final PwmSession pwmSession,
            final UserIdentity theUser,
            final OTPUserRecord otpConfig
    )
            throws ChaiUnavailableException, PwmUnrecoverableException {
        OTPUserRecord otp = readOTPUserConfiguration(theUser);
        return (otp == null || otp.getSecret() == null);
    }

    public boolean supportsRecoveryCodes() {
        Configuration config = pwmApplication.getConfig();
        final OTPStorageFormat format = config.readSettingAsEnum(PwmSetting.OTP_SECRET_STORAGEFORMAT,OTPStorageFormat.class);
        return format.supportsRecoveryCodes();
    }

    public boolean supportsHashedRecoveryCodes() {
        Configuration config = pwmApplication.getConfig();
        final OTPStorageFormat format = config.readSettingAsEnum(PwmSetting.OTP_SECRET_STORAGEFORMAT,OTPStorageFormat.class);
        return format.supportsHashedRecoveryCodes();
    }

    public ServiceInfo serviceInfo()
    {
        return new ServiceInfo(Collections.<DataStorageMethod>emptyList());
    }

    private static String readGuidIfNeeded(
            final PwmApplication pwmApplication,
            final Collection<DataStorageMethod> otpSecretStorageLocations,
            final UserIdentity userIdentity

    )
            throws ChaiUnavailableException, PwmUnrecoverableException
    {
        final String userGUID;
        if (otpSecretStorageLocations.contains(DataStorageMethod.DB) || otpSecretStorageLocations.contains(
                DataStorageMethod.LOCALDB)) {
            userGUID = LdapOperationsHelper.readLdapGuidValue(pwmApplication, userIdentity, false);
        } else {
            userGUID = null;
        }
        return userGUID;
    }
}
