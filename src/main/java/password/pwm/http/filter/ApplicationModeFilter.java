/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2016 The PWM Project
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
import password.pwm.PwmApplicationMode;
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
            final PwmApplicationMode mode,
            final PwmRequest pwmRequest,
            final PwmFilterChain chain
    )
            throws IOException, ServletException
    {
        // add request url to request attribute
        pwmRequest.setAttribute(PwmRequest.Attribute.OriginalUri, pwmRequest.getHttpServletRequest().getRequestURI());

        // ignore if resource request
        final PwmURL pwmURL = pwmRequest.getURL();
        if (!pwmURL.isResourceURL() && !pwmURL.isWebServiceURL() && !pwmURL.isReferenceURL()) {
            // check for valid config
            try {
                if (checkConfigModes(pwmRequest) == ProcessStatus.Halt) {
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

    @Override
    boolean isInterested(PwmApplicationMode mode, PwmURL pwmURL) {
        return !pwmURL.isResourceURL();
    }

    private static ProcessStatus checkConfigModes(
            final PwmRequest pwmRequest
    )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmApplicationMode mode = pwmApplication.getApplicationMode();

        final PwmURL pwmURL = pwmRequest.getURL();

        if (mode == PwmApplicationMode.NEW) {
            // check if current request is actually for the config url, if it is, just do nothing.
            if (pwmURL.isCommandServletURL() || pwmURL.isWebServiceURL()) {
                return ProcessStatus.Continue;
            }

            if (pwmURL.isConfigGuideURL()) {
                return ProcessStatus.Continue;
            } else {
                LOGGER.debug("unable to find a valid configuration, redirecting " + pwmURL + " to ConfigGuide");
                pwmRequest.sendRedirect(PwmServletDefinition.ConfigGuide);
                return ProcessStatus.Halt;
            }
        }

        if (mode == PwmApplicationMode.ERROR) {
            ErrorInformation rootError = ContextManager.getContextManager(pwmRequest.getHttpServletRequest().getSession()).getStartupErrorInformation();
            if (rootError == null) {
                rootError = new ErrorInformation(PwmError.ERROR_APP_UNAVAILABLE, "Application startup failed.");
            }
            pwmRequest.respondWithError(rootError);
            return ProcessStatus.Halt;
        }

        // allow oauth
        if (pwmURL.isOauthConsumer()) {
            return ProcessStatus.Continue;
        }

        // block if public request and not running or in trial
        if (!PwmConstants.TRIAL_MODE) {
            if (mode != PwmApplicationMode.RUNNING) {
                final boolean permittedURl = pwmURL.isResourceURL()
                        || pwmURL.isIndexPage()
                        || pwmURL.isConfigManagerURL()
                        || pwmURL.isConfigGuideURL()
                        || pwmURL.isCommandServletURL()
                        || pwmURL.isReferenceURL()
                        || pwmURL.isCaptchaURL()
                        || pwmURL.isLoginServlet()
                        || pwmURL.isLogoutURL()
                        || pwmURL.isOauthConsumer()
                        || pwmURL.isAdminUrl()
                        || pwmURL.isWebServiceURL();

                if (!permittedURl) {
                    final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_APPLICATION_NOT_RUNNING);
                    pwmRequest.respondWithError(errorInformation);
                    return ProcessStatus.Halt;
                }
            }
        }

        return ProcessStatus.Continue;
    }
}
