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

package password.pwm.svc.wordlist;

import lombok.Builder;
import lombok.Value;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmApplicationMode;
import password.pwm.PwmConstants;
import password.pwm.bean.DomainID;
import password.pwm.bean.SessionLabel;
import password.pwm.config.AppConfig;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.error.PwmException;
import password.pwm.health.HealthRecord;
import password.pwm.svc.AbstractPwmService;
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
import java.util.Optional;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


public class SharedHistoryService extends AbstractPwmService implements PwmService
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( SharedHistoryService.class );

    private static final String KEY_OLDEST_ENTRY = "oldest_entry";
    private static final String KEY_VERSION = "version";
    private static final String KEY_SALT = "salt";

    private static final String DATA_FORMAT_VERSION = "2";

    // 1 hour
    private static final int MIN_CLEANER_FREQUENCY = 1000 * 60 * 60;

    // 1 day
    private static final int MAX_CLEANER_FREQUENCY = 1000 * 60 * 60 * 24;

    private static final LocalDB.DB META_DB = LocalDB.DB.SHAREDHISTORY_META;
    private static final LocalDB.DB WORDS_DB = LocalDB.DB.SHAREDHISTORY_WORDS;

    private ExecutorService executorService;

    private LocalDB localDB;
    private String salt;
    private Instant oldestEntry;

    private Settings settings = Settings.builder().build();
    private final Lock addWordLock = new ReentrantLock();

    public SharedHistoryService( )
    {
    }

    @Override
    public void shutdownImpl( )
    {
        setStatus( STATUS.CLOSED );
        if ( executorService != null )
        {
            executorService.shutdown();
        }
        localDB = null;
    }

    public boolean containsWord( final String word )
    {
        if ( status() != STATUS.OPEN )
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
            final Optional<String> storedValue = localDB.get( WORDS_DB, hashedWord );
            if ( storedValue.isPresent() )
            {
                final Instant timeStamp = Instant.ofEpochMilli( Long.parseLong(  storedValue.get() ) );
                final TimeDuration entryAge = TimeDuration.between( Instant.now(), timeStamp );
                if ( entryAge.isLongerThan( settings.getMaxAge( ) ) )
                {
                    result = true;
                }
            }

        }
        catch ( final Exception e )
        {
            LOGGER.warn( getSessionLabel(), () -> "error checking global history list: " + e.getMessage() );
        }

        //LOGGER.trace(pwmSession, "successfully checked word, result=" + result + ", duration=" + new TimeDuration(System.currentTimeMillis(), startTime).asCompactString());
        return result;
    }

    public Instant getOldestEntryTime( )
    {
       return oldestEntry;
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
                LOGGER.error( getSessionLabel(), () -> "error checking wordlist size: " + e.getMessage() );
                return 0;
            }
        }
        else
        {
            return 0;
        }
    }

    private void checkDbVersion( )
            throws Exception
    {
        LOGGER.trace( getSessionLabel(), () -> "checking version number stored in LocalDB" );

        final String versionInDB = localDB.get( META_DB, KEY_VERSION ).orElse( "" );
        final String currentVersion = "version=" + settings.getVersion();
        final boolean result = currentVersion.equals( versionInDB );

        if ( !result )
        {
            LOGGER.info( getSessionLabel(), () -> "existing db version does not match current db version db=(" + versionInDB + ")  current=(" + currentVersion + "), clearing db" );
            localDB.truncate( WORDS_DB );
            localDB.put( META_DB, KEY_VERSION, currentVersion );
            localDB.remove( META_DB, KEY_OLDEST_ENTRY );
        }
        else
        {
            LOGGER.trace( getSessionLabel(), () -> "existing db version matches current db version db=(" + versionInDB + ")  current=(" + currentVersion + ")" );
        }
    }

    private void init( final PwmApplication pwmApplication, final TimeDuration maxAge )
    {
        final Instant startTime = Instant.now();

        try
        {
            checkDbVersion();
        }
        catch ( final Exception e )
        {
            LOGGER.error( () -> "error checking db version", e );
            setStatus( STATUS.CLOSED );
            return;
        }


        try
        {
            final Optional<String> oldestEntryStr = localDB.get( META_DB, KEY_OLDEST_ENTRY );
            if ( oldestEntryStr.isPresent() )
            {
                oldestEntry = Instant.ofEpochMilli( Long.parseLong( oldestEntryStr.get() ) );
                LOGGER.trace( getSessionLabel(), () -> "oldest timestamp loaded from localDB, age is " + TimeDuration.fromCurrent( oldestEntry ).asCompactString() );
            }
            else
            {
                oldestEntry = Instant.now();
                LOGGER.trace( getSessionLabel(), () -> "no oldestEntry timestamp stored, will rescan" );
                }
        }
        catch ( final LocalDBException e )
        {
            LOGGER.error( () -> "unexpected error loading oldest-entry meta record, will remain closed: " + e.getMessage(), e );
            setStatus( STATUS.CLOSED );
            return;
        }

        try
        {
            final long size = localDB.size( WORDS_DB );
            LOGGER.debug( getSessionLabel(), () -> "open with " + size + " words"
                    + ", maxAgeMs=" + maxAge.asCompactString()
                    + ", oldestEntry=" + TimeDuration.fromCurrent( oldestEntry ).asCompactString(),
                    () -> TimeDuration.fromCurrent( startTime ) );
        }
        catch ( final LocalDBException e )
        {
            LOGGER.error( getSessionLabel(), () -> "unexpected error examining size of DB, will remain closed: " + e.getMessage(), e );
            setStatus( STATUS.CLOSED );
            return;
        }

        setStatus( STATUS.OPEN );
        //populateFromWordlist();  //only used for debugging!!!

        if ( pwmApplication.getApplicationMode() == PwmApplicationMode.RUNNING || pwmApplication.getApplicationMode() == PwmApplicationMode.CONFIGURATION )
        {
            final long frequencyMs = JavaHelper.rangeCheck( MIN_CLEANER_FREQUENCY, MAX_CLEANER_FREQUENCY, maxAge.asMillis() );
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

    public void addWord(
            final SessionLabel sessionLabel,
            final String word
    )
    {
        if ( status() != STATUS.OPEN )
        {
            return;
        }

        final String addWord = normalizeWord( word );

        if ( addWord == null )
        {
            return;
        }

        final Instant startTime = Instant.now();

        addWordLock.lock();
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
        finally
        {
            addWordLock.unlock();
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

        @Override
        public void run( )
        {
            try
            {
                reduceWordDB();
            }
            catch ( final LocalDBException e )
            {
                LOGGER.error( getSessionLabel(), () -> "error during old record purge: " + e.getMessage() );
            }
        }


        private void reduceWordDB( )
                throws LocalDBException
        {

            if ( localDB == null || localDB.status() != LocalDB.Status.OPEN )
            {
                return;
            }

            final TimeDuration oldestEntryAge = TimeDuration.fromCurrent( oldestEntry );
            if ( settings.getMaxAge().isLongerThan( oldestEntryAge ) )

                {
                LOGGER.debug( getSessionLabel(), () -> "skipping wordDB reduce operation, eldestEntry="
                        + oldestEntryAge.asCompactString()
                        + ", maxAge="
                        + settings.getMaxAge().asCompactString() );
                return;
            }

            final Instant startTime = Instant.now();
            final long initialSize = size();
            int removeCount = 0;
            Instant localOldestEntry = Instant.now();

            LOGGER.debug( getSessionLabel(), () -> "beginning wordDB reduce operation, examining " + initialSize
                    + " words for entries older than " + settings.getMaxAge().asCompactString() );


            try ( LocalDB.LocalDBIterator<Map.Entry<String, String>> keyIterator = localDB.iterator( WORDS_DB ) )
            {
                while ( status() == STATUS.OPEN && keyIterator.hasNext() )
                {
                    final Map.Entry<String, String> entry = keyIterator.next();
                    final String key = entry.getKey();
                    final String value = entry.getValue();
                    final Instant entryTimestamp = Instant.ofEpochMilli( Long.parseLong( value ) );

                    if ( settings.getMaxAge().isLongerThan( TimeDuration.fromCurrent( entryTimestamp ) ) )
                    {
                        localDB.remove( WORDS_DB, key );
                        removeCount++;

                        if ( removeCount % 1000 == 0 )
                        {
                            final int finalRemove = removeCount;
                            LOGGER.trace( getSessionLabel(), () -> "wordDB reduce operation in progress, removed=" + finalRemove + ", total=" + ( initialSize - finalRemove ) );
                        }
                    }
                    else
                    {
                        localOldestEntry = localOldestEntry.isBefore( entryTimestamp ) ? entryTimestamp : localOldestEntry;
                    }
                }
            }

            //update the oldest entry
            if ( status() == STATUS.OPEN )
            {
                oldestEntry = localOldestEntry;
                localDB.put( META_DB, KEY_OLDEST_ENTRY, Long.toString( oldestEntry.getEpochSecond() ) );
            }

            {
                final int finalRemove = removeCount;
                LOGGER.debug( getSessionLabel(), () -> "completed wordDB reduce operation" + ", removed=" + finalRemove
                        + ", totalRemaining=" + size()
                        + ", oldestEntry=" + oldestEntry
                        + " in ", () -> TimeDuration.fromCurrent( startTime ) );
            }
        }
    }

    @Override
    public List<HealthRecord> serviceHealthCheck( )
    {
        return Collections.emptyList();
    }

    @Override
    public STATUS postAbstractInit( final PwmApplication pwmApplication, final DomainID domainID )
            throws PwmException
    {
        settings = Settings.fromConfiguration( pwmApplication );

        final int saltLength = Integer.parseInt( pwmApplication.getConfig().readAppProperty( AppProperty.SECURITY_SHAREDHISTORY_SALT_LENGTH ) );
        this.localDB = pwmApplication.getLocalDB();

        boolean needsClearing = false;
        if ( localDB == null )
        {
            LOGGER.info( getSessionLabel(), () -> "LocalDB is not available, will remain closed" );
            setStatus( STATUS.CLOSED );
            return STATUS.CLOSED;
        }

        if ( settings.getMaxAge().isShorterThan( TimeDuration.SECOND ) )
        {
            LOGGER.debug( getSessionLabel(), () -> "max age=" + settings.getMaxAge().asCompactString() + ", will remain closed" );
            needsClearing = true;
        }

        {
            this.salt = localDB.get( META_DB, KEY_SALT ).orElse( null );
            if ( salt == null || salt.length() < saltLength )
            {
                LOGGER.warn( getSessionLabel(), () -> "stored global salt value is not present, creating new salt" );
                this.salt = PwmRandom.getInstance().alphaNumericString( saltLength );
                localDB.put( META_DB, KEY_SALT, this.salt );
                needsClearing = true;
            }
        }

        if ( needsClearing )
        {
            LOGGER.trace( getSessionLabel(), () -> "clearing wordlist" );
            try
            {
                localDB.truncate( WORDS_DB );
            }
            catch ( final Exception e )
            {
                LOGGER.error( getSessionLabel(), () -> "error during wordlist truncate", e );
            }
        }

        pwmApplication.getPwmScheduler().immediateExecuteRunnableInNewThread( () ->
        {
            LOGGER.debug( getSessionLabel(), () -> "starting up in background thread" );
            init( pwmApplication, settings.getMaxAge() );
        }, "shared history initializer" );

        return STATUS.OPEN;
    }

    @Value
    @Builder
    private static class Settings
    {
        private final String hashName;
        private final int hashIterations;
        private final TimeDuration maxAge;
        private final boolean caseInsensitive;

        public String getVersion()
        {
            return DATA_FORMAT_VERSION + "_" + hashName + "_" + hashIterations + "_" + caseInsensitive;
        }

        static Settings fromConfiguration( final PwmApplication pwmApplication )
        {
            final AppConfig config = pwmApplication.getConfig();


            return Settings.builder()
                    .maxAge( TimeDuration.of( config.getDomainConfigs().get( DomainID.DOMAIN_ID_DEFAULT )
                            .readSettingAsLong( PwmSetting.PASSWORD_SHAREDHISTORY_MAX_AGE ), TimeDuration.Unit.SECONDS ) )
                    .caseInsensitive( Boolean.parseBoolean( config.readAppProperty( AppProperty.SECURITY_SHAREDHISTORY_CASE_INSENSITIVE ) ) )
                    .hashName( config.readAppProperty( AppProperty.SECURITY_SHAREDHISTORY_HASH_NAME ) )
                    .hashIterations( Integer.parseInt( config.readAppProperty( AppProperty.SECURITY_SHAREDHISTORY_HASH_ITERATIONS ) ) )
                    .build();
        }
    }

    @Override
    public ServiceInfoBean serviceInfo( )
    {
        if ( status() == STATUS.OPEN )
        {
            return ServiceInfoBean.builder().storageMethod( DataStorageMethod.LOCALDB ).build();
        }
        else
        {
            return ServiceInfoBean.builder().build();
        }
    }

}
