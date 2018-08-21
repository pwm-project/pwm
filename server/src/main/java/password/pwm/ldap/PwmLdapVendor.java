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

package password.pwm.ldap;

import com.novell.ldapchai.provider.DirectoryVendor;

public enum PwmLdapVendor
{
    ACTIVE_DIRECTORY( DirectoryVendor.ACTIVE_DIRECTORY, "MICROSOFT_ACTIVE_DIRECTORY" ),
    EDIRECTORY( DirectoryVendor.EDIRECTORY, "NOVELL_EDIRECTORY" ),
    OPEN_LDAP( DirectoryVendor.OPEN_LDAP ),
    DIRECTORY_SERVER_389( DirectoryVendor.DIRECTORY_SERVER_389 ),
    ORACLE_DS( DirectoryVendor.ORACLE_DS ),
    GENERIC( DirectoryVendor.GENERIC ),;

    private final DirectoryVendor chaiVendor;
    private final String[] otherNames;

    PwmLdapVendor( final DirectoryVendor directoryVendor, final String... otherNames )
    {
        this.chaiVendor = directoryVendor;
        this.otherNames = otherNames;
    }

    public static PwmLdapVendor fromString( final String input )
    {
        if ( input == null )
        {
            return null;
        }

        for ( PwmLdapVendor vendor : PwmLdapVendor.values() )
        {
            if ( vendor.name().equals( input ) )
            {
                return vendor;
            }

            if ( vendor.otherNames != null )
            {
                for ( final String otherName : vendor.otherNames )
                {
                    if ( otherName.equals( input ) )
                    {
                        return vendor;
                    }
                }
            }
        }

        return null;
    }

    public static PwmLdapVendor fromChaiVendor( final DirectoryVendor directoryVendor )
    {
        for ( PwmLdapVendor vendor : PwmLdapVendor.values() )
        {
            if ( vendor.chaiVendor == directoryVendor )
            {
                return vendor;
            }
        }
        return null;
    }
}
