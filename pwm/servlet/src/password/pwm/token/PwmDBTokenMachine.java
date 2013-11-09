package password.pwm.token;

import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.localdb.LocalDB;

class PwmDBTokenMachine implements TokenMachine {
    private LocalDB localDB;
    private TokenService tokenService;

    PwmDBTokenMachine(TokenService tokenService, LocalDB localDB) {
        this.tokenService = tokenService;
        this.localDB = localDB;
    }

    public String generateToken(TokenPayload tokenPayload) throws PwmUnrecoverableException, PwmOperationalException {
        return tokenService.makeUniqueTokenForMachine(this);
    }

    public TokenPayload retrieveToken(String tokenKey)
            throws PwmOperationalException, PwmUnrecoverableException
    {
        final String md5sumToken = TokenService.makeTokenHash(tokenKey);
        final String storedRawValue = localDB.get(LocalDB.DB.TOKENS, md5sumToken);

        if (storedRawValue != null && storedRawValue.length() > 0 ) {
            return tokenService.fromEncryptedString(storedRawValue);
        }

        return null;
    }

    public void storeToken(String tokenKey, TokenPayload tokenPayload) throws PwmOperationalException, PwmUnrecoverableException {
        final String rawValue = tokenService.toEncryptedString(tokenPayload);
        final String md5sumToken = TokenService.makeTokenHash(tokenKey);
        localDB.put(LocalDB.DB.TOKENS, md5sumToken, rawValue);
    }

    public void removeToken(String tokenKey)
            throws PwmOperationalException, PwmUnrecoverableException
    {
        final String md5sumToken = TokenService.makeTokenHash(tokenKey);
        localDB.remove(LocalDB.DB.TOKENS, tokenKey);
        localDB.remove(LocalDB.DB.TOKENS, md5sumToken);
    }

    public int size() throws PwmOperationalException {
        return localDB.size(LocalDB.DB.TOKENS);
    }

    public LocalDB.LocalDBIterator<String> keyIterator() throws PwmOperationalException {
        return localDB.iterator(LocalDB.DB.TOKENS);
    }

    public void cleanup() throws PwmUnrecoverableException, PwmOperationalException {
        tokenService.purgeOutdatedTokens();
    }

    public boolean supportsName() {
        return true;
    }
}
