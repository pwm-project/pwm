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
import password.pwm.util.json.JsonFactory;
import password.pwm.util.logging.PwmLogger;

import java.util.Optional;

public class PwmJsonFormatter implements OtpFormatter
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( PwmJsonFormatter.class );

    @Override
    public Optional<OTPUserRecord> parseStringRecord(
            final SessionLabel sessionLabel,
            final PwmDomain pwmDomain,
            final String value
    )
    {
        try
        {
            return Optional.of( JsonFactory.get().deserialize( value, OTPUserRecord.class ) );
        }
        catch ( final Exception e )
        {
            LOGGER.trace( sessionLabel, () -> "error decoding stored OTP format as JSON: " + e.getMessage() );
        }

        return Optional.empty();
    }

    @Override
    public String stringifyRecord( final OTPUserRecord otpUserRecord )
    {
        return JsonFactory.get().serialize( otpUserRecord );
    }
}
