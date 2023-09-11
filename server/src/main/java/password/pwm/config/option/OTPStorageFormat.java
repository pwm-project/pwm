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

package password.pwm.config.option;

import password.pwm.svc.otp.formatter.Base32Formatter;
import password.pwm.svc.otp.formatter.OtpFormatter;
import password.pwm.svc.otp.formatter.PamOtpFormatter;
import password.pwm.svc.otp.formatter.PwmJsonFormatter;
import password.pwm.svc.otp.formatter.UrlOtpFormatter;
import password.pwm.util.java.EnumUtil;

import java.util.Objects;

/**
 * One Time Password Storage Format, ordered by de-formatting order attempting.
 */
public enum OTPStorageFormat
{

    PWM( new PwmJsonFormatter(), Flag.useRecoveryCodes, Flag.hashRecoveryCodes ),
    PAM( new PamOtpFormatter(), Flag.useRecoveryCodes ),
    OTPURL( new UrlOtpFormatter() ),
    BASE32SECRET( new Base32Formatter() );

    private final boolean useRecoveryCodes;
    private final boolean hashRecoveryCodes;
    private final OtpFormatter formatter;

    private enum Flag
    {
        useRecoveryCodes,
        hashRecoveryCodes,
    }

    OTPStorageFormat( final OtpFormatter formatter, final Flag... flags )
    {
        this.useRecoveryCodes = EnumUtil.enumArrayContainsValue( flags, Flag.useRecoveryCodes );
        this.hashRecoveryCodes = EnumUtil.enumArrayContainsValue( flags, Flag.hashRecoveryCodes );
        this.formatter = Objects.requireNonNull( formatter );
    }

    public boolean supportsRecoveryCodes( )
    {
        return useRecoveryCodes;
    }

    public boolean supportsHashedRecoveryCodes( )
    {
        return useRecoveryCodes && hashRecoveryCodes;
    }

    public OtpFormatter getFormatter()
    {
        return formatter;
    }
}
