/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
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

import password.pwm.BasicAuthInfo;
import password.pwm.Validator;
import password.pwm.config.ShortcutItem;
import password.pwm.error.ErrorInformation;

import javax.servlet.http.HttpServletRequest;
import java.io.Serializable;
import java.util.*;

/**
 *  Only information that is particular to the http session is stored in the
 * session bean.  Information more topical to the user is stored in {@link UserInfoBean}.
 * <p/>
 * For any given HTTP session using PWM, one and only one {@link SessionStateBean} will be
 * created.
 *
 * @author Jason D. Rivard
 */
public class SessionStateBean implements Serializable {
// ------------------------------ FIELDS ------------------------------

    // ------------------------- PUBLIC CONSTANTS -------------------------
    /**
     * if the current session is believed to be authenticated
     */
    private boolean authenticated;

    private ErrorInformation sessionError;
    private ErrorInformation sessionSuccess;
    private String originalRequestURL;
    private String srcAddress;
    private String srcHostname;
    private String forwardURL;
    private String logoutURL;
    private String postWaitURL;
    private Locale locale;
    private String sessionID;

    private int incorrectLogins;

    private Properties lastParameterValues = new Properties();

    private BasicAuthInfo originalBasicAuthInfo;

    private boolean sessionVerified;
    private String sessionVerificationKey;

    private boolean passedCaptcha;

    private long lastAccessTime = System.currentTimeMillis();
    private long lastPageUnloadTime = 0;

    private Map<String, ShortcutItem> visableShortcutItems;

    private FINISH_ACTION finishAction = FINISH_ACTION.FORWARD;

// --------------------- GETTER / SETTER METHODS ---------------------

    public FINISH_ACTION getFinishAction()
    {
        return finishAction;
    }

    public void setFinishAction(final FINISH_ACTION finishAction)
    {
        this.finishAction = finishAction;
    }

    public String getForwardURL()
    {
        return forwardURL;
    }

    public void setForwardURL(final String forwardURL)
    {
        this.forwardURL = forwardURL;
    }

    public int getIncorrectLogins()
    {
        return incorrectLogins;
    }

    public long getLastAccessTime()
    {
        return lastAccessTime;
    }

    public void setLastAccessTime(final long lastAccessTime)
    {
        this.lastAccessTime = lastAccessTime;
    }

    public Properties getLastParameterValues()
    {
        return lastParameterValues;
    }

    public Locale getLocale()
    {
        return locale;
    }

    public void setLocale(final Locale locale)
    {
        this.locale = locale;
    }

    public String getLogoutURL()
    {
        return logoutURL;
    }

    public void setLogoutURL(final String logoutURL)
    {
        this.logoutURL = logoutURL;
    }

    public BasicAuthInfo getOriginalBasicAuthInfo()
    {
        return originalBasicAuthInfo;
    }

    public void setOriginalBasicAuthInfo(final BasicAuthInfo originalBasicAuthInfo)
    {
        this.originalBasicAuthInfo = originalBasicAuthInfo;
    }

    public String getOriginalRequestURL()
    {
        return originalRequestURL;
    }

    public void setOriginalRequestURL(final String originalRequestURL)
    {
        this.originalRequestURL = originalRequestURL;
    }

    public String getPostWaitURL()
    {
        return postWaitURL;
    }

    public void setPostWaitURL(final String postWaitURL)
    {
        this.postWaitURL = postWaitURL;
    }

    public ErrorInformation getSessionError()
    {
        return sessionError;
    }

    public void setSessionError(final ErrorInformation sessionError)
    {
        this.sessionError = sessionError;
    }

    public String getSessionID()
    {
        return sessionID;
    }

    public void setSessionID(final String sessionID)
    {
        this.sessionID = sessionID;
    }

    public ErrorInformation getSessionSuccess()
    {
        return sessionSuccess;
    }

    public void setSessionSuccess(final ErrorInformation sessionSuccess)
    {
        this.sessionSuccess = sessionSuccess;
    }

    public String getSrcAddress()
    {
        return srcAddress;
    }

    public void setSrcAddress(final String srcAddress)
    {
        this.srcAddress = srcAddress;
    }

    public String getSrcHostname()
    {
        return srcHostname;
    }

    public void setSrcHostname(final String srcHostname)
    {
        this.srcHostname = srcHostname;
    }

    public boolean isAuthenticated()
    {
        return authenticated;
    }

    public void setAuthenticated(final boolean authenticated)
    {
        this.authenticated = authenticated;
    }

    public boolean isPassedCaptcha() {
        return passedCaptcha;
    }

    public void setPassedCaptcha(final boolean passedCaptcha) {
        this.passedCaptcha = passedCaptcha;
    }

    // -------------------------- OTHER METHODS --------------------------

    public void clearLastParameterValues()
    {
        this.lastParameterValues = new Properties();
    }

    public long getIdleTime()
    {
        final long lastAccessTime = getLastAccessTime();
        return System.currentTimeMillis() - lastAccessTime;
    }

    public String getLastParameterValue(final String name)
    {
        final Properties props = this.lastParameterValues;
        return props.getProperty(name, "");
    }

    public void incrementIncorrectLogins()
    {
        this.incorrectLogins++;
    }

    public void resetIncorrectLogins()
    {
        this.incorrectLogins = 0;
    }

    public void setLastParameterValues(final HttpServletRequest req)
    {
        final Set keyNames = req.getParameterMap().keySet();
        final Properties newParamProperty = new Properties();

        for (final Object name : keyNames) {
            final String value = Validator.readStringFromRequest(req, (String) name, 4096);
            newParamProperty.setProperty((String) name, value);
        }

        this.lastParameterValues = newParamProperty;
    }

    public String getSessionVerificationKey() {
        return sessionVerificationKey;
    }

    public void setSessionVerificationKey(final String sessionVerificationKey) {
        this.sessionVerificationKey = sessionVerificationKey;
    }

    public boolean isSessionVerified() {
        return sessionVerified;
    }

    public void setSessionVerified(final boolean sessionVerified) {
        this.sessionVerified = sessionVerified;
    }

    public Map<String, ShortcutItem> getVisableShortcutItems() {
        return visableShortcutItems;
    }

    public void setVisableShortcutItems(Map<String, ShortcutItem> visableShortcutItems) {
        this.visableShortcutItems = visableShortcutItems;
    }

    // -------------------------- ENUMERATIONS --------------------------

    public enum FINISH_ACTION {
        LOGOUT, FORWARD
    }

    public long getLastPageUnloadTime() {
        return lastPageUnloadTime;
    }

    public void setLastPageUnloadTime(final long lastPageUnloadTime) {
        this.lastPageUnloadTime = lastPageUnloadTime;
    }
}

