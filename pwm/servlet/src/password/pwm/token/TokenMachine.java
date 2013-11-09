package password.pwm.token;

import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;

import java.util.Iterator;

interface TokenMachine {
    String generateToken(final TokenPayload tokenPayload) throws PwmUnrecoverableException, PwmOperationalException;

    TokenPayload retrieveToken(final String tokenKey) throws PwmOperationalException, PwmUnrecoverableException;

    void storeToken(final String tokenKey, final TokenPayload tokenPayload) throws PwmOperationalException, PwmUnrecoverableException;

    void removeToken(final String tokenKey) throws PwmOperationalException, PwmUnrecoverableException;

    int size() throws PwmOperationalException, PwmUnrecoverableException;

    Iterator keyIterator() throws PwmOperationalException, PwmUnrecoverableException;

    void cleanup() throws PwmUnrecoverableException, PwmOperationalException;

    boolean supportsName();
}
