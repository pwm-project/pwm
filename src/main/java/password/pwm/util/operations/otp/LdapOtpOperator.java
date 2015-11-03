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

package password.pwm.util.operations.otp;

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiError;
import com.novell.ldapchai.exception.ChaiException;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.PwmApplication;
import password.pwm.bean.UserIdentity;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmSession;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.otp.OTPUserRecord;

/**
 *
 * @author Menno Pieters, Jason D. Rivard
 */
public class LdapOtpOperator extends AbstractOtpOperator {

    private static final PwmLogger LOGGER = PwmLogger.forClass(LdapOtpOperator.class);

    public LdapOtpOperator(PwmApplication pwmApplication) {
        setPwmApplication(pwmApplication);
    }

    /**
     * Read OTP secret and instantiate a OTP User Configuration object.
     *
     * @param userIdentity
     * @param userGUID
     * @return
     * @throws PwmUnrecoverableException
     */
    @Override
    public OTPUserRecord readOtpUserConfiguration(UserIdentity userIdentity, String userGUID) throws PwmUnrecoverableException {
        Configuration config = getPwmApplication().getConfig();
        String ldapStorageAttribute = config.readSettingAsString(PwmSetting.OTP_SECRET_LDAP_ATTRIBUTE);
        if (ldapStorageAttribute == null || ldapStorageAttribute.length() < 1) {
            final String errorMsg = "ldap storage attribute is not configured, unable to read OTP secret";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_INVALID_CONFIG, errorMsg);
            throw new PwmUnrecoverableException(errorInformation);
        }
        OTPUserRecord otp = null;
        try {
            final ChaiUser theUser = pwmApplication.getProxiedChaiUser(userIdentity);
            String value = theUser.readStringAttribute(ldapStorageAttribute);
            if (config.readSettingAsBoolean(PwmSetting.OTP_SECRET_ENCRYPT)) {
                value = decryptAttributeValue(value);
            }
            if (value != null) {
                otp = decomposeOtpAttribute(value);
            }
        } catch (ChaiOperationException e) {
            final String errorMsg = "unexpected LDAP error reading responses: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
            throw new PwmUnrecoverableException(errorInformation);
        } catch (ChaiUnavailableException e) {
            final String errorMsg = "unexpected LDAP error reading responses: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
            throw new PwmUnrecoverableException(errorInformation);
        } catch (PwmOperationalException e) {
            final String errorMsg = "unexpected error reading responses: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
            throw new PwmUnrecoverableException(errorInformation);
        }
        return otp;
    }

    /**
     *
     * @param userIdentity
     * @param userGuid
     * @param otpConfig
     * @throws PwmUnrecoverableException
     */
    @Override
    public void writeOtpUserConfiguration(
            final PwmSession pwmSession,
            final UserIdentity userIdentity,
            final String userGuid,
            final OTPUserRecord otpConfig
    ) throws PwmUnrecoverableException {
        Configuration config = pwmApplication.getConfig();
        final String ldapStorageAttribute = config.readSettingAsString(PwmSetting.OTP_SECRET_LDAP_ATTRIBUTE);
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
            if (config.readSettingAsBoolean(PwmSetting.OTP_SECRET_ENCRYPT)) {
                value = encryptAttributeValue(value);
            }
            final ChaiUser theUser = pwmSession == null
                    ? pwmApplication.getProxiedChaiUser(userIdentity)
                    : pwmSession.getSessionManager().getActor(pwmApplication, userIdentity);
            theUser.writeStringAttribute(ldapStorageAttribute, value);
            LOGGER.info("saved OTP secret for user to chai-ldap format");
        } catch (ChaiException ex) {
            final String errorMsg;
            if (ex.getErrorCode() == ChaiError.NO_ACCESS) {
                errorMsg = "permission error writing OTP secret to ldap attribute '" + ldapStorageAttribute + "', user does not appear to have correct permissions to save OTP secret: " + ex.getMessage();
            } else {
                errorMsg = "error writing OTP secret to ldap attribute '" + ldapStorageAttribute + "': " + ex.getMessage();
            }
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_WRITING_OTP_SECRET, errorMsg);
            final PwmUnrecoverableException pwmOE = new PwmUnrecoverableException(errorInfo);
            pwmOE.initCause(ex);
            throw pwmOE;
        } catch (PwmOperationalException ex) {
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_WRITING_OTP_SECRET, ex.getMessage());
            final PwmUnrecoverableException pwmOE = new PwmUnrecoverableException(errorInfo);
            pwmOE.initCause(ex);
            throw pwmOE;
        }
    }

    /**
     *
     * @param userIdentity
     * @param userGuid
     * @throws PwmUnrecoverableException
     */
    @Override
    public void clearOtpUserConfiguration(
            final PwmSession pwmSession,
            final UserIdentity userIdentity,
            final String userGuid
    ) throws PwmUnrecoverableException {
        Configuration config = pwmApplication.getConfig();
        final String ldapStorageAttribute = config.readSettingAsString(PwmSetting.OTP_SECRET_LDAP_ATTRIBUTE);
        if (ldapStorageAttribute == null || ldapStorageAttribute.length() < 1) {
            final String errorMsg = "ldap storage attribute is not configured, unable to clear OTP secret";
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_INVALID_CONFIG, errorMsg);
            throw new PwmUnrecoverableException(errorInformation);
        }
        try {
            final ChaiUser theUser = pwmSession == null
                    ? pwmApplication.getProxiedChaiUser(userIdentity)
                    : pwmSession.getSessionManager().getActor(pwmApplication, userIdentity);
            theUser.deleteAttribute(ldapStorageAttribute,null);
            LOGGER.info("cleared OTP secret for user to chai-ldap format");
        } catch (ChaiOperationException e) {
            final String errorMsg;
            if (e.getErrorCode() == ChaiError.NO_ACCESS) {
                errorMsg = "permission error clearing responses to ldap attribute '" + ldapStorageAttribute + "', user does not appear to have correct permissions to clear OTP secret: " + e.getMessage();
            } else {
                errorMsg = "error clearing OTP secret to ldap attribute '" + ldapStorageAttribute + "': " + e.getMessage();
            }
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_WRITING_OTP_SECRET, errorMsg);
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
