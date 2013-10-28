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
package password.pwm.util.otp;

import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.ArrayList;
import java.util.List;
import javax.crypto.Mac;
import org.apache.commons.codec.binary.Base32;
import java.security.SecureRandom;
import java.util.Date;
import javax.crypto.spec.SecretKeySpec;
import password.pwm.PwmConstants;
import password.pwm.util.PwmLogger;

public class OTPUserConfiguration {

    static final PwmLogger LOGGER = PwmLogger.getLogger(OTPUserConfiguration.class);
    private String identifier = null;
    private String secret = null;
    private List<String> recoveryCodes = null;
    private Long counter = null;
    private Type type = Type.TOTP;

    public OTPUserConfiguration() {
    }

    public OTPUserConfiguration(String identifier) {
        this.identifier = identifier;
    }

    public OTPUserConfiguration(String identifier, String secret, List<String> recoveryCodes) {
        this.identifier = identifier;
        this.secret = secret;
        this.recoveryCodes = recoveryCodes;
        this.type = Type.TOTP;
    }

    public OTPUserConfiguration(String identifier, String secret, List<String> recoveryCodes, Long counter) {
        this.identifier = identifier;
        this.secret = secret;
        this.recoveryCodes = recoveryCodes;
        this.counter = counter;
        this.type = (counter == null) ? Type.TOTP : Type.HOTP;
    }

    private byte[] generateSecret() {
        byte[] secArray = new byte[10];
        SecureRandom random;
        try {
            random = SecureRandom.getInstance("SHA1PRNG", "SUN");
        } catch (NoSuchAlgorithmException ex) {
            random = new SecureRandom();
            LOGGER.error(ex.getMessage(), ex);
        } catch (NoSuchProviderException ex) {
            random = new SecureRandom();
            LOGGER.error(ex.getMessage(), ex);
        }
        random.setSeed((new Date()).getTime());
        random.nextBytes(secArray);
        return secArray;
    }

    public void initRecoveryCodes(int numRecoveryCodes) throws NoSuchAlgorithmException, InvalidKeyException {
        if (numRecoveryCodes > 0) {
            this.recoveryCodes = new ArrayList();
            SecureRandom random = new SecureRandom();
            for (int n = 0; n < numRecoveryCodes; n++) {
                Long modulo = (long) Math.pow(10L, (new Long(PwmConstants.OTP_RECOVERY_TOKEN_LENGTH)));
                LOGGER.debug(modulo);
                String code = String.format("%08d", Math.abs(random.nextLong() % modulo));
                this.recoveryCodes.add(code);
            }
        }
    }

    public void init(boolean counterBased, int numRecoveryCodes) throws NoSuchAlgorithmException, InvalidKeyException {
        LOGGER.trace(String.format("Enter: init(%s)", counterBased));
        Base32 base32 = new Base32();
        byte[] rawSecret = generateSecret();
        this.secret = new String(base32.encode(rawSecret));
        LOGGER.debug(String.format("Generated Secret: %s", secret));
        if (numRecoveryCodes > 0) initRecoveryCodes(numRecoveryCodes);
        if (counterBased) {
            this.counter = new SecureRandom().nextLong();
            this.type = Type.HOTP;
        } else {
            this.counter = null;
            this.type = Type.TOTP;
        }
    }

    /**
     * 
     * @param identifier
     * @param counterBased
     * @param numRecoveryCodes
     * @return
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeyException 
     */
    public static OTPUserConfiguration getInstance(String identifier, Boolean counterBased, int numRecoveryCodes) throws NoSuchAlgorithmException, InvalidKeyException {
        OTPUserConfiguration otpuc = new OTPUserConfiguration(identifier);
        otpuc.init(counterBased, numRecoveryCodes);
        return otpuc;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public List<String> getRecoveryCodes() {
        if (recoveryCodes == null) {
            recoveryCodes = new ArrayList<String>();
        }
        return recoveryCodes;
    }

    public void setRecoveryCodes(List<String> recoveryCodes) {
        this.recoveryCodes = recoveryCodes;
    }

    public Long getCurrentCounter() {
        return counter;
    }

    public Long getNextCounter() {
        return ++counter;
    }

    public void setCounter(Long counter) {
        this.counter = counter;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public static enum Type {

        HOTP,
        TOTP
    }
    
    @Override
    public boolean equals(Object object) {
        if (object instanceof OTPUserConfiguration) {
            return (this.hashCode() == object.hashCode());
        }
        return false;   
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 71 * hash + (this.identifier != null ? this.identifier.hashCode() : 0);
        hash = 71 * hash + (this.secret != null ? this.secret.hashCode() : 0);
        hash = 71 * hash + (this.recoveryCodes != null ? this.recoveryCodes.hashCode() : 0);
        hash = 71 * hash + (this.counter != null ? this.counter.hashCode() : 0);
        hash = 71 * hash + (this.type != null ? this.type.hashCode() : 0);
        return hash;
    }
}