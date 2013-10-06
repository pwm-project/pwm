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

import java.util.ArrayList;
import java.util.List;
import javax.crypto.SecretKey;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.Helper;
import password.pwm.util.PwmLogger;
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
     */
    public String composeOtpAttribute(OTPUserConfiguration otpconfig) {
        String value = "";
        if (otpconfig != null) {
            StorageFormat format = StorageFormat.valueOf(config.readSettingAsString(PwmSetting.OTP_SECRET_STORAGEFORMAT));
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
        OTPUserConfiguration otpconfig = new OTPUserConfiguration();
        /* Try format by format */
        try {
            JSONObject json = new JSONObject(value);
            /* PWM JSON */
            if (json.has("identifier")
                    && json.has("secret")
                    && json.has("type")) {
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
                return otpconfig;
            } else {
                return null;
            }
        } catch (JSONException ex) {
            LOGGER.warn(ex.getMessage(), ex);
            /* So, it's not JSON, try something else */
            /* -- nothing to try, yet; for future use */
            /* no more options */
            return null;
        }
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

        PWM
    }
}
