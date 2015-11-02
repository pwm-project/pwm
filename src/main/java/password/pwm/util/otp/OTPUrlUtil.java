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

package password.pwm.util.otp;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author mpieters
 */
public class OTPUrlUtil {
    
    public final static String OTP_URL_PATTERN = "^otpauth:\\/\\/(totp|hotp)\\/(.*)\\?secret=([A-Z2-7\\=]{16})$";
    public final static int OTP_URL_GROUPS = 3;
    public final static int OTP_URL_TYPE = 1;
    public final static int OTP_URL_IDENT = 2;
    public final static int OTP_URL_SECRET = 3;

    /**
     * Convert a OTPUserRecord object into an otpauth:// url.
     * 
     * @param otp
     * @return 
     */
    public static String composeOtpUrl(OTPUserRecord otp) {
        String ident = otp.getIdentifier();
        String secret = otp.getSecret();
        String otptype = otp.getType().toString();
        String otpInfo = String.format("otpauth://%s/%s?secret=%s", otptype.toLowerCase(), ident, secret);
        return otpInfo;
    }
    
    /**
     * Read a string with an otpauth:// url and convert to an OTPUserRecord object.
     * 
     * @param otpInfo
     * @return 
     */
    public static OTPUserRecord decomposeOtpUrl(String otpInfo) {
        OTPUserRecord otp = null;
        Pattern pattern = Pattern.compile(OTP_URL_PATTERN);
        Matcher matcher = pattern.matcher(otpInfo);
        if (matcher.matches() && matcher.groupCount() == OTP_URL_GROUPS) {
            String type = matcher.group(OTP_URL_TYPE);
            String ident = matcher.group(OTP_URL_IDENT);
            String secret = matcher.group(OTP_URL_SECRET);
            otp = new OTPUserRecord();
            otp.setType(OTPUserRecord.Type.valueOf(type.toUpperCase()));
            otp.setIdentifier(ident);
            otp.setSecret(secret);
        }
        return otp;
    }
}
