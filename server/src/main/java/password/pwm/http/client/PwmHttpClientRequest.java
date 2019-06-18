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
