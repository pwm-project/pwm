/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2019 The PWM Project
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

public enum HmacAlgorithm
{
    HMAC_SHA_256( "HmacSHA256", PwmSecurityKey.Type.HMAC_256, 32 ),
    HMAC_SHA_512( "HmacSHA512", PwmSecurityKey.Type.HMAC_512, 64 ),;

    private final String algorithmName;
    private final PwmSecurityKey.Type keyType;
    private final int length;

    HmacAlgorithm( final String algorithmName, final PwmSecurityKey.Type keyType, final int length )
    {
        this.algorithmName = algorithmName;
        this.keyType = keyType;
        this.length = length;
    }

    public String getAlgorithmName( )
    {
        return algorithmName;
    }

    public PwmSecurityKey.Type getKeyType( )
    {
        return keyType;
    }

    public int getLength( )
    {
        return length;
    }
}
