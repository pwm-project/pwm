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

import password.pwm.PwmApplication;
import password.pwm.bean.SessionLabel;
import password.pwm.error.PwmException;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.svc.PwmService;
import password.pwm.util.DataStore;
import password.pwm.util.java.ClosableIterator;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

public class DataStoreTokenMachine implements TokenMachine
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( DataStoreTokenMachine.class );
    private final TokenService tokenService;

    private final DataStore dataStore;

    private final PwmApplication pwmApplication;

    DataStoreTokenMachine(
            final PwmApplication pwmApplication,
            final TokenService tokenService,
            final DataStore dataStore
    )
    {
        this.pwmApplication = pwmApplication;
        this.tokenService = tokenService;
        this.dataStore = dataStore;
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

    public void cleanup( ) throws PwmUnrecoverableException, PwmOperationalException
    {
        if ( size() < 1 )
        {
            return;
        }

        purgeOutdatedTokens();
    }

    private void purgeOutdatedTokens( ) throws
            PwmUnrecoverableException, PwmOperationalException
    {
        final Instant startTime = Instant.now();
        {
            final long finalSize = size();
            LOGGER.trace( () -> "beginning purge cycle; database size = " + finalSize );
        }
        try ( ClosableIterator<Map.Entry<String, String>> keyIterator = dataStore.iterator() )
        {
            while ( tokenService.status() == PwmService.STATUS.OPEN && keyIterator.hasNext() )
            {
                final String storedHash = keyIterator.next().getKey();
                final TokenKey loopKey = keyFromStoredHash( storedHash );

                // retrieving token tests validity and causes purging
                retrieveToken( null, loopKey );
            }
        }
        catch ( final Exception e )
        {
            LOGGER.error( () -> "unexpected error while cleaning expired stored tokens: " + e.getMessage() );
        }
        {
            final long finalSize = size();
            LOGGER.trace( () -> "completed record purge cycle in " + TimeDuration.compactFromCurrent( startTime )
                    + "; database size = " + finalSize );
        }
    }

    private boolean testIfTokenNeedsPurging( final TokenPayload theToken )
    {
        if ( theToken == null )
        {
            return false;
        }
        final Instant issueDate = theToken.getIssueTime();
        if ( issueDate == null )
        {
            LOGGER.error( () -> "retrieved token has no issueDate, marking as purgable: " + JsonUtil.serialize( theToken ) );
            return true;
        }
        if ( theToken.getExpiration() == null )
        {
            LOGGER.error( () -> "retrieved token has no expiration, marking as purgable: " + JsonUtil.serialize( theToken ) );
            return true;
        }
        return theToken.getExpiration().isBefore( Instant.now() );
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
        final String storedHash = tokenKey.getStoredHash();
        final String storedRawValue = dataStore.get( storedHash );

        if ( storedRawValue != null && storedRawValue.length() > 0 )
        {
            final TokenPayload tokenPayload;
            try
            {
                tokenPayload = tokenService.fromEncryptedString( storedRawValue );
            }
            catch ( final PwmException e )
            {
                LOGGER.trace( sessionLabel, () -> "error while trying to decrypted stored token payload for key '" + storedHash
                        + "', will purge record, error: " + e.getMessage() );
                dataStore.remove( storedHash );
                return Optional.empty();
            }

            if ( testIfTokenNeedsPurging( tokenPayload ) )
            {
                LOGGER.trace( sessionLabel, () -> "stored token key '" + storedHash + "', has an outdated issue/expire date and will be purged" );
                dataStore.remove( storedHash );
            }
            else
            {
                return Optional.of( tokenPayload );
            }
        }

        return Optional.empty();
    }

    public void storeToken( final TokenKey tokenKey, final TokenPayload tokenPayload ) throws PwmOperationalException, PwmUnrecoverableException
    {
        final String rawValue = tokenService.toEncryptedString( tokenPayload );
        final String storedHash = tokenKey.getStoredHash();
        dataStore.put( storedHash, rawValue );
    }

    public void removeToken( final TokenKey tokenKey )
            throws PwmOperationalException, PwmUnrecoverableException
    {
        final String storedHash = tokenKey.getStoredHash();
        dataStore.remove( storedHash );
    }

    public long size( ) throws PwmOperationalException, PwmUnrecoverableException
    {
        return dataStore.size();
    }

    public boolean supportsName( )
    {
        return true;
    }

}
