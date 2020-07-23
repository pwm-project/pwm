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
import password.pwm.PwmApplicationMode;
import password.pwm.PwmConstants;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthMessage;
import password.pwm.health.HealthRecord;
import password.pwm.health.HealthStatus;
import password.pwm.health.HealthTopic;
import password.pwm.svc.PwmService;
import password.pwm.util.PwmScheduler;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.Percent;
import password.pwm.util.java.PwmCallable;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

abstract class AbstractWordlist implements Wordlist, PwmService
{
    static final TimeDuration DEBUG_OUTPUT_FREQUENCY = TimeDuration.MINUTE;
    private static final TimeDuration BUCKECT_CHECK_LOG_WARNING_TIMEOUT = TimeDuration.of( 100, TimeDuration.Unit.MILLISECONDS );

    private WordlistConfiguration wordlistConfiguration;
    private WordlistBucket wordlistBucket;
    private ExecutorService executorService;
    private Set<WordType> wordTypesCache = null;

    private volatile STATUS wlStatus = STATUS.NEW;

    private volatile ErrorInformation lastError;
    private volatile ErrorInformation autoImportError;

    private PwmApplication pwmApplication;
    private final AtomicBoolean inhibitBackgroundImportFlag = new AtomicBoolean( false );
    private final AtomicBoolean backgroundImportRunning = new AtomicBoolean( false );
    private final WordlistStatistics statistics = new WordlistStatistics();

    private volatile Activity activity = Wordlist.Activity.Idle;

    AbstractWordlist( )
    {
    }

    void init(
            final PwmApplication pwmApplication,
            final WordlistType type
    )
            throws PwmException
    {
        this.pwmApplication = pwmApplication;
        this.wordlistConfiguration = WordlistConfiguration.fromConfiguration( pwmApplication.getConfig(), type );

        if ( this.wordlistConfiguration.isTestMode() )
        {
            startTestInstance( type );
            return;
        }
        else
        {
            if ( pwmApplication.getApplicationMode() != PwmApplicationMode.RUNNING
                    || pwmApplication.getLocalDB() == null
            )
            {
                wlStatus = STATUS.CLOSED;
                return;
            }

            if ( pwmApplication.getLocalDB() != null )
            {
                wlStatus = STATUS.OPEN;
            }
            else
            {
                wlStatus = STATUS.CLOSED;
                final String errorMsg = "LocalDB is not available, will remain closed";
                getLogger().warn( () -> errorMsg );
                lastError = new ErrorInformation( PwmError.ERROR_SERVICE_NOT_AVAILABLE, errorMsg );
            }

            this.wordlistBucket = new LocalDBWordlistBucket( pwmApplication, wordlistConfiguration, type );
        }

        executorService = PwmScheduler.makeBackgroundExecutor( pwmApplication, this.getClass() );

        if ( !pwmApplication.getPwmEnvironment().isInternalRuntimeInstance() )
        {
            pwmApplication.getPwmScheduler().scheduleFixedRateJob( new InspectorJob(), executorService, TimeDuration.SECOND, wordlistConfiguration.getInspectorFrequency() );
        }

        getLogger().trace( () -> "opening with configuration: " + JsonUtil.serialize( wordlistConfiguration ) );
    }

    private void startTestInstance( final WordlistType wordlistType )
    {
        this.wordlistBucket = new MemoryWordlistBucket( pwmApplication, wordlistConfiguration, wordlistType );
        final WordlistInspector wordlistInspector = new WordlistInspector( pwmApplication, AbstractWordlist.this, () -> false );
        wordlistInspector.run();
    }

    boolean containsWord( final Set<WordType> wordTypes, final String word ) throws PwmUnrecoverableException
    {
        final Optional<String> testWord = WordlistUtil.normalizeWordLength( word, wordlistConfiguration );

        if ( !testWord.isPresent() )
        {
            return false;
        }

        boolean result = false;
        for ( final WordType wordType : wordTypes )
        {
            if ( !result )
            {
                if ( wordType == WordType.RAW )
                {
                    result = checkRawWords( testWord.get() );
                }
                else
                {
                    result = checkHashWords( wordType, testWord.get() );
                }
            }
        }

        return result;
    }

    private boolean checkHashWords( final WordType wordType, final String word )
            throws PwmUnrecoverableException
    {
        final String hashWord = wordType.convertInputFromUser( pwmApplication, wordlistConfiguration, word );
        return realBucketCheck( hashWord, wordType );
    }

    private boolean checkRawWords( final String word )
            throws PwmUnrecoverableException
    {
        final String normalizedWord = WordType.RAW.convertInputFromUser( pwmApplication, wordlistConfiguration, word );
        final Set<String> testWords = WordlistUtil.chunkWord( normalizedWord, this.wordlistConfiguration.getCheckSize() );

        getStatistics().getChunksPerWordCheck().update( testWords.size() );
        for ( final String t : testWords )
        {

            // stop checking once found
            if ( realBucketCheck( t, WordType.RAW ) )
            {
                return true;
            }
        }

        return false;
    }

