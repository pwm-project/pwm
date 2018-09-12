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

package password.pwm.config.value.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import password.pwm.util.logging.PwmLogger;

import java.io.Serializable;
import java.net.URI;

@Getter
@AllArgsConstructor
public class ShortcutItem implements Serializable
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( ShortcutItem.class );

    private final String label;
    private final URI shortcutURI;
    private final String ldapQuery;
    private final String description;

    public String toString( )
    {
        return "ShortcutItem{"
                + "label='" + label + '\''
                + ", shortcutURI=" + shortcutURI
                + ", ldapQuery='" + ldapQuery + '\''
                + ", description='" + description + '\''
                + '}';
    }

    public static ShortcutItem parsePwmConfigInput( final String input )
    {
        if ( input != null && input.length() > 0 )
        {
            try
            {
                final String[] splitSettings = input.split( "::" );
                return new ShortcutItem(
                        splitSettings[ 0 ],
                        URI.create( splitSettings[ 1 ] ),
                        splitSettings[ 2 ],
                        splitSettings[ 3 ]
                );
            }
            catch ( Exception e )
            {
                LOGGER.warn( "malformed ShortcutItem configuration value of '" + input + "', " + e.getMessage() );
            }
        }
        throw new IllegalArgumentException( "malformed ShortcutItem configuration value of '" + input + "'" );
    }


    public static ShortcutItem parseHeaderInput( final String input )
    {
        if ( input != null && input.length() > 0 )
        {
            try
            {
                final String[] splitSettings = input.split( ";;;" );
                return new ShortcutItem(
                        "",
                        URI.create( splitSettings[ 0 ] ),
                        splitSettings[ 1 ],
                        splitSettings[ 2 ]
                );
            }
            catch ( Exception e )
            {
                LOGGER.warn( "malformed ShortcutItem configuration value of '" + input + "', " + e.getMessage() );
            }
        }
        throw new IllegalArgumentException( "malformed ShortcutItem configuration value of '" + input + "'" );
    }
}
