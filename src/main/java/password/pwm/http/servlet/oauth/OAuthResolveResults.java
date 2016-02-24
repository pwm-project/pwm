package password.pwm.http.servlet.oauth;

import java.io.Serializable;

class OAuthResolveResults implements Serializable {
    private String accessToken;
    private int expiresSeconds;
    private String refreshToken;


    public String getAccessToken()
    {
        return accessToken;
    }

    public void setAccessToken(String accessToken)
    {
        this.accessToken = accessToken;
    }

    public int getExpiresSeconds()
    {
        return expiresSeconds;
    }

    public void setExpiresSeconds(int expiresSeconds)
    {
        this.expiresSeconds = expiresSeconds;
    }

    public String getRefreshToken()
    {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken)
    {
        this.refreshToken = refreshToken;
    }
}
