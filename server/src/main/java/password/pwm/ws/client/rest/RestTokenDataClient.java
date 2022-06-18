/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2021 The PWM Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package password.pwm.ws.client.rest;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import lombok.Value;
import password.pwm.PwmDomain;
import password.pwm.PwmConstants;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.bean.pub.PublicUserInfoBean;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.user.UserInfo;
import password.pwm.user.UserInfoBean;
import password.pwm.ldap.UserInfoFactory;
import password.pwm.svc.token.TokenDestinationDisplayMasker;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroRequest;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class RestTokenDataClient implements RestClient
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( RestTokenDataClient.class );

    private final PwmDomain pwmDomain;

    @Value
    public static class TokenDestinationData implements Serializable
    {
        private String email;
        private String sms;
        private String displayValue;
    }

    public RestTokenDataClient( final PwmDomain pwmDomain )
    {
        this.pwmDomain = pwmDomain;
    }

    private TokenDestinationData invoke(
            final SessionLabel sessionLabel,
            final TokenDestinationData tokenDestinationData,
            final UserIdentity userIdentity,
            final String url,
            final Locale locale
    )
            throws PwmOperationalException, ChaiUnavailableException, PwmUnrecoverableException
    {
        if ( tokenDestinationData == null )
        {
            throw new NullPointerException( "tokenDestinationData can not be null" );
        }

        final Map<String, Object> sendData = new LinkedHashMap<>();
        sendData.put( DATA_KEY_TOKENDATA, tokenDestinationData );
        if ( userIdentity != null )
        {
            final UserInfo userInfo = UserInfoFactory.newUserInfoUsingProxy(
                    pwmDomain.getPwmApplication(),
                    sessionLabel,
                    userIdentity, locale );

            final MacroRequest macroRequest = MacroRequest.forUser(
                    pwmDomain.getPwmApplication(),
                    PwmConstants.DEFAULT_LOCALE,
                    SessionLabel.SYSTEM_LABEL,
                    userInfo.getUserIdentity() );

            final PublicUserInfoBean publicUserInfoBean = UserInfoBean.toPublicUserInfoBean( userInfo, pwmDomain.getConfig(), PwmConstants.DEFAULT_LOCALE, macroRequest );
            sendData.put( RestClient.DATA_KEY_USERINFO, publicUserInfoBean );
        }


        final String jsonRequestData = JsonFactory.get().serializeMap( sendData );
        final String responseBody = RestClientHelper.makeOutboundRestWSCall( pwmDomain, sessionLabel, locale, url, jsonRequestData );
        return JsonFactory.get().deserialize( responseBody, TokenDestinationData.class );
    }

    public TokenDestinationData figureDestTokenDisplayString(
            final SessionLabel sessionLabel,
            final TokenDestinationData tokenDestinationData,
            final UserIdentity userIdentity,
            final Locale locale
    )
            throws PwmUnrecoverableException
    {
        final String configuredUrl = pwmDomain.getConfig().readSettingAsString( PwmSetting.EXTERNAL_MACROS_DEST_TOKEN_URLS );
        if ( configuredUrl != null && !configuredUrl.isEmpty() )
        {
            try
            {
                LOGGER.trace( sessionLabel, () -> "beginning token destination rest client call to " + configuredUrl );
                return invoke( sessionLabel, tokenDestinationData, userIdentity, configuredUrl, locale );
            }
            catch ( final Exception e )
            {
                final String errorMsg = "error making token destination rest client call; error: " + e.getMessage();
                LOGGER.error( sessionLabel, () -> errorMsg );
                throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_SERVICE_NOT_AVAILABLE, errorMsg ) );
            }
        }
        return builtInService( tokenDestinationData );
    }

    private TokenDestinationData builtInService( final TokenDestinationData tokenDestinationData )
    {

        final TokenDestinationDisplayMasker tokenDestinationDisplayMasker = new TokenDestinationDisplayMasker( pwmDomain.getConfig() );

        final StringBuilder tokenSendDisplay = new StringBuilder();

        if ( StringUtil.notEmpty( tokenDestinationData.getEmail() ) )
        {
            tokenSendDisplay.append( tokenDestinationDisplayMasker.maskEmail( tokenDestinationData.getEmail() ) );
        }

        if ( StringUtil.notEmpty( tokenDestinationData.getSms() ) )
        {
            if ( tokenSendDisplay.length() > 0 )
            {
                tokenSendDisplay.append( " / " );
            }

            tokenSendDisplay.append( tokenDestinationDisplayMasker.maskPhone( tokenDestinationData.getSms() ) );
        }

        return new TokenDestinationData(
                tokenDestinationData.getEmail(),
                tokenDestinationData.getSms(),
                tokenSendDisplay.toString()
        );
    }
}
