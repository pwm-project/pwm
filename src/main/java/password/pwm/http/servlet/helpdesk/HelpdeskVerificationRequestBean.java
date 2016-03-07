package password.pwm.http.servlet.helpdesk;

import java.io.Serializable;
import java.util.Date;

public class HelpdeskVerificationRequestBean implements Serializable {

    private String destination;
    private String userKey;
    private String code;
    private String tokenData;

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public String getUserKey() {
        return userKey;
    }

    public void setUserKey(String userKey) {
        this.userKey = userKey;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getTokenData() {
        return tokenData;
    }

    public void setTokenData(String tokenData) {
        this.tokenData = tokenData;
    }

    static class TokenData implements Serializable {
        private String token;
        private Date issueDate;

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public Date getIssueDate() {
            return issueDate;
        }

        public void setIssueDate(Date issueDate) {
            this.issueDate = issueDate;
        }
    }
}
