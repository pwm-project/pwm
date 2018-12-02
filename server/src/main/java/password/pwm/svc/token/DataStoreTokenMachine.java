/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
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
        try ( ClosableIterator<String> keyIterator = dataStore.iterator() )
        {
            while ( tokenService.status() == PwmService.STATUS.OPEN && keyIterator.hasNext() )
            {
                final String storedHash = keyIterator.next();
                final TokenKey loopKey = keyFromStoredHash( storedHash );

                // retrieving token tests validity and causes purging
                retrieveToken( loopKey );
            }
        }
        catch ( Exception e )
        {
            LOGGER.error( "unexpected error while cleaning expired stored tokens: " + e.getMessage() );
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
            LOGGER.error( "retrieved token has no issueDate, marking as purgable: " + JsonUtil.serialize( theToken ) );
            return true;
        }
        if ( theToken.getExpiration() == null )
        {
            LOGGER.error( "retrieved token has no expiration, marking as purgable: " + JsonUtil.serialize( theToken ) );
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

    public TokenPayload retrieveToken( final TokenKey tokenKey )
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
            catch ( PwmException e )
            {
                LOGGER.trace( () -> "error while trying to decrypted stored token payload for key '" + storedHash + "', will purge record, error: " + e.getMessage() );
                dataStore.remove( storedHash );
                return null;
            }

            if ( testIfTokenNeedsPurging( tokenPayload ) )
            {
                LOGGER.trace( () -> "stored token key '" + storedHash + "', has an outdated issue/expire date and will be purged" );
                dataStore.remove( storedHash );
            }
            else
            {
                return tokenPayload;
            }
        }

        return null;
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
