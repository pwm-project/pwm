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

        for ( final PwmLdapVendor vendor : PwmLdapVendor.values() )
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
        for ( final PwmLdapVendor vendor : PwmLdapVendor.values() )
        {
            if ( vendor.chaiVendor == directoryVendor )
            {
                return vendor;
            }
        }
        return null;
    }
}
