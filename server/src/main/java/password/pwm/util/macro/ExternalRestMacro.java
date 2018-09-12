/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
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

package password.pwm.util.macro;

import com.google.gson.reflect.TypeToken;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.pub.PublicUserInfoBean;
import password.pwm.error.PwmException;
import password.pwm.ldap.UserInfo;
import password.pwm.util.java.JsonUtil;
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

    public Pattern getRegExPattern( )
    {
        return pattern;
    }

    public String replaceValue(
            final String matchValue,
            final MacroRequestInfo macroRequestInfo
    )
    {
        final PwmApplication pwmApplication = macroRequestInfo.getPwmApplication();
        final UserInfo userInfoBean = macroRequestInfo.getUserInfo();

        final String inputString = matchValue.substring( 11, matchValue.length() - 1 );
        final Map<String, Object> sendData = new HashMap<>();

        try
        {
            if ( userInfoBean != null )
            {
                final MacroMachine macroMachine = MacroMachine.forUser( pwmApplication, PwmConstants.DEFAULT_LOCALE, SessionLabel.SYSTEM_LABEL, userInfoBean.getUserIdentity() );
                final PublicUserInfoBean publicUserInfoBean = PublicUserInfoBean.fromUserInfoBean(
                        userInfoBean,
                        pwmApplication.getConfig(),
                        PwmConstants.DEFAULT_LOCALE,
                        macroMachine
                );
                sendData.put( "userInfo", publicUserInfoBean );
            }
            sendData.put( "input", inputString );

            final String requestBody = JsonUtil.serializeMap( sendData );
            final String responseBody = RestClientHelper.makeOutboundRestWSCall( pwmApplication,
                    PwmConstants.DEFAULT_LOCALE, url,
                    requestBody );
            final Map<String, Object> responseMap = JsonUtil.deserialize( responseBody,
                    new TypeToken<Map<String, Object>>()
                    {
                    }
            );
            if ( responseMap.containsKey( "output" ) )
            {
                return responseMap.get( "output" ).toString();
            }
            else
            {
                return "";
            }
        }
        catch ( PwmException e )
        {
            final String errorMsg = "error while executing external macro '" + matchValue + "', error: " + e.getMessage();
            LOGGER.error( errorMsg );
            throw new IllegalStateException( errorMsg );
        }
    }
}
