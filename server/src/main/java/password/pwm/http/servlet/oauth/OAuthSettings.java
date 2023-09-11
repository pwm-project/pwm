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

package password.pwm.http.servlet.oauth;

import password.pwm.config.DomainConfig;
import password.pwm.config.PwmSetting;
import password.pwm.config.profile.ForgottenPasswordProfile;
import password.pwm.util.PasswordData;
import password.pwm.util.java.StringUtil;

import java.security.cert.X509Certificate;
import java.util.List;

public record OAuthSettings(
        String loginURL,
        String codeResolveUrl,
        String attributesUrl,
        String scope,
        String clientID,
        PasswordData secret,
        String dnAttributeName,
        OAuthUseCase use,
        List<X509Certificate> certificates,
        String usernameSendValue
)
{
    private static final OAuthSettings EMPTY = new OAuthSettings(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            List.of(),
            null );

    public static OAuthSettings empty()
    {
        return EMPTY;
    }

    public boolean oAuthIsConfigured()
    {
        return !StringUtil.isEmpty( loginURL )
                && !StringUtil.isEmpty( codeResolveUrl )
                && !StringUtil.isEmpty( attributesUrl )
                && !StringUtil.isEmpty( clientID )
                && ( secret != null )
                && !StringUtil.isEmpty( dnAttributeName );
    }

    public static OAuthSettings forSSOAuthentication( final DomainConfig config )
    {
        return new OAuthSettings(
                config.readSettingAsString( PwmSetting.OAUTH_ID_LOGIN_URL ),
                config.readSettingAsString( PwmSetting.OAUTH_ID_CODERESOLVE_URL ),
                config.readSettingAsString( PwmSetting.OAUTH_ID_ATTRIBUTES_URL ),
                config.readSettingAsString( PwmSetting.OAUTH_ID_SCOPE ),
                config.readSettingAsString( PwmSetting.OAUTH_ID_CLIENTNAME ),
                config.readSettingAsPassword( PwmSetting.OAUTH_ID_SECRET ),
                config.readSettingAsString( PwmSetting.OAUTH_ID_DN_ATTRIBUTE_NAME ),
                OAuthUseCase.Authentication,
                config.readSettingAsCertificate( PwmSetting.OAUTH_ID_CERTIFICATE ),
                null );

    }

    public static OAuthSettings forForgottenPassword( final ForgottenPasswordProfile config )
    {
        return new OAuthSettings(
                 config.readSettingAsString( PwmSetting.RECOVERY_OAUTH_ID_LOGIN_URL ),
                 config.readSettingAsString( PwmSetting.RECOVERY_OAUTH_ID_CODERESOLVE_URL ),
                 config.readSettingAsString( PwmSetting.RECOVERY_OAUTH_ID_ATTRIBUTES_URL ),
                 null,
                 config.readSettingAsString( PwmSetting.RECOVERY_OAUTH_ID_CLIENTNAME ),
                 config.readSettingAsPassword( PwmSetting.RECOVERY_OAUTH_ID_SECRET ),
                 config.readSettingAsString( PwmSetting.RECOVERY_OAUTH_ID_DN_ATTRIBUTE_NAME ),
                 OAuthUseCase.ForgottenPassword,
                 config.readSettingAsCertificate( PwmSetting.RECOVERY_OAUTH_ID_CERTIFICATE ),
                 config.readSettingAsString( PwmSetting.RECOVERY_OAUTH_ID_USERNAME_SEND_VALUE ) );
    }
}
