package password.pwm.resttest;

public class SmsGetResponseBody {
    private String successful;
    private String message;

    public SmsGetResponseBody(String successful, String message) {
        this.successful = successful;
        this.message = message;
    }

    /** Getters and Setters */
    public String getSuccessful() {
        return successful;
    }

    public void setSuccessful(String successful) {
        this.successful = successful;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
