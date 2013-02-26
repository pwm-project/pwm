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

import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.*;
import password.pwm.bean.SessionStateBean;
import password.pwm.config.ConfigurationReader;
import password.pwm.config.PwmSetting;
import password.pwm.config.StoredConfiguration;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.PwmLogger;
import password.pwm.util.ServletHelper;
import password.pwm.ws.server.RestResultBean;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class InstallManagerServlet extends TopServlet {

    private static final PwmLogger LOGGER = PwmLogger.getLogger(InstallManagerServlet.class.getName());

    @Override
    protected void processRequest(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException, ChaiUnavailableException, PwmUnrecoverableException
    {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();
        final String actionParam = Validator.readStringFromRequest(req, PwmConstants.PARAM_ACTION_REQUEST);

        if (pwmApplication.getApplicationMode() != PwmApplication.MODE.NEW) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNAUTHORIZED,"InstallManager unavailable unless in NEW mode");
            ssBean.setSessionError(errorInformation);
            LOGGER.error(pwmSession,errorInformation.toDebugStr());
            return;
        }

        if (actionParam != null && actionParam.length() > 0) {
            Validator.validatePwmFormID(req);
            if (actionParam.equalsIgnoreCase("selectTemplate")) {
                restSelectTemplate(req, resp, pwmApplication, pwmSession);
                return;
            }
        }

        forwardToJSP(req,resp);
    }

    private void restSelectTemplate(
            final HttpServletRequest req,
            final HttpServletResponse resp,
            final PwmApplication pwmApplication,
            final PwmSession pwmSession
    )
            throws PwmUnrecoverableException, IOException
    {
        final String requestedTemplate = Validator.readStringFromRequest(req, "template");
        PwmSetting.Template template = null;
        if (requestedTemplate != null && requestedTemplate.length() > 0) {
            try {
                template = PwmSetting.Template.valueOf(requestedTemplate);
            } catch (IllegalArgumentException e) {
                final String errorMsg = "unknown template set request: " + requestedTemplate;
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR,errorMsg);
                final RestResultBean restResultBean = RestResultBean.fromErrorInformation(errorInformation, pwmApplication, pwmSession);
                LOGGER.error(pwmSession,errorInformation.toDebugStr());
                ServletHelper.outputJsonResult(resp,restResultBean);
                return;
            }
        }

        final StoredConfiguration newStoredConfig = StoredConfiguration.getDefaultConfiguration();
        LOGGER.trace("setting template to: " + requestedTemplate);
        newStoredConfig.writeProperty(StoredConfiguration.PROPERTY_KEY_TEMPLATE, template.toString());
        newStoredConfig.writeProperty(StoredConfiguration.PROPERTY_KEY_CONFIG_IS_EDITABLE, "true");
        ConfigurationReader configReader = ContextManager.getContextManager(req.getSession().getServletContext()).getConfigReader();
        try {
            configReader.saveConfiguration(newStoredConfig);
            ContextManager.getContextManager(req.getSession()).reinitialize();
            final RestResultBean restResultBean = new RestResultBean();
            ServletHelper.outputJsonResult(resp,restResultBean);
        } catch (Exception e) {
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_INVALID_CONFIG,"unable to save configuration: " + e.getLocalizedMessage());
            pwmSession.getSessionStateBean().setSessionError(errorInformation);
            LOGGER.error(pwmSession,errorInformation.toDebugStr());
            final RestResultBean restResultBean = RestResultBean.fromErrorInformation(errorInformation, pwmApplication, pwmSession);
            ServletHelper.outputJsonResult(resp,restResultBean);
            return;
        }


    }


    static void forwardToJSP(
            final HttpServletRequest req,
            final HttpServletResponse resp
    )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        final ServletContext servletContext = req.getSession().getServletContext();
        servletContext.getRequestDispatcher('/' + PwmConstants.URL_JSP_INSTALL_MANAGER_MODE_NEW).forward(req, resp);
    }

}
