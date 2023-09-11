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

package password.pwm.util.macro;

import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.PwmDomain;
import password.pwm.bean.pub.PublicUserInfoBean;
import password.pwm.error.PwmException;
import password.pwm.user.UserInfo;
import password.pwm.user.UserInfoBean;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.logging.PwmLogger;
import password.pwm.ws.client.rest.RestClientHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * External macro @External1:&gt;value&lt;@ where 1 is incremental configuration item.
 */
class ExternalRestMacro extends AbstractMacro
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( ExternalRestMacro.class );

    private final Pattern pattern;
    private final String url;

    ExternalRestMacro(
            final int iteration,
            final String url
    )
    {
        this.pattern = Pattern.compile( "@External" + iteration + ":.*@" );
        this.url = url;
    }

    @Override
    public Pattern getRegExPattern( )
    {
        return pattern;
    }

    @Override
    public String replaceValue(
            final String matchValue,
            final MacroRequest macroRequestInfo
    )
    {
        final PwmApplication pwmApplication = macroRequestInfo.pwmApplication();
        final UserInfo userInfoBean = macroRequestInfo.userInfo();

        final String inputString = matchValue.substring( 11, matchValue.length() - 1 );
        final Map<String, Object> sendData = new HashMap<>();

        try
        {
            final PwmDomain pwmDomain = pwmApplication.domains().get( userInfoBean.getUserIdentity().getDomainID() );

            if ( userInfoBean != null )
            {
                final MacroRequest macroRequest = MacroRequest.forUser(
                        pwmApplication,
                        macroRequestInfo.userLocale(),
                        macroRequestInfo.sessionLabel(),
                        userInfoBean.getUserIdentity() );

                final PublicUserInfoBean publicUserInfoBean = UserInfoBean.toPublicUserInfoBean(
                        userInfoBean,
                        pwmDomain.getConfig(),
                        PwmConstants.DEFAULT_LOCALE,
                        macroRequest
                );

                sendData.put( "userInfo", publicUserInfoBean );
            }
            sendData.put( "input", inputString );

            final String requestBody = JsonFactory.get().serializeMap( sendData );
            final String responseBody = RestClientHelper.makeOutboundRestWSCall( pwmDomain,
                    macroRequestInfo.sessionLabel(),
                    PwmConstants.DEFAULT_LOCALE,
                    url,
                    requestBody );

            final Map<String, Object> responseMap = JsonFactory.get().deserializeMap( responseBody, String.class, Object.class );
            if ( responseMap.containsKey( "output" ) )
            {
                return responseMap.get( "output" ).toString();
            }
            else
            {
                return "";
            }
        }
        catch ( final PwmException e )
        {
            final String errorMsg = "error while executing external macro '" + matchValue + "', error: " + e.getMessage();
            LOGGER.error( () -> errorMsg );
            throw new IllegalStateException( errorMsg );
        }
    }

    @Override
    public Scope getScope()
    {
        return Scope.User;
    }
}
