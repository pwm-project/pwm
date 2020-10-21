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

package password.pwm.util.secure;

import password.pwm.PwmConstants;

import java.util.Arrays;

/**
 * A set of predefined arrangements of block cryptography.  Each item has several associated parameters that define the overall implementation.
 * This approach precludes the operator from having to select encryption combinations that are compatible and effective.
 */
public enum PwmBlockAlgorithm
{
    // legacy format predates prefixing
    AES(
            "AES",
            PwmSecurityKey.Type.AES,
            null,
            new byte[ 0 ],
            "AES128"
    ),

    AES128_HMAC256(
            "AES",
            PwmSecurityKey.Type.AES,
            HmacAlgorithm.HMAC_SHA_256,
            "PWM.AES128_HMAC256".getBytes( PwmConstants.DEFAULT_CHARSET ),
            "AES128+Hmac256"
    ),

    AES256_HMAC512(
            "AES",
            PwmSecurityKey.Type.AES_256,
            HmacAlgorithm.HMAC_SHA_512,
            "PWM.AES256_HMAC512".getBytes( PwmConstants.DEFAULT_CHARSET ),
            "AES256+Hmac512"
    ),

    // legacy format predates prefixing
    CONFIG(
            "AES",
            PwmSecurityKey.Type.AES,
            null,
            new byte[ 0 ],
            "PWM.Config.AES"
    ),

    AES128_GCM(
            "AES/GCM/NoPadding",
            PwmSecurityKey.Type.AES,
            null,
            "PWM.GCM1".getBytes( PwmConstants.DEFAULT_CHARSET ),
            "AES128+GCM"
    ),;

    /**
     * Java block crypto algorithm.
     */
    private final String algName;

    /**
     * SecretKey type needed for this item.
     */
    private final PwmSecurityKey.Type blockKey;

    /**
     * HMAC algorithm needed for this item, if any.
     */
    private final HmacAlgorithm hmacAlgorithm;

    /**
     * Prefix that is prepended to encryption output methods and expected to be prepended to input methods.  The prefix is not itself secured and must be treated
     * appropriately.
     */
    private final byte[] prefix;

    /**
     * Debug label to show for debugging or for operator administration.
     */
    private final String label;

    PwmBlockAlgorithm( final String algName, final PwmSecurityKey.Type blockKey, final HmacAlgorithm hmacAlgorithm, final byte[] prefix, final String label )
    {
        this.algName = algName;
        this.blockKey = blockKey;
        this.hmacAlgorithm = hmacAlgorithm;
        this.prefix = prefix;
        this.label = label;
    }

    public String getAlgName( )
    {
        return algName;
    }

    PwmSecurityKey.Type getBlockKey( )
    {
        return blockKey;
    }

    HmacAlgorithm getHmacAlgorithm( )
    {
        return hmacAlgorithm;
    }

    public byte[] getPrefix( )
    {
        return Arrays.copyOf( prefix, prefix.length );
    }

    public String getLabel( )
    {
        return label;
    }

}
