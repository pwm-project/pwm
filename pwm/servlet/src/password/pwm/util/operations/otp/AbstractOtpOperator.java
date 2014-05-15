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

package password.pwm.util.operations.otp;

import java.util.ArrayList;
import java.util.List;
import javax.crypto.SecretKey;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.Helper;
import password.pwm.util.PwmLogger;
import password.pwm.util.otp.OTPPamUtil;
import password.pwm.util.otp.OTPUrlUtil;
import password.pwm.util.otp.OTPUserConfiguration;

/**
 *
 * @author mpieters
 */
public abstract class AbstractOtpOperator implements OtpOperator {

    private static final PwmLogger LOGGER = PwmLogger.getLogger(AbstractOtpOperator.class);
    private Configuration config;

    /**
     * Compose a single line of OTP information.
     *
     * @param otpconfig
     * @return
     * @throws password.pwm.error.PwmUnrecoverableException
     */
    public String composeOtpAttribute(OTPUserConfiguration otpconfig) throws PwmUnrecoverableException {
        String value = "";
        if (otpconfig != null) {
            String formatStr = config.readSettingAsString(PwmSetting.OTP_SECRET_STORAGEFORMAT);
            if (formatStr != null) {
                StorageFormat format = StorageFormat.valueOf(formatStr);
                switch (format) {
                    case PWM:
                        try {
                            JSONObject json = new JSONObject();
                            json.put("identifier", otpconfig.getIdentifier());
                            json.put("secret", otpconfig.getSecret());
                            json.put("type", otpconfig.getType().toString());
                            json.put("recoverycodes", otpconfig.getRecoveryCodes());
                            json.put("counter", otpconfig.getCurrentCounter());
                            value = json.toString();
                        } catch (JSONException ex) {
                            LOGGER.warn(ex.getMessage(), ex);
                        }
                        break;
                    case OTPURL:
                        value = OTPUrlUtil.composeOtpUrl(otpconfig);
                        break;
                    case BASE32SECRET:
                        value = otpconfig.getSecret();
                        break;
                    case PAM:
                        value = OTPPamUtil.composePamData(otpconfig);
                        break;
                    default:
                        String errorStr = String.format("Unsupported storage format: ", format.toString());
                        ErrorInformation error = new ErrorInformation(PwmError.ERROR_INVALID_CONFIG, errorStr);
                        throw new PwmUnrecoverableException(error);
                }
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
        SecretKey key = config.getSecurityKey();
        return Helper.SimpleTextCrypto.encryptValue(unencrypted, key);
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
        SecretKey key = config.getSecurityKey();
        return Helper.SimpleTextCrypto.decryptValue(encrypted, key);
    }

    /**
     *
     * @param value
     * @return
     */
    public OTPUserConfiguration decomposeOtpAttribute(String value) {
        if (value == null) {
            return null;
        }
        OTPUserConfiguration otpconfig = null;
        /* Try format by format */
        /* - PWM JSON */
        try {
            JSONObject json = new JSONObject(value);
            if (json.has("identifier")
                    && json.has("secret")
                    && json.has("type")) {
                otpconfig = new OTPUserConfiguration();
                otpconfig.setIdentifier(json.getString("identifier"));
                otpconfig.setSecret(json.getString("secret"));
                otpconfig.setType(OTPUserConfiguration.Type.valueOf(json.getString("type")));
                if (json.has("counter")) {
                    otpconfig.setCounter(json.getLong("counter"));
                }
                if (json.has("recoverycodes")) {
                    JSONArray reccodes = json.getJSONArray("recoverycodes");
                    if (reccodes != null && reccodes.length() > 0) {
                        List<String> recoverycodes = new ArrayList();
                        for (int i = 0; i < reccodes.length(); i++) {
                            recoverycodes.add(reccodes.getString(i));
                        }
                        otpconfig.setRecoveryCodes(recoverycodes);
                    }
                }
                LOGGER.debug("Detected JSON format - returning");
                return otpconfig;
            } else {
                return null;
            }
        } catch (JSONException ex) {
            LOGGER.info(ex.getMessage(), ex);
            /* So, it's not JSON, try something else */
            /* -- nothing to try, yet; for future use */
            /* no more options */
        }
        /* - otpauth:// URL */
        otpconfig = OTPUrlUtil.decomposeOtpUrl(value);
        if (otpconfig != null) {
            LOGGER.debug("Detected otpauth URL format - returning");
            return otpconfig;
        }
        /* - PAM */
        otpconfig = OTPPamUtil.decomposePamData(value);
        if (otpconfig != null) {
            LOGGER.debug("Detected PAM text format - returning");
            return otpconfig;
        }
        /* - BASE32 secret */
        if (value.trim().matches("^[A-Z2-7\\=]{16}$")) {
            LOGGER.debug("Detected plain Base32 secret - returning");
            otpconfig = new OTPUserConfiguration();
            otpconfig.setSecret(value.trim());
            return otpconfig;
        }
        return otpconfig;
    }

    /**
     *
     * @return
     */
    public Configuration getConfig() {
        return config;
    }

    /**
     *
     * @param config
     */
    public void setConfig(Configuration config) {
        this.config = config;
    }

    public enum StorageFormat {

        PWM(true),
        BASE32SECRET(false),
        OTPURL(false),
        PAM(true);

        private final boolean useRecoveryCodes;

        StorageFormat(boolean useRecoveryCodes) {
            this.useRecoveryCodes = useRecoveryCodes;
        }

        public boolean supportsRecoveryCodes() {
            return useRecoveryCodes;
        }
    }
}
