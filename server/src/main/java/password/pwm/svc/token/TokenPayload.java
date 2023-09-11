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

package password.pwm.svc.token;

import com.google.gson.annotations.SerializedName;
import password.pwm.bean.TokenDestinationItem;
import password.pwm.bean.UserIdentity;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.json.JsonFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public record TokenPayload(
        @SerializedName( "n" )
        String name,

        @SerializedName( "e" )
        Instant expiration,

        @SerializedName( "t" )
        Instant issueTime,

        @SerializedName( "p" )
        Map<String, String> data,

        @SerializedName( "user" )
        UserIdentity userIdentity,

        @SerializedName( "d" )
        TokenDestinationItem destination,

        @SerializedName( "g" )
        String guid
)
{
    public TokenPayload(
            final String name,
            final Instant expiration,
            final Instant issueTime,
            final Map<String, String> data,
            final UserIdentity userIdentity,
            final TokenDestinationItem destination,
            final String guid
    )
    {
        this.name = name;
        this.expiration = Objects.requireNonNull( expiration );
        this.issueTime = Objects.requireNonNull( issueTime );
        this.data = CollectionUtil.stripNulls( data );
        this.userIdentity = userIdentity;
        this.destination = destination;
        this.guid = guid;
    }


    public String toDebugString( )
    {
        final Map<String, String> debugMap = new HashMap<>();
        debugMap.put( "issueTime", StringUtil.toIsoDate( issueTime ) );
        debugMap.put( "expiration", StringUtil.toIsoDate( expiration ) );
        debugMap.put( "name", name() );
        if ( userIdentity() != null )
        {
            debugMap.put( "user", userIdentity().toDisplayString() );
        }
        debugMap.put( "guid", guid() );
        return JsonFactory.get().serializeMap( debugMap );
    }
}
