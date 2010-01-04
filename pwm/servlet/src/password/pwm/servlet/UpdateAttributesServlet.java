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

package password.pwm.servlet;

import com.novell.ldapchai.ChaiFactory;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiProvider;
import password.pwm.*;
import password.pwm.bean.SessionStateBean;
import password.pwm.bean.UpdateAttributesServletBean;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.Message;
import password.pwm.config.ParameterConfig;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmException;
import password.pwm.error.ValidationException;
import password.pwm.util.PwmLogger;
import password.pwm.util.stats.Statistic;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;

/**
 * User interaction servlet for updating user attributes
 *
 * @author Jason D. Rivard
 */
public class UpdateAttributesServlet extends TopServlet {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(UpdateAttributesServlet.class);

// -------------------------- OTHER METHODS --------------------------

    protected void processRequest(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws ServletException, ChaiUnavailableException, IOException, PwmException
    {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);

        if (!pwmSession.getConfig().readSettingAsBoolean(PwmSetting.ENABLE_UPDATE_ATTRIBUTES)) {
            final SessionStateBean ssBean = pwmSession.getSessionStateBean();
            ssBean.setSessionError(new ErrorInformation(Message.ERROR_SERVICE_NOT_AVAILABLE));
            Helper.forwardToErrorPage(req, resp, this.getServletContext());
            return;
        }

        final String actionParam = Validator.readStringFromRequest(req, Constants.PARAM_ACTION_REQUEST, 1024);

        if (actionParam != null && actionParam.equalsIgnoreCase("updateAttributes")) {
            doUpdate(req,resp);
            return;
        }

        populateFormFromLdap(req,resp);
    }


    private void populateFormFromLdap(final HttpServletRequest req, final HttpServletResponse resp)
            throws PwmException, ChaiUnavailableException, IOException, ServletException
    {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final String userDN = pwmSession.getUserInfoBean().getUserDN();
        final Map<String, ParameterConfig> validationParams = pwmSession.getUpdateAttributesServletBean().getUpdateAttributesParams();

        final Collection<String> involvedAttrs = new HashSet<String>(validationParams.keySet());

        final Properties formProps = pwmSession.getSessionStateBean().getLastParameterValues();
        final ChaiProvider provider = pwmSession.getSessionManager().getChaiProvider();

        try {
            final Properties userAttrValues = provider.readStringAttributes(userDN, involvedAttrs.toArray(new String[involvedAttrs.size()]));

            for (final String key : validationParams.keySet()) {
                final String value = userAttrValues.getProperty(key);
                if (value != null) {
                    formProps.setProperty(key, value);
                }
            }
        } catch (ChaiOperationException e) {
            LOGGER.warn(pwmSession, "error reading current attributes for user: " + e.getMessage());
        }

        this.forwardToJSP(req, resp);

    }

    private void doUpdate(final HttpServletRequest req, final HttpServletResponse resp)
            throws PwmException, ChaiUnavailableException, IOException, ServletException
    {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();
        final UserInfoBean uiBean = pwmSession.getUserInfoBean();

        final UpdateAttributesServletBean updateBean = pwmSession.getUpdateAttributesServletBean();
        final Map<String, ParameterConfig> validationParams = updateBean.getUpdateAttributesParams();

        //read the values from the request
        try {
            Validator.updateParamValues(pwmSession, req, validationParams);
        } catch (ValidationException e) {
            ssBean.setSessionError(e.getError());
            this.forwardToJSP(req, resp);
            return;
        }

        // see if the values meet requirements.
        try {
            Validator.validateParmValuesMeetRequirements(validationParams, pwmSession);
        } catch (ValidationException e) {
            ssBean.setSessionError(e.getError());
            this.forwardToJSP(req, resp);
            return;
        }

        try {
            // write values.
            LOGGER.info("updating attributes for " + uiBean.getUserDN());

            //write the values
            final ChaiProvider provider = pwmSession.getSessionManager().getChaiProvider();
            final ChaiUser actor = ChaiFactory.createChaiUser(pwmSession.getUserInfoBean().getUserDN(), provider);
            Helper.writeMapToEdir(pwmSession, actor, validationParams);

            // mark the event log
            UserHistory.updateUserHistory(pwmSession, UserHistory.Record.Event.ACTIVATE_USER, null);

            // re-populate the uiBean because we have changed some values.
            UserStatusHelper.populateActorUserInfoBean(pwmSession, uiBean.getUserDN(), uiBean.getUserCurrentPassword());
            
            // success, so forward to success page
            pwmSession.getContextManager().getStatisticsManager().incrementValue(Statistic.UPDATE_ATTRIBUTES);
            ssBean.setSessionSuccess(new ErrorInformation(Message.SUCCESS_UPDATE_ATTRIBUTES));
            Helper.forwardToSuccessPage(req, resp, this.getServletContext());

        } catch (ChaiOperationException e) {
            final ErrorInformation info = new ErrorInformation(Message.ERROR_UNKNOWN,"unexpected error writing to ldap: " + e.getMessage());
            LOGGER.warn(pwmSession, info);
            ssBean.setSessionError(info);
            Helper.forwardToErrorPage(req, resp, this.getServletContext());
        }

    }

    private void forwardToJSP(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException
    {
        this.getServletContext().getRequestDispatcher('/' + Constants.URL_JSP_UPDATE_ATTRIBUTES).forward(req, resp);
    }
}

