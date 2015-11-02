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

package password.pwm.http.bean;

import password.pwm.util.logging.PwmLogger;
import password.pwm.util.otp.OTPUserRecord;

import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.util.Date;
import java.util.List;

public class SetupOtpBean implements PwmSessionBean {

    private static final PwmLogger LOGGER = PwmLogger.forClass(SetupOtpBean.class);

    private OTPUserRecord otpUserRecord;
    private boolean confirmed;
    private boolean codeSeen;
    private boolean written;
    private List<String> recoveryCodes;
    private Long challenge; // for HOTP only
    private boolean hasPreExistingOtp;

    public SetupOtpBean() {
    }

    public OTPUserRecord getOtpUserRecord() {
        return otpUserRecord;
    }

    public boolean isHasPreExistingOtp()
    {
        return hasPreExistingOtp;
    }

    public void setHasPreExistingOtp(boolean hasPreExistingOtp)
    {
        this.hasPreExistingOtp = hasPreExistingOtp;
    }

    public void setOtpUserRecord(OTPUserRecord otp) {
        this.otpUserRecord = otp;
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public void setConfirmed(boolean confirmed) {
        this.confirmed = confirmed;
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

    public List<String> getRecoveryCodes()
    {
        return recoveryCodes;
    }

    public void setRecoveryCodes(List<String> recoveryCodes)
    {
        this.recoveryCodes = recoveryCodes;
    }

    public boolean isCodeSeen()
    {
        return codeSeen;
    }

    public void setCodeSeen(boolean codeSeen)
    {
        this.codeSeen = codeSeen;
    }

    public boolean isWritten()
    {
        return written;
    }

    public void setWritten(boolean written)
    {
        this.written = written;
    }
}
