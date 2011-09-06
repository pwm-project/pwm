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

package password.pwm.servlet;

import com.novell.ldapchai.ChaiFactory;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiProvider;
import password.pwm.*;
import password.pwm.bean.SessionStateBean;
import password.pwm.bean.UserInfoBean;
import password.pwm.bean.UserInformationServletBean;
import password.pwm.config.Configuration;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.PwmLogger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;


/**
 * @author Jason D. Rivard
 */
public class UserInformationServlet extends TopServlet {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(UserInformationServlet.class);


// -------------------------- OTHER METHODS --------------------------

    public void processRequest(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws ServletException, IOException, ChaiUnavailableException, PwmUnrecoverableException
    {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();
        final UserInformationServletBean uisBean = pwmSession.getUserInformationServletBean();

        uisBean.setUserExists(false);        

        final String actionParam = Validator.readStringFromRequest(req, PwmConstants.PARAM_ACTION_REQUEST, 1024);

        if (actionParam != null && actionParam.equalsIgnoreCase("search")) {
            Validator.validatePwmFormID(req);

            final String username = Validator.readStringFromRequest(req, "username", 255);
            final String context = Validator.readStringFromRequest(req, "context", 255);

            if (username.length() < 1) {
                ssBean.setSessionError(new ErrorInformation(PwmError.ERROR_MISSING_PARAMETER));
                this.forwardToJSP(req, resp);
                return;
            }

            processUserSearch(pwmSession, username, context);

            if (!uisBean.isUserExists()) {
                ssBean.setSessionError(new ErrorInformation(PwmError.ERROR_CANT_MATCH_USER));
            }
        }

        forwardToJSP(req,resp);
    }

    private void processUserSearch(
            final PwmSession pwmSession,
            final String username,
            final String context
    )
            throws ChaiUnavailableException, PwmUnrecoverableException {
        final UserInformationServletBean uisBean = pwmSession.getUserInformationServletBean();


        final String userDN;
        try {
            userDN = UserStatusHelper.convertUsernameFieldtoDN(username, pwmSession, context);
        } catch (PwmOperationalException e) {
            LOGGER.trace(pwmSession, "can't find username: " + e.getMessage());
            uisBean.setUserExists(false);
            return;
        }

        uisBean.setUserExists(true);
        final UserInfoBean uiBean = new UserInfoBean();
        {
            final ChaiProvider provider = pwmSession.getSessionManager().getChaiProvider();
            final Locale userLocale = pwmSession.getSessionStateBean().getLocale();
            final Configuration config = pwmSession.getConfig();
            UserStatusHelper.populateUserInfoBean(pwmSession, uiBean, config, userLocale, userDN, null, provider);
        }
        uisBean.setUserInfoBean(uiBean);

        final ChaiUser theUser = ChaiFactory.createChaiUser(userDN, pwmSession.getSessionManager().getChaiProvider());

        try {
            uisBean.setResponseSet(CrUtility.readUserResponseSet(pwmSession, theUser));
        } catch (Exception e) {
            LOGGER.error(pwmSession,"unexpected error reading responses for user '" + userDN + "', " + e.getMessage());
        }

        try {
            uisBean.setIntruderLocked(theUser.isLocked());
        } catch (Exception e) {
            LOGGER.error(pwmSession,"unexpected error reading responses for user '" + userDN + "', " + e.getMessage());
        }

        try {
             uisBean.setLastLoginTime(theUser.readLastLoginTime());
         } catch (Exception e) {
             LOGGER.error(pwmSession,"unexpected error reading responses for user '" + userDN + "', " + e.getMessage());
         }

        uisBean.setPwmIntruder(false);
        try {
            pwmSession.getContextManager().getIntruderManager().checkUser(userDN,pwmSession);
        } catch (Exception e) {
            uisBean.setPwmIntruder(true);
        }

        {
            uisBean.setPasswordPolicyDN(null);
            final PwmPasswordPolicy searchedUsersPasswordPolicy = uiBean.getPasswordPolicy();
            String passwordPolicyDN = null;
            if (searchedUsersPasswordPolicy.getChaiPasswordPolicy() != null) {
                if (searchedUsersPasswordPolicy.getChaiPasswordPolicy().getPolicyEntry() != null) {
                    passwordPolicyDN = searchedUsersPasswordPolicy.getChaiPasswordPolicy().getPolicyEntry().getEntryDN();
                }
            }
            uisBean.setPasswordPolicyDN(passwordPolicyDN);
        }

        {
            uisBean.setPasswordRetrievable(false);
            try {
                final String usersPassword = ChaiFactory.createChaiUser(userDN, pwmSession.getContextManager().getProxyChaiProvider()).readPassword();
                if (usersPassword != null && usersPassword.length() > 0 ) {
                    uisBean.setPasswordRetrievable(true);
                }
            } catch (Exception e) {
                LOGGER.trace("error while testing if password retrievable for " + userDN + ": " + e.getMessage());
            }
        }
    }


    private void forwardToJSP(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException
    {
        this.getServletContext().getRequestDispatcher('/' + PwmConstants.URL_JSP_USER_INFORMATION).forward(req, resp);
    }
}