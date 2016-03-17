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
import java.util.*;


/**
 * This bean is synchronized across application sessions by {@link password.pwm.http.state.SessionLoginProvider}
 */
public class LoginInfoBean implements Serializable {

    public enum LoginFlag {
        skipOtp,
        skipNewPw,
        noSso, // bypass sso
        authRecordSet,
    }

    private UserIdentity user;
    private boolean auth;
    private PasswordData pw;

    private AuthenticationType type = AuthenticationType.UNAUTHENTICATED;
    private List<AuthenticationType> authFlags = new ArrayList<>();
    private PwmAuthenticationSource authSource;
    private Date authTime;
    private Date reqTime;

    private String guid;

    private BasicAuthInfo basicAuth;

    private Date oauthExp;
    private String oauthRefToken;
    
    private int reqCounter;

    private Set<LoginFlag> loginFlags = new HashSet<>();

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

    public Date getOauthExp()
    {
        return oauthExp;
    }

    public void setOauthExp(Date oauthExp)
    {
        this.oauthExp = oauthExp;
    }

    public String getOauthRefToken()
    {
        return oauthRefToken;
    }

    public void setOauthRefToken(String oauthRefToken)
    {
        this.oauthRefToken = oauthRefToken;
    }

    public List<AuthenticationType> getAuthFlags() {
        return authFlags;
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

    public int getReqCounter() {
        return reqCounter;
    }

    public void setReqCounter(int reqCounter) {
        this.reqCounter = reqCounter;
    }

    public UserIdentity getUserIdentity() {
        return user;
    }

    public void setUserIdentity(UserIdentity userIdentity) {
        this.user = userIdentity;
    }

    public boolean isAuthenticated() {
        return auth;
    }

    public void setAuthenticated(boolean authenticated) {
        this.auth = authenticated;
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

    public boolean isLoginFlag(LoginFlag loginStateFlag) {
        return loginFlags.contains(loginStateFlag);
    }

    public void setFlag(final LoginFlag loginFlag) {
        loginFlags.add(loginFlag);
    }

    public void removeFlag(final LoginFlag loginFlag) {
        loginFlags.remove(loginFlag);
    }

    public String toDebugString() throws PwmUnrecoverableException {
        final LoginInfoBean debugLoginCookieBean = JsonUtil.cloneUsingJson(this, LoginInfoBean.class);
        debugLoginCookieBean.setUserCurrentPassword(new PasswordData(PwmConstants.LOG_REMOVED_VALUE_REPLACEMENT));
        return JsonUtil.serialize(debugLoginCookieBean);
    }
}
