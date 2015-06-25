package password.pwm;

import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserInfoBean;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmUnrecoverableException;

import java.io.Serializable;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public interface RecoveryVerificationMethod {
    enum VerificationState {
        INPROGRESS,
        FAILED,
        COMPLETE,
    }

    interface UserPrompt {
        String getDisplayPrompt();
        String getIdentifier();
    }

    class UserPromptBean implements Serializable, UserPrompt {
        private String displayPrompt;
        private String identifier;

        public String getDisplayPrompt() {
            return displayPrompt;
        }

        public void setDisplayPrompt(String displayPrompt) {
            this.displayPrompt = displayPrompt;
        }

        public String getIdentifier() {
            return identifier;
        }

        public void setIdentifier(String identifier) {
            this.identifier = identifier;
        }
    }

    public List<UserPrompt> getCurrentPrompts() throws PwmUnrecoverableException;

    public String getCurrentDisplayInstructions();

    public ErrorInformation respondToPrompts(final Map<String, String> answers) throws PwmUnrecoverableException;

    public VerificationState getVerificationState();

    public void init(final PwmApplication pwmApplication, final UserInfoBean userInfoBean, SessionLabel sessionLabel, Locale locale)
            throws PwmUnrecoverableException
            ;


}