    private boolean realBucketCheck( final String word, final WordType wordType )
            throws PwmUnrecoverableException
    {
        getStatistics().getWordChecks().next();

        final Instant startTime = Instant.now();
        final boolean isContainsWord = wordlistBucket.containsWord( word );

        final TimeDuration timeDuration = TimeDuration.fromCurrent( startTime );
        getStatistics().getWordCheckTimeMS().update( timeDuration.asMillis() );

        if ( timeDuration.isLongerThan( BUCKECT_CHECK_LOG_WARNING_TIMEOUT ) )
        {
            getLogger().debug( () -> "wordlist search time for wordlist permutations was greater then 100ms: " + timeDuration.asCompactString() );
        }

        if ( isContainsWord )
        {
            getStatistics().getWordTypeHits().get( wordType ).next();
        }
        else
        {
            getStatistics().getMisses().next();
        }

        return isContainsWord;
    }

    String randomSeed() throws PwmUnrecoverableException
    {
        return getWordlistBucket().randomSeed();
    }

    @Override
    public WordlistConfiguration getConfiguration( )
    {
        return wordlistConfiguration;
    }

    @Override
    public ErrorInformation getAutoImportError( )
    {
        return autoImportError;
    }

    public long size( )
    {
        if ( wlStatus != STATUS.OPEN )
        {
            return 0;
        }

        try
        {
            return wordlistBucket.size();
        }
        catch ( final PwmUnrecoverableException e )
        {
            getLogger().error( () -> "error reading size: " + e.getMessage() );
        }

        return -1;
    }

    public synchronized void close( )
    {
        final TimeDuration closeWaitTime = TimeDuration.of( 5, TimeDuration.Unit.SECONDS );

        wlStatus = STATUS.CLOSED;
        inhibitBackgroundImportFlag.set( true );
        if ( executorService != null )
        {
            executorService.shutdown();

            JavaHelper.closeAndWaitExecutor( executorService, closeWaitTime );
            if ( backgroundImportRunning.get() )
            {
                getLogger().warn( () -> "background thread still running after waiting " + closeWaitTime.asCompactString() );
            }
        }
    }

    public STATUS status( )
    {
        return wlStatus;
    }

    public List<HealthRecord> healthCheck( )
    {
        final List<HealthRecord> returnList = new ArrayList<>();

        if ( autoImportError != null )
        {
            final HealthRecord healthRecord = HealthRecord.forMessage( HealthMessage.Wordlist_AutoImportFailure,
                    wordlistConfiguration.getWordlistFilenameSetting().toMenuLocationDebug( null, PwmConstants.DEFAULT_LOCALE ),
                    autoImportError.getDetailedErrorMsg(),
                    JavaHelper.toIsoDate( autoImportError.getDate() )
            );
            returnList.add( healthRecord );
        }

        if ( backgroundImportRunning.get() )
        {
            final Activity activity = getActivity();
            final String suffix = "("
                    + ( ( activity == Activity.Importing ) ? getImportPercentComplete() : activity.getLabel() )
                    + ")";
            final HealthRecord healthRecord = HealthRecord.forMessage( HealthMessage.Wordlist_ImportInProgress, suffix );
            returnList.add( healthRecord );
        }

        if ( lastError != null )
        {
            final HealthRecord healthRecord = new HealthRecord( HealthStatus.WARN, HealthTopic.Application, this.getClass().getName() + " error: " + lastError.toDebugStr() );
            returnList.add( healthRecord );
        }
        return Collections.unmodifiableList( returnList );
    }

    public WordlistStatus readWordlistStatus( )
    {
        if ( wlStatus == STATUS.CLOSED )
        {
            return WordlistStatus.builder().build();
        }

        return wordlistBucket.readWordlistStatus();
    }

    void writeWordlistStatus( final WordlistStatus wordlistStatus )
    {
        wordTypesCache = null;
        wordlistBucket.writeWordlistStatus( wordlistStatus );
    }

    @Override
    public void clear( ) throws PwmUnrecoverableException
    {
        if ( wlStatus != STATUS.OPEN )
        {
            throw new PwmUnrecoverableException( PwmError.ERROR_SERVICE_NOT_AVAILABLE.toInfo() );
        }

        cancelBackgroundAndRunImmediate( () ->
        {
            clearImpl( Activity.Idle );
            executorService.execute( new InspectorJob() );
        } );
    }

