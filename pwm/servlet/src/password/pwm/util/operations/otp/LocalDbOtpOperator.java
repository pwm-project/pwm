/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package password.pwm.util.operations.otp;

import com.novell.ldapchai.ChaiUser;
import password.pwm.bean.UserIdentity;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.PwmLogger;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBException;
import password.pwm.util.otp.OTPUserConfiguration;

/**
 *
 * @author Menno Pieters, Jason D. Rivard
 */
public class LocalDbOtpOperator extends AbstractOtpOperator {

    private static final PwmLogger LOGGER = PwmLogger.getLogger(LocalDbOtpOperator.class);
    private final LocalDB localDB;

    public LocalDbOtpOperator(LocalDB localDB, Configuration config) {
        this.localDB = localDB;
        setConfig(config);
    }

    @Override
    public OTPUserConfiguration readOtpUserConfiguration(UserIdentity theUser, String userGUID) throws PwmUnrecoverableException {
        LOGGER.trace(String.format("Enter: readOtpUserConfiguration(%s, %s)", theUser, userGUID));
        if (userGUID == null || userGUID.length() < 1) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_MISSING_GUID, "cannot save responses to pwmDB, user does not have a pwmGUID"));
        }

        if (localDB == null || localDB.status() != LocalDB.Status.OPEN) {
            final String errorMsg = "LocalDB is not available, unable to write user responses";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_PWMDB_UNAVAILABLE, errorMsg);
            throw new PwmUnrecoverableException(errorInformation);
        }

        OTPUserConfiguration otpConfig = null;
        try {
            Configuration config = this.getConfig();
            String value = localDB.get(LocalDB.DB.OTP_SECRET, userGUID);
            if (value != null && value.length() > 0) {
                if (config.readSettingAsBoolean(PwmSetting.OTP_SECRET_ENCRYPT)) {
                    value = decryptAttributeValue(value);
                }
                if (value != null) {
                    otpConfig = decomposeOtpAttribute(value);
                }
                if (otpConfig != null) {
                    LOGGER.debug("found user OTP secret in LocalDB: " + otpConfig.toString());
                }
            }
        } catch (LocalDBException e) {
            final String errorMsg = "unexpected LocalDB error reading responses: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
            throw new PwmUnrecoverableException(errorInformation);
        } catch (PwmOperationalException e) {
            final String errorMsg = "unexpected error reading responses: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
            throw new PwmUnrecoverableException(errorInformation);
        }
        return otpConfig;
    }

    @Override
    public void writeOtpUserConfiguration(UserIdentity theUser, String userGUID, OTPUserConfiguration otpConfig) throws PwmUnrecoverableException {
        LOGGER.trace(String.format("Enter: writeOtpUserConfiguration(%s, %s, %s)", theUser, userGUID, otpConfig));
        if (userGUID == null || userGUID.length() < 1) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_MISSING_GUID, "cannot save responses to pwmDB, user does not have a pwmGUID"));
        }

        if (localDB == null || localDB.status() != LocalDB.Status.OPEN) {
            final String errorMsg = "LocalDB is not available, unable to write user responses";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_PWMDB_UNAVAILABLE, errorMsg);
            throw new PwmUnrecoverableException(errorInformation);
        }

        try {
            Configuration config = this.getConfig();
            String value = composeOtpAttribute(otpConfig);
            if (config.readSettingAsBoolean(PwmSetting.OTP_SECRET_ENCRYPT)) {
                LOGGER.debug("Encrypting OTP secret for storage");
                value = encryptAttributeValue(value);
            }

            localDB.put(LocalDB.DB.OTP_SECRET, userGUID, value);
            LOGGER.info("saved OTP secret for user in LocalDB");
        } catch (LocalDBException ex) {
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_WRITING_OTP_SECRET, "unexpected LocalDB error saving responses to pwmDB: " + ex.getMessage());
            final PwmUnrecoverableException pwmOE = new PwmUnrecoverableException(errorInfo);
            pwmOE.initCause(ex);
            throw pwmOE;
        } catch (PwmOperationalException ex) {
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_WRITING_OTP_SECRET, "unexpected error saving responses to pwmDB: " + ex.getMessage());
            final PwmUnrecoverableException pwmOE = new PwmUnrecoverableException(errorInfo);
            pwmOE.initCause(ex);
            throw pwmOE;
        }
    }

    @Override
    public void clearOtpUserConfiguration(UserIdentity theUser, String userGUID) throws PwmUnrecoverableException {
        LOGGER.trace(String.format("Enter: clearOtpUserConfiguration(%s, %s)", theUser, userGUID));
        if (userGUID == null || userGUID.length() < 1) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_MISSING_GUID, "cannot save responses to pwmDB, user does not have a pwmGUID"));
        }

        if (localDB == null || localDB.status() != LocalDB.Status.OPEN) {
            final String errorMsg = "LocalDB is not available, unable to write user responses";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_PWMDB_UNAVAILABLE, errorMsg);
            throw new PwmUnrecoverableException(errorInformation);
        }

        try {
            localDB.remove(LocalDB.DB.OTP_SECRET, userGUID);
            LOGGER.info("cleared OTP secret for user in LocalDB");
        } catch (LocalDBException ex) {
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_WRITING_RESPONSES, "unexpected LocalDB error saving responses to pwmDB: " + ex.getMessage());
            final PwmUnrecoverableException pwmOE = new PwmUnrecoverableException(errorInfo);
            pwmOE.initCause(ex);
            throw pwmOE;
        }
    }

    @Override
    public void close() {
        LOGGER.trace("Enter: close()");
        // No operation
    }

}
