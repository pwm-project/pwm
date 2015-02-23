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

package password.pwm.util.operations;

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import password.pwm.PwmApplication;
import password.pwm.bean.UserIdentity;
import password.pwm.config.ActionConfiguration;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmSession;
import password.pwm.util.Helper;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;

import java.net.URI;
import java.util.List;

public class ActionExecutor {

    private static final PwmLogger LOGGER = PwmLogger.forClass(ActionExecutor.class);

    private PwmApplication pwmApplication;

    public ActionExecutor(PwmApplication pwmApplication) {
        this.pwmApplication = pwmApplication;
    }

    public void executeActions(
            final List<ActionConfiguration> configValues,
            final ActionExecutorSettings settings,
            final PwmSession pwmSession
    )
            throws ChaiUnavailableException, PwmOperationalException, PwmUnrecoverableException
    {
        for (final ActionConfiguration loopAction : configValues) {
            this.executeAction(loopAction, settings, pwmSession);
        }
    }

    public void executeAction(
            final ActionConfiguration actionConfiguration,
            final ActionExecutorSettings actionExecutorSettings,
            final PwmSession pwmSession
    )
            throws ChaiUnavailableException, PwmOperationalException, PwmUnrecoverableException
    {
        switch (actionConfiguration.getType()) {
            case ldap:
                executeLdapAction(actionConfiguration, actionExecutorSettings);
                break;

            case webservice:
                executeWebserviceAction(actionConfiguration, actionExecutorSettings);
                break;
        }

        LOGGER.info(pwmSession,"action " + actionConfiguration.getName() + " completed successfully");
    }

    private void executeLdapAction(final ActionConfiguration actionConfiguration, final ActionExecutorSettings settings)
            throws ChaiUnavailableException, PwmOperationalException, PwmUnrecoverableException
    {
        final String attributeName = actionConfiguration.getAttributeName();
        final String attributeValue = actionConfiguration.getAttributeValue();
        final ChaiUser theUser = settings.getChaiUser() != null ?
                settings.getChaiUser() :
                pwmApplication.getProxiedChaiUser(settings.getUserIdentity());

        writeLdapAttribute(
                theUser,
                attributeName,
                attributeValue,
                actionConfiguration.getLdapMethod(),
                settings.getMacroMachine()
        );
    }

