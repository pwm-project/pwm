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

package password.pwm.util.operations;

import com.novell.ldapchai.ChaiUser;
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
import password.pwm.PwmSession;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.ActionConfiguration;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.util.Helper;
import password.pwm.util.MacroMachine;
import password.pwm.util.PwmLogger;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ActionExecutor {

    private static final PwmLogger LOGGER = PwmLogger.getLogger(ActionExecutor.class);

    private PwmApplication pwmApplication;

    public ActionExecutor(PwmApplication pwmApplication) {
        this.pwmApplication = pwmApplication;
    }

    public void executeActions(
            final List<ActionConfiguration> configValues,
            final ActionExecutorSettings settings,
            final PwmSession pwmSession
    )
            throws ChaiUnavailableException, PwmOperationalException
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
            throws ChaiUnavailableException, PwmOperationalException
    {
        switch (actionConfiguration.getType()) {
            case ldap:
                executeLdapAction(actionConfiguration, actionExecutorSettings);
                break;

            case webservice:
                executeWebserviceAction(actionConfiguration, actionExecutorSettings);
                break;
        }

        String username = "<unknown>";
        if (actionExecutorSettings.getUserInfoBean() != null) {
            username = actionExecutorSettings.getUserInfoBean().getUserID();
        }
        LOGGER.info(pwmSession,"action " + actionConfiguration.getName() + " completed successfully upon user " + username);
    }

    private void executeLdapAction(final ActionConfiguration actionConfiguration, final ActionExecutorSettings settings)
            throws ChaiUnavailableException, PwmOperationalException
    {
        final String attributeName = actionConfiguration.getAttributeName();
        final String attributeValue = actionConfiguration.getAttributeValue();
        final Map<String,String> attributeMap = Collections.singletonMap(attributeName,attributeValue);

        Helper.writeMapToLdap(
                pwmApplication,
                settings.getUser(),
                attributeMap,
                settings.getUserInfoBean(),
                settings.isExpandPwmMacros()
        );
    }

    private void executeWebserviceAction(
            final ActionConfiguration actionConfiguration,
            final ActionExecutorSettings settings) throws PwmOperationalException {
        String url = actionConfiguration.getUrl();
        String body = actionConfiguration.getBody();
        final UserInfoBean userInfoBean = settings.getUserInfoBean();
        final UserDataReader userDataReader = new UserDataReader(settings.getUser());

        try {
            // expand using pwm macros
            if (settings.isExpandPwmMacros()) {
                url = MacroMachine.expandMacros(url, pwmApplication, userInfoBean, userDataReader, new MacroMachine.URLEncoderReplacer());
                body = body == null ? "" : MacroMachine.expandMacros(body, pwmApplication, userInfoBean, userDataReader, new MacroMachine.URLEncoderReplacer());
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
                    headerValue = headerValue == null ? "" : MacroMachine.expandMacros(headerValue, pwmApplication, userInfoBean, userDataReader);
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


    public static class ActionExecutorSettings {
        private UserInfoBean userInfoBean;
        private boolean expandPwmMacros = true;
        private ChaiUser user;

        public UserInfoBean getUserInfoBean() {
            return userInfoBean;
        }

        public void setUserInfoBean(UserInfoBean userInfoBean) {
            this.userInfoBean = userInfoBean;
        }

        public boolean isExpandPwmMacros() {
            return expandPwmMacros;
        }

        public void setExpandPwmMacros(boolean expandPwmMacros) {
            this.expandPwmMacros = expandPwmMacros;
        }

        public ChaiUser getUser() {
            return user;
        }

        public void setUser(ChaiUser user) {
            this.user = user;
        }
    }
}
