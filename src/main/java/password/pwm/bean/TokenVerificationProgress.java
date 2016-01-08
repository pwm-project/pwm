package password.pwm.bean;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

public class TokenVerificationProgress implements Serializable {
    private Set<TokenChannel> passedTokens = new HashSet<>();
    private Set<TokenChannel> issuedTokens = new HashSet<>();
    private TokenChannel phase;
    private String tokenDisplayText;

    public enum TokenChannel {
        EMAIL,
        SMS,
    }

    public Set<TokenChannel> getPassedTokens() {
        return passedTokens;
    }

    public Set<TokenChannel> getIssuedTokens() {
        return issuedTokens;
    }

    public TokenChannel getPhase() {
        return phase;
    }

    public void setPhase(TokenChannel phase) {
        this.phase = phase;
    }

    public String getTokenDisplayText() {
        return tokenDisplayText;
    }

    public void setTokenDisplayText(String tokenDisplayText) {
        this.tokenDisplayText = tokenDisplayText;
    }
}
