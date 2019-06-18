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

class WordlistBucket
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( WordlistBucket.class );

    private final PwmApplication pwmApplication;
    private final WordlistConfiguration wordlistConfiguration;
    private final LocalDB.DB db;
    private final WordlistType type;


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
            LOGGER.debug( () -> "wordlist search time for " + testWords.size() + " wordlist permutations was greater then 100ms: " + timeDuration.asCompactString() );
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
            final long seedCount = size();
            if ( seedCount > 1000 )
            {
                final long randomKey = pwmApplication.getSecureService().pwmRandom().nextLong( seedCount );
                return pwmApplication.getLocalDB().get( db, seedlistLongToKey( randomKey ) );
            }
        }
        catch ( Exception e )
        {
            throw PwmUnrecoverableException.newException( PwmError.ERROR_INTERNAL, "error while generating random word: " + e.getMessage() );
        }

        throw new PwmUnrecoverableException( PwmError.ERROR_INTERNAL, "seedlist word not available" );
    }

    void addWords( final Collection<String> words, final AbstractWordlist abstractWordlist )
            throws LocalDBException
    {
        final WordlistStatus initialStatus = abstractWordlist.readWordlistStatus();
        final MutableLongIncrementer valueIncrementer = new MutableLongIncrementer( initialStatus.getValueCount() );
        pwmApplication.getLocalDB().putAll( db, getWriteTxnForValue( words, valueIncrementer ) );
        if ( initialStatus.getValueCount() != valueIncrementer.get() )
        {
            final WordlistStatus incrementedStatus = initialStatus.toBuilder().valueCount( valueIncrementer.get() ).build();
            abstractWordlist.writeWordlistStatus( incrementedStatus );
        }
    }

    long size() throws LocalDBException
    {
        return pwmApplication.getLocalDB().size( db );
    }


    void clear() throws LocalDBException
    {
        pwmApplication.getLocalDB().truncate( db );
    }

    private Map<String, String> getWriteTxnForValue( final Collection<String> words, final MutableLongIncrementer valueIncrementer ) throws LocalDBException
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
                        final long nextLong = valueIncrementer.getAndIncrement();
                        final String nextKey = seedlistLongToKey( nextLong );
                        returnSet.put( nextKey, normalizedWord );
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
                        valueIncrementer.getAndIncrement();
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

    private static long seedlistKeyToLong( final String key )
    {
        return Long.parseLong( key, 36 );
    }

    private static String seedlistLongToKey( final long longValue )
    {
        return Long.toString( longValue, 36 );
    }

    public static class MutableLongIncrementer
    {
        private long value;

        MutableLongIncrementer( final long value )
        {
            this.value = value;
        }

        public long getAndIncrement()
        {
            value++;
            return value;
        }

        public long get()
        {
            return value;
        }
    }
}
