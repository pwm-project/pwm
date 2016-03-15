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

package password.pwm.http;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.*;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.bean.UserSessionDataCacheBean;
import password.pwm.ldap.UserStatusReader;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsManager;
import password.pwm.util.JsonUtil;
import password.pwm.util.LocaleHelper;
import password.pwm.util.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmRandom;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.*;

/**
 * @author Jason D. Rivard
 */
public class PwmSession implements Serializable {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.forClass(PwmSession.class);

    private final LocalSessionStateBean sessionStateBean;

    private LoginInfoBean loginInfoBean;
    private UserInfoBean userInfoBean;
    private UserSessionDataCacheBean userSessionDataCacheBean;

    private Settings settings = new Settings();
    private final static Object creationLock = new Object();

    private transient SessionManager sessionManager;

    public static PwmSession createPwmSession(final PwmApplication pwmApplication)
            throws PwmUnrecoverableException
    {
        synchronized (creationLock) {
            return new PwmSession(pwmApplication);
        }
    }


    private PwmSession(final PwmApplication pwmApplication)
            throws PwmUnrecoverableException
    {
        if (pwmApplication == null) {
            throw new IllegalStateException("PwmApplication must be available during session creation");
        }

        final int sessionValidationKeyLength = Integer.parseInt(pwmApplication.getConfig().readAppProperty(AppProperty.HTTP_SESSION_VALIDATION_KEY_LENGTH));
        sessionStateBean = new LocalSessionStateBean(sessionValidationKeyLength);
        sessionStateBean.regenerateSessionVerificationKey();
        this.sessionStateBean.setSessionID(null);

        final StatisticsManager statisticsManager = pwmApplication.getStatisticsManager();
        if (statisticsManager != null) {
            String nextID = pwmApplication.getStatisticsManager().getStatBundleForKey(StatisticsManager.KEY_CUMULATIVE).getStatistic(Statistic.HTTP_SESSIONS);
            try {
                nextID = new BigInteger(nextID).toString();
            } catch (Exception e) { /* ignore */ }
            this.getSessionStateBean().setSessionID(nextID);
        }

        this.sessionStateBean.setSessionLastAccessedTime(new Date());

        if (pwmApplication.getStatisticsManager() != null) {
            pwmApplication.getStatisticsManager().incrementValue(Statistic.HTTP_SESSIONS);
        }

        pwmApplication.getSessionTrackService().addSessionData(this);

        settings.restKeyLength = Integer.parseInt(pwmApplication.getConfig().readAppProperty(AppProperty.SECURITY_WS_REST_CLIENT_KEY_LENGTH));
        LOGGER.trace(this,"created new session");
    }


    public SessionManager getSessionManager() {
        if (sessionManager == null) {
            sessionManager = new SessionManager(this);
        }
        return sessionManager;
    }

    public LocalSessionStateBean getSessionStateBean() {
        return sessionStateBean;
    }

    public UserInfoBean getUserInfoBean() {
        if (!isAuthenticated()) {
            throw new IllegalStateException("attempt to read user info bean, but session not authenticated");
        }
        if (userInfoBean == null) {
            userInfoBean = new UserInfoBean();
        }
        return userInfoBean;
    }

    public LoginInfoBean getLoginInfoBean() {
        if (loginInfoBean == null) {
            loginInfoBean = new LoginInfoBean();
        }
        if (loginInfoBean.getGuid() == null) {
            loginInfoBean.setGuid((Long.toString(new Date().getTime(),36) + PwmRandom.getInstance().alphaNumericString(64)));
        }

        return loginInfoBean;
    }

    public void setLoginInfoBean(final LoginInfoBean loginInfoBean) {
        this.loginInfoBean = loginInfoBean;
    }

    public UserSessionDataCacheBean getUserSessionDataCacheBean() {
        if (userSessionDataCacheBean == null) {
            userSessionDataCacheBean = new UserSessionDataCacheBean();
        }
        return userSessionDataCacheBean;
    }

    public SessionLabel getLabel() {
        final LocalSessionStateBean ssBean = this.getSessionStateBean();
        final String userID = isAuthenticated() ? this.getUserInfoBean().getUsername() : null;
        final UserIdentity userIdentity = isAuthenticated() ? this.getUserInfoBean().getUserIdentity() : null;
        return new SessionLabel(ssBean.getSessionID(),userIdentity,userID,ssBean.getSrcAddress(),ssBean.getSrcAddress());
    }

