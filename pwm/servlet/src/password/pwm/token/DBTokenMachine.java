package password.pwm.token;

import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.db.DatabaseAccessorImpl;
import password.pwm.util.db.DatabaseTable;

import java.util.Iterator;

class DBTokenMachine implements TokenMachine {
    private DatabaseAccessorImpl databaseAccessor;
    private TokenService tokenService;

    DBTokenMachine(TokenService tokenService, DatabaseAccessorImpl databaseAccessor) {
        this.tokenService = tokenService;
        this.databaseAccessor = databaseAccessor;
    }

    public String generateToken(TokenPayload tokenPayload) throws PwmUnrecoverableException, PwmOperationalException {
        return tokenService.makeUniqueTokenForMachine(this);
    }

    public TokenPayload retrieveToken(String tokenKey)
            throws PwmOperationalException, PwmUnrecoverableException
    {
        final String md5sumToken = TokenService.makeTokenHash(tokenKey);
        final String storedRawValue = databaseAccessor.get(DatabaseTable.TOKENS,md5sumToken);

        if (storedRawValue != null && storedRawValue.length() > 0 ) {
            return tokenService.fromEncryptedString(storedRawValue);
        }

        return null;
    }

    public void storeToken(String tokenKey, TokenPayload tokenPayload) throws PwmOperationalException, PwmUnrecoverableException {
        final String rawValue = tokenService.toEncryptedString(tokenPayload);
        final String md5sumToken = TokenService.makeTokenHash(tokenKey);
        databaseAccessor.put(DatabaseTable.TOKENS, md5sumToken, rawValue);
    }

    public void removeToken(String tokenKey) throws PwmOperationalException, PwmUnrecoverableException {
        final String md5sumToken = TokenService.makeTokenHash(tokenKey);
        databaseAccessor.remove(DatabaseTable.TOKENS,tokenKey);
        databaseAccessor.remove(DatabaseTable.TOKENS,md5sumToken);
    }

    public int size() throws PwmOperationalException, PwmUnrecoverableException {
        return databaseAccessor.size(DatabaseTable.TOKENS);
    }

    public Iterator keyIterator() throws PwmOperationalException, PwmUnrecoverableException {
        return databaseAccessor.iterator(DatabaseTable.TOKENS);
    }

    public void cleanup() throws PwmUnrecoverableException, PwmOperationalException {
        tokenService.purgeOutdatedTokens();
    }

    public boolean supportsName() {
        return true;
    }
}
