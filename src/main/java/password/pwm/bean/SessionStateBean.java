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

package password.pwm.bean;

import password.pwm.config.ShortcutItem;
import password.pwm.http.bean.PwmSessionBean;
import password.pwm.i18n.Message;
import password.pwm.util.secure.PwmRandom;

import java.util.Date;
import java.util.Locale;
import java.util.Map;

/**
 * Only information that is particular to the http session is stored in the
 * session bean.  Information more topical to the user is stored in {@link UserInfoBean}.
 * <p/>
 * For any given HTTP session using PWM, one and only one {@link SessionStateBean} will be
 * created.
 *
 * @author Jason D. Rivard
 */
public class SessionStateBean implements PwmSessionBean {
// ------------------------------ FIELDS ------------------------------

    // ------------------------- PUBLIC CONSTANTS -------------------------
    /**
     * if the current session is believed to be authenticated
     */
    private boolean authenticated;

    private Message sessionSuccess;
    private String sessionSuccessField;
    private String preCaptchaRequestURL;
    private String srcAddress;
    private String srcHostname;
    private String forwardURL;
    private String logoutURL;
    private Locale locale;
    private String sessionID;
    private String theme;
    private String lastRequestURL;

    private Map<String, ShortcutItem> visibleShortcutItems;

    private String requestVerificationKey = "key";
    private String sessionVerificationKey = "key";
    private String paramPrefixKey = "key";
    private String restClientKey;

    private boolean passedCaptcha;
    private boolean debugInitialized;
    private boolean sessionVerified;

    private Date pageLeaveNoticeTime;
    private Date sessionCreationTime;
    private Date sessionLastAccessedTime;

    private boolean passwordModified;
    private boolean privateUrlAccessed;

    private int intruderAttempts;
    private boolean oauthInProgress;

    // settings
    private int sessionVerificationKeyLength;

    private boolean skippedOtpSetup;
    private boolean skippedRequireNewPassword;
    
    private boolean sessionIdRecycleNeeded;



// --------------------- GETTER / SETTER METHODS ---------------------

    public SessionStateBean(final int sessionVerificationKeyLength) {
        this.sessionVerificationKeyLength = sessionVerificationKeyLength;
    }

    public boolean isPasswordModified() {
        return passwordModified;
    }

    public void setPasswordModified(boolean passwordModified) {
        this.passwordModified = passwordModified;
    }

    public boolean isPrivateUrlAccessed() {
        return this.privateUrlAccessed;
    }

    public void setPrivateUrlAccessed(final boolean privateUrlAccessed) {
        this.privateUrlAccessed = privateUrlAccessed;
    }

    public String getForwardURL() {
        return forwardURL;
    }

    public void setForwardURL(final String forwardURL) {
        this.forwardURL = forwardURL;
    }

    public Locale getLocale() {
        return locale;
    }

    public void setLocale(final Locale locale) {
        this.locale = locale;
    }

    public String getLogoutURL() {
        return logoutURL;
    }

    public void setLogoutURL(final String logoutURL) {
        this.logoutURL = logoutURL;
    }

    public String getPreCaptchaRequestURL() {
        return preCaptchaRequestURL;
    }

    public void setPreCaptchaRequestURL(String preCaptchaRequestURL) {
        this.preCaptchaRequestURL = preCaptchaRequestURL;
    }

    public String getSessionID() {
        return sessionID;
    }

    public void setSessionID(final String sessionID) {
        this.sessionID = sessionID;
    }

    public Message getSessionSuccess() {
        return sessionSuccess;
    }

    public String getSessionSuccessField() {
        return sessionSuccessField;
    }

    public void setSessionSuccess(final Message sessionSuccess, final String field) {
        this.sessionSuccessField = field;
        this.sessionSuccess = sessionSuccess;
    }

    public String getSrcAddress() {
        return srcAddress;
    }

    public void setSrcAddress(final String srcAddress) {
        this.srcAddress = srcAddress;
    }

    public String getSrcHostname() {
        return srcHostname;
    }

