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

package password.pwm.util.secure;

import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmInternalException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public enum PwmHashAlgorithm
{
    MD5( "MD5", 32 ),
    SHA1( "SHA1", 40 ),
    SHA256( "SHA-256", 64 ),
    SHA512( "SHA-512", 128 ),
    SHA3_256( "SHA3-256", 64 ),;

    private final String algName;
    private final int hexValueLength;

    PwmHashAlgorithm( final String algName, final int hexValueLength )
    {
        this.algName = algName;
        this.hexValueLength = hexValueLength;
    }

    public String getAlgName( )
    {
        return algName;
    }

    public int getHexValueLength()
    {
        return hexValueLength;
    }

    public MessageDigest newMessageDigest()
    {
        try
        {
            return MessageDigest.getInstance( getAlgName() );
        }
        catch ( final NoSuchAlgorithmException e )
        {
            final String errorMsg = "missing hash algorithm: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_CRYPT_ERROR, errorMsg );
            throw new PwmInternalException( errorInformation );
        }
    }
}
