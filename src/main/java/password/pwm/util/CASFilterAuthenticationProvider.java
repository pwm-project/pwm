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

package password.pwm.util;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import org.jasig.cas.client.util.AbstractCasFilter;
import org.jasig.cas.client.util.CommonUtils;
import org.jasig.cas.client.util.XmlUtils;
import org.jasig.cas.client.validation.Assertion;
import password.pwm.PwmApplication;
import password.pwm.PwmHttpFilterAuthenticationProvider;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmSession;
import password.pwm.ldap.auth.PwmAuthenticationSource;
import password.pwm.ldap.auth.SessionAuthenticator;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.http.HttpSession;
import java.io.UnsupportedEncodingException;

public class CASFilterAuthenticationProvider implements PwmHttpFilterAuthenticationProvider {

    private static final PwmLogger LOGGER = PwmLogger.forClass(CASFilterAuthenticationProvider.class);

    @Override
    public void attemptAuthentication(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException
    {
        try {
            final String clearPassUrl = pwmRequest.getConfig().readSettingAsString(PwmSetting.CAS_CLEAR_PASS_URL);
            if (clearPassUrl != null && clearPassUrl.length() > 0) {
                LOGGER.trace(pwmRequest, "checking for authentication via CAS");
                if (authUserUsingCASClearPass(pwmRequest, clearPassUrl)) {
                    LOGGER.debug(pwmRequest, "login via CAS successful");
                }
            }
        } catch (ChaiUnavailableException e) {
            throw PwmUnrecoverableException.fromChaiException(e);
        } catch (PwmOperationalException e) {
            throw new PwmUnrecoverableException(e.getErrorInformation());
        } catch (UnsupportedEncodingException e) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_UNKNOWN,"error during CAS authentication: " + e.getMessage()));
        }
    }

    @Override
    public boolean hasRedirectedResponse() {
        return false;
    }

    private static boolean authUserUsingCASClearPass(
            final PwmRequest pwmRequest,
            final String clearPassUrl
    )
            throws UnsupportedEncodingException, PwmUnrecoverableException, ChaiUnavailableException, PwmOperationalException
    {
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final HttpSession session = pwmRequest.getHttpServletRequest().getSession();

        //make sure user session isn't already authenticated
        if (pwmSession.isAuthenticated()) {
            return false;
        }

        // read CAS assertion out of the header (if it exists);
        final Assertion assertion = (Assertion) session.getAttribute(AbstractCasFilter.CONST_CAS_ASSERTION);
        if (assertion == null) {
            LOGGER.trace(pwmSession,"no CAS assertion header present, skipping CAS authentication attempt");
            return false;
        }

        // read cas proxy ticket
        final String proxyTicket = assertion.getPrincipal().getProxyTicketFor(clearPassUrl);
        if (proxyTicket == null) {
            LOGGER.trace(pwmSession,"no CAS proxy ticket available, skipping CAS authentication attempt");
            return false;
        }

        final String clearPassRequestUrl = clearPassUrl + "?" + "ticket="
                + proxyTicket + "&" + "service="
                + StringUtil.urlEncode(clearPassUrl);

        final String response = CommonUtils.getResponseFromServer(
                clearPassRequestUrl, "UTF-8");

        final String username = assertion.getPrincipal().getName();
        final PasswordData password = new PasswordData(XmlUtils.getTextForElement(response, "credentials"));

        if (password == null) {
            final String errorMsg = "CAS server did not return credentials for user '" + username + "'";
            LOGGER.trace(pwmSession, errorMsg);
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_WRONGPASSWORD,errorMsg);
            throw new PwmOperationalException(errorInformation);
        }

        //user isn't already authenticated and has CAS assertion and password, so try to auth them.
        LOGGER.debug(pwmSession, "attempting to authenticate user '" + username + "' using CAS assertion and password");
        final SessionAuthenticator sessionAuthenticator = new SessionAuthenticator(pwmApplication, pwmSession, PwmAuthenticationSource.CAS);
        sessionAuthenticator.searchAndAuthenticateUser(username, password, null, null);
        return true;
    }
}
