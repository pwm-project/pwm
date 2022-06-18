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
import lombok.Value;
import password.pwm.bean.TokenDestinationItem;
import password.pwm.bean.UserIdentity;
import password.pwm.util.java.StringUtil;
import password.pwm.util.json.JsonFactory;

import java.io.Serializable;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Value
public class TokenPayload implements Serializable
{
    @SerializedName( "t" )
    private final Instant issueTime;

    @SerializedName( "e" )
    private final Instant expiration;

    @SerializedName( "n" )
    private final String name;

    @SerializedName( "p" )
    private final Map<String, String> data;

    @SerializedName( "user" )
    private final UserIdentity userIdentity;

    @SerializedName( "d" )
    private final TokenDestinationItem destination;

    @SerializedName( "g" )
    private final String guid;

    TokenPayload(
            final String name,
            final Instant expiration,
            final Map<String, String> data,
            final UserIdentity user,
            final TokenDestinationItem destination,
            final String guid
    )
    {
        this.issueTime = Instant.now();
        this.expiration = expiration;
        this.data = data == null ? Collections.emptyMap() : Collections.unmodifiableMap( data );
        this.name = name;
        this.userIdentity = user;
        this.destination = destination;
        this.guid = guid;
    }


    public String toDebugString( )
    {
        final Map<String, String> debugMap = new HashMap<>();
        debugMap.put( "issueTime", StringUtil.toIsoDate( issueTime ) );
        debugMap.put( "expiration", StringUtil.toIsoDate( expiration ) );
        debugMap.put( "name", getName() );
        if ( getUserIdentity() != null )
        {
            debugMap.put( "user", getUserIdentity().toDisplayString() );
        }
        debugMap.put( "guid", getGuid() );
        return JsonFactory.get().serializeMap( debugMap );
    }
}
