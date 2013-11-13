/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package password.pwm.util.operations.otp;

import com.novell.ldapchai.ChaiUser;
import password.pwm.PwmApplication;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.PwmLogger;
import password.pwm.util.db.DatabaseAccessor;
import password.pwm.util.db.DatabaseException;
import password.pwm.util.db.DatabaseTable;
import password.pwm.util.localdb.LocalDBException;
import password.pwm.util.otp.OTPUserConfiguration;

/**
 *
 * @author mpieters
 */
public class DbOtpOperator extends AbstractOtpOperator {

    final static private PwmLogger LOGGER = PwmLogger.getLogger(DbOtpOperator.class);

    final PwmApplication pwmApplication;

    public DbOtpOperator(PwmApplication pwmApplication) {
        this.pwmApplication = pwmApplication;
        super.setConfig(pwmApplication.getConfig());
    }
    
    @Override
    public OTPUserConfiguration readOtpUserConfiguration(ChaiUser theUser, String userGUID) throws PwmUnrecoverableException {
        LOGGER.trace(String.format("Enter: readOtpUserConfiguration(%s, %s)", theUser, userGUID));
        if (userGUID == null || userGUID.length() < 1) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_MISSING_GUID, "cannot save responses to pwmDB, user does not have a pwmGUID"));
        }

        OTPUserConfiguration otpConfig = null;
        try {
            final DatabaseAccessor databaseAccessor = pwmApplication.getDatabaseAccessor();
            String value = databaseAccessor.get(DatabaseTable.OTP, userGUID);
            if (value != null && value.length() > 0) {
                if (getConfig().readSettingAsBoolean(PwmSetting.OTP_SECRET_ENCRYPT)) {
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
    public void writeOtpUserConfiguration(ChaiUser theUser, String userGUID, OTPUserConfiguration otpConfig) throws PwmUnrecoverableException {
        if (userGUID == null || userGUID.length() < 1) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_MISSING_GUID, "cannot save OTP secret to remote database, user " + theUser.getEntryDN() +  " does not have a guid"));
        }

        LOGGER.trace("attempting to save OTP secret for " + theUser.getEntryDN() + " in remote database (key=" + userGUID + ")");
        
        try {
            String value = composeOtpAttribute(otpConfig);
            if (getConfig().readSettingAsBoolean(PwmSetting.OTP_SECRET_ENCRYPT)) {
                LOGGER.debug("Encrypting OTP secret for storage");
                value = encryptAttributeValue(value);
            }
            final DatabaseAccessor databaseAccessor = pwmApplication.getDatabaseAccessor();
            databaseAccessor.put(DatabaseTable.OTP, userGUID, value);
            LOGGER.info("saved OTP secret for " + theUser.getEntryDN() + " in remote database (key=" + userGUID + ")");
        } catch (PwmOperationalException ex) {
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_WRITING_OTP_SECRET, "unexpected error saving responses to pwmDB: " + ex.getMessage());
            final PwmUnrecoverableException pwmOE = new PwmUnrecoverableException(errorInfo);
            pwmOE.initCause(ex);
            throw pwmOE;
        }
    }

    @Override
    public void clearOtpUserConfiguration(ChaiUser theUser, String userGUID) throws PwmUnrecoverableException {
        if (userGUID == null || userGUID.length() < 1) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_MISSING_GUID, "cannot save OTP secret to remote database, user " + theUser.getEntryDN() +  " does not have a guid"));
        }

        LOGGER.trace("attempting to clear OTP secret for " + theUser.getEntryDN() + " in remote database (key=" + userGUID + ")");
        
        try {
            final DatabaseAccessor databaseAccessor = pwmApplication.getDatabaseAccessor();
            databaseAccessor.remove(DatabaseTable.OTP, userGUID);
            LOGGER.info("cleared OTP secret for " + theUser.getEntryDN() + " in remote database (key=" + userGUID + ")");
        } catch (DatabaseException ex) {
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_WRITING_OTP_SECRET, "unexpected error saving responses to pwmDB: " + ex.getMessage());
            final PwmUnrecoverableException pwmOE = new PwmUnrecoverableException(errorInfo);
            pwmOE.initCause(ex);
            throw pwmOE;
        }
    }

    @Override
    public void close() {
    }

}
