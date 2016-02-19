package password.pwm.http.servlet.oauth;

import java.io.Serializable;
import java.util.Date;

class OAuthState implements Serializable {

    private static int oauthStateIdCounter = 0;

    private final int stateID = oauthStateIdCounter++;
    private final Date issueTime = new Date();
    private String sessionID;
    private String nextUrl;

    public OAuthState(String sessionID, String nextUrl) {
        this.sessionID = sessionID;
        this.nextUrl = nextUrl;
    }

    public static int getOauthStateIdCounter() {
        return oauthStateIdCounter;
    }

    public int getStateID() {
        return stateID;
    }

    public Date getIssueTime() {
        return issueTime;
    }

    public String getSessionID() {
        return sessionID;
    }

    public String getNextUrl() {
        return nextUrl;
    }
}
