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

package password.pwm.bean;

import com.google.gson.annotations.SerializedName;
import lombok.Data;
import password.pwm.PwmConstants;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.ldap.auth.AuthenticationType;
import password.pwm.ldap.auth.PwmAuthenticationSource;
import password.pwm.util.BasicAuthInfo;
import password.pwm.util.PasswordData;
import password.pwm.util.java.JsonUtil;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


/**
 * <p>This bean is synchronized across application sessions by {@code SessionLoginProvider}.</p>
 *
 * <p>Short serialized names are used to shrink the effective size of the login cookie.</p>
 */
@Data
public class LoginInfoBean implements Serializable
{

    public enum LoginFlag
    {
        skipOtp,
        skipNewPw,
        skipSetupCr,

        // bypass sso
        noSso,
        authRecordSet,
        forcePwChange
    }

    @SerializedName( "u" )
    private UserIdentity userIdentity;

    @SerializedName( "a" )
    private boolean authenticated;

    @SerializedName( "p" )
    private PasswordData userCurrentPassword;

    @SerializedName( "t" )
    private AuthenticationType type = AuthenticationType.UNAUTHENTICATED;

    @SerializedName( "af" )
    private List<AuthenticationType> authFlags = new ArrayList<>();

    @SerializedName( "as" )
    private PwmAuthenticationSource authSource;

    @SerializedName( "at" )
    private Instant authTime;

    @SerializedName( "rq" )
    private Instant reqTime;

    @SerializedName( "g" )
    private String guid;

    @SerializedName( "ba" )
    private BasicAuthInfo basicAuth;

    @SerializedName( "oe" )
    private Instant oauthExp;

    @SerializedName( "or" )
    private String oauthRefToken;

    @SerializedName( "c" )
    private int reqCounter;

    @SerializedName( "lf" )
    private Set<LoginFlag> loginFlags = new HashSet<>();

    public boolean isLoginFlag( final LoginFlag loginStateFlag )
    {
        return loginFlags.contains( loginStateFlag );
    }

    public void setFlag( final LoginFlag loginFlag )
    {
        loginFlags.add( loginFlag );
    }

    public void removeFlag( final LoginFlag loginFlag )
    {
        loginFlags.remove( loginFlag );
    }

    public String toDebugString( ) throws PwmUnrecoverableException
    {
        final LoginInfoBean debugLoginCookieBean = JsonUtil.cloneUsingJson( this, LoginInfoBean.class );
        debugLoginCookieBean.setUserCurrentPassword( new PasswordData( PwmConstants.LOG_REMOVED_VALUE_REPLACEMENT ) );
        return JsonUtil.serialize( debugLoginCookieBean );
    }
}
