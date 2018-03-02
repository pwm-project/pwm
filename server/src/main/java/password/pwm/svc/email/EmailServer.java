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

package password.pwm.svc.email;

import lombok.Builder;
import lombok.Value;
import password.pwm.util.PasswordData;
import password.pwm.util.java.StringUtil;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

@Value
@Builder
public class EmailServer
{
    private final String id;
    private final String host;
    private final int port;
    private final String username;
    private final PasswordData password;
    private final Properties javaMailProps;
    private final javax.mail.Session session;

    public String toDebugString()
    {
        final Map<String, String> debugProps = new LinkedHashMap<>(  );
        debugProps.put( "id", id );
        debugProps.put( "host", host );
        debugProps.put( "port", String.valueOf( port ) );
        if ( !StringUtil.isEmpty( username ) )
        {
            debugProps.put( "username", username );
        }
        return StringUtil.mapToString( debugProps );
    }
}
