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
