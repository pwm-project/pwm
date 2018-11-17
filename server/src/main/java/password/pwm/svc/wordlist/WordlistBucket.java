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

package password.pwm.svc.wordlist;

import password.pwm.PwmApplication;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBException;
import password.pwm.util.logging.PwmLogger;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;

class WordlistBucket
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( WordlistBucket.class );
    private static final String KEY_LAST_ISSUED_KEY = "_______lastKey_";

    private final PwmApplication pwmApplication;
    private final WordlistConfiguration wordlistConfiguration;
    private final LocalDB.DB db;
    private final WordlistType type;
    private final AtomicLong seedlistTopKey = new AtomicLong(  );


    WordlistBucket(
            final PwmApplication pwmApplication,
            final WordlistConfiguration wordlistConfiguration,
            final WordlistType type
    )
            throws LocalDBException
    {
        this.pwmApplication = pwmApplication;
        this.wordlistConfiguration = wordlistConfiguration;
        this.db = wordlistConfiguration.getDb();
        this.type = type;

        final String valueOfLastKey = pwmApplication.getLocalDB().get( db, KEY_LAST_ISSUED_KEY );

        seedlistTopKey.set(
                StringUtil.isEmpty( valueOfLastKey )
                        ? 0
                        : Long.parseLong( valueOfLastKey )
        );
    }

    boolean containsWord( final String word ) throws LocalDBException
    {
        if ( type == WordlistType.SEEDLIST )
        {
            throw new IllegalStateException( "unable to containWord check SEEDLIST wordlist" );
        }

        final String testWord = normalizeWord( word );

        if ( testWord == null || testWord.length() < 1 )
        {
            return false;
        }

        final Set<String> testWords = chunkWord( testWord, this.wordlistConfiguration.getCheckSize() );

        final Instant startTime = Instant.now();
        boolean result = false;

        searchLoop:
        for ( final String t : testWords )
        {
            // stop checking once found
            if ( pwmApplication.getLocalDB().contains( db, t ) )
            {
                result = true;
                break searchLoop;
            }
        }

        final TimeDuration timeDuration = TimeDuration.fromCurrent( startTime );
        if ( timeDuration.isLongerThan( 100 ) )
        {
            LOGGER.debug( "wordlist search time for " + testWords.size() + " wordlist permutations was greater then 100ms: " + timeDuration.asCompactString() );
        }

        return result;
    }

    String randomSeed( ) throws PwmUnrecoverableException
    {
        if ( type == WordlistType.WORDLIST )
        {
            throw new IllegalStateException( "unable to read randomSeed from WORDLIST wordlist" );
        }

        try
        {
            final int seedCount = size();
            if ( seedCount > 1000 )
            {
                final int randomKey = pwmApplication.getSecureService().pwmRandom().nextInt( seedCount );
                return pwmApplication.getLocalDB().get( db, String.valueOf( randomKey ) );
            }
        }
        catch ( Exception e )
        {
            throw PwmUnrecoverableException.newException( PwmError.ERROR_INTERNAL, "error while generating random word: " + e.getMessage() );
        }

        throw new PwmUnrecoverableException( PwmError.ERROR_INTERNAL, "seedlist word not available" );
    }

    void addWords( final Collection<String> words ) throws LocalDBException
    {
        pwmApplication.getLocalDB().putAll( db, getWriteTxnForValue( words ) );
    }

    int size() throws LocalDBException
    {
        return pwmApplication.getLocalDB().size( db );
    }


    void clear() throws LocalDBException
    {
        seedlistTopKey.set( 0 );
        pwmApplication.getLocalDB().truncate( db );
    }

    private Map<String, String> getWriteTxnForValue( final Collection<String> words ) throws LocalDBException
    {
        switch ( type )
        {
            case SEEDLIST:
            {
                final Map<String, String> returnSet = new TreeMap<>();
                for ( final String word : words )
                {
                    final String normalizedWord = normalizeWord( word );
                    if ( !StringUtil.isEmpty( normalizedWord ) )
                    {
                        returnSet.put( String.valueOf( seedlistTopKey.incrementAndGet() ), normalizedWord );
                        returnSet.put( KEY_LAST_ISSUED_KEY, String.valueOf( seedlistTopKey.get() ) );
                    }
                }
                return Collections.unmodifiableMap( returnSet );
            }

            case WORDLIST:
            {
                final Map<String, String> returnSet = new TreeMap<>();
                for ( final String word : words )
                {
                    final String normalizedWord = normalizeWord( word );
                    if ( !StringUtil.isEmpty( normalizedWord ) )
                    {
                        returnSet.put( normalizedWord, "" );
                    }
                }
                return returnSet;
            }

            default:
                JavaHelper.unhandledSwitchStatement( type );
        }

        throw new IllegalStateException( "unreachable switch statement" );
    }

    private String normalizeWord( final String input )
    {
        if ( input == null )
        {
            return null;
        }

        String word = input.trim();

        if ( word.length() < wordlistConfiguration.getMinSize() )
        {
            return null;
        }

        if ( word.length() > wordlistConfiguration.getMaxSize() )
        {
            word = word.substring( 0, wordlistConfiguration.getMaxSize() );
        }

        if ( !wordlistConfiguration.isCaseSensitive() )
        {
            word = word.toLowerCase();
        }

        return word.length() > 0 ? word : null;
    }

    private Set<String> chunkWord( final String input, final int size )
    {
        if ( StringUtil.isEmpty( input ) )
        {
            return Collections.emptySet();
        }

        if ( size == 0 )
        {
            return Collections.singleton( input );
        }

        int checkSize = size == 0 || size > input.length() ? input.length() : size;
        final TreeSet<String> testWords = new TreeSet<>();
        while ( checkSize <= input.length() )
        {
            for ( int i = 0; i + checkSize <= input.length(); i++ )
            {
                final String loopWord = input.substring( i, i + checkSize );
                testWords.add( loopWord );
            }
            checkSize++;
        }

        return testWords;
    }

}
