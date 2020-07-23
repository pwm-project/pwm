/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2019 The PWM Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package password.pwm.svc.token;

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiException;
import com.novell.ldapchai.util.SearchHelper;
import password.pwm.PwmApplication;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.ldap.UserInfo;
import password.pwm.ldap.UserInfoFactory;
import password.pwm.ldap.search.SearchConfiguration;
import password.pwm.ldap.search.UserSearchEngine;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

class LdapTokenMachine implements TokenMachine
{

    private static final String KEY_VALUE_DELIMITER = " ";

    private PwmApplication pwmApplication;
    private String tokenAttribute;
    private TokenService tokenService;

    LdapTokenMachine( final TokenService tokenService, final PwmApplication pwmApplication )
            throws PwmOperationalException
    {
        this.tokenService = tokenService;
        this.pwmApplication = pwmApplication;
        this.tokenAttribute = pwmApplication.getConfig().readSettingAsString( PwmSetting.TOKEN_LDAP_ATTRIBUTE );
    }

    public String generateToken(
            final SessionLabel sessionLabel,
            final TokenPayload tokenPayload
    )
            throws PwmUnrecoverableException, PwmOperationalException
    {
        return tokenService.makeUniqueTokenForMachine( sessionLabel, this );
    }

    public Optional<TokenPayload> retrieveToken( final SessionLabel sessionLabel, final TokenKey tokenKey )
            throws PwmOperationalException, PwmUnrecoverableException
    {
        final String searchFilter;
        {
            final String storedHash = tokenKey.getStoredHash();
            final SearchHelper tempSearchHelper = new SearchHelper();
            final Map<String, String> filterAttributes = new HashMap<>();
            for ( final String loopStr : pwmApplication.getConfig().readSettingAsStringArray( PwmSetting.DEFAULT_OBJECT_CLASSES ) )
            {
                filterAttributes.put( "objectClass", loopStr );
            }
            filterAttributes.put( tokenAttribute, storedHash + "*" );
            tempSearchHelper.setFilterAnd( filterAttributes );
            searchFilter = tempSearchHelper.getFilter();
        }

        try
        {
            final UserSearchEngine userSearchEngine = pwmApplication.getUserSearchEngine();
            final SearchConfiguration searchConfiguration = SearchConfiguration.builder()
                    .filter( searchFilter )
                    .build();
            final UserIdentity user = userSearchEngine.performSingleUserSearch( searchConfiguration, sessionLabel );
            if ( user == null )
            {
                return Optional.empty();
            }
            final UserInfo userInfo = UserInfoFactory.newUserInfoUsingProxy( pwmApplication, sessionLabel, user, null );
            final String tokenAttributeValue = userInfo.readStringAttribute( tokenAttribute );
            if ( tokenAttribute != null && tokenAttributeValue.length() > 0 )
            {
                final String[] splitString = tokenAttributeValue.split( KEY_VALUE_DELIMITER );
                if ( splitString.length != 2 )
                {
                    final String errorMsg = "error parsing ldap stored token, not enough delimited values";
                    final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_TOKEN_INCORRECT, errorMsg );
                    throw new PwmOperationalException( errorInformation );
                }
                return Optional.of( tokenService.fromEncryptedString( splitString[ 1 ] ) );
            }
        }
        catch ( final PwmOperationalException e )
        {
            if ( e.getError() == PwmError.ERROR_CANT_MATCH_USER )
            {
                return Optional.empty();
            }
            throw e;
        }
        catch ( final PwmUnrecoverableException e )
        {
            final String errorMsg = "unexpected ldap error searching for token: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_TOKEN_INCORRECT, errorMsg );
            throw new PwmOperationalException( errorInformation );
        }
        return Optional.empty();
    }

    public void storeToken( final TokenKey tokenKey, final TokenPayload tokenPayload )
            throws PwmOperationalException, PwmUnrecoverableException
    {
        try
        {
            final String md5sumToken = tokenKey.getStoredHash();
            final String encodedTokenPayload = tokenService.toEncryptedString( tokenPayload );

            final UserIdentity userIdentity = tokenPayload.getUserIdentity();
            final ChaiUser chaiUser = pwmApplication.getProxiedChaiUser( userIdentity );
            chaiUser.writeStringAttribute( tokenAttribute, md5sumToken + KEY_VALUE_DELIMITER + encodedTokenPayload );
        }
        catch ( final ChaiException e )
        {
            final String errorMsg = "unexpected ldap error saving token: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INTERNAL, errorMsg );
            throw new PwmOperationalException( errorInformation );
        }
    }

    public void removeToken( final TokenKey tokenKey )
            throws PwmOperationalException, PwmUnrecoverableException
    {
        final Optional<TokenPayload> payload = retrieveToken( null, tokenKey );
        if ( payload.isPresent() )
        {
            final UserIdentity userIdentity = payload.get().getUserIdentity();
            try
            {
                final ChaiUser chaiUser = pwmApplication.getProxiedChaiUser( userIdentity );
                chaiUser.deleteAttribute( tokenAttribute, null );
            }
            catch ( final ChaiException e )
            {
                final String errorMsg = "unexpected ldap error removing token: " + e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INTERNAL, errorMsg );
                throw new PwmOperationalException( errorInformation );
            }
        }
    }

    public long size( ) throws PwmOperationalException
    {
        return -1;
    }

    public void cleanup( ) throws PwmUnrecoverableException, PwmOperationalException
    {
    }

    @Override
    public boolean supportsName( )
    {
        return false;
    }

    @Override
    public TokenKey keyFromKey( final String key ) throws PwmUnrecoverableException
    {
        return StoredTokenKey.fromKeyValue( pwmApplication, key );
    }

    @Override
    public TokenKey keyFromStoredHash( final String storedHash )
    {
        return StoredTokenKey.fromStoredHash( storedHash );
    }

}
