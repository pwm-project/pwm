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

package password.pwm.http;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.SessionStateBean;
import password.pwm.bean.UserIdentity;
import password.pwm.bean.UserInfoBean;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.bean.*;
import password.pwm.ldap.UserStatusReader;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsManager;
import password.pwm.util.JsonUtil;
import password.pwm.util.LocaleHelper;
import password.pwm.util.LoginCookieManager;
import password.pwm.util.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmRandom;

import javax.servlet.http.HttpSession;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.*;

/**
 * @author Jason D. Rivard
 */
public class PwmSession implements Serializable {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.forClass(PwmSession.class);

    private final SessionStateBean sessionStateBean;
    private transient Map<Class<? extends PwmSessionBean>,PwmSessionBean> sessionBeans = new HashMap<>();

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
        sessionStateBean = new SessionStateBean(sessionValidationKeyLength);
        sessionStateBean.regenerateSessionVerificationKey();
        this.sessionStateBean.setSessionID(null);

        final StatisticsManager statisticsManager = pwmApplication.getStatisticsManager();
        if (statisticsManager != null) {
            String nextID = pwmApplication.getStatisticsManager().getStatBundleForKey(StatisticsManager.KEY_CUMULATIVE).getStatistic(Statistic.HTTP_SESSIONS);
            try {
                nextID = new BigInteger(nextID).toString(Character.MAX_RADIX);
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

// --------------------- GETTER / SETTER METHODS ---------------------


    public ChangePasswordBean getChangePasswordBean() {
        return getSessionBean(ChangePasswordBean.class);
    }

    public ForgottenPasswordBean getForgottenPasswordBean() {
        return getSessionBean(ForgottenPasswordBean.class);
    }

    public void clearForgottenPasswordBean() {
        sessionBeans.remove(ForgottenPasswordBean.class);
    }

    public ConfigManagerBean getConfigManagerBean() {
        return getSessionBean(ConfigManagerBean.class);
    }

    public GuestRegistrationBean getGuestRegistrationBean() {
        return getSessionBean(GuestRegistrationBean.class);
    }

    public SessionManager getSessionManager() {
        if (sessionManager == null) {
            sessionManager = new SessionManager(this);
        }
        return sessionManager;
    }

    public SessionStateBean getSessionStateBean() {
        return sessionStateBean;
    }

    public UserInfoBean getUserInfoBean() {
        if (!getSessionStateBean().isAuthenticated()) {
            throw new IllegalStateException("attempt to read user info bean, but session not authenticated");
        }
        return getSessionBean(UserInfoBean.class);
    }

    public LoginInfoBean getLoginInfoBean() {
        return getSessionBean(LoginInfoBean.class);
    }

    public UserSessionDataCacheBean getUserSessionDataCacheBean() {
        return getSessionBean(UserSessionDataCacheBean.class);
    }

    public UpdateProfileBean getUpdateProfileBean() {
        return getSessionBean(UpdateProfileBean.class);
    }

    public void clearUpdateProfileBean() {
        sessionBeans.remove(UpdateProfileBean.class);
    }

    public ActivateUserBean getActivateUserBean() {
        return getSessionBean(ActivateUserBean.class);
    }

    public void clearActivateUserBean() {
        sessionBeans.remove(ActivateUserBean.class);
    }

    public boolean clearSessionBean(final Class userBeanClass) {
        final boolean exists = sessionBeans.containsKey(userBeanClass);
        sessionBeans.remove(userBeanClass);
        return exists;
    }

    public void clearSessionBeans() // clears all but the session state bean.
    {
        sessionBeans.clear();
    }

    public SessionLabel getLabel() {
        final SessionStateBean ssBean = this.getSessionStateBean();
        final String userID = ssBean.isAuthenticated() ? this.getUserInfoBean().getUsername() : null;
        final UserIdentity userIdentity = ssBean.isAuthenticated() ? this.getUserInfoBean().getUserIdentity() : null;
        return new SessionLabel(ssBean.getSessionID(),userIdentity,userID,ssBean.getSrcAddress(),ssBean.getSrcAddress());
    }

    /**
     * Unauthenticate the pwmSession
     */
    public void unauthenticateUser(final PwmRequest pwmRequest) {
        final SessionStateBean ssBean = getSessionStateBean();

        if (ssBean.isAuthenticated()) { // try to tear out a session normally.
            getUserSessionDataCacheBean().clearPermissions();

            final StringBuilder sb = new StringBuilder();

            sb.append("unauthenticate session from ").append(ssBean.getSrcAddress());
            if (getUserInfoBean().getUserIdentity() != null) {
                sb.append(" (").append(getUserInfoBean().getUserIdentity()).append(")");
            }

            // mark the session state bean as no longer being authenticated
            ssBean.setAuthenticated(false);

            // close out any outstanding connections
            getSessionManager().closeConnections();

            LOGGER.debug(this, sb.toString());
        }

        clearSessionBeans();

        if (pwmRequest != null) {
            try {
                LoginCookieManager.clearLoginCookie(pwmRequest);
            } catch (PwmUnrecoverableException e) {
                final String errorMsg = "unexpected error writing removing login cookie from response: " + e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN,errorMsg);
                LOGGER.error(pwmRequest, errorInformation);
            }
        }
    }

    public SetupResponsesBean getSetupResponseBean() {
        return getSessionBean(SetupResponsesBean.class);
    }

    public SetupOtpBean getSetupOtpBean() {
        return getSessionBean(SetupOtpBean.class);
    }

    public NewUserBean getNewUserBean() {
        return getSessionBean(NewUserBean.class);
    }

    protected void finalize()
            throws Throwable
    {
        super.finalize();
    }

    public <E extends PwmSessionBean> E getSessionBean(final Class<E> theClass) {
        if (sessionBeans == null) {
            sessionBeans = new HashMap<>();
        }
        if (!sessionBeans.containsKey(theClass)) {
            try {
                final Object newBean = theClass.newInstance();
                sessionBeans.put(theClass,(PwmSessionBean)newBean);
            } catch (Exception e) {
                LOGGER.error("unexpected error trying to instantiate bean class " + theClass.getName() + ": " + e.getMessage(),e);
            }

        }
        return (E)sessionBeans.get(theClass);
    }

    public TimeDuration getIdleTime() {
        return TimeDuration.fromCurrent(sessionStateBean.getSessionLastAccessedTime());
    }

    public String toString() {
        final Map<String,Object> debugData = new LinkedHashMap<>();
        try {
            debugData.put("sessionID",getSessionStateBean().getSessionID());
            debugData.put("auth",getSessionStateBean().isAuthenticated());
            if (getSessionStateBean().isAuthenticated()) {
                debugData.put("passwordStatus",getUserInfoBean().getPasswordState());
                debugData.put("guid",getUserInfoBean().getUserGuid());
                debugData.put("dn",getUserInfoBean().getUserIdentity());
                debugData.put("authType",getLoginInfoBean().getAuthenticationType());
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
            final SessionStateBean ssBean = this.getSessionStateBean();
            ssBean.setLocale(localeString.equalsIgnoreCase("default") ? PwmConstants.DEFAULT_LOCALE : requestedLocale);
            if (ssBean.isAuthenticated()) {
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
        if (!this.getSessionStateBean().isAuthenticated()) {
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

    public void setSessionTimeout(final HttpSession session, final int maxSeconds)
            throws PwmUnrecoverableException
    {
        if (maxSeconds > 0) {
            session.setMaxInactiveInterval(maxSeconds);
        }
    }

    public boolean isAuthenticated() {
        return getSessionStateBean().isAuthenticated();
    }

    private static class Settings implements Serializable {
        private int restKeyLength = 36; // default
    }

    public int size() {
        return JsonUtil.serialize(this).length();
    }
}
