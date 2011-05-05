/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2010 The PWM Project
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
import password.pwm.config.Configuration;
import password.pwm.config.FormConfiguration;
import password.pwm.config.Message;
import password.pwm.config.PwmSetting;
import password.pwm.error.*;
import password.pwm.util.Helper;
import password.pwm.util.PwmLogger;
import password.pwm.util.ServletHelper;
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
            throws ServletException, ChaiUnavailableException, IOException, PwmUnrecoverableException
    {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);

        if (!pwmSession.getConfig().readSettingAsBoolean(PwmSetting.UPDATE_ATTRIBUTES_ENABLE)) {
            final SessionStateBean ssBean = pwmSession.getSessionStateBean();
            ssBean.setSessionError(new ErrorInformation(PwmError.ERROR_SERVICE_NOT_AVAILABLE));
            ServletHelper.forwardToErrorPage(req, resp, this.getServletContext());
            return;
        }

        final String actionParam = Validator.readStringFromRequest(req, PwmConstants.PARAM_ACTION_REQUEST, 1024);

        if (actionParam != null && actionParam.equalsIgnoreCase("updateAttributes")) {
            handleUpdateRequest(req, resp);
            return;
        }

        populateFormFromLdap(req, resp);
    }


    private void populateFormFromLdap(final HttpServletRequest req, final HttpServletResponse resp)
            throws PwmUnrecoverableException, ChaiUnavailableException, IOException, ServletException
    {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final String userDN = pwmSession.getUserInfoBean().getUserDN();
        final Map<String, FormConfiguration> validationParams = pwmSession.getUpdateAttributesServletBean().getUpdateAttributesParams();

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

    private void handleUpdateRequest(final HttpServletRequest req, final HttpServletResponse resp)
            throws PwmUnrecoverableException, ChaiUnavailableException, IOException, ServletException
    {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();
        final UserInfoBean uiBean = pwmSession.getUserInfoBean();

        Validator.validatePwmFormID(req);

        final UpdateAttributesServletBean updateBean = pwmSession.getUpdateAttributesServletBean();
        final Map<String, FormConfiguration> validationParams = updateBean.getUpdateAttributesParams();

        //read the values from the request
        try {
            Validator.updateParamValues(pwmSession, req, validationParams);
        } catch (PwmDataValidationException e) {
            ssBean.setSessionError(e.getErrorInformation());
            this.forwardToJSP(req, resp);
            return;
        }

        // see if the values meet requirements.
        try {
            Validator.validateParmValuesMeetRequirements(validationParams, pwmSession);
        } catch (PwmDataValidationException e) {
            ssBean.setSessionError(e.getErrorInformation());
            this.forwardToJSP(req, resp);
            return;
        }

        try {
            // write values.
            LOGGER.info("updating attributes for " + uiBean.getUserDN());

            // write the form values
            final ChaiProvider provider = pwmSession.getSessionManager().getChaiProvider();
            final ChaiUser actor = ChaiFactory.createChaiUser(pwmSession.getUserInfoBean().getUserDN(), provider);
            Helper.writeMapToEdir(pwmSession, actor, validationParams);

            // write configured values
            final Collection<String> configValues = pwmSession.getConfig().readStringArraySetting(PwmSetting.UPDATE_ATTRIBUTES_WRITE_ATTRIBUTES);
            final Map<String, String> writeAttributesSettings = Configuration.convertStringListToNameValuePair(configValues, "=");
            final ChaiUser proxiedUser = ChaiFactory.createChaiUser(actor.getEntryDN(), pwmSession.getContextManager().getProxyChaiProvider());
            Helper.writeMapToEdir(pwmSession, proxiedUser, writeAttributesSettings);

            // mark the event log
            UserHistory.updateUserHistory(pwmSession, UserHistory.Record.Event.ACTIVATE_USER, null);

            // re-populate the uiBean because we have changed some values.
            UserStatusHelper.populateActorUserInfoBean(pwmSession, uiBean.getUserDN(), uiBean.getUserCurrentPassword());

            // success, so forward to success page
            pwmSession.getContextManager().getStatisticsManager().incrementValue(Statistic.UPDATE_ATTRIBUTES);
            ssBean.setSessionSuccess(Message.SUCCESS_UPDATE_ATTRIBUTES, null);
            ServletHelper.forwardToSuccessPage(req, resp, this.getServletContext());

        } catch (PwmOperationalException e) {
            final ErrorInformation info = new ErrorInformation(PwmError.ERROR_UPDATE_ATTRS_FAILURE, e.getErrorInformation().getDetailedErrorMsg(), e.getErrorInformation().getFieldValues());
            LOGGER.error(pwmSession, info);
            ssBean.setSessionError(info);
            ServletHelper.forwardToErrorPage(req, resp, this.getServletContext());
        }
    }

    private void forwardToJSP(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException {
        this.getServletContext().getRequestDispatcher('/' + PwmConstants.URL_JSP_UPDATE_ATTRIBUTES).forward(req, resp);
    }
}

