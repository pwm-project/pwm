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

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmApplicationMode;
import password.pwm.PwmConstants;
import password.pwm.bean.SessionLabel;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.error.PwmException;
import password.pwm.health.HealthRecord;
import password.pwm.svc.PwmService;
import password.pwm.util.PwmScheduler;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBException;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmRandom;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;


public class SharedHistoryManager implements PwmService
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( SharedHistoryManager.class );

    private static final String KEY_OLDEST_ENTRY = "oldest_entry";
    private static final String KEY_VERSION = "version";
    private static final String KEY_SALT = "salt";

    // 1 hour
    private static final int MIN_CLEANER_FREQUENCY = 1000 * 60 * 60;

    // 1 day
    private static final int MAX_CLEANER_FREQUENCY = 1000 * 60 * 60 * 24;

    private static final LocalDB.DB META_DB = LocalDB.DB.SHAREDHISTORY_META;
    private static final LocalDB.DB WORDS_DB = LocalDB.DB.SHAREDHISTORY_WORDS;

    private volatile PwmService.STATUS status = STATUS.NEW;

    private ExecutorService executorService;

    private LocalDB localDB;
    private String salt;
    private long oldestEntry;

    private final Settings settings = new Settings();

    public SharedHistoryManager( ) throws LocalDBException
    {
    }

    public void close( )
    {
        status = STATUS.CLOSED;
        if ( executorService != null )
        {
            executorService.shutdown();
        }
        localDB = null;
    }

    public boolean containsWord( final String word )
    {
        if ( status != STATUS.OPEN )
        {
            return false;
        }

        final String testWord = normalizeWord( word );

        if ( testWord == null )
        {
            return false;
        }

        //final long startTime = System.currentTimeMillis();
        boolean result = false;

        try
        {
            final String hashedWord = hashWord( testWord );
            final boolean inDB = localDB.contains( WORDS_DB, hashedWord );
            if ( inDB )
            {
                final long timeStamp = Long.parseLong( localDB.get( WORDS_DB, hashedWord ) );
                final long entryAge = System.currentTimeMillis() - timeStamp;
                if ( entryAge < settings.maxAgeMs )
                {
                    result = true;
                }
            }

        }
        catch ( final Exception e )
        {
            LOGGER.warn( () -> "error checking global history list: " + e.getMessage() );
        }

        //LOGGER.trace(pwmSession, "successfully checked word, result=" + result + ", duration=" + new TimeDuration(System.currentTimeMillis(), startTime).asCompactString());
        return result;
    }

    public PwmService.STATUS status( )
    {
        return status;
    }

    public Instant getOldestEntryTime( )
    {
        if ( size() > 0 )
        {
            return Instant.ofEpochMilli( oldestEntry );
        }
        return null;
    }

    public long size( )
    {
        if ( localDB != null )
        {
            try
            {
                return localDB.size( WORDS_DB );
            }
            catch ( final Exception e )
            {
                LOGGER.error( () -> "error checking wordlist size: " + e.getMessage() );
                return 0;
            }
        }
        else
        {
            return 0;
        }
    }

    private boolean checkDbVersion( )
            throws Exception
    {
        LOGGER.trace( () -> "checking version number stored in LocalDB" );

        final Object versionInDB = localDB.get( META_DB, KEY_VERSION );
        final String currentVersion = "version=" + settings.version;
        final boolean result = currentVersion.equals( versionInDB );

        if ( !result )
        {
            LOGGER.info( () -> "existing db version does not match current db version db=(" + versionInDB + ")  current=(" + currentVersion + "), clearing db" );
            localDB.truncate( WORDS_DB );
            localDB.put( META_DB, KEY_VERSION, currentVersion );
            localDB.remove( META_DB, KEY_OLDEST_ENTRY );
        }
        else
        {
            LOGGER.trace( () -> "existing db version matches current db version db=(" + versionInDB + ")  current=(" + currentVersion + ")" );
        }

        return result;
    }

    private void init( final PwmApplication pwmApplication, final long maxAgeMs )
    {
        status = STATUS.OPENING;
        final Instant startTime = Instant.now();

        try
        {
            checkDbVersion();
        }
        catch ( final Exception e )
        {
            LOGGER.error( () -> "error checking db version", e );
            status = STATUS.CLOSED;
            return;
        }


        try
        {
            final String oldestEntryStr = localDB.get( META_DB, KEY_OLDEST_ENTRY );
            if ( oldestEntryStr == null || oldestEntryStr.length() < 1 )
            {
                oldestEntry = 0;
                LOGGER.trace( () -> "no oldestEntry timestamp stored, will rescan" );
            }
            else
            {
                oldestEntry = Long.parseLong( oldestEntryStr );
                LOGGER.trace( () -> "oldest timestamp loaded from localDB, age is " + TimeDuration.fromCurrent( oldestEntry ).asCompactString() );
            }
        }
        catch ( final LocalDBException e )
        {
            LOGGER.error( () -> "unexpected error loading oldest-entry meta record, will remain closed: " + e.getMessage(), e );
            status = STATUS.CLOSED;
            return;
        }

        try
        {
            final long size = localDB.size( WORDS_DB );
            LOGGER.info( () -> "open with " + size + " words ("
                    + TimeDuration.compactFromCurrent( startTime ) + ")"
                    + ", maxAgeMs=" + TimeDuration.of( maxAgeMs, TimeDuration.Unit.MILLISECONDS ).asCompactString()
                    + ", oldestEntry=" + TimeDuration.fromCurrent( oldestEntry ).asCompactString() );
        }
        catch ( final LocalDBException e )
        {
            LOGGER.error( () -> "unexpected error examining size of DB, will remain closed: " + e.getMessage(), e );
            status = STATUS.CLOSED;
            return;
        }

        status = STATUS.OPEN;
        //populateFromWordlist();  //only used for debugging!!!

        if ( pwmApplication.getApplicationMode() == PwmApplicationMode.RUNNING || pwmApplication.getApplicationMode() == PwmApplicationMode.CONFIGURATION )
        {
            long frequencyMs = maxAgeMs > MAX_CLEANER_FREQUENCY ? MAX_CLEANER_FREQUENCY : maxAgeMs;
            frequencyMs = frequencyMs < MIN_CLEANER_FREQUENCY ? MIN_CLEANER_FREQUENCY : frequencyMs;
            final TimeDuration frequency = TimeDuration.of( frequencyMs, TimeDuration.Unit.MILLISECONDS );

            LOGGER.debug( () -> "scheduling cleaner task to run once every " + frequency.asCompactString() );
            executorService = PwmScheduler.makeBackgroundExecutor( pwmApplication, this.getClass() );
            pwmApplication.getPwmScheduler().scheduleFixedRateJob( new CleanerTask(), executorService, null, frequency );
        }
    }

    private String normalizeWord( final String input )
    {
        if ( input == null )
        {
            return null;
        }

        String word = input.trim();

        if ( settings.caseInsensitive )
        {
            word = word.toLowerCase();
        }

        return word.length() > 0 ? word : null;
    }

    public synchronized void addWord(
            final SessionLabel sessionLabel,
            final String word
    )
    {
        if ( status != STATUS.OPEN )
        {
            return;
        }

        final String addWord = normalizeWord( word );

        if ( addWord == null )
        {
            return;
        }

        final Instant startTime = Instant.now();

        try
        {
            final String hashedWord = hashWord( addWord );

            final boolean preExisting = localDB.contains( WORDS_DB, hashedWord );
            localDB.put( WORDS_DB, hashedWord, Long.toString( System.currentTimeMillis() ) );

            LOGGER.trace( () -> ( preExisting ? "updated" : "added" ) + " word"
                    + " (" + TimeDuration.compactFromCurrent( startTime ) + ")"
                    + " (" + this.size() + " total words)" );
        }
        catch ( final Exception e )
        {
            LOGGER.warn( sessionLabel, () -> "error adding word to global history list: " + e.getMessage() );
        }
    }

    private String hashWord( final String word ) throws NoSuchAlgorithmException
    {
        final MessageDigest md = MessageDigest.getInstance( settings.hashName );
        final String wordWithSalt = salt + word;
        final int hashLoopCount = settings.hashIterations;
        byte[] hashedAnswer = md.digest( ( wordWithSalt ).getBytes( PwmConstants.DEFAULT_CHARSET ) );

        for ( int i = 0; i < hashLoopCount; i++ )
        {
            hashedAnswer = md.digest( hashedAnswer );
        }

        return JavaHelper.binaryArrayToHex( hashedAnswer );
    }

    private class CleanerTask extends TimerTask
    {
        private CleanerTask( )
        {
        }

        public void run( )
        {
            try
            {
                reduceWordDB();
            }
            catch ( final LocalDBException e )
            {
                LOGGER.error( () -> "error during old record purge: " + e.getMessage() );
            }
        }


        private void reduceWordDB( )
                throws LocalDBException
        {

            if ( localDB == null || localDB.status() != LocalDB.Status.OPEN )
            {
                return;
            }

            final long oldestEntryAge = System.currentTimeMillis() - oldestEntry;
            if ( oldestEntryAge < settings.maxAgeMs )
            {
                LOGGER.debug( () -> "skipping wordDB reduce operation, eldestEntry="
                        + TimeDuration.asCompactString( oldestEntryAge )
                        + ", maxAge="
                        + TimeDuration.asCompactString( settings.maxAgeMs ) );
                return;
            }

            final Instant startTime = Instant.now();
            final long initialSize = size();
            int removeCount = 0;
            long localOldestEntry = System.currentTimeMillis();

            LOGGER.debug( () -> "beginning wordDB reduce operation, examining " + initialSize
                    + " words for entries older than " + TimeDuration.asCompactString( settings.maxAgeMs ) );

            LocalDB.LocalDBIterator<Map.Entry<String, String>> keyIterator = null;
            try
            {
                keyIterator = localDB.iterator( WORDS_DB );
                while ( status == STATUS.OPEN && keyIterator.hasNext() )
                {
                    final Map.Entry<String, String> entry = keyIterator.next();
                    final String key = entry.getKey();
                    final String value = entry.getValue();
                    final long timeStamp = Long.parseLong( value );
                    final long entryAge = System.currentTimeMillis() - timeStamp;

                    if ( entryAge > settings.maxAgeMs )
                    {
                        localDB.remove( WORDS_DB, key );
                        removeCount++;

                        if ( removeCount % 1000 == 0 )
                        {
                            final int finalRemove = removeCount;
                            LOGGER.trace( () -> "wordDB reduce operation in progress, removed=" + finalRemove + ", total=" + ( initialSize - finalRemove ) );
                        }
                    }
                    else
                    {
                        localOldestEntry = timeStamp < localOldestEntry ? timeStamp : localOldestEntry;
                    }
                }
            }
            finally
            {
                try
                {
                    if ( keyIterator != null )
                    {
                        keyIterator.close();
                    }
                }
                catch ( final Exception e )
                {
                    LOGGER.warn( () -> "error returning LocalDB iterator: " + e.getMessage() );
                }
            }

            //update the oldest entry
            if ( status == STATUS.OPEN )
            {
                oldestEntry = localOldestEntry;
                localDB.put( META_DB, KEY_OLDEST_ENTRY, Long.toString( oldestEntry ) );
            }

            {
                final int finalRemove = removeCount;
                LOGGER.debug( () -> "completed wordDB reduce operation" + ", removed=" + finalRemove
                        + ", totalRemaining=" + size()
                        + ", oldestEntry=" + TimeDuration.asCompactString( oldestEntry )
                        + " in " + TimeDuration.compactFromCurrent( startTime ) );
            }
        }
    }

    public List<HealthRecord> healthCheck( )
    {
        return null;
    }

    public void init( final PwmApplication pwmApplication )
            throws PwmException
    {
        // convert to MS;
        settings.maxAgeMs = 1000 * pwmApplication.getConfig().readSettingAsLong( PwmSetting.PASSWORD_SHAREDHISTORY_MAX_AGE );
        settings.caseInsensitive = Boolean.parseBoolean( pwmApplication.getConfig().readAppProperty( AppProperty.SECURITY_SHAREDHISTORY_CASE_INSENSITIVE ) );
        settings.hashName = pwmApplication.getConfig().readAppProperty( AppProperty.SECURITY_SHAREDHISTORY_HASH_NAME );
        settings.hashIterations = Integer.parseInt( pwmApplication.getConfig().readAppProperty( AppProperty.SECURITY_SHAREDHISTORY_HASH_ITERATIONS ) );
        settings.version = "2" + "_" + settings.hashName + "_" + settings.hashIterations + "_" + settings.caseInsensitive;

        final int saltLength = Integer.parseInt( pwmApplication.getConfig().readAppProperty( AppProperty.SECURITY_SHAREDHISTORY_SALT_LENGTH ) );
        this.localDB = pwmApplication.getLocalDB();

        boolean needsClearing = false;
        if ( localDB == null )
        {
            LOGGER.info( () -> "LocalDB is not available, will remain closed" );
            status = STATUS.CLOSED;
            return;
        }

        if ( settings.maxAgeMs < 1 )
        {
            LOGGER.debug( () -> "max age=" + settings.maxAgeMs + ", will remain closed" );
            needsClearing = true;
        }

        {
            this.salt = localDB.get( META_DB, KEY_SALT );
            if ( salt == null || salt.length() < saltLength )
            {
                LOGGER.warn( () -> "stored global salt value is not present, creating new salt" );
                this.salt = PwmRandom.getInstance().alphaNumericString( saltLength );
                localDB.put( META_DB, KEY_SALT, this.salt );
                needsClearing = true;
            }
        }

        if ( needsClearing )
        {
            LOGGER.trace( () -> "clearing wordlist" );
            try
            {
                localDB.truncate( WORDS_DB );
            }
            catch ( final Exception e )
            {
                LOGGER.error( () -> "error during wordlist truncate", e );
            }
        }

        new Thread( new Runnable()
        {
            public void run( )
            {
                LOGGER.debug( () -> "starting up in background thread" );
                init( pwmApplication, settings.maxAgeMs );
            }
        }, PwmScheduler.makeThreadName( pwmApplication, this.getClass() ) + " initializer" ).start();
    }

    private static class Settings
    {
        private String version;
        private String hashName;
        private int hashIterations;
        private long maxAgeMs;
        private boolean caseInsensitive;
    }

    public ServiceInfoBean serviceInfo( )
    {
        if ( status == STATUS.OPEN )
        {
            return new ServiceInfoBean( Collections.singletonList( DataStorageMethod.LOCALDB ) );
        }
        else
        {
            return new ServiceInfoBean( Collections.<DataStorageMethod>emptyList() );
        }
    }
}