    /**
     * Unauthenticate the pwmSession
     */
    public void unauthenticateUser(final PwmRequest pwmRequest) {
        final LocalSessionStateBean ssBean = getSessionStateBean();

        if (getLoginInfoBean().isAuthenticated()) { // try to tear out a session normally.
            getUserSessionDataCacheBean().clearPermissions();

            final StringBuilder sb = new StringBuilder();

            sb.append("unauthenticate session from ").append(ssBean.getSrcAddress());
            if (getUserInfoBean().getUserIdentity() != null) {
                sb.append(" (").append(getUserInfoBean().getUserIdentity()).append(")");
            }

            // mark the session state bean as no longer being authenticated
            this.getLoginInfoBean().setAuthenticated(false);

            // close out any outstanding connections
            getSessionManager().closeConnections();

            LOGGER.debug(this, sb.toString());
        }

        if (pwmRequest != null) {
            try {
                pwmRequest.getPwmApplication().getSessionStateService().clearLoginSession(pwmRequest);
            } catch (PwmUnrecoverableException e) {
                final String errorMsg = "unexpected error writing removing login cookie from response: " + e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN,errorMsg);
                LOGGER.error(pwmRequest, errorInformation);
            }

            pwmRequest.getHttpServletRequest().setAttribute(PwmConstants.SESSION_ATTR_BEANS,null);
        }

        userInfoBean = null;
        loginInfoBean = null;
        userSessionDataCacheBean = null;
    }

    protected void finalize()
            throws Throwable
    {
        super.finalize();
    }

    public TimeDuration getIdleTime() {
        return TimeDuration.fromCurrent(sessionStateBean.getSessionLastAccessedTime());
    }

    public String toString() {
        final Map<String,Object> debugData = new LinkedHashMap<>();
        try {
            debugData.put("sessionID",getSessionStateBean().getSessionID());
            debugData.put("auth",this.isAuthenticated());
            if (this.isAuthenticated()) {
                debugData.put("passwordStatus",getUserInfoBean().getPasswordState());
                debugData.put("guid",getUserInfoBean().getUserGuid());
                debugData.put("dn",getUserInfoBean().getUserIdentity());
                debugData.put("authType",getLoginInfoBean().getType());
                debugData.put("needsNewPW",getUserInfoBean().isRequiresNewPassword());
                debugData.put("needsNewCR",getUserInfoBean().isRequiresResponseConfig());
                debugData.put("needsNewProfile",getUserInfoBean().isRequiresUpdateProfile());
                debugData.put("hasCRPolicy",getUserInfoBean().getChallengeProfile() != null && getUserInfoBean().getChallengeProfile().getChallengeSet() != null);
            }
            debugData.put("locale",getSessionStateBean().getLocale());
            debugData.put("theme",getSessionStateBean().getTheme());
        } catch (Exception e) {
            return "exception generating PwmSession.toString(): " + e.getMessage();
        }

        return "PwmSession instance: " + JsonUtil.serializeMap(debugData);
    }

    public boolean setLocale(final PwmApplication pwmApplication, final String localeString)
            throws PwmUnrecoverableException
    {
        if (pwmApplication == null) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_APP_UNAVAILABLE, "unable to read context manager"));
        }

        final List<Locale> knownLocales = pwmApplication.getConfig().getKnownLocales();
        final Locale requestedLocale = LocaleHelper.parseLocaleString(localeString);
        if (knownLocales.contains(requestedLocale) || localeString.equalsIgnoreCase("default")) {
            LOGGER.debug(this, "setting session locale to '" + localeString + "'");
            final LocalSessionStateBean ssBean = this.getSessionStateBean();
            ssBean.setLocale(localeString.equalsIgnoreCase("default") ? PwmConstants.DEFAULT_LOCALE : requestedLocale);
            if (this.isAuthenticated()) {
                try {
                    final UserStatusReader userStatusReader = new UserStatusReader(pwmApplication, this.getLabel());
                    userStatusReader.populateLocaleSpecificUserInfoBean(this.getUserInfoBean(), ssBean.getLocale());
                } catch (ChaiUnavailableException e) {
                    LOGGER.warn("unable to refresh locale-specific user data, error:" + e.getLocalizedMessage());
                }
            }
            return true;
        } else {
            LOGGER.error(this, "ignoring unknown locale value set request for locale '" + localeString + "'");
            return false;
        }
    }

    public String getRestClientKey() {
        if (!this.isAuthenticated()) {
            return "";
        }

        final String restClientKey = this.getSessionStateBean().getRestClientKey();
        if (restClientKey != null && restClientKey.length() > 0) {
            return restClientKey;
        }

        final String newKey = Long.toString(System.currentTimeMillis(),36) + PwmRandom.getInstance().alphaNumericString(settings.restKeyLength);
        this.getSessionStateBean().setRestClientKey(newKey);
        return newKey;
    }

    public boolean isAuthenticated() {
        return getLoginInfoBean().isAuthenticated();
    }

    private static class Settings implements Serializable {
        private int restKeyLength = 36; // default
    }

    public int size() {
        return JsonUtil.serialize(this).length();
    }
}
