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

package password.pwm.http.client;

import lombok.Value;
import password.pwm.http.HttpMethod;
import password.pwm.util.java.StringUtil;

import java.io.Serializable;
import java.net.URI;
import java.util.Map;

@Value
public class PwmHttpClientRequest implements Serializable
{
    private final HttpMethod method;
    private final String url;
    private final String body;
    private final Map<String, String> headers;

    public String toDebugString( final PwmHttpClient pwmHttpClient, final String additionalText )
    {
        final String topLine = "HTTP " + method + " request to " + url
                + ( StringUtil.isEmpty( additionalText )
                ? ""
                : " " + additionalText );
        return pwmHttpClient.entityToDebugString( topLine, headers, body );
    }

    public boolean isHttps()
    {
        return "https".equals( URI.create( getUrl() ).getScheme() );
    }
}
