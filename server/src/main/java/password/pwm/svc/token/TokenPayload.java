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

package password.pwm.svc.token;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import password.pwm.bean.UserIdentity;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;

import java.io.Serializable;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Getter
public class TokenPayload implements Serializable
{
    @SerializedName( "t" )
    private final Instant issueTime;

    @SerializedName( "e" )
    private final Instant expiration;

    @SerializedName( "n" )
    private final String name;

    private final Map<String, String> data;

    @SerializedName( "user" )
    private final UserIdentity userIdentity;

    @SerializedName( "d" )
    private final String destination;

    @SerializedName( "g" )
    private final String guid;

    TokenPayload(
            final String name,
            final Instant expiration,
            final Map<String, String> data,
            final UserIdentity user,
            final String destination,
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
        debugMap.put( "issueTime", JavaHelper.toIsoDate( issueTime ) );
        debugMap.put( "expiration", JavaHelper.toIsoDate( expiration ) );
        debugMap.put( "name", getName() );
        if ( getUserIdentity() != null )
        {
            debugMap.put( "user", getUserIdentity().toDisplayString() );
        }
        debugMap.put( "guid", getGuid() );
        return JsonUtil.serializeMap( debugMap );
    }
}
