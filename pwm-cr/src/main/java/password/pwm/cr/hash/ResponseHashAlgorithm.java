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

package password.pwm.cr.hash;

public enum ResponseHashAlgorithm
{
    TEXT( TextHashMachine.class ),
    MD5( TypicalHashMachine.class ),
    SHA1( TypicalHashMachine.class ),
    SHA1_SALT( TypicalHashMachine.class ),
    SHA256_SALT( TypicalHashMachine.class ),
    SHA512_SALT( TypicalHashMachine.class ),
    //    BCRYPT(),
//    SCRYPT(),
    PBKDF2( PBKDF2HashMachine.class ),
    PBKDF2_SHA256( PBKDF2HashMachine.class ),
    PBKDF2_SHA512( PBKDF2HashMachine.class ),;

    private final Class<? extends ResponseHashMachineSpi> implementingClass;

    ResponseHashAlgorithm( final Class<? extends ResponseHashMachineSpi> responseHashMachineSpi )
    {
        this.implementingClass = responseHashMachineSpi;
    }

    public Class<? extends ResponseHashMachineSpi> getImplementingClass( )
    {
        return implementingClass;
    }
}
