/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
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
