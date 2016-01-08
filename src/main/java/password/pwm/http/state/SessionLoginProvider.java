package password.pwm.http.state;

import password.pwm.PwmApplication;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;

interface SessionLoginProvider {
    void init(PwmApplication pwmApplication) throws PwmException;

    void clearLoginSession(PwmRequest pwmRequest) throws PwmUnrecoverableException;

    void saveLoginSessionState(PwmRequest pwmRequest);

    void readLoginSessionState(PwmRequest pwmRequest) throws PwmUnrecoverableException;
}
