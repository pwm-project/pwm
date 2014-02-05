/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2014 The PWM Project
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

package password.pwm;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.bean.PwmSessionBean;
import password.pwm.bean.SessionStateBean;
import password.pwm.bean.UserInfoBean;
import password.pwm.bean.servlet.*;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.ldap.UserStatusReader;
import password.pwm.util.Helper;
import password.pwm.util.PwmLogger;
import password.pwm.util.PwmRandom;
import password.pwm.util.TimeDuration;
import password.pwm.util.stats.Statistic;
import password.pwm.util.stats.StatisticsManager;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.*;

/**
 * @author Jason D. Rivard
 */
public class PwmSession implements Serializable {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(PwmSession.class);

    private final SessionStateBean sessionStateBean;
    private final Map<Class,PwmSessionBean> sessionBeans = new HashMap<Class, PwmSessionBean>();

    private boolean valid = true;
    private Settings settings = new Settings();

    private transient SessionManager sessionManager;

    public static PwmSession getPwmSession(final PwmApplication pwmApplication, final HttpSession httpSession)
            throws PwmUnrecoverableException
    {
        if (httpSession == null) {
            final RuntimeException e = new NullPointerException("cannot fetch a pwmSession using a null httpSession");
            LOGGER.warn("attempt to fetch a pwmSession with a null session", e);
            throw e;
        }

        PwmSession returnSession = (PwmSession) httpSession.getAttribute(PwmConstants.SESSION_ATTR_PWM_SESSION);
        if (returnSession != null && !returnSession.isValid()) {
            httpSession.removeAttribute(PwmConstants.SESSION_ATTR_PWM_SESSION);
            returnSession = null;
        }

        if (returnSession == null) {
            final PwmSession newPwmSession = new PwmSession(pwmApplication, httpSession);
            httpSession.setAttribute(PwmConstants.SESSION_ATTR_PWM_SESSION, newPwmSession);
            returnSession = newPwmSession;
        }

        return returnSession;
    }

    public static PwmSession getPwmSession(final HttpServletRequest httpRequest) throws PwmUnrecoverableException {
        return getPwmSession(httpRequest.getSession());
    }

    public static PwmSession getPwmSession(final HttpSession httpSession) throws PwmUnrecoverableException {
        return getPwmSession(ContextManager.getPwmApplication(httpSession),httpSession);
    }

// --------------------------- CONSTRUCTORS ---------------------------

    private PwmSession(final PwmApplication pwmApplication, final HttpSession httpSession)
            throws PwmUnrecoverableException
    {
        if (pwmApplication == null) {
            throw new IllegalStateException("PwmApplication must be available during session creation");
        }

        final int sessionValidationKeyLength = Integer.parseInt(pwmApplication.getConfig().readAppProperty(AppProperty.HTTP_SESSION_VALIDATION_KEY_LENGTH));
        sessionStateBean = new SessionStateBean(sessionValidationKeyLength);
        sessionStateBean.regenerateSessionVerificationKey();
        this.sessionStateBean.setSessionID("");

        final StatisticsManager statisticsManager = pwmApplication.getStatisticsManager();
        if (statisticsManager != null) {
            String sessionID = pwmApplication.getStatisticsManager().getStatBundleForKey(StatisticsManager.KEY_CUMULATIVE).getStatistic(Statistic.HTTP_SESSIONS);
            try {
                sessionID = new BigInteger(sessionID).toString(Character.MAX_RADIX);
            } catch (Exception e) { /* ignore */ }
            this.getSessionStateBean().setSessionID(sessionID);
        }

        final int sessionIdle = (int)pwmApplication.getConfig().readSettingAsLong(PwmSetting.IDLE_TIMEOUT_SECONDS);
        this.setSessionTimeout(httpSession, sessionIdle);

        this.sessionStateBean.setSessionLastAccessedTime(new Date());

        ContextManager.getContextManager(httpSession).addPwmSession(this);
        if (pwmApplication.getStatisticsManager() != null) {
            pwmApplication.getStatisticsManager().incrementValue(Statistic.HTTP_SESSIONS);
        }

        settings.restKeyLength = Integer.parseInt(pwmApplication.getConfig().readAppProperty(AppProperty.SECURITY_WS_REST_CLIENT_KEY_LENGTH));
    }

// --------------------- GETTER / SETTER METHODS ---------------------


