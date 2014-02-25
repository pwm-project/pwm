/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
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

    public static String composeOtpUrl(OTPUserConfiguration otp) {
        String ident = otp.getIdentifier();
        String secret = otp.getSecret();
        String otptype = otp.getType().toString();
        String otpInfo = String.format("otpauth://%s/%s?secret=%s", otptype.toLowerCase(), ident, secret);
        return otpInfo;
    }
    
    public static OTPUserConfiguration decomposeOtpUrl(String otpInfo) {
        OTPUserConfiguration otp = null;
        Pattern pattern = Pattern.compile(OTP_URL_PATTERN);
        Matcher matcher = pattern.matcher(otpInfo);
        if (matcher.matches() && matcher.groupCount() == OTP_URL_GROUPS) {
            String type = matcher.group(OTP_URL_TYPE);
            String ident = matcher.group(OTP_URL_IDENT);
            String secret = matcher.group(OTP_URL_SECRET);
            otp = new OTPUserConfiguration();
            otp.setType(OTPUserConfiguration.Type.valueOf(type.toUpperCase()));
            otp.setIdentifier(ident);
            otp.setSecret(secret);
        }
        return otp;
    }
}
