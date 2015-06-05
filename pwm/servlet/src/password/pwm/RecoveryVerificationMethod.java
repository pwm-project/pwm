package password.pwm;

import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserInfoBean;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmUnrecoverableException;

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

    public List<UserPrompt> getCurrentPrompts() throws PwmUnrecoverableException;

    public String getCurrentDisplayInstructions();

    public ErrorInformation respondToPrompts(final Map<String, String> answers) throws PwmUnrecoverableException;

    public VerificationState getVerificationState();

    public void init(final PwmApplication pwmApplication, final UserInfoBean userInfoBean, SessionLabel sessionLabel, Locale locale)
            throws PwmUnrecoverableException
            ;


}
