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

package password.pwm;

import org.jasig.cas.client.util.AbstractCasFilter;
import password.pwm.bean.*;
import password.pwm.config.Configuration;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.PwmLogger;
import password.pwm.util.stats.Statistic;
import password.pwm.util.stats.StatisticsManager;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Jason D. Rivard
 */
public class PwmSession implements Serializable {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(PwmSession.class);

    private long creationTime;

    private final SessionStateBean sessionStateBean = new SessionStateBean();

    private final Map<Class,Serializable> userBeans = new HashMap<Class, Serializable>();

    private Configuration config;
    private SessionManager sessionManager;

    private transient HttpSession httpSession;

// -------------------------- STATIC METHODS --------------------------

    public static PwmSession getPwmSession(final HttpSession httpSession) throws PwmUnrecoverableException {
        if (httpSession == null) {
            final RuntimeException e = new NullPointerException("cannot fetch a pwmSession using a null httpSession");
            LOGGER.warn("attempt to fetch a pwmSession with a null session", e);
            throw e;
        }

        PwmSession returnSession = (PwmSession) httpSession.getAttribute(PwmConstants.SESSION_ATTR_PWM_SESSION);
        if (returnSession == null) {
            final PwmSession newPwmSession = new PwmSession(httpSession);
            httpSession.setAttribute(PwmConstants.SESSION_ATTR_PWM_SESSION, newPwmSession);
            returnSession = newPwmSession;
        } else if (returnSession.httpSession == null) { // stale session (was previously passivated)
            returnSession.httpSession = httpSession;
            ContextManager.getContextManager(httpSession.getServletContext()).addPwmSession(returnSession);
            final String oldSessionID = returnSession.getSessionStateBean().getSessionID();
            if (!oldSessionID.contains("~")) {
                returnSession.getSessionStateBean().setSessionID(oldSessionID + "~");
            }
        }

        return returnSession;
    }

    public static PwmSession getPwmSession(final HttpServletRequest httpRequest) throws PwmUnrecoverableException {
        return PwmSession.getPwmSession(httpRequest.getSession());
    }

// --------------------------- CONSTRUCTORS ---------------------------

    private PwmSession(final HttpSession httpSession) throws PwmUnrecoverableException {
        this.httpSession = httpSession;

        final PwmApplication pwmApplication = ContextManager.getPwmApplication(httpSession);

        if (pwmApplication == null) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_PWM_UNAVAILABLE, "unable to read context manager"));
        }

        this.creationTime = System.currentTimeMillis();
        this.getSessionStateBean().setSessionID("");
        config = pwmApplication.getConfig();
        clearAllUserBeans();

        final StatisticsManager statisticsManager = pwmApplication.getStatisticsManager();
        if (statisticsManager != null) {
            String sessionID = pwmApplication.getStatisticsManager().getStatBundleForKey(StatisticsManager.KEY_CUMULATIVE).getStatistic(Statistic.HTTP_SESSIONS);
            try {
                sessionID = new BigInteger(sessionID).toString(Character.MAX_RADIX);
            } catch (Exception e) { /* ignore */ }
            this.getSessionStateBean().setSessionID(sessionID);
        }
    }

// --------------------- GETTER / SETTER METHODS ---------------------


    public ChangePasswordBean getChangePasswordBean() {
        return (ChangePasswordBean)getUserBean(ChangePasswordBean.class);
    }

    public long getCreationTime() {
        return creationTime;
    }

    public ForgottenPasswordBean getForgottenPasswordBean() {
        return (ForgottenPasswordBean)getUserBean(ForgottenPasswordBean.class);
    }

    public void clearForgottenPasswordBean() {
        userBeans.remove(ForgottenPasswordBean.class);
    }

    public ConfigManagerBean getConfigManagerBean() {
        return (ConfigManagerBean)getUserBean(ConfigManagerBean.class);
    }

    public GuestRegistrationBean getGuestRegistrationBean() {
        return (GuestRegistrationBean)getUserBean(GuestRegistrationBean.class);
    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }

    public SessionStateBean getSessionStateBean() {
        return sessionStateBean;
    }

    public UserInfoBean getUserInfoBean() {
        return (UserInfoBean)getUserBean(UserInfoBean.class);
    }

    public HelpdeskBean getHelpdeskBean() {
        return (HelpdeskBean)getUserBean(HelpdeskBean.class);
    }

    public UpdateProfileBean getUpdateProfileBean() {
        return (UpdateProfileBean)getUserBean(UpdateProfileBean.class);
    }

    public void clearUpdateProfileBean() {
        userBeans.remove(UpdateProfileBean.class);
    }

    public ActivateUserBean getActivateUserBean() {
        return (ActivateUserBean)getUserBean(ActivateUserBean.class);
    }

    public void clearActivateUserBean() {
        userBeans.remove(ActivateUserBean.class);
    }

    public boolean clearUserBean(final Class userBeanClass) {
        final boolean exists = userBeans.containsKey(userBeanClass);
        userBeans.remove(userBeanClass);
        return exists;
    }