    private void executeWebserviceAction(
            final ActionConfiguration actionConfiguration,
            final ActionExecutorSettings settings
    )
            throws PwmOperationalException, PwmUnrecoverableException
    {
        String url = actionConfiguration.getUrl();
        String body = actionConfiguration.getBody();
        final MacroMachine macroMachine = settings.getMacroMachine();

        try {
            // expand using pwm macros
            if (settings.isExpandPwmMacros()) {
                url = macroMachine.expandMacros(url, new MacroMachine.URLEncoderReplacer());
                body = body == null ? "" : macroMachine.expandMacros(body, new MacroMachine.URLEncoderReplacer());
            }

            LOGGER.debug("sending HTTP request: " + url);
            final URI requestURI = new URI(url);
            final HttpRequestBase httpRequest;
            switch (actionConfiguration.getMethod()) {
                case post:
                    httpRequest = new HttpPost(requestURI.toString());
                    ((HttpPost)httpRequest).setEntity(new StringEntity(body));
                    break;

                case put:
                    httpRequest = new HttpPut(requestURI.toString());
                    ((HttpPut)httpRequest).setEntity(new StringEntity(body));
                    break;

                case get:
                    httpRequest = new HttpGet(requestURI.toString());
                    break;

                case delete:
                    httpRequest = new HttpGet(requestURI.toString());
                    break;

                default:
                    throw new IllegalStateException("method not yet implemented");
            }

            if (actionConfiguration.getHeaders() != null) {
                for (final String headerName : actionConfiguration.getHeaders().keySet()) {
                    String headerValue = actionConfiguration.getHeaders().get(headerName);
                    headerValue = headerValue == null ? "" : macroMachine.expandMacros(headerValue);
                    httpRequest.setHeader(headerName,headerValue);
                }
            }

            final HttpClient httpClient = Helper.getHttpClient(pwmApplication.getConfig());
            final HttpResponse httpResponse = httpClient.execute(httpRequest);
            if (httpResponse.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                throw new PwmOperationalException(new ErrorInformation(
                        PwmError.ERROR_UNKNOWN,
                        "unexpected HTTP status code while calling external web service: "
                                + httpResponse.getStatusLine().getStatusCode() + " " + httpResponse.getStatusLine().getReasonPhrase()
                ));
            }

            final String responseBody = EntityUtils.toString(httpResponse.getEntity());
            LOGGER.debug("response from http rest request: " + httpResponse.getStatusLine());
            LOGGER.trace("response body from http rest request: " + responseBody);
        } catch (Exception e) {
            if (e instanceof PwmOperationalException) {
                throw (PwmOperationalException)e;
            }

            final String errorMsg = "unexpected error during API execution: " + e.getMessage();
            LOGGER.error(errorMsg);
            throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg));
        }
    }

    private static void writeLdapAttribute(
            final ChaiUser theUser,
            final String attrName,
            String attrValue,
            ActionConfiguration.LdapMethod ldapMethod,
            final MacroMachine macroMachine
    )
            throws PwmOperationalException, ChaiUnavailableException
    {
        if (ldapMethod == null) {
            ldapMethod = ActionConfiguration.LdapMethod.replace;
        }

        if (macroMachine != null) {
            attrValue  = macroMachine.expandMacros(attrValue);
        }

        LOGGER.trace("beginning ldap " + ldapMethod.toString() + " operation on " + theUser.getEntryDN() + ", attribute " + attrName);
        switch (ldapMethod) {
            case replace:
            {
                try {
                    theUser.writeStringAttribute(attrName, attrValue);
                    LOGGER.info("replaced attribute on user " + theUser.getEntryDN() + " (" + attrName + "=" + attrValue + ")");
                } catch (ChaiOperationException e) {
                    final String errorMsg = "error setting '" + attrName + "' attribute on user " + theUser.getEntryDN() + ", error: " + e.getMessage();
                    final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
                    final PwmOperationalException newException = new PwmOperationalException(errorInformation);
                    newException.initCause(e);
                    throw newException;
                }
            }
            break;

            case add:
            {
                try {
                    theUser.addAttribute(attrName, attrValue);
                    LOGGER.info("added attribute on user " + theUser.getEntryDN() + " (" + attrName + "=" + attrValue + ")");
                } catch (ChaiOperationException e) {
                    final String errorMsg = "error adding '" + attrName + "' attribute value from user " + theUser.getEntryDN() + ", error: " + e.getMessage();
                    final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
                    final PwmOperationalException newException = new PwmOperationalException(errorInformation);
                    newException.initCause(e);
                    throw newException;
                }

            }
            break;

            case remove:
            {
                try {
                    theUser.deleteAttribute(attrName, attrValue);
                    LOGGER.info("deleted attribute value on user " + theUser.getEntryDN() + " (" + attrName + ")");
                } catch (ChaiOperationException e) {
                    final String errorMsg = "error deletig '" + attrName + "' attribute value on user " + theUser.getEntryDN() + ", error: " + e.getMessage();
                    final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
                    final PwmOperationalException newException = new PwmOperationalException(errorInformation);
                    newException.initCause(e);
                    throw newException;
                }
            }
            break;

            default:
                throw new IllegalStateException("unexpected ldap method type " + ldapMethod);
        }
    }



    public static class ActionExecutorSettings {
        private MacroMachine macroMachine;
        private ChaiUser chaiUser;
        private UserIdentity userIdentity;
        private boolean expandPwmMacros = true;

        public boolean isExpandPwmMacros() {
            return expandPwmMacros;
        }

        public void setExpandPwmMacros(boolean expandPwmMacros) {
            this.expandPwmMacros = expandPwmMacros;
        }

        public ChaiUser getChaiUser()
        {
            return chaiUser;
        }

        public void setChaiUser(ChaiUser chaiUser)
        {
            this.chaiUser = chaiUser;
        }

        public MacroMachine getMacroMachine()
        {
            return macroMachine;
        }

        public void setMacroMachine(MacroMachine macroMachine)
        {
            this.macroMachine = macroMachine;
        }

        public UserIdentity getUserIdentity()
        {
            return userIdentity;
        }

        public void setUserIdentity(UserIdentity userIdentity)
        {
            this.userIdentity = userIdentity;
        }
    }
}
