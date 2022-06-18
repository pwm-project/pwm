/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2021 The PWM Project
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

import password.pwm.PwmDomain;
import password.pwm.bean.SessionLabel;
import password.pwm.error.PwmException;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.svc.PwmService;
import password.pwm.util.DataStore;
import password.pwm.util.java.ClosableIterator;
import password.pwm.util.json.JsonFactory;
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

    private final PwmDomain pwmDomain;

    DataStoreTokenMachine(
            final PwmDomain pwmDomain,
            final TokenService tokenService,
            final DataStore dataStore
    )
    {
        this.pwmDomain = pwmDomain;
        this.tokenService = tokenService;
        this.dataStore = dataStore;
    }

    @Override
    public TokenKey keyFromKey( final String key ) throws PwmUnrecoverableException
    {
        return StoredTokenKey.fromKeyValue( pwmDomain, key );
    }

    @Override
    public TokenKey keyFromStoredHash( final String storedHash )
    {
        return StoredTokenKey.fromStoredHash( storedHash );
    }

    @Override
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
            LOGGER.trace( () -> "completed record purge cycle; database size = " + finalSize, TimeDuration.fromCurrent( startTime ) );
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
            LOGGER.error( () -> "retrieved token has no issueDate, marking as purgable: " + JsonFactory.get().serialize( theToken ) );
            return true;
        }
        if ( theToken.getExpiration() == null )
        {
            LOGGER.error( () -> "retrieved token has no expiration, marking as purgable: " + JsonFactory.get().serialize( theToken ) );
            return true;
        }
        return theToken.getExpiration().isBefore( Instant.now() );
    }

    @Override
    public String generateToken(
            final SessionLabel sessionLabel,
            final TokenPayload tokenPayload
    )
            throws PwmUnrecoverableException, PwmOperationalException
    {
        return tokenService.makeUniqueTokenForMachine( sessionLabel, this );
    }

    @Override
    public Optional<TokenPayload> retrieveToken( final SessionLabel sessionLabel, final TokenKey tokenKey )
            throws PwmOperationalException, PwmUnrecoverableException
    {
        final String storedHash = tokenKey.getStoredHash();
        final Optional<String> storedRawValue = dataStore.get( storedHash );

        if ( storedRawValue.isPresent() )
        {
            final TokenPayload tokenPayload;
            try
            {
                tokenPayload = tokenService.fromEncryptedString( storedRawValue.get() );
            }
            catch ( final PwmException e )
            {
                LOGGER.trace( sessionLabel, () -> "error while trying to decrypted stored token payload for key '" + storedHash
                        + "', will purge record, error: " + e.getMessage() );
                removeToken( tokenKey );
                return Optional.empty();
            }

            if ( testIfTokenNeedsPurging( tokenPayload ) )
            {
                LOGGER.trace( sessionLabel, () -> "stored token key '" + storedHash + "', has an outdated issue/expire date and will be purged" );
                removeToken( tokenKey );
            }
            else
            {
                return Optional.of( tokenPayload );
            }
        }

        return Optional.empty();
    }

    @Override
    public void storeToken( final TokenKey tokenKey, final TokenPayload tokenPayload ) throws PwmOperationalException, PwmUnrecoverableException
    {
        final String rawValue = tokenService.toEncryptedString( tokenPayload );
        final String storedHash = tokenKey.getStoredHash();
        dataStore.put( storedHash, rawValue );
    }

    @Override
    public void removeToken( final TokenKey tokenKey )
            throws PwmOperationalException, PwmUnrecoverableException
    {
        tokenService.getStats().increment( TokenService.StatsKey.tokensRemoved );
        final String storedHash = tokenKey.getStoredHash();
        dataStore.remove( storedHash );
    }

    @Override
    public long size( ) throws PwmOperationalException, PwmUnrecoverableException
    {
        return dataStore.size();
    }

    @Override
    public boolean supportsName( )
    {
        return true;
    }

}
