/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2012 The PWM Project
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
import password.pwm.config.ShortcutItem;
import password.pwm.error.ErrorInformation;
import password.pwm.i18n.Message;
import password.pwm.util.BasicAuthInfo;
import password.pwm.util.FormMap;
import password.pwm.util.PwmRandom;

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

    private ErrorInformation sessionError;
    private Message sessionSuccess;
    private String sessionSuccessField;
    private String originalRequestURL;
    private String preCaptchaRequestURL;
    private String srcAddress;
    private String srcHostname;
    private String forwardURL;
    private String logoutURL;
    private Locale locale;
    private String sessionID;
    private String theme;
    private String lastRequestURL;

    private FormMap lastParameterValues = new FormMap();
    private Map<String, ShortcutItem> visibleShortcutItems;
    private BasicAuthInfo originalBasicAuthInfo;

    private int requestCounter = PwmRandom.getInstance().nextInt(Integer.MAX_VALUE);
    private String sessionVerificationKey;

    private boolean passedCaptcha;
    private boolean debugInitialized;
    private boolean sessionVerified;

    private Date pageLeaveNoticeTime;
    private Date sessionCreationTime;
    private Date sessionLastAccessedTime;

    private boolean passwordModified;
    private boolean privateUrlAccessed;


// --------------------- GETTER / SETTER METHODS ---------------------

    public SessionStateBean() {
        regenerateSessionVerificationKey();
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

    public FormMap getLastParameterValues() {
        return lastParameterValues;
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

    public BasicAuthInfo getOriginalBasicAuthInfo() {
        return originalBasicAuthInfo;
    }

    public void setOriginalBasicAuthInfo(final BasicAuthInfo originalBasicAuthInfo) {
        this.originalBasicAuthInfo = originalBasicAuthInfo;
    }

    public String getOriginalRequestURL() {
        return originalRequestURL;
    }

    public void setOriginalRequestURL(final String originalRequestURL) {
        this.originalRequestURL = originalRequestURL;
    }

    public String getPreCaptchaRequestURL() {
        return preCaptchaRequestURL;
    }

    public void setPreCaptchaRequestURL(String preCaptchaRequestURL) {
        this.preCaptchaRequestURL = preCaptchaRequestURL;
    }

    public ErrorInformation getSessionError() {
        return sessionError;
    }

    public void setSessionError(final ErrorInformation sessionError) {
        this.sessionError = sessionError;
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

    // -------------------------- OTHER METHODS --------------------------

    public void setLastParameterValues(final Map<String,String> props) {
        final FormMap newProps = new FormMap();
        newProps.putAll(props);
        this.lastParameterValues = newProps;
    }

    public String getSessionVerificationKey() {
        return sessionVerificationKey;
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

    public int getRequestCounter() {
        return requestCounter;
    }

    public void incrementRequestCounter() {
        requestCounter = requestCounter++;
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

    // -------------------------- ENUMERATIONS --------------------------

    public void regenerateSessionVerificationKey() {
        sessionVerificationKey = PwmRandom.getInstance().alphaNumericString(PwmConstants.HTTP_SESSION_VALIDATION_KEY_LENGTH) + Long.toHexString(System.currentTimeMillis());
    }
}

