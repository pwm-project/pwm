/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2016 The PWM Project
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

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package password.pwm.util.operations.otp;

import password.pwm.PwmApplication;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmSession;
import password.pwm.util.db.DatabaseAccessorImpl;
import password.pwm.util.db.DatabaseException;
import password.pwm.util.db.DatabaseTable;
import password.pwm.util.localdb.LocalDBException;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.otp.OTPUserRecord;

/**
 *
 * @author mpieters
 */
public class DbOtpOperator extends AbstractOtpOperator {

    final static private PwmLogger LOGGER = PwmLogger.forClass(DbOtpOperator.class);

    public DbOtpOperator(PwmApplication pwmApplication) {
        super.setPwmApplication(pwmApplication);
    }
    
    @Override
    public OTPUserRecord readOtpUserConfiguration(UserIdentity theUser, String userGUID) throws PwmUnrecoverableException {
        LOGGER.trace(String.format("Enter: readOtpUserConfiguration(%s, %s)", theUser, userGUID));
        if (userGUID == null || userGUID.length() < 1) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_MISSING_GUID, "cannot save otp to db, user does not have a GUID"));
        }

        OTPUserRecord otpConfig = null;
        try {
            final DatabaseAccessorImpl databaseAccessor = pwmApplication.getDatabaseAccessor();
            String value = databaseAccessor.get(DatabaseTable.OTP, userGUID);
            if (value != null && value.length() > 0) {
                if (getPwmApplication().getConfig().readSettingAsBoolean(PwmSetting.OTP_SECRET_ENCRYPT)) {
                    value = decryptAttributeValue(value);
                }
                if (value != null) {
                    otpConfig = decomposeOtpAttribute(value);
                }
                if (otpConfig != null) {
                    LOGGER.debug("found user OTP secret in db: " + otpConfig.toString());
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
    public void writeOtpUserConfiguration(
            final PwmSession pwmSession,
            final UserIdentity theUser,
            final String userGUID,
            final OTPUserRecord otpConfig
    )
            throws PwmUnrecoverableException
    {
        if (userGUID == null || userGUID.length() < 1) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_MISSING_GUID, "cannot save OTP secret to remote database, user " + theUser +  " does not have a guid"));
        }

        LOGGER.trace("attempting to save OTP secret for " + theUser + " in remote database (key=" + userGUID + ")");
        
        try {
            String value = composeOtpAttribute(otpConfig);
            if (getPwmApplication().getConfig().readSettingAsBoolean(PwmSetting.OTP_SECRET_ENCRYPT)) {
                LOGGER.debug("Encrypting OTP secret for storage");
                value = encryptAttributeValue(value);
            }
            final DatabaseAccessorImpl databaseAccessor = pwmApplication.getDatabaseAccessor();
            databaseAccessor.put(DatabaseTable.OTP, userGUID, value);
            LOGGER.info("saved OTP secret for " + theUser + " in remote database (key=" + userGUID + ")");
        } catch (PwmOperationalException ex) {
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_WRITING_OTP_SECRET, "unexpected error saving otp to db: " + ex.getMessage());
            final PwmUnrecoverableException pwmOE = new PwmUnrecoverableException(errorInfo);
            pwmOE.initCause(ex);
            throw pwmOE;
        }
    }

    @Override
    public void clearOtpUserConfiguration(
            final PwmSession pwmSession,
            final UserIdentity theUser,
            final String userGUID
    )
            throws PwmUnrecoverableException
    {
        if (userGUID == null || userGUID.length() < 1) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_MISSING_GUID, "cannot save OTP secret to remote database, user " + theUser +  " does not have a guid"));
        }

        LOGGER.trace("attempting to clear OTP secret for " + theUser + " in remote database (key=" + userGUID + ")");
        
        try {
            final DatabaseAccessorImpl databaseAccessor = pwmApplication.getDatabaseAccessor();
            databaseAccessor.remove(DatabaseTable.OTP, userGUID);
            LOGGER.info("cleared OTP secret for " + theUser + " in remote database (key=" + userGUID + ")");
        } catch (DatabaseException ex) {
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_WRITING_OTP_SECRET, "unexpected error saving otp to db: " + ex.getMessage());
            final PwmUnrecoverableException pwmOE = new PwmUnrecoverableException(errorInfo);
            pwmOE.initCause(ex);
            throw pwmOE;
        }
    }

    @Override
    public void close() {
    }

}
