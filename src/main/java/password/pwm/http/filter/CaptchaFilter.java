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

import password.pwm.bean.SessionStateBean;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.ApplicationPage;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmURL;
import password.pwm.http.servlet.PwmServletDefinition;
import password.pwm.util.PasswordData;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.Set;

public class CaptchaFilter extends AbstractPwmFilter {

    private static final PwmLogger LOGGER = PwmLogger.forClass(CaptchaFilter.class);

    public void processFilter(
            final PwmRequest pwmRequest,
            final PwmFilterChain chain
    )
            throws IOException, ServletException
    {
        try {
            final boolean redirectPerformed = performRedirectIfRequired(pwmRequest);
            if (!redirectPerformed) {
                chain.doFilter();
            }
        } catch (PwmUnrecoverableException e) {
            LOGGER.fatal("unexpected error processing captcha filter: " + e.getMessage(), e );
        }
    }

    private boolean performRedirectIfRequired(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException, IOException
    {

        //if captcha is done then stop
        if (pwmRequest.getPwmSession().getSessionStateBean().isPassedCaptcha()) {
            return false;
        }

        //if captcha not enabled then stop
        if (!checkIfCaptchaEnabled(pwmRequest)) {
            LOGGER.debug(pwmRequest,"reCaptcha private or public key not configured, skipping captcha check");
            pwmRequest.getPwmSession().getSessionStateBean().setPassedCaptcha(true);
            return false;
        }

        final PwmURL pwmURL = pwmRequest.getURL();

        //if on captcha page, then stop.
        if (pwmURL.isCaptchaURL()) {
            return false;
        }


        final Set<ApplicationPage> protectedModules = pwmRequest.getConfig().readSettingAsOptionList(
                PwmSetting.CAPTCHA_PROTECTED_PAGES,
                ApplicationPage.class
        );

        boolean redirectNeeded = false;
        if (protectedModules != null) {
            if (protectedModules.contains(ApplicationPage.LOGIN) && pwmURL.isLoginServlet()) {
                redirectNeeded = true;
            } else if (protectedModules.contains(ApplicationPage.FORGOTTEN_PASSWORD) && pwmURL.isForgottenPasswordServlet()) {
                redirectNeeded = true;
            } else if (protectedModules.contains(ApplicationPage.FORGOTTEN_USERNAME) && pwmURL.isForgottenUsernameServlet()) {
                redirectNeeded = true;
            } else if (protectedModules.contains(ApplicationPage.USER_ACTIVATION) && pwmURL.isUserActivationServlet()) {
                redirectNeeded = true;
            } else if (protectedModules.contains(ApplicationPage.NEW_USER_REGISTRATION) && pwmURL.isNewUserRegistrationServlet()) {
                redirectNeeded = true;
            }
        }

        if (redirectNeeded) {
            LOGGER.debug(pwmRequest, "session requires captcha verification, redirecting to Captcha servlet");
            redirectToCaptchaServlet(pwmRequest);
            return true;
        }

        return false;
    }

    private void redirectToCaptchaServlet(
            final PwmRequest pwmRequest
    )
            throws IOException, PwmUnrecoverableException
    {
        final SessionStateBean sessionStateBean = pwmRequest.getPwmSession().getSessionStateBean();

        // store the original requested url
        if (sessionStateBean.getPreCaptchaRequestURL() == null) {
            final String urlToStore = pwmRequest.getURLwithQueryString();
            sessionStateBean.setPreCaptchaRequestURL(urlToStore);
        }

        pwmRequest.sendRedirect(PwmServletDefinition.Captcha);
    }

    private boolean checkIfCaptchaEnabled(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException
    {
        final Configuration config = pwmRequest.getPwmApplication().getConfig();
        final PasswordData privateKey = config.readSettingAsPassword(PwmSetting.RECAPTCHA_KEY_PRIVATE);
        final String publicKey = config.readSettingAsString(PwmSetting.RECAPTCHA_KEY_PUBLIC);

        return (privateKey != null && publicKey != null && !publicKey.isEmpty());
    }
}
