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

package password.pwm.svc.cache;

import password.pwm.bean.UserIdentity;
import password.pwm.util.java.JavaHelper;

import java.util.Objects;

public record CacheKey(
        Class<?> srcClass,
        UserIdentity userIdentity,
        String valueID
)
{
    public CacheKey( final Class<?> srcClass, final UserIdentity userIdentity, final String valueID )
    {
        this.srcClass = Objects.requireNonNull( srcClass, "srcClass can not be null" );
        this.userIdentity = userIdentity;
        this.valueID = JavaHelper.requireNonEmpty( valueID );
    }

    public static CacheKey newKey(
            final Class<?> srcClass,
            final UserIdentity userIdentity,
            final String valueID
    )
    {
        return new CacheKey( srcClass, userIdentity, valueID );
    }
}
