/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2011 The PWM Project
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

import password.pwm.bean.*;
import password.pwm.config.Configuration;
import password.pwm.config.FormConfiguration;
import password.pwm.config.PwmSetting;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.PwmLogger;
import password.pwm.util.stats.Statistic;
import password.pwm.util.stats.StatisticsManager;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;

/**
 * @author Jason D. Rivard
 */
public class PwmSession implements Serializable {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(PwmSession.class);

    private long creationTime;

    private final SessionStateBean sessionStateBean = new SessionStateBean();
    private ConfigManagerBean configManagerBean = new ConfigManagerBean();
    private ForgottenPasswordBean forgottenPasswordBean = new ForgottenPasswordBean();
    private UserInfoBean userInfoBean = new UserInfoBean();
    private ChangePasswordBean changePasswordBean = new ChangePasswordBean();
    private SessionManager sessionManager = new SessionManager(this);
    private SetupResponsesBean setupResponseBean = new SetupResponsesBean();

    private GuestUpdateServletBean guestUpdateServletBean;

    private UserInformationServletBean userInformationServletBean = new UserInformationServletBean();
    private HelpdeskBean helpdeskBean = new HelpdeskBean();
    private NewUserBean newUserBean = new NewUserBean();

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

    private PwmSession() {
    }

    private PwmSession(final HttpSession httpSession) throws PwmUnrecoverableException {
        this.creationTime = System.currentTimeMillis();
        this.httpSession = httpSession;
        this.getSessionStateBean().setSessionID("");

        final PwmApplication pwmApplication = getPwmApplication();
        if (pwmApplication != null) {
            final StatisticsManager statisticsManager = pwmApplication.getStatisticsManager();
            if (statisticsManager != null) {
                String sessionID = getPwmApplication().getStatisticsManager().getStatBundleForKey(StatisticsManager.KEY_CUMULATIVE).getStatistic(Statistic.HTTP_SESSIONS);
                try {
                    sessionID = new BigInteger(sessionID).toString(Character.MAX_RADIX);
                } catch (Exception e) { /* ignore */ }
                this.getSessionStateBean().setSessionID(sessionID);
            }
        }

    }

// --------------------- GETTER / SETTER METHODS ---------------------


    public ChangePasswordBean getChangePasswordBean() {
        return changePasswordBean;
    }

    public long getCreationTime() {
        return creationTime;
    }

    public ForgottenPasswordBean getForgottenPasswordBean() {
        return forgottenPasswordBean;
    }

    public void clearForgottenPasswordBean() {
        forgottenPasswordBean = new ForgottenPasswordBean();
    }

    public ConfigManagerBean getConfigManagerBean() {
        return configManagerBean;
    }

    public GuestUpdateServletBean getGuestUpdateServletBean() throws PwmUnrecoverableException {
        if (guestUpdateServletBean == null) {
            guestUpdateServletBean = new GuestUpdateServletBean();
            final List<FormConfiguration> formMap = getConfig().readSettingAsForm(PwmSetting.GUEST_FORM, sessionStateBean.getLocale());
            final String expAttr = getConfig().readSettingAsString(PwmSetting.GUEST_EXPIRATION_ATTRIBUTE);
            if (expAttr != null && expAttr.length() > 0) {
            	final String expConfig = "__accountDuration__:" + "Account Validity Duration (Days)" + ":number:1:5:true:false";
                try {
                    formMap.add(FormConfiguration.parseConfigString(expConfig));
                } catch (PwmOperationalException e) {
                    LOGGER.error(this, "unexpected error setting account duration form line: " + e.getMessage(), e);
                }
            }
            guestUpdateServletBean.setUpdateParams(Collections.unmodifiableList(formMap));
            final String namingAttribute = getConfig().readSettingAsString(PwmSetting.LDAP_NAMING_ATTRIBUTE);
            guestUpdateServletBean.setNamingAttribute(namingAttribute);
            Integer dur = 30;
            try {
	            dur = Integer.parseInt(getConfig().readSettingAsString(PwmSetting.GUEST_MAX_VALID_DAYS));
            } catch (Exception e) {
            }
            guestUpdateServletBean.setMaximumDuration(dur);
        }
        return guestUpdateServletBean;
    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }

    public SessionStateBean getSessionStateBean() {
        return sessionStateBean;
    }

    public UserInfoBean getUserInfoBean() {
        if (userInfoBean == null) {
            userInfoBean = new UserInfoBean();
        }
        return userInfoBean;
    }

    public HelpdeskBean getHelpdeskBean() {
        if (helpdeskBean == null) {
            helpdeskBean = new HelpdeskBean();
        }
        return helpdeskBean;
    }

// -------------------------- OTHER METHODS --------------------------

    public void clearChangePasswordBean() {
        changePasswordBean = new ChangePasswordBean();
    }

    public void clearAllUserBeans() // clears all but the session state bean. 
    {
        forgottenPasswordBean = new ForgottenPasswordBean();
        userInfoBean = new UserInfoBean();
        changePasswordBean = new ChangePasswordBean();
        setupResponseBean = new SetupResponsesBean();
        configManagerBean = new ConfigManagerBean();

        userInformationServletBean = new UserInformationServletBean();
        helpdeskBean = new HelpdeskBean();
        newUserBean = new NewUserBean();

        if (sessionManager != null) {
            sessionManager.closeConnections();
        }
        sessionManager = new SessionManager(this);
    }

    public Configuration getConfig() throws PwmUnrecoverableException {
        return getPwmApplication().getConfig();
    }

    public PwmApplication getPwmApplication() throws PwmUnrecoverableException {
        return ContextManager.getPwmApplication(httpSession);
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
    }

    public SetupResponsesBean getSetupResponseBean() {
        return setupResponseBean;
    }

    public UserInformationServletBean getUserInformationServletBean() {
        return userInformationServletBean;
    }

    public int getMaxInactiveInterval() {
        return httpSession == null ? -1 : httpSession.getMaxInactiveInterval();
    }

    public NewUserBean getNewUserBean() {
        return newUserBean;
    }

    public void invalidate() {
        if (httpSession != null) {
            httpSession.invalidate();
        }

        try {
            this.unauthenticateUser();
        } catch (Exception e) {
            //ignore
        }
    }
}
