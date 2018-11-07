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
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.localdb.LocalDBException;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmHashAlgorithm;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

abstract class AbstractWordlist implements Wordlist, PwmService
{

    static final PwmHashAlgorithm CHECKSUM_HASH_ALG = PwmHashAlgorithm.SHA1;

    private WordlistConfiguration wordlistConfiguration;
    private WordlistBucket wordklistBucket;
    private ScheduledExecutorService executorService;

    private volatile STATUS wlStatus = STATUS.NEW;

    private volatile ErrorInformation lastError;
    private volatile ErrorInformation autoImportError;

    private PwmApplication pwmApplication;
    private final AtomicBoolean inhibitBackgroundImportFlag = new AtomicBoolean( false );
    private final AtomicBoolean backgroundImportRunning = new AtomicBoolean( false );

    private Class concreteClass;

    protected AbstractWordlist( )
    {
    }

    public void init(
            final PwmApplication pwmApplication,
            final WordlistType type,
            final Class overrideClass
    )
            throws PwmException
    {
        this.pwmApplication = pwmApplication;
        this.wordlistConfiguration = WordlistConfiguration.fromConfiguration( pwmApplication.getConfig(), type );
        this.wordklistBucket = new WordlistBucket( pwmApplication, wordlistConfiguration, type );
        this.concreteClass = overrideClass;

        if ( pwmApplication.getLocalDB() != null )
        {
            executorService = Executors.newSingleThreadScheduledExecutor(
                    JavaHelper.makePwmThreadFactory(
                            JavaHelper.makeThreadName( pwmApplication, overrideClass ) + "-",
                            true
                    ) );



            executorService.scheduleWithFixedDelay( new PopulateJob(), 0, 1, TimeUnit.MINUTES );

            wlStatus = STATUS.OPEN;
        }
        else
        {
            wlStatus = STATUS.CLOSED;
            final String errorMsg = "LocalDB is not available, will remain closed";
            getLogger().warn( errorMsg );
            lastError = new ErrorInformation( PwmError.ERROR_SERVICE_NOT_AVAILABLE, errorMsg );
        }
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

    public int size( )
    {
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
        wlStatus = STATUS.CLOSED;
        inhibitBackgroundImportFlag.set( true );
        executorService.shutdown();
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
            final HealthRecord healthRecord = new HealthRecord( HealthStatus.WARN, HealthTopic.Application, this.concreteClass.getName() + " error: " + lastError.toDebugStr() );
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
        final PwmApplication.AppAttribute appAttribute = wordlistConfiguration.getMetaDataAppAttribute();
        final WordlistStatus storedValue = pwmApplication.readAppAttribute( appAttribute, WordlistStatus.class );
        if ( storedValue != null )
        {
            return storedValue;
        }
        return WordlistStatus.builder().build();
    }

    void writeMetadata( final WordlistStatus metadataBean )
    {
        final PwmApplication.AppAttribute appAttribute = wordlistConfiguration.getMetaDataAppAttribute();
        pwmApplication.writeAppAttribute( appAttribute, metadataBean );
        getLogger().trace( "updated stored state: " + JsonUtil.serialize( metadataBean ) );
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
            writeMetadata( WordlistStatus.builder().build() );
            try
            {
                wordklistBucket.clear();
            }
            catch ( LocalDBException e )
            {
                throw new PwmUnrecoverableException( e.getErrorInformation() );
            }
        } );

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
            setAutoImportError( null );
            final WordlistZipReader wordlistZipReader = new WordlistZipReader( inputStream );
            final WordlistImporter wordlistImporter = new WordlistImporter(
                    null,
                    wordlistZipReader,
                    WordlistSourceType.BuiltIn,
                    AbstractWordlist.this,
                    () -> false
            );
            wordlistImporter.run();
        } );
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
            JavaHelper.pause( 10_000, 100, o -> !backgroundImportRunning.get() );
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

    class PopulateJob implements Runnable
    {
        @Override
        public void run()
        {
            try
            {
                if ( !inhibitBackgroundImportFlag.get() )
                {
                    final BooleanSupplier cancelFlag = () -> inhibitBackgroundImportFlag.get();
                    backgroundImportRunning.set( true );
                    final PopulationWorker populationWorker = new PopulationWorker( pwmApplication, AbstractWordlist.this, cancelFlag );
                    populationWorker.run();
                }
            }
            finally
            {
                backgroundImportRunning.set( false );
            }
        }
    }

}
