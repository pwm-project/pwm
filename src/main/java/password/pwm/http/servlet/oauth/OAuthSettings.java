package password.pwm.http.servlet.oauth;

import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.util.PasswordData;

import java.io.Serializable;

public class OAuthSettings implements Serializable {
    private String loginURL;
    private String codeResolveUrl;
    private String attributesUrl;
    private String clientID;
    private PasswordData secret;
    private String dnAttributeName;

    public String getLoginURL()
    {
        return loginURL;
    }

    public String getCodeResolveUrl()
    {
        return codeResolveUrl;
    }

    public String getAttributesUrl()
    {
        return attributesUrl;
    }

    public String getClientID()
    {
        return clientID;
    }

    public PasswordData getSecret()
    {
        return secret;
    }

    public String getDnAttributeName()
    {
        return dnAttributeName;
    }

    public boolean oAuthIsConfigured() {
        return (loginURL != null && !loginURL.isEmpty())
                && (codeResolveUrl != null && !codeResolveUrl.isEmpty())
                && (attributesUrl != null && !attributesUrl.isEmpty())
                && (clientID != null && !clientID.isEmpty())
                && (secret != null)
                && (dnAttributeName != null && !dnAttributeName.isEmpty());
    }

    public static OAuthSettings fromConfiguration(final Configuration config) {
        final OAuthSettings settings = new OAuthSettings();
        settings.loginURL = config.readSettingAsString(PwmSetting.OAUTH_ID_LOGIN_URL);
        settings.codeResolveUrl = config.readSettingAsString(PwmSetting.OAUTH_ID_CODERESOLVE_URL);
        settings.attributesUrl = config.readSettingAsString(PwmSetting.OAUTH_ID_ATTRIBUTES_URL);
        settings.clientID = config.readSettingAsString(PwmSetting.OAUTH_ID_CLIENTNAME);
        settings.secret = config.readSettingAsPassword(PwmSetting.OAUTH_ID_SECRET);
        settings.dnAttributeName = config.readSettingAsString(PwmSetting.OAUTH_ID_DN_ATTRIBUTE_NAME);
        return settings;
    }
}
