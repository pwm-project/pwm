/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2020 The PWM Project
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

import lombok.Builder;
import lombok.Value;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.profile.ForgottenPasswordProfile;
import password.pwm.util.PasswordData;

import java.io.Serializable;
import java.security.cert.X509Certificate;
import java.util.List;

@Value
@Builder
public class OAuthSettings implements Serializable
{
    private String loginURL;
    private String codeResolveUrl;
    private String attributesUrl;
    private String scope;
    private String clientID;
    private PasswordData secret;
    private String dnAttributeName;
    private OAuthUseCase use;
    private List<X509Certificate> certificates;
    private String usernameSendValue;

    public boolean oAuthIsConfigured()
    {
        return ( loginURL != null && !loginURL.isEmpty() )
                && ( codeResolveUrl != null && !codeResolveUrl.isEmpty() )
                && ( attributesUrl != null && !attributesUrl.isEmpty() )
                && ( clientID != null && !clientID.isEmpty() )
                && ( secret != null )
                && ( dnAttributeName != null && !dnAttributeName.isEmpty() );
    }

    public static OAuthSettings forSSOAuthentication( final Configuration config )
    {
        return OAuthSettings.builder()
                .loginURL( config.readSettingAsString( PwmSetting.OAUTH_ID_LOGIN_URL ) )
                .codeResolveUrl( config.readSettingAsString( PwmSetting.OAUTH_ID_CODERESOLVE_URL ) )
                .attributesUrl( config.readSettingAsString( PwmSetting.OAUTH_ID_ATTRIBUTES_URL ) )
                .clientID( config.readSettingAsString( PwmSetting.OAUTH_ID_CLIENTNAME ) )
                .secret( config.readSettingAsPassword( PwmSetting.OAUTH_ID_SECRET ) )
                .dnAttributeName( config.readSettingAsString( PwmSetting.OAUTH_ID_DN_ATTRIBUTE_NAME ) )
                .certificates( config.readSettingAsCertificate( PwmSetting.OAUTH_ID_CERTIFICATE ) )
                .scope( config.readSettingAsString( PwmSetting.OAUTH_ID_SCOPE ) )
                .use( OAuthUseCase.Authentication )
                .build();
    }

    public static OAuthSettings forForgottenPassword( final ForgottenPasswordProfile config )
    {
        return OAuthSettings.builder()
                .loginURL( config.readSettingAsString( PwmSetting.RECOVERY_OAUTH_ID_LOGIN_URL ) )
                .codeResolveUrl( config.readSettingAsString( PwmSetting.RECOVERY_OAUTH_ID_CODERESOLVE_URL ) )
                .attributesUrl( config.readSettingAsString( PwmSetting.RECOVERY_OAUTH_ID_ATTRIBUTES_URL ) )
                .clientID( config.readSettingAsString( PwmSetting.RECOVERY_OAUTH_ID_CLIENTNAME ) )
                .secret( config.readSettingAsPassword( PwmSetting.RECOVERY_OAUTH_ID_SECRET ) )
                .dnAttributeName( config.readSettingAsString( PwmSetting.RECOVERY_OAUTH_ID_DN_ATTRIBUTE_NAME ) )
                .certificates( config.readSettingAsCertificate( PwmSetting.RECOVERY_OAUTH_ID_CERTIFICATE ) )
                .use( OAuthUseCase.ForgottenPassword )
                .usernameSendValue( config.readSettingAsString( PwmSetting.RECOVERY_OAUTH_ID_USERNAME_SEND_VALUE ) )
                .build();
    }
}
