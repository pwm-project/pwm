package password.pwm.token;

import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;

import java.util.Collections;
import java.util.Iterator;

class CryptoTokenMachine implements TokenMachine {

    private TokenService tokenService;

    CryptoTokenMachine(TokenService tokenService)
            throws PwmOperationalException
    {
        this.tokenService = tokenService;
    }

    public String generateToken(TokenPayload tokenPayload) throws PwmUnrecoverableException, PwmOperationalException {
        final int WRAP_LENGTH = 60;
        final StringBuilder returnString = new StringBuilder(tokenService.toEncryptedString(tokenPayload));
        for (int i = WRAP_LENGTH - 1; i < returnString.length(); i += WRAP_LENGTH) {
            returnString.insert(i,"\n");
        }
        return returnString.toString();
    }

    public TokenPayload retrieveToken(String tokenKey)
            throws PwmOperationalException, PwmUnrecoverableException
    {
        if (tokenKey == null || tokenKey.length() < 1) {
            return null;
        }
        return tokenService.fromEncryptedString(tokenKey);
    }

    public void storeToken(String tokenKey, TokenPayload tokenPayload) throws PwmOperationalException, PwmUnrecoverableException {
    }

    public void removeToken(String tokenKey) throws PwmOperationalException, PwmUnrecoverableException {
    }

    public int size() throws PwmOperationalException, PwmUnrecoverableException {
        return 0;
    }

    public Iterator keyIterator() throws PwmOperationalException, PwmUnrecoverableException {
        return Collections.<String>emptyList().iterator();
    }

    public void cleanup() {
    }

    public boolean supportsName() {
        return true;
    }

}
