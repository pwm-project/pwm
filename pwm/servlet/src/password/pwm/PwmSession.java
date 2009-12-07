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

package password.pwm;

import password.pwm.bean.*;
import password.pwm.config.Configuration;
import password.pwm.config.LocalizedConfiguration;
import password.pwm.config.ParameterConfig;
import password.pwm.util.PwmLogger;
import password.pwm.util.StatisticsManager;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Jason D. Rivard
 */
public class PwmSession implements Serializable {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(PwmSession.class);

    private long creationTime;

    private final SessionStateBean sessionStateBean = new SessionStateBean();
    private ForgottenPasswordBean forgottenPasswordBean = new ForgottenPasswordBean();
    private UserInfoBean userInfoBean = new UserInfoBean();
    private NewUserServletBean newUserServletBean = new NewUserServletBean();
    private UpdateAttributesServletBean updateAttributesServletBean = new UpdateAttributesServletBean();
    private ChangePasswordBean changePasswordBean = new ChangePasswordBean();
    private ActivateUserServletBean activateUserServletBean = new ActivateUserServletBean();
    private SessionManager sessionManager = new SessionManager(this);
    private SetupResponsesBean setupResponseBean = new SetupResponsesBean();

    private transient HttpSession httpSession;

// -------------------------- STATIC METHODS --------------------------

    public static ChangePasswordBean getChangePasswordBean(final HttpSession session)
    {
        return PwmSession.getPwmSession(session).getChangePasswordBean();
    }

    public static ForgottenPasswordBean getForgottenPasswordBean(final HttpServletRequest req)
    {
        return PwmSession.getPwmSession(req).getForgottenPasswordBean();
    }

    public static PwmSession getPwmSession(final HttpSession httpSession)
    {
        if (httpSession == null) {
            final RuntimeException e = new NullPointerException("cannot fetch a pwmSession using a null httpSession");
            LOGGER.warn("attempt to fetch a pwmSession with a null session",e);
            throw e;
        }

        PwmSession returnSession = (PwmSession)httpSession.getAttribute(Constants.SESSION_ATTR_PWM_SESSION);
        if (returnSession == null) {
            final PwmSession newBean = new PwmSession(httpSession);
            httpSession.setAttribute(Constants.SESSION_ATTR_PWM_SESSION, newBean);
            returnSession = newBean;
        } else if (returnSession.httpSession == null) { // stale session (was previously passivated)
            returnSession.httpSession = httpSession;
            ContextManager.getContextManager(httpSession).addPwmSession(returnSession);
            final String oldSessionID = returnSession.getSessionStateBean().getSessionID();
            if (!oldSessionID.contains("-stale")) {
                returnSession.getSessionStateBean().setSessionID(oldSessionID + "-stale");
            }
        }

        return returnSession;
    }

    public static PwmSession getPwmSession(final HttpServletRequest httpRequest)
    {
        return PwmSession.getPwmSession(httpRequest.getSession());
    }

    public static SessionStateBean getSessionStateBean(final HttpSession session)
    {
        return PwmSession.getPwmSession(session).getSessionStateBean();
    }

    public static UserInfoBean getUserInfoBean(final HttpSession session)
    {
        return PwmSession.getPwmSession(session).getUserInfoBean();
    }

// --------------------------- CONSTRUCTORS ---------------------------

    public PwmSession()
    {
        //should not be used, only made for the servlet listener.
    }

    private PwmSession(final HttpSession httpSession)
    {
        this.creationTime = System.currentTimeMillis();
        this.httpSession = httpSession;

        this.initFormBeans();

        final String sessionID = getContextManager().getStatisticsManager().getCurrentStat(StatisticsManager.Statistic.HTTP_SESSIONS);
        this.getSessionStateBean().setSessionID(sessionID);

    }

    private void initFormBeans() {
        {
            final Map<String, ParameterConfig> configMap = getLocaleConfig().getUpdateAttributesAttributes();
            final Map<String, ParameterConfig> paramMap = new LinkedHashMap<String, ParameterConfig>(configMap);
            updateAttributesServletBean.setUpdateAttributesParams(paramMap);
        }

        {
            final Map<String, ParameterConfig> configMap = getLocaleConfig().getActivateUserAttributes();
            final Map<String, ParameterConfig> paramMap = new LinkedHashMap<String, ParameterConfig>(configMap);
            activateUserServletBean.setActivateUserParams(paramMap);
        }

        {
            final Map<String, ParameterConfig> configMap = getLocaleConfig().getNewUserCreationAttributes();
            final Map<String, ParameterConfig> paramMap =  new LinkedHashMap<String, ParameterConfig>(configMap);
            newUserServletBean.setCreationParams(paramMap);
        }
    }

// --------------------- GETTER / SETTER METHODS ---------------------

    public ActivateUserServletBean getActivateUserServletBean()
    {
        return activateUserServletBean;
    }

    public ChangePasswordBean getChangePasswordBean()
    {
        return changePasswordBean;
    }

    public long getCreationTime()
    {
        return creationTime;
    }

    public ForgottenPasswordBean getForgottenPasswordBean()
    {
        return forgottenPasswordBean;
    }

    public NewUserServletBean getNewUserServletBean()
    {
        return newUserServletBean;
    }

    public SessionManager getSessionManager()
    {
        return sessionManager;
    }

    public SessionStateBean getSessionStateBean()
    {
        return sessionStateBean;
    }

    public UpdateAttributesServletBean getUpdateAttributesServletBean()
    {
        return updateAttributesServletBean;
    }

    public UserInfoBean getUserInfoBean()
    {
        if (userInfoBean == null) {
            userInfoBean = new UserInfoBean();
        }
        return userInfoBean;
    }

// -------------------------- OTHER METHODS --------------------------

    public void clearChangePasswordBean()
    {
        changePasswordBean = new ChangePasswordBean();
    }

    public void clearAllUserBeans() // clears all but the session state bean. 
    {
        forgottenPasswordBean = new ForgottenPasswordBean();
        userInfoBean = new UserInfoBean();
        newUserServletBean = new NewUserServletBean();
        updateAttributesServletBean = new UpdateAttributesServletBean();
        changePasswordBean = new ChangePasswordBean();
        activateUserServletBean = new ActivateUserServletBean();
        setupResponseBean = new SetupResponsesBean();

        if (sessionManager != null) {
            sessionManager.closeConnections();
        }
        sessionManager = new SessionManager(this);

        initFormBeans();
    }

    public Configuration getConfig()
    {
        return getContextManager().getConfig();
    }

    public ContextManager getContextManager() {
        return ContextManager.getContextManager(httpSession);
    }

    public LocalizedConfiguration getLocaleConfig() {
        return getContextManager().getLocaleConfig(getSessionStateBean().getLocale());
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
    public void unauthenticateUser()
    {
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
}
