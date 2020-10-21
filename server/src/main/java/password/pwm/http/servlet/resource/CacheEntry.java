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

package password.pwm.http.servlet.resource;

import password.pwm.http.bean.ImmutableByteArray;

import java.io.Serializable;
import java.util.Map;

final class CacheEntry implements Serializable
{
    private final ImmutableByteArray entity;
    private final Map<String, String> headerStrings;

    CacheEntry( final ImmutableByteArray entity, final Map<String, String> headerStrings )
    {
        this.entity = entity;
        this.headerStrings = headerStrings;
    }

    public ImmutableByteArray getEntity( )
    {
        return entity;
    }

    public Map<String, String> getHeaderStrings( )
    {
        return headerStrings;
    }
}