    public void setSrcHostname(final String srcHostname) {
        this.srcHostname = srcHostname;
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public void setAuthenticated(final boolean authenticated) {
        this.authenticated = authenticated;
    }

    public boolean isPassedCaptcha() {
        return passedCaptcha;
    }

    public void setPassedCaptcha(final boolean passedCaptcha) {
        this.passedCaptcha = passedCaptcha;
    }

    public String getSessionVerificationKey() {
        return sessionVerificationKey;
    }


    public String getParamPrefixKey() {
        return paramPrefixKey;
    }

    public boolean isSessionVerified() {
        return sessionVerified;
    }

    public void setSessionVerified(final boolean sessionVerified) {
        this.sessionVerified = sessionVerified;
    }

    public Map<String, ShortcutItem> getVisibleShortcutItems() {
        return visibleShortcutItems;
    }

    public void setVisibleShortcutItems(final Map<String, ShortcutItem> visibleShortcutItems) {
        this.visibleShortcutItems = visibleShortcutItems;
    }

    public boolean isDebugInitialized() {
        return debugInitialized;
    }

    public void setDebugInitialized(final boolean debugInitialized) {
        this.debugInitialized = debugInitialized;
    }

    public String getRequestVerificationKey()
    {
        return requestVerificationKey;
    }

    public void setRequestVerificationKey(String requestVerificationKey)
    {
        this.requestVerificationKey = requestVerificationKey;
    }

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    public Date getPageLeaveNoticeTime() {
        return pageLeaveNoticeTime;
    }

    public void setPageLeaveNoticeTime(final Date pageLeaveNoticeTime) {
        this.pageLeaveNoticeTime = pageLeaveNoticeTime;
    }

    public Date getSessionCreationTime() {
        return sessionCreationTime;
    }

    public void setSessionCreationTime(Date sessionCreationTime) {
        this.sessionCreationTime = sessionCreationTime;
    }

    public Date getSessionLastAccessedTime() {
        return sessionLastAccessedTime;
    }

    public void setSessionLastAccessedTime(Date sessionLastAccessedTime) {
        this.sessionLastAccessedTime = sessionLastAccessedTime;
    }

    public String getLastRequestURL() {
        return lastRequestURL;
    }

    public void setLastRequestURL(String lastRequestURL) {
        this.lastRequestURL = lastRequestURL;
    }

    public int getIntruderAttempts() {
        return intruderAttempts;
    }

    public void incrementIntruderAttempts() {
        intruderAttempts++;
    }

    public void clearIntruderAttempts() {
        intruderAttempts = 0;
    }

    public boolean isOauthInProgress()
    {
        return oauthInProgress;
    }

    public void setOauthInProgress(boolean oauthInProgress)
    {
        this.oauthInProgress = oauthInProgress;
    }

    public String getRestClientKey() {
        return restClientKey;
    }

    public void setRestClientKey(String restClientKey) {
        this.restClientKey = restClientKey;
    }

    public void regenerateSessionVerificationKey() {
        sessionVerificationKey = PwmRandom.getInstance().alphaNumericString(sessionVerificationKeyLength) + Long.toHexString(System.currentTimeMillis());
        paramPrefixKey = PwmRandom.getInstance().alphaNumericString(10);
    }

    public boolean isSkippedOtpSetup()
    {
        return skippedOtpSetup;
    }

    public void setSkippedOtpSetup(boolean skippedOtpSetup)
    {
        this.skippedOtpSetup = skippedOtpSetup;
    }

    public boolean isSkippedRequireNewPassword()
    {
        return skippedRequireNewPassword;
    }

    public void setSkippedRequirePassword(boolean skippedPasswordWarn)
    {
        this.skippedRequireNewPassword = skippedPasswordWarn;
    }

    public boolean isSessionIdRecycleNeeded() {
        return sessionIdRecycleNeeded;
    }

    public void setSessionIdRecycleNeeded(boolean sessionIdRecycleNeeded) {
        this.sessionIdRecycleNeeded = sessionIdRecycleNeeded;
    }
}