    void clearImpl( final Activity postCleanActivity ) throws PwmUnrecoverableException
    {
        final Instant startTime = Instant.now();
        getLogger().trace( () -> "clearing stored wordlist" );
        activity = Wordlist.Activity.Clearing;
        writeWordlistStatus( WordlistStatus.builder().build() );
        getWordlistBucket().clear();
        getLogger().debug( () -> "cleared stored wordlist (" + TimeDuration.compactFromCurrent( startTime ) + ")" );
        setActivity( postCleanActivity );
    }


    void setAutoImportError( final ErrorInformation autoImportError )
    {
        this.autoImportError = autoImportError;
    }

    abstract PwmLogger getLogger();

    WordlistBucket getWordlistBucket()
    {
        return wordlistBucket;
    }

    @Override
    public void populate( final InputStream inputStream ) throws PwmUnrecoverableException
    {
        if ( wlStatus != STATUS.OPEN )
        {
            throw new PwmUnrecoverableException( PwmError.ERROR_SERVICE_NOT_AVAILABLE.toInfo() );
        }

        cancelBackgroundAndRunImmediate( () ->
        {
            setActivity( Activity.Importing );
            getLogger().debug( () -> "beginning direct user-supplied wordlist import" );
            setAutoImportError( null );
            final WordlistZipReader wordlistZipReader = new WordlistZipReader( inputStream );
            final WordlistImporter wordlistImporter = new WordlistImporter(
                    null,
                    wordlistZipReader,
                    WordlistSourceType.User,
                    AbstractWordlist.this,
                    () -> wlStatus != STATUS.OPEN
            );
            wordlistImporter.run();
            getLogger().debug( () -> "completed direct user-supplied wordlist import" );
        } );

        setActivity( Activity.Idle );
        executorService.execute( new InspectorJob() );
    }

    private void cancelBackgroundAndRunImmediate( final PwmCallable runnable ) throws PwmUnrecoverableException
    {
        inhibitBackgroundImportFlag.set( true );
        try
        {
            TimeDuration.of( 10, TimeDuration.Unit.SECONDS ).pause( () -> !backgroundImportRunning.get() );
            if ( backgroundImportRunning.get() )
            {
                throw PwmUnrecoverableException.newException( PwmError.ERROR_WORDLIST_IMPORT_ERROR, "unable to cancel background operation in progress" );
            }

            runnable.call();
        }
        finally
        {
            inhibitBackgroundImportFlag.set( false );
        }

    }

    class InspectorJob implements Runnable
    {
        @Override
        public void run()
        {
            try
            {
                if ( !inhibitBackgroundImportFlag.get() )
                {
                    activity = Wordlist.Activity.ReadingWordlistFile;
                    final BooleanSupplier cancelFlag = inhibitBackgroundImportFlag::get;
                    backgroundImportRunning.set( true );
                    final WordlistInspector wordlistInspector = new WordlistInspector( pwmApplication, AbstractWordlist.this, cancelFlag );
                    wordlistInspector.run();
                    activity = Wordlist.Activity.Idle;
                }
            }
            catch ( final Throwable t )
            {
                getLogger().error( () -> "error running InspectorJob: " + t.getMessage(), t );
                throw t;
            }
            finally
            {
                backgroundImportRunning.set( false );
            }
        }
    }

    Set<WordType> getWordTypesCache()
    {
        if ( wordTypesCache == null )
        {
            wordTypesCache = Collections.unmodifiableSet( new HashMap<>( this.readWordlistStatus().getWordTypes() ).keySet() );
        }
        return wordTypesCache;
    }

    @Override
    public Activity getActivity()
    {
        return activity;
    }

    void setActivity( final Activity activity )
    {
        this.activity = activity;
        wordTypesCache = null;
    }


    public ServiceInfoBean serviceInfo( )
    {
        if ( status() == STATUS.OPEN )
        {
            return new ServiceInfoBean( Collections.singletonList( DataStorageMethod.LOCALDB ), getStatistics().asDebugMap() );
        }
        else
        {
            return new ServiceInfoBean( Collections.emptyList() );
        }
    }

    WordlistStatistics getStatistics()
    {
        return statistics;
    }

    @Override
    public String getImportPercentComplete()
    {
        if ( backgroundImportRunning.get() )
        {
            final WordlistStatus wordlistStatus = readWordlistStatus();
            if ( wordlistStatus != null )
            {
                final WordlistSourceInfo wordlistSourceInfo = wordlistStatus.getRemoteInfo();
                if ( wordlistSourceInfo != null )
                {
                    final long totalBytes = wordlistSourceInfo.getBytes();
                    final long importBytes = wordlistStatus.getBytes();
                    if ( importBytes > 0 && totalBytes > 0 )
                    {
                        final Percent percent = new Percent( importBytes, totalBytes );
                        return percent.pretty( 3 );
                    }
                }

            }
        }

        return "";
    }
}
