package password.pwm.ldap.auth;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.PasswordData;

public interface AuthenticationRequest {
    AuthenticationResult authUsingUnknownPw()
            throws ChaiUnavailableException, PwmUnrecoverableException;

    AuthenticationResult authenticateUser(PasswordData password)
                    throws PwmUnrecoverableException, ChaiUnavailableException, PwmOperationalException;
}
