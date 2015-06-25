package password.pwm.bean;

import java.io.Serializable;
import java.util.Map;

public class RemoteVerificationRequestBean implements Serializable {
    private String responseSessionID;
    private PublicUserInfoBean userInfo;
    private Map<String, String> userResponses;

    public String getResponseSessionID() {
        return responseSessionID;
    }

    public void setResponseSessionID(String responseSessionID) {
        this.responseSessionID = responseSessionID;
    }

    public PublicUserInfoBean getUserInfo() {
        return userInfo;
    }

    public void setUserInfo(PublicUserInfoBean userInfo) {
        this.userInfo = userInfo;
    }

    public Map<String, String> getUserResponses() {
        return userResponses;
    }

    public void setUserResponses(Map<String, String> userResponses) {
        this.userResponses = userResponses;
    }
}
