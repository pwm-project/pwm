/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2016 The PWM Project
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

import password.pwm.PwmConstants;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.ldap.auth.AuthenticationType;
import password.pwm.ldap.auth.PwmAuthenticationSource;
import password.pwm.util.BasicAuthInfo;
import password.pwm.util.JsonUtil;
import password.pwm.util.PasswordData;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class LoginInfoBean implements Serializable {

    private UserIdentity userIdentity;
    private boolean authenticated;
    private PasswordData pw;

    private AuthenticationType type = AuthenticationType.UNAUTHENTICATED;
    private List<AuthenticationType> flags = new ArrayList<>();
    private PwmAuthenticationSource authSource;
    private Date authTime;
    private Date reqTime;

    private String guid;

    private BasicAuthInfo basicAuth;

    private Date oauthExpiration;
    private transient String oauthRefreshToken;
    
    private boolean authRecordCookieSet;
    private int postReqCounter;

    public Date getAuthTime()
    {
        return authTime;
    }

    public void setAuthTime(final Date authTime)
    {
        this.authTime = authTime;
    }

    public AuthenticationType getType()
    {
        return type;
    }

    public void setType(AuthenticationType type)
    {
        this.type = type;
    }

    public PasswordData getUserCurrentPassword()
    {
        return pw;
    }

    public void setUserCurrentPassword(PasswordData userCurrentPassword)
    {
        this.pw = userCurrentPassword;
    }

    public BasicAuthInfo getBasicAuth()
    {
        return basicAuth;
    }

    public void setBasicAuth(final BasicAuthInfo basicAuth)
    {
        this.basicAuth = basicAuth;
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

    public List<AuthenticationType> getFlags() {
        return flags;
    }

    public PwmAuthenticationSource getAuthSource() {
        return authSource;
    }

    public void setAuthSource(PwmAuthenticationSource authSource) {
        this.authSource = authSource;
    }

    public String getGuid() {
        return guid;
    }

    public void setGuid(String guid) {
        this.guid = guid;
    }

    public int getPostReqCounter() {
        return postReqCounter;
    }

    public void setPostReqCounter(int postReqCounter) {
        this.postReqCounter = postReqCounter;
    }

    public UserIdentity getUserIdentity() {
        return userIdentity;
    }

    public void setUserIdentity(UserIdentity userIdentity) {
        this.userIdentity = userIdentity;
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public void setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
    }

    public PasswordData getPw() {
        return pw;
    }

    public void setPw(PasswordData pw) {
        this.pw = pw;
    }

    public Date getReqTime() {
        return reqTime;
    }

    public void setReqTime(Date reqTime) {
        this.reqTime = reqTime;
    }

    public String toDebugString() throws PwmUnrecoverableException {
        final LoginInfoBean debugLoginCookieBean = JsonUtil.cloneUsingJson(this, LoginInfoBean.class);
        debugLoginCookieBean.setUserCurrentPassword(new PasswordData(PwmConstants.LOG_REMOVED_VALUE_REPLACEMENT));
        return JsonUtil.serialize(debugLoginCookieBean);
    }
}
