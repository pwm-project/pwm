package password.pwm.http.servlet.peoplesearch;

import java.io.Serializable;

class UserReferenceBean implements Serializable {
    private String userKey;
    private String displayName;

    public String getUserKey() {
        return userKey;
    }

    public void setUserKey(String userKey) {
        this.userKey = userKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
}
