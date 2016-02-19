package password.pwm.http.servlet.oauth;

public class OAuthRequestState {
    private OAuthState oAuthState;
    private boolean sessionMatch;

    public OAuthRequestState(OAuthState oAuthState, boolean sessionMatch) {
        this.oAuthState = oAuthState;
        this.sessionMatch = sessionMatch;
    }

    public OAuthState getoAuthState() {
        return oAuthState;
    }

    public boolean isSessionMatch() {
        return sessionMatch;
    }
}
