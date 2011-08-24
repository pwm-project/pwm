package password.pwm.util.db;

import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;

public class DatabaseException extends PwmOperationalException {
    public DatabaseException(final ErrorInformation error) {
        super(error);
    }

    public DatabaseException(final PwmError error) {
        super(error);
    }
}
