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
import password.pwm.util.java.TimeDuration;
import password.pwm.util.localdb.LocalDBException;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmHashAlgorithm;

import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

abstract class AbstractWordlist implements Wordlist, PwmService
{
    static final PwmHashAlgorithm CHECKSUM_HASH_ALG = PwmHashAlgorithm.SHA256;
    static final TimeDuration DEBUG_OUTPUT_FREQUENCY = TimeDuration.MINUTE;

    private WordlistConfiguration wordlistConfiguration;
    private WordlistBucket wordklistBucket;
    private ExecutorService executorService;

    private volatile STATUS wlStatus = STATUS.NEW;

    private volatile ErrorInformation lastError;
    private volatile ErrorInformation autoImportError;

    private PwmApplication pwmApplication;
    private final AtomicBoolean inhibitBackgroundImportFlag = new AtomicBoolean( false );
    private final AtomicBoolean backgroundImportRunning = new AtomicBoolean( false );

    private volatile Activity activity = Wordlist.Activity.Idle;

    AbstractWordlist( )
    {
    }

    public void init(
            final PwmApplication pwmApplication,
            final WordlistType type
    )
            throws PwmException
    {
        this.pwmApplication = pwmApplication;
        this.wordlistConfiguration = WordlistConfiguration.fromConfiguration( pwmApplication.getConfig(), type );

        if ( pwmApplication.getApplicationMode() != PwmApplicationMode.RUNNING
                || pwmApplication.getLocalDB() == null
        )
        {
            wlStatus = STATUS.CLOSED;
            return;
        }

        this.wordklistBucket = new WordlistBucket( pwmApplication, wordlistConfiguration, type );

        executorService = PwmScheduler.makeBackgroundExecutor( pwmApplication, this.getClass() );

        if ( pwmApplication.getLocalDB() != null )
        {
            wlStatus = STATUS.OPEN;
        }
        else
        {
            wlStatus = STATUS.CLOSED;
            final String errorMsg = "LocalDB is not available, will remain closed";
            getLogger().warn( errorMsg );
            lastError = new ErrorInformation( PwmError.ERROR_SERVICE_NOT_AVAILABLE, errorMsg );
        }

        pwmApplication.getPwmScheduler().scheduleFixedRateJob( new InspectorJob(), executorService, TimeDuration.SECOND, wordlistConfiguration.getInspectorFrequency() );
    }

    boolean containsWord( final String word ) throws PwmUnrecoverableException
    {
        try
        {
            return wordklistBucket.containsWord( word );
        }
        catch ( LocalDBException e )
        {
            throw PwmUnrecoverableException.newException( PwmError.ERROR_LOCALDB_UNAVAILABLE, e.getMessage() );
        }
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
            return wordklistBucket.size();
        }
        catch ( LocalDBException e )
        {
            getLogger().error( "error reading size: " + e.getMessage() );
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
                getLogger().warn( "background thread still running after waiting " + closeWaitTime.asCompactString() );
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

        if ( lastError != null )
        {
            final HealthRecord healthRecord = new HealthRecord( HealthStatus.WARN, HealthTopic.Application, this.getClass().getName() + " error: " + lastError.toDebugStr() );
            returnList.add( healthRecord );
        }
        return Collections.unmodifiableList( returnList );
    }

    public ServiceInfoBean serviceInfo( )
    {
        if ( status() == STATUS.OPEN )
        {
            return new ServiceInfoBean( Collections.singletonList( DataStorageMethod.LOCALDB ) );
        }
        else
        {
            return new ServiceInfoBean( Collections.emptyList() );
        }
    }

    public WordlistStatus readWordlistStatus( )
    {
        if ( wlStatus == STATUS.CLOSED )
        {
            return WordlistStatus.builder().build();
        }

        final PwmApplication.AppAttribute appAttribute = wordlistConfiguration.getMetaDataAppAttribute();
        final WordlistStatus storedValue = pwmApplication.readAppAttribute( appAttribute, WordlistStatus.class );
        if ( storedValue != null )
        {
            return storedValue;
        }
        return WordlistStatus.builder().build();
    }

    void writeWordlistStatus( final WordlistStatus metadataBean )
    {
        final PwmApplication.AppAttribute appAttribute = wordlistConfiguration.getMetaDataAppAttribute();
        pwmApplication.writeAppAttribute( appAttribute, metadataBean );
        //getLogger().trace( "updated stored state: " + JsonUtil.serialize( metadataBean ) );
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
            try
            {
                clearImpl( Activity.Idle );
                executorService.execute( new InspectorJob() );
            }
            catch ( LocalDBException e )
            {
                throw new PwmUnrecoverableException( e.getErrorInformation() );
            }
        } );
    }

    void clearImpl( final Activity postCleanActivity ) throws LocalDBException
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
        return wordklistBucket;
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

    private interface PwmCallable
    {
        void call() throws PwmUnrecoverableException;
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
            finally
            {
                backgroundImportRunning.set( false );
            }
        }
    }

    @Override
    public Activity getActivity()
    {
        return activity;
    }

    void setActivity( final Activity activity )
    {
        this.activity = activity;
    }
}
