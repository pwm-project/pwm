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

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package password.pwm.util.operations.otp;

import password.pwm.PwmApplication;
import password.pwm.bean.UserIdentity;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmSession;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBException;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.otp.OTPUserRecord;

/**
 *
 * @author Menno Pieters, Jason D. Rivard
 */
public class LocalDbOtpOperator extends AbstractOtpOperator {

    private static final PwmLogger LOGGER = PwmLogger.forClass(LocalDbOtpOperator.class);
    private final LocalDB localDB;

    public LocalDbOtpOperator(PwmApplication pwmApplication) {
        this.localDB = pwmApplication.getLocalDB();
        setPwmApplication(pwmApplication);
    }

    @Override
    public OTPUserRecord readOtpUserConfiguration(UserIdentity theUser, String userGUID) throws PwmUnrecoverableException {
        LOGGER.trace(String.format("Enter: readOtpUserConfiguration(%s, %s)", theUser, userGUID));
        if (userGUID == null || userGUID.length() < 1) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_MISSING_GUID, "cannot save otp to localDB, user does not have a GUID"));
        }

        if (localDB == null || localDB.status() != LocalDB.Status.OPEN) {
            final String errorMsg = "LocalDB is not available, unable to write user otp";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_LOCALDB_UNAVAILABLE, errorMsg);
            throw new PwmUnrecoverableException(errorInformation);
        }

        OTPUserRecord otpConfig = null;
        try {
            Configuration config = this.getPwmApplication().getConfig();
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
            final String errorMsg = "unexpected LocalDB error reading otp: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
            throw new PwmUnrecoverableException(errorInformation);
        } catch (PwmOperationalException e) {
            final String errorMsg = "unexpected error reading otp: " + e.getMessage();
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
        LOGGER.trace(pwmSession,String.format("Enter: writeOtpUserConfiguration(%s, %s, %s)", theUser, userGUID, otpConfig));
        if (userGUID == null || userGUID.length() < 1) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_MISSING_GUID, "cannot save otp to localDB, user does not have a pwmGUID"));
        }

        if (localDB == null || localDB.status() != LocalDB.Status.OPEN) {
            final String errorMsg = "LocalDB is not available, unable to write user otp";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_LOCALDB_UNAVAILABLE, errorMsg);
            throw new PwmUnrecoverableException(errorInformation);
        }

        try {
            Configuration config = this.getPwmApplication().getConfig();
            String value = composeOtpAttribute(otpConfig);
            if (config.readSettingAsBoolean(PwmSetting.OTP_SECRET_ENCRYPT)) {
                LOGGER.debug(pwmSession,"Encrypting OTP secret for storage");
                value = encryptAttributeValue(value);
            }

            localDB.put(LocalDB.DB.OTP_SECRET, userGUID, value);
            LOGGER.info(pwmSession,"saved OTP secret for user in LocalDB");
        } catch (LocalDBException ex) {
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_WRITING_OTP_SECRET, "unexpected LocalDB error saving otp to localDB: " + ex.getMessage());
            final PwmUnrecoverableException pwmOE = new PwmUnrecoverableException(errorInfo);
            pwmOE.initCause(ex);
            throw pwmOE;
        } catch (PwmOperationalException ex) {
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_WRITING_OTP_SECRET, "unexpected error saving otp to localDB: " + ex.getMessage());
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
        LOGGER.trace(pwmSession, String.format("Enter: clearOtpUserConfiguration(%s, %s)", theUser, userGUID));
        if (userGUID == null || userGUID.length() < 1) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_MISSING_GUID, "cannot save otp to localDB, user does not have a pwmGUID"));
        }

        if (localDB == null || localDB.status() != LocalDB.Status.OPEN) {
            final String errorMsg = "LocalDB is not available, unable to write user OTP";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_LOCALDB_UNAVAILABLE, errorMsg);
            throw new PwmUnrecoverableException(errorInformation);
        }

        try {
            localDB.remove(LocalDB.DB.OTP_SECRET, userGUID);
            LOGGER.info(pwmSession, "cleared OTP secret for user in LocalDB");
        } catch (LocalDBException ex) {
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_WRITING_OTP_SECRET, "unexpected error saving otp to localDB: " + ex.getMessage());
            final PwmUnrecoverableException pwmOE = new PwmUnrecoverableException(errorInfo);
            pwmOE.initCause(ex);
            throw pwmOE;
        }
    }

    @Override
    public void close() {
        // No operation
    }

}