    public ChangePasswordBean getChangePasswordBean() {
        return (ChangePasswordBean) getSessionBean(ChangePasswordBean.class);
    }

    public ForgottenPasswordBean getForgottenPasswordBean() {
        return (ForgottenPasswordBean) getSessionBean(ForgottenPasswordBean.class);
    }

    public void clearForgottenPasswordBean() {
        sessionBeans.remove(ForgottenPasswordBean.class);
    }

    public ConfigManagerBean getConfigManagerBean() {
        return (ConfigManagerBean) getSessionBean(ConfigManagerBean.class);
    }

    public GuestRegistrationBean getGuestRegistrationBean() {
        return (GuestRegistrationBean) getSessionBean(GuestRegistrationBean.class);
    }

    public SessionManager getSessionManager() {
        if (sessionManager == null) {
            preModifyCheck();
            sessionManager = new SessionManager(this);
        }
        return sessionManager;
    }

    public SessionStateBean getSessionStateBean() {
        return sessionStateBean;
    }

    public UserInfoBean getUserInfoBean() {
        return (UserInfoBean) getSessionBean(UserInfoBean.class);
    }

    public HelpdeskBean getHelpdeskBean() {
        return (HelpdeskBean) getSessionBean(HelpdeskBean.class);
    }

    public UpdateProfileBean getUpdateProfileBean() {
        return (UpdateProfileBean) getSessionBean(UpdateProfileBean.class);
    }

    public void clearUpdateProfileBean() {
        sessionBeans.remove(UpdateProfileBean.class);
    }

    public ActivateUserBean getActivateUserBean() {
        return (ActivateUserBean) getSessionBean(ActivateUserBean.class);
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

        if (sessionManager != null) {
            sessionManager.closeConnections();
        }
    }

    public boolean isValid() {
        if (valid) {
            final TimeDuration idleTime = getIdleTime();
            if (idleTime.isLongerThan(sessionStateBean.getSessionMaximumTimeout())) {
                invalidate();
            }
        }
        return valid;
    }

    public String getSessionLabel() {
        final StringBuilder sb = new StringBuilder();
        sb.append(this.getSessionStateBean().getSessionID());
        if (this.getSessionStateBean().isAuthenticated()) {
            final String userID = this.getUserInfoBean().getUsername();
            if (userID != null && userID.length() > 0) {
                sb.append(",");
                sb.append(userID);
            }
        }
        return sb.toString();
    }

    /**
     * Unauthenticate the pwmSession
     */
    public void unauthenticateUser() {
        final SessionStateBean ssBean = getSessionStateBean();

        getUserInfoBean().clearPermissions();

        if (ssBean.isAuthenticated()) { // try to tear out a session normally.
            final StringBuilder sb = new StringBuilder();

            sb.append("unauthenticate session from ").append(ssBean.getSrcAddress());
            if (getUserInfoBean().getUserIdentity() != null) {
                sb.append(" (").append(getUserInfoBean().getUserIdentity()).append(")");
            }

            // mark the session state bean as no longer being authenticated
            ssBean.setAuthenticated(false);
            ssBean.setOriginalRequestURL(null);

            // close out any outstanding connections
            getSessionManager().closeConnections();

            LOGGER.debug(this, sb.toString());
        }

        clearSessionBeans();

        // clear CAS session if it exists.
        // @todo add this back if needed (but recycle session should also take care of this
        /*
        try {
            if (httpSession.getAttribute(AbstractCasFilter.CONST_CAS_ASSERTION) != null) {
                httpSession.removeAttribute(AbstractCasFilter.CONST_CAS_ASSERTION);
                LOGGER.debug("CAS assertion removed");
            }
        } catch (Exception e) {
            // session already invalided
        }
        */
    }

    public SetupResponsesBean getSetupResponseBean() {
        return (SetupResponsesBean) getSessionBean(SetupResponsesBean.class);
    }

    public SetupOtpBean getSetupOtpBean() {
        return (SetupOtpBean) getSessionBean(SetupOtpBean.class);
    }

    public NewUserBean getNewUserBean() {
        return (NewUserBean) getSessionBean(NewUserBean.class);
    }

    protected void finalize()
            throws Throwable
    {
        super.finalize();
        invalidate();
    }

