package password.pwm.bean;

import java.io.Serializable;
import java.util.Map;

public class NewUserBean implements Serializable {
    private Map<String,String> formValues;
    private String token;
    private boolean tokenPassed;
    private String tokenEmailAddress;

    public NewUserBean() {
    }

    public Map<String, String> getFormValues() {
        return formValues;
    }

    public void setFormValues(Map<String, String> formValues) {
        this.formValues = formValues;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public boolean isTokenPassed() {
        return tokenPassed;
    }

    public void setTokenPassed(boolean tokenPassed) {
        this.tokenPassed = tokenPassed;
    }

    public String getTokenEmailAddress() {
        return tokenEmailAddress;
    }

    public void setTokenEmailAddress(String tokenEmailAddress) {
        this.tokenEmailAddress = tokenEmailAddress;
    }
}
