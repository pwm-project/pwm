package password.pwm.http.servlet.helpdesk;

import java.io.Serializable;

public class HelpdeskVerificationResponseBean implements Serializable {
    private boolean passed;
    private HelpdeskVerificationStateBean verificationState;

    public HelpdeskVerificationResponseBean(boolean passed, HelpdeskVerificationStateBean verificationState) {
        this.passed = passed;
        this.verificationState = verificationState;
    }

    public boolean isPassed() {
        return passed;
    }

    public HelpdeskVerificationStateBean getVerificationState() {
        return verificationState;
    }
}
