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

package password.pwm.svc.token;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public enum TokenType
{
    FORGOTTEN_PW( "password.pwm.servlet.ForgottenPasswordServlet" ),
    ACTIVATION( "password.pwm.servlet.ActivateUserServlet" ),
    UPDATE( ),
    NEWUSER( ),;

    private final Set<String> otherNames;

    TokenType( final String... otherNames )
    {
        final Set<String> otherNamesSet = new HashSet<>();
        if ( otherNames != null )
        {
            otherNamesSet.addAll( Arrays.asList( otherNames ) );
        }
        otherNamesSet.add( getName() );
        this.otherNames = Collections.unmodifiableSet( otherNamesSet );
    }

    public String getName( )
    {
        return this.toString();
    }

    public boolean matchesName( final String input )
    {
        return otherNames.contains( input );
    }
}
