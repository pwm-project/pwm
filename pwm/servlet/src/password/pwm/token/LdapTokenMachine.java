package password.pwm.token;

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiException;
import com.novell.ldapchai.util.SearchHelper;
import password.pwm.PwmApplication;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.ldap.UserDataReader;
import password.pwm.ldap.UserSearchEngine;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

class LdapTokenMachine implements TokenMachine {
    private PwmApplication pwmApplication;
    private String tokenAttribute;
    private final String KEY_VALUE_DELIMITER = " ";
    private TokenService tokenService;

    LdapTokenMachine(TokenService tokenService, PwmApplication pwmApplication)
            throws PwmOperationalException
    {
        this.tokenService = tokenService;
        this.pwmApplication = pwmApplication;
        this.tokenAttribute = pwmApplication.getConfig().readSettingAsString(PwmSetting.TOKEN_LDAP_ATTRIBUTE);
    }

    public String generateToken(TokenPayload tokenPayload) throws PwmUnrecoverableException, PwmOperationalException {
        return tokenService.makeUniqueTokenForMachine(this);
    }

    public TokenPayload retrieveToken(String tokenKey)
            throws PwmOperationalException, PwmUnrecoverableException
    {
        final String searchFilter;
        {
            final String md5sumToken = TokenService.makeTokenHash(tokenKey);
            final SearchHelper tempSearchHelper = new SearchHelper();
            final Map<String,String> filterAttributes = new HashMap<String,String>();
            for (final String loopStr : pwmApplication.getConfig().readSettingAsStringArray(PwmSetting.DEFAULT_OBJECT_CLASSES)) {
                filterAttributes.put("objectClass", loopStr);
            }
            filterAttributes.put(tokenAttribute,md5sumToken + "*");
            tempSearchHelper.setFilterAnd(filterAttributes);
            searchFilter = tempSearchHelper.getFilter();
        }

        try {
            final UserSearchEngine userSearchEngine = new UserSearchEngine(pwmApplication);
            final UserSearchEngine.SearchConfiguration searchConfiguration = new UserSearchEngine.SearchConfiguration();
            searchConfiguration.setFilter(searchFilter);
            final UserIdentity user = userSearchEngine.performSingleUserSearch(null, searchConfiguration);
            if (user == null) {
                return null;
            }
            final UserDataReader userDataReader = UserDataReader.appProxiedReader(pwmApplication, user);
            final String tokenAttributeValue = userDataReader.readStringAttribute(tokenAttribute);
            if (tokenAttribute != null && tokenAttributeValue.length() > 0) {
                final String splitString[] = tokenAttributeValue.split(KEY_VALUE_DELIMITER);
                if (splitString.length != 2) {
                    final String errorMsg = "error parsing ldap stored token, not enough delimited values";
                    final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_TOKEN_INCORRECT,errorMsg);
                    throw new PwmOperationalException(errorInformation);
                }
                return tokenService.fromEncryptedString(splitString[1]);
            }
        } catch (PwmOperationalException e) {
            if (e.getError() == PwmError.ERROR_CANT_MATCH_USER) {
                return null;
            }
            throw e;
        } catch (ChaiException e) {
            final String errorMsg = "unexpected ldap error searching for token: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_TOKEN_INCORRECT,errorMsg);
            throw new PwmOperationalException(errorInformation);
        }
        return null;
    }

    public void storeToken(String tokenKey, TokenPayload tokenPayload)
            throws PwmOperationalException, PwmUnrecoverableException
    {
        try {
            final String md5sumToken = TokenService.makeTokenHash(tokenKey);
            final String encodedTokenPayload = tokenService.toEncryptedString(tokenPayload);

            final UserIdentity userIdentity = tokenPayload.getUserIdentity();
            final ChaiUser chaiUser = pwmApplication.getProxiedChaiUser(userIdentity);
            chaiUser.writeStringAttribute(tokenAttribute, md5sumToken + KEY_VALUE_DELIMITER + encodedTokenPayload);
        } catch (ChaiException e) {
            final String errorMsg = "unexpected ldap error saving token: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN,errorMsg);
            throw new PwmOperationalException(errorInformation);
        }
    }

    public void removeToken(String tokenKey)
            throws PwmOperationalException, PwmUnrecoverableException
    {
        final TokenPayload payload = retrieveToken(tokenKey);
        if (payload != null) {
            final UserIdentity userIdentity = payload.getUserIdentity();
            try {
                final ChaiUser chaiUser = pwmApplication.getProxiedChaiUser(userIdentity);
                chaiUser.deleteAttribute(tokenAttribute, null);
            } catch (ChaiException e) {
                final String errorMsg = "unexpected ldap error removing token: " + e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN,errorMsg);
                throw new PwmOperationalException(errorInformation);
            }
        }
    }

    public int size() throws PwmOperationalException {
        return -1;
    }

    public Iterator keyIterator() throws PwmOperationalException {
        return Collections.<String>emptyList().iterator();
    }

    public void cleanup() throws PwmUnrecoverableException, PwmOperationalException {
    }

    @Override
    public boolean supportsName() {
        return false;
    }
}
