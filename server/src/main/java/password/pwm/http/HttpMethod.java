/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2020 The PWM Project
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

package password.pwm.http;

import java.util.Optional;

public enum HttpMethod
{
    POST( false, true ),
    GET( true, false ),
    DELETE( false, true ),
    PUT( false, true ),
    PATCH( false, true ),;

    private final boolean idempotent;
    private final boolean hasBody;

    HttpMethod( final boolean idempotent, final boolean hasBody )
    {
        this.hasBody = hasBody;
        this.idempotent = idempotent;
    }

    public static Optional<HttpMethod> fromString( final String input )
    {
        for ( final HttpMethod method : HttpMethod.values() )
        {
            if ( method.toString().equalsIgnoreCase( input ) )
            {
                return Optional.of( method );
            }
        }
        return Optional.empty();
    }

    public boolean isIdempotent( )
    {
        return idempotent;
    }

    public boolean isHasBody( )
    {
        return hasBody;
    }
}
