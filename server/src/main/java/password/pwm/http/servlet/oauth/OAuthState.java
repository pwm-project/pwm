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

import java.io.Serializable;
import java.util.Date;

/*
    This serialized JSON object is passed to the browser during the OAuth request sequence.  The state is forwarded to the OAuth server and then returned (without
    modification when the OAuth server redirects back here.
 */
class OAuthState implements Serializable
{
    private static int oauthStateIdCounter = 0;

    @SerializedName( "c" )
    private final int stateID = oauthStateIdCounter++;

    @SerializedName( "t" )
    private final Date issueTime = new Date();

    @SerializedName( "i" )
    private String sessionID;

    @SerializedName( "n" )
    private String nextUrl;

    @SerializedName( "u" )
    private OAuthUseCase use;

    @SerializedName( "f" )
    private String forgottenProfileId;

    @SerializedName( "v" )
    private int version = 1;

    private OAuthState( )
    {
    }

    public static int getOauthStateIdCounter( )
    {
        return oauthStateIdCounter;
    }

    public int getStateID( )
    {
        return stateID;
    }

    public Date getIssueTime( )
    {
        return issueTime;
    }

    public String getSessionID( )
    {
        return sessionID;
    }

    public String getNextUrl( )
    {
        return nextUrl;
    }

    public OAuthUseCase getUseCase( )
    {
        return use;
    }

    public int getVersion( )
    {
        return version;
    }

    public String getForgottenProfileId( )
    {
        return forgottenProfileId;
    }

    public static OAuthState newSSOAuthenticationState( final String sessionID, final String nextUrl )
    {
        final OAuthState state = new OAuthState();
        state.sessionID = sessionID;
        state.nextUrl = nextUrl;
        state.use = OAuthUseCase.Authentication;
        return state;
    }

    public static OAuthState newForgottenPasswordState( final String sessionID, final String forgottenProfileId )
    {
        final OAuthState state = new OAuthState();
        state.sessionID = sessionID;
        state.forgottenProfileId = forgottenProfileId;
        state.use = OAuthUseCase.ForgottenPassword;
        return state;
    }


}
