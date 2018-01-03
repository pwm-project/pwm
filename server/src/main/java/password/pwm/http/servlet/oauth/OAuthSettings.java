/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
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

package password.pwm.http.servlet.oauth;

import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.profile.ForgottenPasswordProfile;
import password.pwm.util.PasswordData;

import java.io.Serializable;
import java.security.cert.X509Certificate;
import java.util.List;

public class OAuthSettings implements Serializable
{
    private String loginURL;
    private String codeResolveUrl;
    private String attributesUrl;
    private String clientID;
    private PasswordData secret;
    private String dnAttributeName;
    private OAuthUseCase use;
    private List<X509Certificate> certificates;
    private String usernameSendValue;


    private OAuthSettings( )
    {
    }

    public String getLoginURL( )
    {
        return loginURL;
    }

    public String getCodeResolveUrl( )
    {
        return codeResolveUrl;
    }

    public String getAttributesUrl( )
    {
        return attributesUrl;
    }

    public String getClientID( )
    {
        return clientID;
    }

    public PasswordData getSecret( )
    {
        return secret;
    }

    public String getDnAttributeName( )
    {
        return dnAttributeName;
    }

    public OAuthUseCase getUse( )
    {
        return use;
    }

    public List<X509Certificate> getCertificates( )
    {
        return certificates;
    }

    public String getUsernameSendValue( )
    {
        return usernameSendValue;
    }

    public boolean oAuthIsConfigured( )
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
        final OAuthSettings settings = new OAuthSettings();
        settings.loginURL = config.readSettingAsString( PwmSetting.OAUTH_ID_LOGIN_URL );
        settings.codeResolveUrl = config.readSettingAsString( PwmSetting.OAUTH_ID_CODERESOLVE_URL );
        settings.attributesUrl = config.readSettingAsString( PwmSetting.OAUTH_ID_ATTRIBUTES_URL );
        settings.clientID = config.readSettingAsString( PwmSetting.OAUTH_ID_CLIENTNAME );
        settings.secret = config.readSettingAsPassword( PwmSetting.OAUTH_ID_SECRET );
        settings.dnAttributeName = config.readSettingAsString( PwmSetting.OAUTH_ID_DN_ATTRIBUTE_NAME );
        settings.certificates = config.readSettingAsCertificate( PwmSetting.OAUTH_ID_CERTIFICATE );
        settings.use = OAuthUseCase.Authentication;
        return settings;
    }

    public static OAuthSettings forForgottenPassword( final ForgottenPasswordProfile config )
    {
        final OAuthSettings settings = new OAuthSettings();
        settings.loginURL = config.readSettingAsString( PwmSetting.RECOVERY_OAUTH_ID_LOGIN_URL );
        settings.codeResolveUrl = config.readSettingAsString( PwmSetting.RECOVERY_OAUTH_ID_CODERESOLVE_URL );
        settings.attributesUrl = config.readSettingAsString( PwmSetting.RECOVERY_OAUTH_ID_ATTRIBUTES_URL );
        settings.clientID = config.readSettingAsString( PwmSetting.RECOVERY_OAUTH_ID_CLIENTNAME );
        settings.secret = config.readSettingAsPassword( PwmSetting.RECOVERY_OAUTH_ID_SECRET );
        settings.dnAttributeName = config.readSettingAsString( PwmSetting.RECOVERY_OAUTH_ID_DN_ATTRIBUTE_NAME );
        settings.certificates = config.readSettingAsCertificate( PwmSetting.RECOVERY_OAUTH_ID_CERTIFICATE );
        settings.use = OAuthUseCase.ForgottenPassword;
        settings.usernameSendValue = config.readSettingAsString( PwmSetting.RECOVERY_OAUTH_ID_USERNAME_SEND_VALUE );
        return settings;
    }
}
