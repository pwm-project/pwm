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

package password.pwm.http.servlet.oauth;

import com.google.gson.annotations.SerializedName;
import lombok.Builder;
import lombok.Value;
import password.pwm.util.java.AtomicLoopIntIncrementer;

import java.io.Serializable;
import java.time.Instant;

/*
    This serialized JSON object is passed to the browser during the OAuth request sequence.  The state is forwarded to the OAuth server and then returned (without
    modification when the OAuth server redirects back here.
 */

@Value
@Builder
class OAuthState implements Serializable
{
    private static final AtomicLoopIntIncrementer OAUTH_STATE_ID_COUNTER = new AtomicLoopIntIncrementer();

    @SerializedName( "c" )
    @Builder.Default
    private final int stateID = OAUTH_STATE_ID_COUNTER.next();

    @SerializedName( "t" )
    @Builder.Default
    private final Instant issueTime = Instant.now();

    @SerializedName( "i" )
    private String sessionID;

    @SerializedName( "n" )
    private String nextUrl;

    @SerializedName( "u" )
    private OAuthUseCase useCase;

    @SerializedName( "f" )
    private String forgottenProfileId;

    @SerializedName( "v" )
    private int version = 1;

    static OAuthState newSSOAuthenticationState( final String sessionID, final String nextUrl )
    {
        return OAuthState.builder()
                .sessionID( sessionID )
                .nextUrl( nextUrl )
                .useCase( OAuthUseCase.Authentication )
                .build();
    }

    static OAuthState newForgottenPasswordState( final String sessionID, final String forgottenProfileId )
    {
        return OAuthState.builder()
                .sessionID( sessionID )
                .forgottenProfileId( forgottenProfileId )
                .useCase( OAuthUseCase.ForgottenPassword )
                .build();
    }


}
