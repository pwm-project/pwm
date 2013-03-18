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

package password.pwm.servlet;

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.*;
import password.pwm.bean.SessionStateBean;
import password.pwm.config.Configuration;
import password.pwm.config.FormConfiguration;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.i18n.Message;
import password.pwm.util.PwmLogger;
import password.pwm.util.ServletHelper;
import password.pwm.util.operations.UserSearchEngine;
import password.pwm.util.stats.Statistic;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class ForgottenUsernameServlet extends TopServlet {
    private static final PwmLogger LOGGER = PwmLogger.getLogger(ForgottenUsernameServlet.class);

// -------------------------- OTHER METHODS --------------------------

    protected void processRequest(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws ServletException, ChaiUnavailableException, IOException, PwmUnrecoverableException {
        final Configuration config = ContextManager.getPwmApplication(req).getConfig();

        final String actionParam = Validator.readStringFromRequest(req, PwmConstants.PARAM_ACTION_REQUEST);

        if (!config.readSettingAsBoolean(PwmSetting.FORGOTTEN_USERNAME_ENABLE)) {
            PwmSession.getPwmSession(req).getSessionStateBean().setSessionError(PwmError.ERROR_SERVICE_NOT_AVAILABLE.toInfo());
            ServletHelper.forwardToErrorPage(req, resp, this.getServletContext());
            return;
        }

        if (actionParam != null && actionParam.equalsIgnoreCase("search")) {
            handleSearchRequest(req, resp);
            return;
        }

        forwardToJSP(req, resp);
    }

    public void handleSearchRequest(final HttpServletRequest req, final HttpServletResponse resp)
            throws PwmUnrecoverableException, ChaiUnavailableException, IOException, ServletException {
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();

        Validator.validatePwmFormID(req);

        final List<FormConfiguration> forgottenUsernameForm = pwmApplication.getConfig().readSettingAsForm(PwmSetting.FORGOTTEN_USERNAME_FORM);

        try {
            //read the values from the request
            final Map<FormConfiguration, String> formValues = Validator.readFormValuesFromRequest(req, forgottenUsernameForm, ssBean.getLocale());

            // see if the values meet the configured form requirements.
            Validator.validateParmValuesMeetRequirements(formValues, ssBean.getLocale());

            final UserSearchEngine userSearchEngine = new UserSearchEngine(pwmApplication);
            final UserSearchEngine.SearchConfiguration searchConfiguration = new UserSearchEngine.SearchConfiguration();
            searchConfiguration.setFilter(pwmApplication.getConfig().readSettingAsString(PwmSetting.FORGOTTEN_USERNAME_SEARCH_FILTER));
            searchConfiguration.setFormValues(formValues);
            final ChaiUser theUser = userSearchEngine.performSingleUserSearch(pwmSession, searchConfiguration);

            if (theUser == null) {
                ssBean.setSessionError(new ErrorInformation(PwmError.ERROR_CANT_MATCH_USER));
                pwmApplication.getIntruderManager().mark(null,null,pwmSession);
                pwmApplication.getStatisticsManager().incrementValue(Statistic.FORGOTTEN_USERNAME_FAILURES);
                forwardToJSP(req, resp);
                return;
            }

            // make sure the user isn't locked.
            pwmApplication.getIntruderManager().check(null,theUser.getEntryDN(),pwmSession);

            // redirect user to success page.
            LOGGER.info(pwmSession, "found user " + theUser.getEntryDN());
            try {
                final String usernameAttribute = pwmApplication.getConfig().readSettingAsString(PwmSetting.FORGOTTEN_USERNAME_USERNAME_ATTRIBUTE);
                final String username = theUser.readStringAttribute(usernameAttribute);
                LOGGER.trace(pwmSession, "read username attribute '" + usernameAttribute + "' value=" + username);
                ssBean.setSessionSuccess(Message.SUCCESS_FORGOTTEN_USERNAME, username);
                pwmApplication.getIntruderManager().clear(null,null,pwmSession);
                pwmApplication.getStatisticsManager().incrementValue(Statistic.FORGOTTEN_USERNAME_SUCCESSES);
                ServletHelper.forwardToSuccessPage(req, resp);
                return;
            } catch (Exception e) {
                LOGGER.error("error reading username value for " + theUser.getEntryDN() + ", " + e.getMessage());
            }

        } catch (PwmOperationalException e) {
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_CANT_MATCH_USER, e.getErrorInformation().getDetailedErrorMsg(), e.getErrorInformation().getFieldValues());
            ssBean.setSessionError(errorInfo);
            pwmApplication.getIntruderManager().mark(null,null,pwmSession);
        }

        pwmApplication.getStatisticsManager().incrementValue(Statistic.FORGOTTEN_USERNAME_FAILURES);
        forwardToJSP(req, resp);
    }

    private void forwardToJSP(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException {
        this.getServletContext().getRequestDispatcher('/' + PwmConstants.URL_JSP_FORGOTTEN_USERNAME).forward(req, resp);
    }
}
