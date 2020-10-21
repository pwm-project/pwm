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

package password.pwm.config.option;

/**
 * One Time Password Storage Format.
 */
public enum OTPStorageFormat
{

    PWM( true, true ),
    BASE32SECRET( false ),
    OTPURL( false ),
    PAM( true, false );

    private final boolean useRecoveryCodes;
    private final boolean hashRecoveryCodes;

    OTPStorageFormat( final boolean useRecoveryCodes )
    {
        this.useRecoveryCodes = useRecoveryCodes;

        // defaults to true, if recovery codes enabled.
        this.hashRecoveryCodes = useRecoveryCodes;
    }

    OTPStorageFormat(
            final boolean useRecoveryCodes,
            final boolean hashRecoveryCodes
    )
    {
        this.useRecoveryCodes = useRecoveryCodes;
        this.hashRecoveryCodes = useRecoveryCodes && hashRecoveryCodes;
    }

    /**
     * Check support for recovery codes.
     *
     * @return true if recovery codes are supported.
     */
    public boolean supportsRecoveryCodes( )
    {
        return useRecoveryCodes;
    }

    /**
     * Check support for hashed recovery codes.
     *
     * @return true if recovery codes are supported and hashes are to be used.
     */
    public boolean supportsHashedRecoveryCodes( )
    {
        return useRecoveryCodes && hashRecoveryCodes;
    }

}
