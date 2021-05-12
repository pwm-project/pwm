/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2020 The PWM Project
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
import password.pwm.bean.DomainID;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthMessage;
import password.pwm.health.HealthRecord;
import password.pwm.svc.AbstractPwmService;
import password.pwm.svc.PwmService;
import password.pwm.util.PwmScheduler;
import password.pwm.util.java.ConditionalTaskExecutor;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.Percent;
import password.pwm.util.java.PwmCallable;
import password.pwm.util.java.StringUtil;
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
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;

abstract class AbstractWordlist extends AbstractPwmService implements Wordlist, PwmService
{
    static final TimeDuration DEBUG_OUTPUT_FREQUENCY = TimeDuration.MINUTE;

    private WordlistConfiguration wordlistConfiguration;
    private WordlistBucket wordlistBucket;
    private ExecutorService executorService;
    private volatile Set<WordType> wordTypesCache = null;

    private volatile ErrorInformation lastError;
    private volatile ErrorInformation autoImportError;

    private final AtomicBoolean inhibitBackgroundImportFlag = new AtomicBoolean( false );
    private final ReentrantLock backgroundImportRunning = new ReentrantLock();
    private final WordlistStatistics statistics = new WordlistStatistics();
    private final ConditionalTaskExecutor statsOutput = ConditionalTaskExecutor.forPeriodicTask( this::outputStats,
            TimeDuration.of( 5, TimeDuration.Unit.MINUTES ) );

    private volatile Activity activity = Wordlist.Activity.Idle;

    AbstractWordlist( )
    {
    }

    @Override
    protected Set<PwmApplication.Condition> openConditions()
    {
        return Collections.emptySet();
    }

    protected STATUS postAbstractInit(
            final PwmApplication pwmApplication,
            final DomainID domainID
    )
            throws PwmException
    {
        final WordlistType type = getWordlistType();

        this.wordlistConfiguration = WordlistConfiguration.fromConfiguration( pwmApplication.getConfig(), type );

        if ( this.wordlistConfiguration.isTestMode() )
        {
            startTestInstance( type );
            return STATUS.OPEN;
        }
        else
        {
            if ( pwmApplication.getApplicationMode() != PwmApplicationMode.RUNNING )
            {
                return STATUS.CLOSED;
            }

            if ( pwmApplication.getLocalDB() == null )
            {
                final String errorMsg = "LocalDB is not available, will remain closed";
                getLogger().warn( getSessionLabel(), () -> errorMsg );
                lastError = new ErrorInformation( PwmError.ERROR_SERVICE_NOT_AVAILABLE, errorMsg );
                return STATUS.CLOSED;
            }

            this.wordlistBucket = new LocalDBWordlistBucket( pwmApplication, wordlistConfiguration, type );
        }

        inhibitBackgroundImportFlag.set( false );
        executorService = PwmScheduler.makeBackgroundExecutor( pwmApplication, this.getClass() );

        if ( !pwmApplication.getPwmEnvironment().isInternalRuntimeInstance() )
        {
            pwmApplication.getPwmScheduler().scheduleFixedRateJob(
                    new InspectorJob(), executorService, TimeDuration.SECOND, wordlistConfiguration.getInspectorFrequency() );
        }

        getLogger().trace( getSessionLabel(), () -> "opening with configuration: " + JsonUtil.serialize( wordlistConfiguration ) );

        warmup();

        return STATUS.OPEN;
    }

    protected abstract WordlistType getWordlistType();

    protected abstract PwmLogger getLogger();

    protected abstract void warmup();

    private void startTestInstance( final WordlistType wordlistType )
    {
        this.wordlistBucket = new MemoryWordlistBucket( getPwmApplication(), wordlistConfiguration, wordlistType );
        final WordlistInspector wordlistInspector = new WordlistInspector( getPwmApplication(), AbstractWordlist.this, () -> false );
        wordlistInspector.run();
    }

    boolean containsWord( final Set<WordType> wordTypes, final String word ) throws PwmUnrecoverableException
    {
        final Instant startTime = Instant.now();

        final Optional<String> testWord = WordlistUtil.normalizeWordLength( word, wordlistConfiguration );

        if ( testWord.isEmpty() )
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

        getStatistics().getAverageStats().update( WordlistStatistics.AverageStat.avgWordCheckLength, word.length() );
        getStatistics().getAverageStats().update( WordlistStatistics.AverageStat.wordCheckTimeMS, TimeDuration.fromCurrent( startTime ) );
        getStatistics().getCounterStats().increment( WordlistStatistics.CounterStat.wordChecks );
        if ( result )
        {
            getStatistics().getCounterStats().increment( WordlistStatistics.CounterStat.wordHits );
        }
        else
        {
            getStatistics().getCounterStats().increment( WordlistStatistics.CounterStat.wordMisses );
        }

        return result;
    }