    public void invalidate() {
        LOGGER.debug(this, "invalidating session");
        sessionBeans.clear();
        if (sessionManager != null) {
            sessionManager.closeConnections();
            sessionManager = null;
        }
        valid = false;
    }

    public PwmSessionBean getSessionBean(final Class theClass) {
        if (!sessionBeans.containsKey(theClass)) {
            preModifyCheck();
            try {
                final Object newBean = theClass.newInstance();
                sessionBeans.put(theClass,(PwmSessionBean)newBean);
            } catch (Exception e) {
                LOGGER.error("unexpected error trying to instantiate bean class " + theClass.getName() + ": " + e.getMessage(),e);
            }

        }
        return sessionBeans.get(theClass);
    }

    public TimeDuration getIdleTime() {
        return TimeDuration.fromCurrent(sessionStateBean.getSessionLastAccessedTime());
    }

    public String toString() {
        final LinkedHashMap<String,Object> debugData = new LinkedHashMap<String, Object>();
        try {
            if (valid) {
                debugData.put("sessionID",getSessionStateBean().getSessionID());
                debugData.put("auth",getSessionStateBean().isAuthenticated());
                if (getSessionStateBean().isAuthenticated()) {
                    debugData.put("passwordStatus",getUserInfoBean().getPasswordState());
                    debugData.put("guid",getUserInfoBean().getUserGuid());
                    debugData.put("dn",getUserInfoBean().getUserIdentity());
                    debugData.put("authType",getUserInfoBean().getAuthenticationType());
                    debugData.put("needsNewPW",getUserInfoBean().isRequiresNewPassword());
                    debugData.put("needsNewCR",getUserInfoBean().isRequiresResponseConfig());
                    debugData.put("needsNewProfile",getUserInfoBean().isRequiresUpdateProfile());
                    debugData.put("hasCRPolicy",getUserInfoBean().getChallengeProfile() != null && getUserInfoBean().getChallengeProfile().getChallengeSet() != null);
                }
                debugData.put("locale",getSessionStateBean().getLocale());
                debugData.put("theme",getSessionStateBean().getTheme());
            }
        } catch (Exception e) {
            return "exception generating PwmSession.toString(): " + e.getMessage();
        }

        return "PwmSession instance: " + Helper.getGson().toJson(debugData);
    }

    public boolean setLocale(final PwmApplication pwmApplication, final String localeString)
            throws PwmUnrecoverableException
    {
        if (pwmApplication == null) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_APP_UNAVAILABLE, "unable to read context manager"));
        }

        final List<Locale> knownLocales = pwmApplication.getConfig().getKnownLocales();
        final Locale requestedLocale = Helper.parseLocaleString(localeString);
        if (knownLocales.contains(requestedLocale) || localeString.equalsIgnoreCase("default")) {
            LOGGER.debug(this, "setting session locale to '" + localeString + "'");
            final SessionStateBean ssBean = this.getSessionStateBean();
            ssBean.setLocale(new Locale(localeString.equalsIgnoreCase("default") ? "" : localeString));
            if (ssBean.isAuthenticated()) {
                try {
                    final UserStatusReader userStatusReader = new UserStatusReader(pwmApplication);
                    userStatusReader.populateLocaleSpecificUserInfoBean(this, this.getUserInfoBean(), ssBean.getLocale());
                } catch (ChaiUnavailableException e) {
                    LOGGER.warn("unable to refresh locale-specific user data, error:" + e.getLocalizedMessage());
                }
            }
            return true;
        } else {
            LOGGER.error(this, "ignoring unknown locale value set request for locale '" + localeString);
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

    private void preModifyCheck() {
        if (!valid) {
            throw new IllegalStateException("PwmSession is not valid, operation not permitted");
        }
    }

    public void setSessionTimeout(final HttpSession session, final int maxSeconds)
            throws PwmUnrecoverableException
    {
        if (session.getMaxInactiveInterval() < maxSeconds) {
            session.setMaxInactiveInterval(maxSeconds);
        }

        final SessionStateBean ssBean = this.getSessionStateBean();
        final long maxMs = maxSeconds * 1000;
        if (ssBean.getSessionMaximumTimeout() == null || ssBean.getSessionMaximumTimeout().getTotalMilliseconds() < maxMs) {
            ssBean.setSessionMaximumTimeout(new TimeDuration(maxMs));
        }
    }

    private static class Settings implements Serializable {
        private int restKeyLength = 36; // default
    }
}
