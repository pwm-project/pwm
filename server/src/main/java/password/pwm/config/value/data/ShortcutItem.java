/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2021 The PWM Project
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

import password.pwm.bean.SessionLabel;
import password.pwm.util.logging.PwmLogger;

import java.net.URI;

public record ShortcutItem(
        String label,
        URI shortcutURI,
        String ldapQuery,
        String description
)
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( ShortcutItem.class );

    public static ShortcutItem parsePwmConfigInput( final String input, final SessionLabel sessionLabel )
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
                LOGGER.warn( sessionLabel, () -> "malformed ShortcutItem configuration value of '" + input + "', " + e.getMessage() );
            }
        }
        throw new IllegalArgumentException( "malformed ShortcutItem configuration value of '" + input + "'" );
    }


    public static ShortcutItem parseHeaderInput( final String input, final SessionLabel sessionLabel )
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
                LOGGER.warn( sessionLabel, () -> "malformed ShortcutItem configuration value of '" + input + "', " + e.getMessage() );
            }
        }
        throw new IllegalArgumentException( "malformed ShortcutItem configuration value of '" + input + "'" );
    }
}
