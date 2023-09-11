/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2021 The PWM Project
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

package password.pwm.svc.otp.formatter;

import password.pwm.PwmDomain;
import password.pwm.bean.SessionLabel;
import password.pwm.svc.otp.OTPUserRecord;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author mpieters
 */
public class UrlOtpFormatter implements OtpFormatter
{
    private static final String OTP_URL_PATTERN = "^otpauth:\\/\\/(totp|hotp)\\/(.*)\\?secret=([A-Z2-7\\=]{16})$";
    private static final int OTP_URL_GROUPS = 3;
    private static final int OTP_URL_TYPE = 1;
    private static final int OTP_URL_IDENT = 2;
    private static final int OTP_URL_SECRET = 3;

    /**
     * Convert a OTPUserRecord object into an otpauth:// url.
     * @param otpUserRecord a valid otp user record
     *
     * @return a valid otp url string.
     */
    public String stringifyRecord( final OTPUserRecord otpUserRecord )
    {
        final String ident = otpUserRecord.getIdentifier();
        final String secret = otpUserRecord.getSecret();
        final String otptype = otpUserRecord.getType().toString();

        return String.format( "otpauth://%s/%s?secret=%s", otptype.toLowerCase(), ident, secret );
    }

    /**
     * Read a string with an otpauth:// url and convert to an OTPUserRecord object.
     *
     * @param value otp input url string.
     * @return a user recorded generated from the input string.
     */
    @Override
    public Optional<OTPUserRecord> parseStringRecord(
            final SessionLabel sessionLabel,
            final PwmDomain pwmDomain,
            final String value
    )
    {
        final Pattern pattern = Pattern.compile( OTP_URL_PATTERN );
        final Matcher matcher = pattern.matcher( value );
        if ( matcher.matches() && matcher.groupCount() == OTP_URL_GROUPS )
        {
            final String type = matcher.group( OTP_URL_TYPE );
            final String ident = matcher.group( OTP_URL_IDENT );
            final String secret = matcher.group( OTP_URL_SECRET );
            final OTPUserRecord otp = new OTPUserRecord();
            otp.setType( OTPUserRecord.Type.valueOf( type.toUpperCase() ) );
            otp.setIdentifier( ident );
            otp.setSecret( secret );
            return Optional.of( otp );
        }
        return Optional.empty();
    }
}
