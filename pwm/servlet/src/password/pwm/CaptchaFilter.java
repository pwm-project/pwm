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

package password.pwm;

import password.pwm.bean.SessionStateBean;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.PwmLogger;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class CaptchaFilter implements Filter {

    private static final PwmLogger LOGGER = PwmLogger.getLogger(CaptchaFilter.class);

    public void init(final FilterConfig filterConfig) throws ServletException {
    }

    public void doFilter(final ServletRequest servletRequest, final ServletResponse servletResponse, final FilterChain filterChain)
            throws IOException, ServletException
    {
        final HttpServletRequest req = (HttpServletRequest) servletRequest;
        final HttpServletResponse resp = (HttpServletResponse) servletResponse;

        try {
            processFilter(req,resp,filterChain);
        } catch (PwmUnrecoverableException e) {
            LOGGER.fatal("unexpected error processing captcha filter: " + e.getMessage(), e );
        }
    }

    private void processFilter(final HttpServletRequest req, final HttpServletResponse resp, final FilterChain filterChain)
            throws PwmUnrecoverableException, IOException {
        final PwmSession pwmSession = PwmSession.getPwmSession(req);
        final PwmApplication pwmApplication = ContextManager.getPwmApplication(req.getSession());
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();

        final String captchaServletURL = req.getContextPath() + "/public/" + PwmConstants.URL_SERVLET_CAPTCHA;

        checkIfCaptchaEnabled(pwmApplication,pwmSession);

        try {
            if (ssBean.isPassedCaptcha()) {
                filterChain.doFilter(req, resp);
                return;
            }

            final String requestedURL = req.getRequestURI();

            // check if current request is actually for the captcha servlet url, if it is, just do nothing.
            if (requestedURL.equals(captchaServletURL)) {
                LOGGER.trace(pwmSession, "permitting unverified request of captcha page");
                filterChain.doFilter(req, resp);
                return;
            }
        } catch (Exception e) {
            LOGGER.warn(pwmSession, "error during captcha filter: " + e.getMessage(),e);
        }

        LOGGER.debug(pwmSession, "session requires captcha verification, redirecting to Captcha servlet");

        // store the original requested url
        final String urlToStore = req.getRequestURI() + (req.getQueryString() != null ? ('?' + req.getQueryString()) : "");
        if (ssBean.getPreCaptchaRequestURL() == null) {
            ssBean.setPreCaptchaRequestURL(urlToStore);
        }

        resp.sendRedirect(SessionFilter.rewriteRedirectURL(SessionFilter.rewriteRedirectURL(captchaServletURL,req,resp), req, resp));
    }

    private void checkIfCaptchaEnabled(final PwmApplication pwmApplication, final PwmSession pwmSession) throws PwmUnrecoverableException {
        if (pwmSession.getSessionStateBean().isPassedCaptcha()) {
            return;
        }

        final Configuration config = pwmApplication.getConfig();
        final String privateKey = config.readSettingAsString(PwmSetting.RECAPTCHA_KEY_PRIVATE);
        final String publicKey = config.readSettingAsString(PwmSetting.RECAPTCHA_KEY_PUBLIC);

        if ((privateKey == null || privateKey.length() < 1) && (publicKey == null || publicKey.length() < 1)) {
            LOGGER.debug(pwmSession,"reCaptcha private or public key not configured, skipping captcha check");
            pwmSession.getSessionStateBean().setPassedCaptcha(true);
        }
    }

    public void destroy() {
    }
}
