package password.pwm.bean;

import java.io.Serializable;
import java.util.Map;

public class NewUserBean implements Serializable {
    private boolean tokenIssued;
    private String tokenEmailAddress;
    private Map<String,String> formData;

    public NewUserBean() {
    }

    public boolean isTokenIssued() {
        return tokenIssued;
    }

    public void setTokenIssued(final boolean tokenIssued) {
        this.tokenIssued = tokenIssued;
    }

    public String getTokenEmailAddress() {
        return tokenEmailAddress;
    }

    public void setTokenEmailAddress(String tokenEmailAddress) {
        this.tokenEmailAddress = tokenEmailAddress;
    }

    public Map<String, String> getFormData() {
        return formData;
    }

    public void setFormData(Map<String, String> formData) {
        this.formData = formData;
    }
}
