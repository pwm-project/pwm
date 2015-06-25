package password.pwm.bean;

import password.pwm.RecoveryVerificationMethod;

import java.io.Serializable;
import java.util.List;

public class RemoteVerificationResponseBean implements Serializable {
    private String displayInstructions;
    private RecoveryVerificationMethod.VerificationState verificationState;
    private List<RecoveryVerificationMethod.UserPromptBean> userPrompts;
    private String errorMessage;

    public String getDisplayInstructions() {
        return displayInstructions;
    }

    public void setDisplayInstructions(String displayInstructions) {
        this.displayInstructions = displayInstructions;
    }

    public RecoveryVerificationMethod.VerificationState getVerificationState() {
        return verificationState;
    }

    public void setVerificationState(RecoveryVerificationMethod.VerificationState verificationState) {
        this.verificationState = verificationState;
    }

    public List<RecoveryVerificationMethod.UserPromptBean> getUserPrompts() {
        return userPrompts;
    }

    public void setUserPrompts(List<RecoveryVerificationMethod.UserPromptBean> userPrompts) {
        this.userPrompts = userPrompts;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
