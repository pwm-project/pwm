/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2020 The PWM Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package password.pwm.util.operations.otp;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author mpieters
 */
public class OTPUrlUtil
{

    public static final String OTP_URL_PATTERN = "^otpauth:\\/\\/(totp|hotp)\\/(.*)\\?secret=([A-Z2-7\\=]{16})$";
    public static final int OTP_URL_GROUPS = 3;
    public static final int OTP_URL_TYPE = 1;
    public static final int OTP_URL_IDENT = 2;
    public static final int OTP_URL_SECRET = 3;

    /**
     * Convert a OTPUserRecord object into an otpauth:// url.
     * @param otp a valid otp user record
     *
     * @return a valid otp url string.
     */
    public static String composeOtpUrl( final OTPUserRecord otp )
    {
        final String ident = otp.getIdentifier();
        final String secret = otp.getSecret();
        final String otptype = otp.getType().toString();
        final String otpInfo = String.format( "otpauth://%s/%s?secret=%s", otptype.toLowerCase(), ident, secret );
        return otpInfo;
    }

    /**
     * Read a string with an otpauth:// url and convert to an OTPUserRecord object.
     *
     * @param otpInfo otp input url string.
     * @return a user recorded generated from the input string.
     */
    public static OTPUserRecord decomposeOtpUrl( final String otpInfo )
    {
        OTPUserRecord otp = null;
        final Pattern pattern = Pattern.compile( OTP_URL_PATTERN );
        final Matcher matcher = pattern.matcher( otpInfo );
        if ( matcher.matches() && matcher.groupCount() == OTP_URL_GROUPS )
        {
            final String type = matcher.group( OTP_URL_TYPE );
            final String ident = matcher.group( OTP_URL_IDENT );
            final String secret = matcher.group( OTP_URL_SECRET );
            otp = new OTPUserRecord();
            otp.setType( OTPUserRecord.Type.valueOf( type.toUpperCase() ) );
            otp.setIdentifier( ident );
            otp.setSecret( secret );
        }
        return otp;
    }
}
