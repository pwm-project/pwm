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
package password.pwm.bean.servlet;

import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.util.Date;
import password.pwm.bean.PwmSessionBean;
import password.pwm.util.otp.OTPUserConfiguration;

import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.apache.commons.codec.binary.Base32;
import password.pwm.PwmConstants;
import password.pwm.util.PwmLogger;
import password.pwm.util.otp.PasscodeGenerator;

public class SetupOtpBean implements PwmSessionBean {

    private static final PwmLogger LOGGER = PwmLogger.getLogger(SetupOtpBean.class);

    private OTPUserConfiguration otp = null;
    private boolean confirmed = false;
    private boolean cleared = false;
    private Locale userLocale = Locale.getDefault();
    private Long challenge = null; // for HOTP only

    public SetupOtpBean() {
    }

    public OTPUserConfiguration getOtp() {
        return otp;
    }

    public boolean validateToken(String token) {
        LOGGER.trace(String.format("Enter: validateToken(%s)", token));
        try {
            if (otp != null) {
                Base32 base32 = new Base32();
                byte[] rawSecret = base32.decode(otp.getSecret());
                Mac mac = Mac.getInstance("HMACSHA1");
                mac.init(new SecretKeySpec(rawSecret, ""));
                PasscodeGenerator generator = new PasscodeGenerator(mac, PwmConstants.OTP_TOKEN_LENGTH, PwmConstants.TOTP_INTERVAL);
                switch (otp.getType()) {
                    case TOTP:
                        return generator.verifyTimeoutCode(token, PwmConstants.TOTP_PAST_INTERVALS, PwmConstants.TOTP_FUTURE_INTERVALS);
                    case HOTP:
                        /* Not yet implemented */
                        break;
                }
            }
        } catch (NoSuchAlgorithmException ex) {
            LOGGER.error(ex.getMessage(), ex);
        } catch (InvalidKeyException ex) {
            LOGGER.error(ex.getMessage(), ex);
        } catch (GeneralSecurityException ex) {
            LOGGER.error(ex.getMessage(), ex);
        }
        return false;
    }

    public boolean hasValidOtp() {
        return (this.otp != null && this.otp.getSecret() != null);
    }
    
    public void setOtp(OTPUserConfiguration otp) {
        this.otp = otp;
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public void setConfirmed(boolean confirmed) {
        this.confirmed = confirmed;
    }

    public boolean isCleared() {
        return cleared;
    }

    public void setCleared(boolean cleared) {
        this.cleared = cleared;
    }

    public Locale getUserLocale() {
        return userLocale;
    }

    public void setUserLocale(Locale userLocale) {
        this.userLocale = userLocale;
    }

    public Long getChallenge() {
        if (challenge == null) {
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
            challenge = random.nextLong() % (10 ^ 6);
        }
        return challenge;
    }

    public void setChallenge(Long challenge) {
        this.challenge = challenge;
    }
}
