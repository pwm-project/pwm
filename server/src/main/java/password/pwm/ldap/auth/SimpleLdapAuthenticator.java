package password.pwm.ldap.auth;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.PwmApplication;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.PasswordData;

public class SimpleLdapAuthenticator {
    public static AuthenticationResult authenticateUser(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final UserIdentity userIdentity,
            final PasswordData password
    ) throws PwmUnrecoverableException
    {
        final AuthenticationRequest authEngine = LDAPAuthenticationRequest.createLDAPAuthenticationRequest(
                pwmApplication,
                sessionLabel,
                userIdentity,
                AuthenticationType.AUTHENTICATED,
                PwmAuthenticationSource.BASIC_AUTH
        );

        final AuthenticationResult authResult;
        try {
            authResult = authEngine.authenticateUser(password);
        } catch (ChaiUnavailableException e) {
            throw PwmUnrecoverableException.fromChaiException(e);
        } catch (PwmOperationalException e) {
            throw new PwmUnrecoverableException(e.getErrorInformation());
        }

        if (authResult.getAuthenticationType() == AuthenticationType.AUTHENTICATED) {
            return authResult;
        }

        return null;
    }
}