    private boolean checkHashWords( final WordType wordType, final String word )
            throws PwmUnrecoverableException
    {
        final String hashWord = wordType.convertInputFromUser( getPwmApplication(), wordlistConfiguration, word );
        return realBucketCheck( hashWord, wordType );
    }

    private boolean checkRawWords( final String word )
            throws PwmUnrecoverableException
    {
        final String normalizedWord = WordType.RAW.convertInputFromUser( getPwmApplication(), wordlistConfiguration, word );
        final Set<String> testWords = WordlistUtil.chunkWord( normalizedWord, this.wordlistConfiguration.getCheckSize() );

        getStatistics().getAverageStats().update( WordlistStatistics.AverageStat.chunksPerWordCheck, testWords.size() );

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

    void outputStats()
    {
        getLogger().trace( getSessionLabel(), () -> "periodic statistics: " + StringUtil.mapToString( getStatistics().asDebugMap() ) );

        {
            final TimeDuration timeDuration = TimeDuration.of(
                    ( long ) getStatistics().getAverageStats().getAverage( WordlistStatistics.AverageStat.wordCheckTimeMS ),
                    TimeDuration.Unit.MILLISECONDS );
            if ( timeDuration.isLongerThan( wordlistConfiguration.getBucketCheckLogWarningTimeout() ) )
            {
                getLogger().warn( getSessionLabel(), () -> "avg wordlist search time (" + timeDuration.asCompactString() + ") for wordlist permutations was greater than "
                        + wordlistConfiguration.getBucketCheckLogWarningTimeout().asCompactString()
                );
            }
        }
    }

    private boolean realBucketCheck( final String word, final WordType wordType )
            throws PwmUnrecoverableException
    {
        final Instant startTime = Instant.now();
        final boolean results = wordlistBucket.containsWord( word );

        statsOutput.conditionallyExecuteTask();

        getStatistics().getAverageStats().update( WordlistStatistics.AverageStat.chunkCheckTimeMS, TimeDuration.fromCurrent( startTime ) );
        getStatistics().getCounterStats().increment( WordlistStatistics.CounterStat.chunkChecks );
        if ( results )
        {
            getStatistics().getWordTypeHits().get( wordType ).increment();
            getStatistics().getCounterStats().increment( WordlistStatistics.CounterStat.chunkHits );
        }
        else
        {
            getStatistics().getCounterStats().increment( WordlistStatistics.CounterStat.chunkMisses );
        }

        return results;
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

    @Override
    public long size( )
    {
        if ( status() != STATUS.OPEN )
        {
            return 0;
        }

        try
        {
            return wordlistBucket.size();
        }
        catch ( final PwmUnrecoverableException e )
        {
            getLogger().error( getSessionLabel(), () -> "error reading size: " + e.getMessage() );
        }

        return -1;
    }

    @Override
    public void close( )
    {
        final TimeDuration closeWaitTime = TimeDuration.of( 1, TimeDuration.Unit.MINUTES );

        setStatus( STATUS.CLOSED );
        inhibitBackgroundImportFlag.set( true );
        if ( executorService != null )
        {
            executorService.shutdown();

            JavaHelper.closeAndWaitExecutor( executorService, closeWaitTime );
            if ( backgroundImportRunning.isLocked() )
            {
                getLogger().warn( getSessionLabel(), () -> "background thread still running after waiting " + closeWaitTime.asCompactString() );
            }
        }
    }

    @Override
    public List<HealthRecord> serviceHealthCheck( )
    {
        final List<HealthRecord> returnList = new ArrayList<>();

        if ( autoImportError != null )
        {
            final HealthRecord healthRecord = HealthRecord.forMessage(
                    DomainID.systemId(),
                    HealthMessage.Wordlist_AutoImportFailure,
                    wordlistConfiguration.getWordlistFilenameSetting().toMenuLocationDebug( null, PwmConstants.DEFAULT_LOCALE ),
                    autoImportError.getDetailedErrorMsg(),
                    JavaHelper.toIsoDate( autoImportError.getDate() )
            );
            returnList.add( healthRecord );
        }

        if ( backgroundImportRunning.isLocked() )
        {
            final Activity activity = getActivity();
            final String suffix = "("
                    + ( ( activity == Activity.Importing ) ? getImportPercentComplete() : activity.getLabel() )
                    + ")";
            final HealthRecord healthRecord = HealthRecord.forMessage(
                    DomainID.systemId(),
                    HealthMessage.Wordlist_ImportInProgress,
                    suffix );

            returnList.add( healthRecord );
        }

        if ( lastError != null )
        {
            final HealthRecord healthRecord = HealthRecord.forMessage(
                    DomainID.systemId(),
                    HealthMessage.ServiceClosed,
                    this.getClass().getSimpleName(),
                    lastError.toDebugStr() );
            returnList.add( healthRecord );
        }
        return Collections.unmodifiableList( returnList );
    }

    @Override
    public WordlistStatus readWordlistStatus( )
    {
        if ( status() == STATUS.CLOSED )
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
        if ( status() != STATUS.OPEN )
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
        getLogger().trace( getSessionLabel(), () -> "clearing stored wordlist" );
        activity = Wordlist.Activity.Clearing;
        writeWordlistStatus( WordlistStatus.builder().build() );
        getWordlistBucket().clear();
        getLogger().debug( getSessionLabel(), () -> "cleared stored wordlist", () -> TimeDuration.fromCurrent( startTime ) );
        setActivity( postCleanActivity );
    }

    void setAutoImportError( final ErrorInformation autoImportError )
    {
        this.autoImportError = autoImportError;
    }

    WordlistBucket getWordlistBucket()
    {
        return wordlistBucket;
    }

    @Override
    public void populate( final InputStream inputStream ) throws PwmUnrecoverableException
    {
        if ( status() != STATUS.OPEN )
        {
            throw new PwmUnrecoverableException( PwmError.ERROR_SERVICE_NOT_AVAILABLE.toInfo() );
        }

        cancelBackgroundAndRunImmediate( () ->
        {
            setActivity( Activity.Importing );
            getLogger().debug( getSessionLabel(), () -> "beginning direct user-supplied wordlist import" );
            setAutoImportError( null );
            final WordlistZipReader wordlistZipReader = new WordlistZipReader( inputStream );
            final WordlistImporter wordlistImporter = new WordlistImporter(
                    null,
                    wordlistZipReader,
                    WordlistSourceType.User,
                    AbstractWordlist.this,
                    makeProcessCancelSupplier()
            );
            wordlistImporter.run();
            getLogger().debug( getSessionLabel(), () -> "completed direct user-supplied wordlist import" );
        } );

        setActivity( Activity.Idle );
        executorService.execute( new InspectorJob() );
    }

    private void cancelBackgroundAndRunImmediate( final PwmCallable runnable ) throws PwmUnrecoverableException
    {
        inhibitBackgroundImportFlag.set( true );
        try
        {
            TimeDuration.of( 10, TimeDuration.Unit.SECONDS ).pause( () -> !backgroundImportRunning.isLocked() );
            if ( backgroundImportRunning.isLocked() )
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
            if ( inhibitBackgroundImportFlag.get() )
            {
                return;
            }

            backgroundImportRunning.lock();
            try
            {
                activity = Wordlist.Activity.ReadingWordlistFile;
                final BooleanSupplier cancelFlag = makeProcessCancelSupplier( );
                final WordlistInspector wordlistInspector = new WordlistInspector( getPwmApplication(), AbstractWordlist.this, cancelFlag );
                wordlistInspector.run();
                activity = Wordlist.Activity.Idle;
            }
            catch ( final Throwable t )
            {
                getLogger().error( getSessionLabel(), () -> "error running InspectorJob: " + t.getMessage(), t );
                throw t;
            }
            finally
            {
                backgroundImportRunning.unlock();
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


    @Override
    public ServiceInfoBean serviceInfo( )
    {
        if ( status() == STATUS.OPEN )
        {
            return ServiceInfoBean.builder()
                    .storageMethod( DataStorageMethod.LOCALDB )
                    .debugProperties( getStatistics().asDebugMap() )
                    .build();
        }

        return ServiceInfoBean.builder().build();
    }

    WordlistStatistics getStatistics()
    {
        return statistics;
    }

    @Override
    public String getImportPercentComplete()
    {
        if ( backgroundImportRunning.isLocked() )
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
                        final Percent percent = Percent.of( importBytes, totalBytes );
                        return percent.pretty( 3 );
                    }
                }

            }
        }

        return "";
    }

    private BooleanSupplier makeProcessCancelSupplier( )
    {
        return () -> inhibitBackgroundImportFlag.get()
                || !STATUS.OPEN.equals( status() );
    }
}
