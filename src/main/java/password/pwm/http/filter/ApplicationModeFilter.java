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

package password.pwm.http.filter;

import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.ContextManager;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmURL;
import password.pwm.http.servlet.PwmServletDefinition;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.ServletException;
import java.io.IOException;

public class ApplicationModeFilter extends AbstractPwmFilter {

    private static final PwmLogger LOGGER = PwmLogger.getLogger(ApplicationModeFilter.class.getName());

    @Override
    public void processFilter(
            final PwmRequest pwmRequest,
            final PwmFilterChain chain
    )
            throws IOException, ServletException
    {
        // add request url to request attribute
        pwmRequest.setAttribute(PwmConstants.REQUEST_ATTR.OriginalUri, pwmRequest.getHttpServletRequest().getRequestURI());

        // ignore if resource request
        final PwmURL pwmURL = pwmRequest.getURL();
        if (!pwmURL.isResourceURL() && !pwmURL.isWebServiceURL() && !pwmURL.isReferenceURL()) {
            // check for valid config
            try {
                if (checkConfigModes(pwmRequest)) {
                    return;
                }
            } catch (PwmUnrecoverableException e) {
                if (e.getError() == PwmError.ERROR_UNKNOWN) {
                    try { LOGGER.error(e.getMessage()); } catch (Exception ignore) { /* noop */ }
                }
                pwmRequest.respondWithError(e.getErrorInformation(),true);
                return;
            }
        }

        chain.doFilter();
    }

    private static boolean checkConfigModes(
            final PwmRequest pwmRequest
    )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmApplication.MODE mode = pwmApplication.getApplicationMode();

        final PwmURL pwmURL = pwmRequest.getURL();

        if (mode == PwmApplication.MODE.NEW) {
            // check if current request is actually for the config url, if it is, just do nothing.
            if (pwmURL.isCommandServletURL() || pwmURL.isWebServiceURL()) {
                return false;
            }

            if (pwmURL.isConfigGuideURL()) {
                return false;
            } else {
                LOGGER.debug("unable to find a valid configuration, redirecting " + pwmURL + " to ConfigGuide");
                pwmRequest.sendRedirect(PwmServletDefinition.ConfigGuide);
                return true;
            }
        }

        if (mode == PwmApplication.MODE.ERROR) {
            ErrorInformation rootError = ContextManager.getContextManager(pwmRequest.getHttpServletRequest().getSession()).getStartupErrorInformation();
            if (rootError == null) {
                rootError = new ErrorInformation(PwmError.ERROR_APP_UNAVAILABLE, "Application startup failed.");
            }
            pwmRequest.respondWithError(rootError);
            return true;
        }

        // allow oauth
        if (pwmURL.isOauthConsumer()) {
            return false;
        }

        // block if public request and not running or in trial
        if (!PwmConstants.TRIAL_MODE) {
            if (pwmURL.isPublicUrl() && !pwmURL.isLogoutURL() && !pwmURL.isCommandServletURL() && !pwmURL.isCaptchaURL())  {
                if (mode == PwmApplication.MODE.CONFIGURATION) {
                    pwmRequest.respondWithError(new ErrorInformation(PwmError.ERROR_SERVICE_NOT_AVAILABLE,"public services are not available while configuration is open"));
                    return true;
                }
                if (mode != PwmApplication.MODE.RUNNING) {
                    pwmRequest.respondWithError(new ErrorInformation(PwmError.ERROR_SERVICE_NOT_AVAILABLE,"public services are not available while application is not in running mode"));
                    return true;
                }
            }
        }

        return false;
    }
}
