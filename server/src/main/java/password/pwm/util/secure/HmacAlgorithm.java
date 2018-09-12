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

enum HmacAlgorithm
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
