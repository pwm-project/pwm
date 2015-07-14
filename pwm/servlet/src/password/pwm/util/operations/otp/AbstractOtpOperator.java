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

import com.google.gson.JsonSyntaxException;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.OTPStorageFormat;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.Helper;
import password.pwm.util.JsonUtil;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.otp.OTPPamUtil;
import password.pwm.util.otp.OTPUrlUtil;
import password.pwm.util.otp.OTPUserRecord;
import password.pwm.util.secure.PwmBlockAlgorithm;
import password.pwm.util.secure.PwmSecurityKey;
import password.pwm.util.secure.SecureEngine;

/**
 *
 * @author mpieters
 */
public abstract class AbstractOtpOperator implements OtpOperator {

    private static final PwmLogger LOGGER = PwmLogger.forClass(AbstractOtpOperator.class);
    protected PwmApplication pwmApplication;

    /**
     * Compose a single line of OTP information.
     *
     * @param otpUserRecord
     * @return
     * @throws password.pwm.error.PwmUnrecoverableException
     */
    public String composeOtpAttribute(OTPUserRecord otpUserRecord) throws PwmUnrecoverableException {
        String value = "";
        if (otpUserRecord != null) {
            final Configuration config = pwmApplication.getConfig();
            final OTPStorageFormat format = config.readSettingAsEnum(PwmSetting.OTP_SECRET_STORAGEFORMAT,OTPStorageFormat.class);
            switch (format) {
                case PWM:
                    value = JsonUtil.serialize(otpUserRecord);
                    break;
                case OTPURL:
                    value = OTPUrlUtil.composeOtpUrl(otpUserRecord);
                    break;
                case BASE32SECRET:
                    value = otpUserRecord.getSecret();
                    break;
                case PAM:
                    value = OTPPamUtil.composePamData(otpUserRecord);
                    break;
                default:
                    String errorStr = String.format("Unsupported storage format: ", format.toString());
                    ErrorInformation error = new ErrorInformation(PwmError.ERROR_INVALID_CONFIG, errorStr);
                    throw new PwmUnrecoverableException(error);
            }
        }
        return value;
    }

    /**
     * Encrypt the given string using the PWM encryption key.
     *
     * @param unencrypted
     * @return
     * @throws PwmUnrecoverableException
     * @throws PwmOperationalException
     */
    public String encryptAttributeValue(String unencrypted) throws PwmUnrecoverableException, PwmOperationalException {
        final PwmBlockAlgorithm pwmBlockAlgorithm = figureBlockAlg();
        final PwmSecurityKey pwmSecurityKey = pwmApplication.getConfig().getSecurityKey();
        return SecureEngine.encryptToString(unencrypted, pwmSecurityKey, pwmBlockAlgorithm);
    }

    public PwmBlockAlgorithm figureBlockAlg() {
        final String otpEncryptionAlgString = pwmApplication.getConfig().readAppProperty(AppProperty.OTP_ENCRYPTION_ALG);
        return Helper.readEnumFromString(PwmBlockAlgorithm.class, PwmBlockAlgorithm.AES, otpEncryptionAlgString);
    }

    /**
     * Decrypt the given string using the PWM encryption key.
     *
     * @param encrypted
     * @return
     * @throws PwmUnrecoverableException
     * @throws PwmOperationalException
     */
    public String decryptAttributeValue(String encrypted) throws PwmUnrecoverableException, PwmOperationalException {
        final PwmBlockAlgorithm pwmBlockAlgorithm = figureBlockAlg();
        final PwmSecurityKey pwmSecurityKey = pwmApplication.getConfig().getSecurityKey();
        return SecureEngine.decryptStringValue(encrypted, pwmSecurityKey, pwmBlockAlgorithm);
    }

    /**
     *
     * @param value
     * @return
     */
    public OTPUserRecord decomposeOtpAttribute(String value) {
        if (value == null) {
            return null;
        }
        OTPUserRecord otpconfig = null;
        /* Try format by format */
        LOGGER.trace(String.format("detecting format from value: %s", value));
        /* - PWM JSON */
        try {
            otpconfig = JsonUtil.deserialize(value, OTPUserRecord.class);
            LOGGER.debug("detected JSON format - returning");
            return otpconfig;
        } catch (JsonSyntaxException ex) {
            LOGGER.debug("no JSON format detected - returning");
            /* So, it's not JSON, try something else */
            /* -- nothing to try, yet; for future use */
            /* no more options */
        }
        /* - otpauth:// URL */
        otpconfig = OTPUrlUtil.decomposeOtpUrl(value);
        if (otpconfig != null) {
            LOGGER.debug("detected otpauth URL format - returning");
            return otpconfig;
        }
        /* - PAM */
        otpconfig = OTPPamUtil.decomposePamData(value);
        if (otpconfig != null) {
            LOGGER.debug("detected PAM text format - returning");
            return otpconfig;
        }
        /* - BASE32 secret */
        if (value.trim().matches("^[A-Z2-7\\=]{16}$")) {
            LOGGER.debug("detected plain Base32 secret - returning");
            otpconfig = new OTPUserRecord();
            otpconfig.setSecret(value.trim());
            return otpconfig;
        }

        return otpconfig;
    }

    public PwmApplication getPwmApplication() {
        return pwmApplication;
    }

    public void setPwmApplication(PwmApplication pwmApplication) {
        this.pwmApplication = pwmApplication;
    }
}
