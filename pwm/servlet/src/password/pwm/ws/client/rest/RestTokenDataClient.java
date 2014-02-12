/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2014 The PWM Project
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

package password.pwm.ws.client.rest;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import org.apache.commons.lang.NullArgumentException;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.PwmSession;
import password.pwm.bean.UserIdentity;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.PwmSetting;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.ldap.UserStatusReader;
import password.pwm.util.Helper;
import password.pwm.util.PwmLogger;
import password.pwm.ws.server.rest.RestStatusServer;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Locale;

public class RestTokenDataClient implements RestClient {

    private static final PwmLogger LOGGER = PwmLogger.getLogger(RestTokenDataClient.class);

    public static class TokenDestinationData implements Serializable {
        private String email;
        private String sms;
        private String displayValue;

        public TokenDestinationData(
                String email,
                String sms,
                String displayValue
        )
        {
            this.email = email;
            this.sms = sms;
            this.displayValue = displayValue;
        }

        public String getEmail()
        {
            return email;
        }

        public String getSms()
        {
            return sms;
        }

        public String getDisplayValue()
        {
            return displayValue;
        }
    }

    private final PwmApplication pwmApplication;

    public RestTokenDataClient(PwmApplication pwmApplication)
    {
        this.pwmApplication = pwmApplication;
    }

    private TokenDestinationData invoke(
            final PwmSession pwmSession,
            final TokenDestinationData tokenDestinationData,
            final UserIdentity userIdentity,
            final String url,
            final Locale locale
    )
            throws PwmOperationalException, ChaiUnavailableException, PwmUnrecoverableException
    {
        if (tokenDestinationData == null) {
            throw new NullArgumentException("tokenDestinationData can not be null");
        }

        final LinkedHashMap<String,Object> sendData = new LinkedHashMap<String, Object>();
        sendData.put(DATA_KEY_TOKENDATA, tokenDestinationData);
        final UserInfoBean userInfoBean = new UserInfoBean();
        UserStatusReader userStatusReader = new UserStatusReader(pwmApplication);
        userStatusReader.populateUserInfoBean(
                pwmSession,
                userInfoBean,
                locale,
                userIdentity,
                null
        );

        final RestStatusServer.JsonStatusData jsonStatusData = RestStatusServer.JsonStatusData.fromUserInfoBean(userInfoBean,pwmApplication.getConfig(), PwmConstants.DEFAULT_LOCALE);
        sendData.put(RestClient.DATA_KEY_USERINFO, jsonStatusData);

        final String jsonRequestData = Helper.getGson().toJson(sendData);
        final String responseBody = RestClientHelper.makeOutboundRestWSCall(pwmApplication, locale, url, jsonRequestData);
        return Helper.getGson().fromJson(responseBody,TokenDestinationData.class);
    }

    public TokenDestinationData figureDestTokenDisplayString(
            final PwmSession pwmSession,
            final TokenDestinationData tokenDestinationData,
            final UserIdentity userIdentity,
            final Locale locale
    ) {
        final String configuredUrl = pwmApplication.getConfig().readSettingAsString(PwmSetting.EXTERNAL_MACROS_DEST_TOKEN_URLS);
        if (configuredUrl != null && !configuredUrl.isEmpty()) {
            try {
                LOGGER.trace(pwmSession, "beginning token destination rest client call to " + configuredUrl);
                return invoke(pwmSession, tokenDestinationData, userIdentity, configuredUrl, locale);
            } catch (Exception e) {
                LOGGER.error(pwmSession, "error making token destination rest client call, error: " + e.getMessage());
            }
        }
        return builtInService(tokenDestinationData);
    }

    private TokenDestinationData builtInService(final TokenDestinationData tokenDestinationData) {
        final StringBuilder tokenSendDisplay = new StringBuilder();
        tokenSendDisplay.append(tokenDestinationData.getEmail());
        if (tokenSendDisplay.length() > 0) {
            tokenSendDisplay.append(" / ");
        }
        tokenSendDisplay.append(tokenDestinationData.getSms());

        return new TokenDestinationData(
                tokenDestinationData.getEmail(),
                tokenDestinationData.getSms(),
                tokenSendDisplay.toString()
        );
    }
}
