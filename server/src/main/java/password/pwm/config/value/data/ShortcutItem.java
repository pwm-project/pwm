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

package password.pwm.config.value.data;

import lombok.Value;
import password.pwm.util.logging.PwmLogger;

import java.io.Serializable;
import java.net.URI;

@Value
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
            catch ( final Exception e )
            {
                LOGGER.warn( () -> "malformed ShortcutItem configuration value of '" + input + "', " + e.getMessage() );
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
            catch ( final Exception e )
            {
                LOGGER.warn( () -> "malformed ShortcutItem configuration value of '" + input + "', " + e.getMessage() );
            }
        }
        throw new IllegalArgumentException( "malformed ShortcutItem configuration value of '" + input + "'" );
    }
}