// -------------------------- OTHER METHODS --------------------------

    public void clearChangePasswordBean() {
        userBeans.remove(ChangePasswordBean.class);
    }

    public void clearAllUserBeans() // clears all but the session state bean. 
    {
        userBeans.clear();

        if (sessionManager != null) {
            sessionManager.closeConnections();
        }
        sessionManager = new SessionManager(this, config);
    }

    public boolean isValid() {
        if (httpSession == null) {
            return false;
        }

        try {
            httpSession.getAttribute("test");
        } catch (IllegalStateException e) {
            return false;
        }

        return true;
    }

    public String getSessionLabel() {
        final StringBuilder sb = new StringBuilder();
        sb.append(this.getSessionStateBean().getSessionID());
        if (this.getSessionStateBean().isAuthenticated()) {
            final String userID = this.getUserInfoBean().getUserID();
            if (userID != null && userID.length() > 0) {
                sb.append(",");
                sb.append(userID);
            }
        }
        return sb.toString();
    }

    /**
     * Unautenticate the pwmSession
     */
    public void unauthenticateUser() {
        final SessionStateBean ssBean = getSessionStateBean();

        getUserInfoBean().clearPermissions();

        if (ssBean.isAuthenticated()) { // try to tear out a session normally.
            final StringBuilder sb = new StringBuilder();

            sb.append("unauthenticate session from ").append(ssBean.getSrcAddress());
            if (getUserInfoBean().getUserDN() != null) {
                sb.append(" (").append(getUserInfoBean().getUserDN()).append(")");
            }

            // mark the session state bean as no longer being authenticated
            ssBean.setAuthenticated(false);
            ssBean.setOriginalRequestURL(null);

            // close out any outstanding connections
            getSessionManager().closeConnections();

            LOGGER.debug(this, sb.toString());
        }

        clearAllUserBeans();

        // clear CAS session if it exists.
        try {
            if (httpSession.getAttribute(AbstractCasFilter.CONST_CAS_ASSERTION) != null) {
                httpSession.removeAttribute(AbstractCasFilter.CONST_CAS_ASSERTION);
                LOGGER.debug("CAS assertion removed");
            }
        } catch (Exception e) {
            /* session already invalided */
        }
    }

    public SetupResponsesBean getSetupResponseBean() {
        return (SetupResponsesBean)getUserBean(SetupResponsesBean.class);
    }

    public UserInformationServletBean getUserInformationServletBean() {
        return (UserInformationServletBean)getUserBean(UserInformationServletBean.class);
    }

    public NewUserBean getNewUserBean() {
        return (NewUserBean)getUserBean(NewUserBean.class);
    }

    public void invalidate() {
        clearAllUserBeans();

        try {
            this.unauthenticateUser();
        } catch (Exception e) {
            //ignore
        }

        if (httpSession != null) {
            LOGGER.debug(this, "invalidating PwmSession");
            try { httpSession.invalidate(); } catch (Exception e) { /* nothing to do */ }
        }
    }

    private Serializable getUserBean(final Class theClass) {
        if (!userBeans.containsKey(theClass)) {
            try {
                final Object newBean = theClass.newInstance();
                userBeans.put(theClass,(Serializable)newBean);
            } catch (Exception e) {
                LOGGER.error("unexpected error trying to instantiate bean class " + theClass.getName() + ": " + e.getMessage(),e);
            }

        }
        return userBeans.get(theClass);
    }

    public long getLastAccessedTime() {
        if (httpSession != null) {
            return httpSession.getLastAccessedTime();
        } else {
            return 0;
        }
    }

    public String toString() {
        final StringBuilder sb = new StringBuilder();
        try {
            sb.append("sessionID=").append(getSessionStateBean().getSessionID());
            sb.append(", ");
            sb.append("auth=").append(getSessionStateBean().isAuthenticated());
            if (getSessionStateBean().isAuthenticated()) {
                sb.append(", ");
                sb.append("passwordStatus=").append(getUserInfoBean().getPasswordState());
                sb.append(", ");
                sb.append("guid=").append(getUserInfoBean().getUserGuid());
                sb.append(", ");
                sb.append("dn=").append(getUserInfoBean().getUserDN());
                sb.append(", ");
                sb.append("knownPassword=").append(!getUserInfoBean().isAuthFromUnknownPw());
                sb.append(", ");
                sb.append("needsNewPW=").append(getUserInfoBean().isRequiresNewPassword());
                sb.append(", ");
                sb.append("needsNewCR=").append(getUserInfoBean().isRequiresResponseConfig());
                sb.append(", ");
                sb.append("needsNewProfile=").append(getUserInfoBean().isRequiresUpdateProfile());
                sb.append(", ");
                sb.append("hasCRPolicy=").append(getUserInfoBean().getChallengeSet() != null);
            }
            sb.append(", ");
            sb.append("locale=").append(getSessionStateBean().getLocale());
            sb.append(", ");
            sb.append("theme=").append(getSessionStateBean().getTheme());
        } catch (Exception e) {
            sb.append("exception generating PwmSession.toString(): ").append(e.getMessage());
        }

        return sb.toString();
    }


}
