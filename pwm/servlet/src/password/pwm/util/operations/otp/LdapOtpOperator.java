/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2013 The PWM Project
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
package password.pwm.util.operations.otp;

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiError;
import com.novell.ldapchai.exception.ChaiException;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import java.util.logging.Level;
import java.util.logging.Logger;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.PwmLogger;
import password.pwm.util.otp.OTPUserConfiguration;

/**
 *
 * @author mpieters
 */
public class LdapOtpOperator extends AbstractOtpOperator {

    private static final PwmLogger LOGGER = PwmLogger.getLogger(LdapOtpOperator.class);

    /**
     * Constructor.
     *
     * @param config
     */
    public LdapOtpOperator(Configuration config) {
        super(config);
    }

    /**
     * Read OTP secret and instantiate a OTP User Configuration object.
     *
     * @param theUser
     * @param userGUID
     * @return
     * @throws PwmUnrecoverableException
     */
    @Override
    public OTPUserConfiguration readOtpUserConfiguration(ChaiUser theUser, String userGUID) throws PwmUnrecoverableException {
        Configuration config = getConfig();
        String ldapStorageAttribute = config.readSettingAsString(PwmSetting.OTP_SECRET_LDAP_ATTRIBUTE);
        if (ldapStorageAttribute == null || ldapStorageAttribute.length() < 1) {
            final String errorMsg = "ldap storage attribute is not configured, unable to read OTP secret";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_INVALID_CONFIG, errorMsg);
            throw new PwmUnrecoverableException(errorInformation);
        }
        OTPUserConfiguration otp = null;
        try {
            String value = theUser.readStringAttribute(ldapStorageAttribute);
            if (config.readSettingAsBoolean(PwmSetting.OTP_SECRET_ENCRYPT)) {
                value = decryptAttributeValue(value);
            }
            if (value != null) {
                otp = decomposeOtpAttribute(value);
            }
        } catch (ChaiOperationException ex) {
            LOGGER.error(ex.getMessage(), ex);
        } catch (ChaiUnavailableException ex) {
            LOGGER.error(ex.getMessage(), ex);
        }
        return otp;
    }

    /**
     *
     * @param theUser
     * @param userGuid
     * @param otpConfig
     * @throws PwmUnrecoverableException
     */
    @Override
    public void writeOtpUserConfiguration(ChaiUser theUser, String userGuid, OTPUserConfiguration otpConfig) throws PwmUnrecoverableException {
        Configuration config = getConfig();
        final String ldapStorageAttribute = config.readSettingAsString(PwmSetting.CHALLENGE_USER_ATTRIBUTE);
        if (ldapStorageAttribute == null || ldapStorageAttribute.length() < 1) {
            final String errorMsg = "ldap storage attribute is not configured, unable to write OTP secret";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_INVALID_CONFIG, errorMsg);
            throw new PwmUnrecoverableException(errorInformation);
        }
        String value = composeOtpAttribute(otpConfig);
        if (value == null || value.length() == 0) {
            final String errorMsg = "Invalid value for OTP secret, unable to store";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_INVALID_CONFIG, errorMsg);
            throw new PwmUnrecoverableException(errorInformation);
        }
        try {
            theUser.writeStringAttribute(ldapStorageAttribute, value);
            LOGGER.info("saved OTP secret for user to chai-ldap format");
        } catch (ChaiException e) {
            final String errorMsg;
            if (e.getErrorCode() == ChaiError.NO_ACCESS) {
                errorMsg = "permission error writing OTP secret to ldap attribute '" + ldapStorageAttribute + "', user does not appear to have correct permissions to save OTP secret: " + e.getMessage();
            } else {
                errorMsg = "error writing OTP secret to ldap attribute '" + ldapStorageAttribute + "': " + e.getMessage();
            }
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_WRITING_RESPONSES, errorMsg);
            final PwmUnrecoverableException pwmOE = new PwmUnrecoverableException(errorInfo);
            pwmOE.initCause(e);
            throw pwmOE;
        }
    }

    /**
     *
     * @param theUser
     * @param userGuid
     * @throws PwmUnrecoverableException
     */
    @Override
    public void clearOtpUserConfiguration(ChaiUser theUser, String userGuid) throws PwmUnrecoverableException {
        Configuration config = getConfig();
        final String ldapStorageAttribute = config.readSettingAsString(PwmSetting.OTP_SECRET_LDAP_ATTRIBUTE);
        if (ldapStorageAttribute == null || ldapStorageAttribute.length() < 1) {
            final String errorMsg = "ldap storage attribute is not configured, unable to clear OTP secret";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_INVALID_CONFIG, errorMsg);
            throw new PwmUnrecoverableException(errorInformation);
        }
        try {
            final String currentValue = theUser.readStringAttribute(ldapStorageAttribute);
            if (currentValue != null && currentValue.length() > 0) {
                theUser.deleteAttribute(ldapStorageAttribute, null);
            }
            LOGGER.info("cleared OTP secret for user to chai-ldap format");
        } catch (ChaiOperationException e) {
            final String errorMsg;
            if (e.getErrorCode() == ChaiError.NO_ACCESS) {
                errorMsg = "permission error clearing responses to ldap attribute '" + ldapStorageAttribute + "', user does not appear to have correct permissions to clear OTP secret: " + e.getMessage();
            } else {
                errorMsg = "error clearing OTP secret to ldap attribute '" + ldapStorageAttribute + "': " + e.getMessage();
            }
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_WRITING_RESPONSES, errorMsg);
            final PwmUnrecoverableException pwmOE = new PwmUnrecoverableException(errorInfo);
            pwmOE.initCause(e);
            throw pwmOE;
        } catch (ChaiUnavailableException e) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_DIRECTORY_UNAVAILABLE, e.getMessage()));
        }
    }

    /**
     * Close the operator. Does nothing in this case.
     */
    @Override
    public void close() {
    }
}
