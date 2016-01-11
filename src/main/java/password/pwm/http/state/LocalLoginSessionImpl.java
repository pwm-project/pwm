package password.pwm.http.state;

import password.pwm.PwmApplication;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;

class LocalLoginSessionImpl implements SessionLoginProvider {
    @Override
    public void init(PwmApplication pwmApplication) throws PwmException {

    }

    @Override
    public void clearLoginSession(PwmRequest pwmRequest) throws PwmUnrecoverableException {

    }

    @Override
    public void saveLoginSessionState(PwmRequest pwmRequest) {

    }

    @Override
    public void readLoginSessionState(PwmRequest pwmRequest) throws PwmUnrecoverableException {

    }
}
