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

package password.pwm.i18n;

import password.pwm.PwmConstants;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;

public enum PwmLocaleBundle
{
    DISPLAY( Display.class, false ),
    ERRORS( Error.class, false ),
    MESSAGE( Message.class, false ),

    CONFIG( Config.class, true ),
    ADMIN( Admin.class, true ),
    HEALTH( Health.class, true ),;

    private final Class<? extends PwmDisplayBundle> theClass;
    private final boolean adminOnly;
    private Set<String> keys;

    PwmLocaleBundle( final Class<? extends PwmDisplayBundle> theClass, final boolean adminOnly )
    {
        this.theClass = theClass;
        this.adminOnly = adminOnly;
    }

    public Class<? extends PwmDisplayBundle> getTheClass( )
    {
        return theClass;
    }

    public boolean isAdminOnly( )
    {
        return adminOnly;
    }

    public Set<String> getKeys( )
    {
        if ( keys == null )
        {
            final ResourceBundle defaultBundle = ResourceBundle.getBundle( this.getTheClass().getName(), PwmConstants.DEFAULT_LOCALE );
            keys = Collections.unmodifiableSet( new HashSet<>( defaultBundle.keySet() ) );
        }
        return keys;
    }

    public static Collection<PwmLocaleBundle> allValues( )
    {
        return Collections.unmodifiableList( Arrays.asList( PwmLocaleBundle.values() ) );
    }

    public static Collection<PwmLocaleBundle> userFacingValues( )
    {
        final List<PwmLocaleBundle> returnValue = new ArrayList<>( allValues() );
        for ( final Iterator<PwmLocaleBundle> iter = returnValue.iterator(); iter.hasNext(); )
        {
            if ( iter.next().isAdminOnly() )
            {
                iter.remove();
            }
        }
        return Collections.unmodifiableList( returnValue );
    }
}
