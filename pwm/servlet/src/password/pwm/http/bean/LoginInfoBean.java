/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2015 The PWM Project
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

package password.pwm.http.bean;

import password.pwm.ldap.auth.AuthenticationType;
import password.pwm.ldap.auth.PwmAuthenticationSource;
import password.pwm.util.BasicAuthInfo;
import password.pwm.util.PasswordData;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class LoginInfoBean implements PwmSessionBean {
    private transient PasswordData userCurrentPassword;

    private AuthenticationType authenticationType = AuthenticationType.UNAUTHENTICATED;
    private List<AuthenticationType> authenticationFlags = new ArrayList<>();
    private PwmAuthenticationSource authenticationSource;
    private Date localAuthTime;

    private transient BasicAuthInfo originalBasicAuthInfo;

    private Date oauthExpiration;
    private transient String oauthRefreshToken;
    
    private boolean authRecordCookieSet;
    
    public Date getLocalAuthTime()
    {
        return localAuthTime;
    }

    public void setLocalAuthTime(final Date localAuthTime)
    {
        this.localAuthTime = localAuthTime;
    }

    public AuthenticationType getAuthenticationType()
    {
        return authenticationType;
    }

    public void setAuthenticationType(AuthenticationType authenticationType)
    {
        this.authenticationType = authenticationType;
    }

    public PasswordData getUserCurrentPassword()
    {
        return userCurrentPassword;
    }

    public void setUserCurrentPassword(PasswordData userCurrentPassword)
    {
        this.userCurrentPassword = userCurrentPassword;
    }

    public BasicAuthInfo getOriginalBasicAuthInfo()
    {
        return originalBasicAuthInfo;
    }

    public void setOriginalBasicAuthInfo(final BasicAuthInfo originalBasicAuthInfo)
    {
        this.originalBasicAuthInfo = originalBasicAuthInfo;
    }

    public Date getOauthExpiration()
    {
        return oauthExpiration;
    }

    public void setOauthExpiration(Date oauthExpiration)
    {
        this.oauthExpiration = oauthExpiration;
    }

    public String getOauthRefreshToken()
    {
        return oauthRefreshToken;
    }

    public void setOauthRefreshToken(String oauthRefreshToken)
    {
        this.oauthRefreshToken = oauthRefreshToken;
    }

    public boolean isAuthRecordCookieSet() {
        return authRecordCookieSet;
    }

    public void setAuthRecordCookieSet(boolean authRecordCookieSet) {
        this.authRecordCookieSet = authRecordCookieSet;
    }

    public List<AuthenticationType> getAuthenticationFlags() {
        return authenticationFlags;
    }

    public PwmAuthenticationSource getAuthenticationSource() {
        return authenticationSource;
    }

    public void setAuthenticationSource(PwmAuthenticationSource authenticationSource) {
        this.authenticationSource = authenticationSource;
    }
}
