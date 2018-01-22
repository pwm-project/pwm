/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
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

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.NullOutputStream;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthMessage;
import password.pwm.health.HealthRecord;
import password.pwm.health.HealthStatus;
import password.pwm.health.HealthTopic;
import password.pwm.http.ContextManager;
import password.pwm.http.client.PwmHttpClient;
import password.pwm.http.client.PwmHttpClientConfiguration;
import password.pwm.svc.PwmService;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBException;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.ChecksumInputStream;
import password.pwm.util.secure.PwmHashAlgorithm;
import password.pwm.util.secure.SecureEngine;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

abstract class AbstractWordlist implements Wordlist, PwmService
{

    static final PwmHashAlgorithm CHECKSUM_HASH_ALG = PwmHashAlgorithm.SHA1;

    protected WordlistConfiguration wordlistConfiguration;

    protected volatile STATUS wlStatus = STATUS.NEW;
    protected LocalDB localDB;

    protected PwmLogger logger = PwmLogger.forClass( AbstractWordlist.class );
    protected String debugLabel = "Generic Word List";

    protected int storedSize = 0;
    protected boolean debugTrace;

    private ErrorInformation lastError;
    private ErrorInformation autoImportError;

    private PwmApplication pwmApplication;
    protected Populator populator;

    private ScheduledExecutorService executorService;
    private PopulationManager populationManager = new PopulationManager();


    protected AbstractWordlist( )
    {
    }

    public void init( final PwmApplication pwmApplication ) throws PwmException
    {
        this.pwmApplication = pwmApplication;
        this.localDB = pwmApplication.getLocalDB();
        if ( pwmApplication.getConfig().isDevDebugMode() )
        {
            debugTrace = true;
        }

        executorService = Executors.newSingleThreadScheduledExecutor(
                JavaHelper.makePwmThreadFactory(
                        JavaHelper.makeThreadName( pwmApplication, this.getClass() ) + "-",
                        true
                ) );
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

    protected final void backgroundStartup( )
    {
        executorService.schedule( ( ) ->
        {
            try
            {
                startup();
            }
            catch ( Exception e )
            {
                logger.warn( "error during startup: " + e.getMessage() );
            }
        }, 0, TimeUnit.MILLISECONDS );
    }


    protected final void startup( )
    {
        wlStatus = STATUS.OPENING;

        if ( localDB == null )
        {
            final String errorMsg = "LocalDB is not available, " + debugLabel + " will remain closed";
            logger.warn( errorMsg );
            lastError = new ErrorInformation( PwmError.ERROR_SERVICE_NOT_AVAILABLE, errorMsg );
            close();
            return;
        }

        try
        {
            populationManager.checkPopulation();
        }
        catch ( Exception e )
        {
            final String errorMsg = "unexpected error while examining wordlist db: " + e.getMessage();
            if ( ( e instanceof PwmUnrecoverableException ) || ( e instanceof NullPointerException ) || ( e instanceof LocalDBException ) )
            {
                logger.warn( errorMsg );
            }
            else
            {
                logger.warn( errorMsg, e );
            }
            lastError = new ErrorInformation( PwmError.ERROR_SERVICE_NOT_AVAILABLE, errorMsg );
            populator = null;
            close();
            return;
        }

        //read stored size
        storedSize = readMetadata().getSize();
        wlStatus = STATUS.OPEN;
    }

    String normalizeWord( final String input )
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

    public boolean containsWord( final String word )
    {
        if ( wlStatus != STATUS.OPEN )
        {
            return false;
        }

        final String testWord = normalizeWord( word );

        if ( testWord == null || testWord.length() < 1 )
        {
            return false;
        }


        final Set<String> testWords = chunkWord( testWord, this.wordlistConfiguration.getCheckSize() );

        final Instant startTime = Instant.now();
        try
        {
            boolean result = false;
            for ( final String t : testWords )
            {
                if ( !result )
                {
                    // stop checking once found
                    if ( localDB.contains( getWordlistDB(), t ) )
                    {
                        result = true;
                    }
                }
            }
            final TimeDuration timeDuration = TimeDuration.fromCurrent( startTime );
            if ( timeDuration.isLongerThan( 100 ) )
            {
                logger.debug( "wordlist search time for " + testWords.size() + " wordlist permutations was greater then 100ms: " + timeDuration.asCompactString() );
            }
            return result;
        }
        catch ( Exception e )
        {
            logger.error( "database error checking for word: " + e.getMessage() );
        }

        return false;
    }

    public int size( )
    {
        if ( populator != null )
        {
            return 0;
        }

        return storedSize;
    }

    public synchronized void close( )
    {
        if ( populator != null )
        {
            try
            {
                populator.cancel();
                populator = null;
            }
            catch ( PwmUnrecoverableException e )
            {
                logger.error( "wordlist populator failed to exit" );
            }
        }

        executorService.shutdown();
        wlStatus = STATUS.CLOSED;
        localDB = null;
    }

    public STATUS status( )
    {
        return wlStatus;
    }

    public String getDebugStatus( )
    {
        if ( wlStatus == STATUS.OPENING && populator != null )
        {
            return populator.makeStatString();
        }
        else
        {
            return wlStatus.toString();
        }
    }

    protected abstract Map<String, String> getWriteTxnForValue( String value );

    protected abstract PwmApplication.AppAttribute getMetaDataAppAttribute( );

    protected abstract AppProperty getBuiltInWordlistLocationProperty( );

    protected abstract LocalDB.DB getWordlistDB( );

    protected abstract PwmSetting getWordlistFileSetting( );

    public List<HealthRecord> healthCheck( )
    {
        final List<HealthRecord> returnList = new ArrayList<>();

        if ( autoImportError != null )
        {
            final HealthRecord healthRecord = HealthRecord.forMessage( HealthMessage.Wordlist_AutoImportFailure,
                    this.getWordlistFileSetting().toMenuLocationDebug( null, PwmConstants.DEFAULT_LOCALE ),
                    autoImportError.getDetailedErrorMsg(),
                    JavaHelper.toIsoDate( autoImportError.getDate() )
            );
            returnList.add( healthRecord );
        }

        if ( wlStatus == STATUS.OPENING )
        {
            final HealthRecord healthRecord = new HealthRecord( HealthStatus.CAUTION, HealthTopic.Application, this.debugLabel + " is not yet open: " + this.getDebugStatus() );
            returnList.add( healthRecord );
        }

        if ( lastError != null )
        {
            final HealthRecord healthRecord = new HealthRecord( HealthStatus.WARN, HealthTopic.Application, this.debugLabel + " error: " + lastError.toDebugStr() );
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
            return new ServiceInfoBean( Collections.<DataStorageMethod>emptyList() );
        }
    }

    protected Set<String> chunkWord( final String input, final int size )
    {
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

    protected String readAutoImportUrl( )
    {
        final String inputUrl = pwmApplication.getConfig().readSettingAsString( getWordlistFileSetting() );

        if ( inputUrl == null || inputUrl.isEmpty() )
        {
            return null;
        }

        if ( !inputUrl.startsWith( "http:" ) && !inputUrl.startsWith( "https:" ) && !inputUrl.startsWith( "file:" ) )
        {
            logger.debug( "assuming configured auto-import url is a file url; derived url is " + inputUrl );
            return "file:" + inputUrl;
        }

        return inputUrl;
    }

    public StoredWordlistDataBean readMetadata( )
    {
        final StoredWordlistDataBean storedValue = pwmApplication.readAppAttribute( getMetaDataAppAttribute(), StoredWordlistDataBean.class );
        if ( storedValue != null )
        {
            return storedValue;
        }
        return StoredWordlistDataBean.builder().build();
    }

    void writeMetadata( final StoredWordlistDataBean metadataBean )
    {
        pwmApplication.writeAppAttribute( getMetaDataAppAttribute(), metadataBean );
        logger.trace( "updated stored state: " + JsonUtil.serialize( metadataBean ) );
    }

    @Override
    public void populate( final InputStream inputStream )
            throws IOException, PwmUnrecoverableException
    {
        try
        {
            populationManager.populateImpl( inputStream, StoredWordlistDataBean.Source.User );
        }
        finally
        {
            if ( !readMetadata().isCompleted() )
            {
                logger.debug( "beginning population using builtin source in background thread" );
                final Thread t = new Thread( ( ) ->
                {
                    try
                    {
                        populationManager.populateBuiltIn();
                    }
                    catch ( Exception e )
                    {
                        logger.warn( "unexpected error during builtin source population process: " + e.getMessage(), e );
                    }
                    populator = null;
                }, JavaHelper.makeThreadName( pwmApplication, WordlistManager.class ) );
                t.setDaemon( true );
                t.start();
            }
        }
    }

    @Override
    public void clear( ) throws IOException, PwmUnrecoverableException
    {
        if ( wlStatus == STATUS.OPEN )
        {
            executorService.schedule( ( ) ->
            {
                try
                {
                    writeMetadata( StoredWordlistDataBean.builder().build() );
                    populationManager.checkPopulation();
                }
                catch ( Exception e )
                {
                    logger.error( "error during clear operation: " + e.getMessage() );
                }
            }, 0, TimeUnit.MILLISECONDS );
        }
    }

    private class PopulationManager
    {
        protected void checkPopulation( )
                throws Exception
        {
            final boolean autoImportUrlConfigured = wordlistConfiguration.getAutoImportUrl() != null;

            if ( autoImportUrlConfigured )
            {
                final String remoteHash = readImportUrlHash();
                if ( remoteHash != null )
                {
                    if ( !remoteHash.equals( readMetadata().getSha1hash() ) )
                    {
                        logger.debug( "auto-import url remote hash does not equal currently stored hash, will start auto-import" );
                        populateAutoImport();
                    }
                }

                if ( autoImportError != null )
                {
                    final int retrySeconds = Integer.parseInt( pwmApplication.getConfig().readAppProperty( AppProperty.APPLICATION_WORDLIST_RETRY_SECONDS ) );
                    logger.error( "auto-import of remote wordlist failed, will retry in " + ( new TimeDuration( retrySeconds, TimeUnit.SECONDS ).asCompactString() ) );
                    executorService.schedule( ( ) ->
                    {
                        try
                        {
                            logger.debug( "attempting wordlist remote import" );
                            checkPopulation();
                        }
                        catch ( Exception e )
                        {
                            logger.error( "error during auto-import retry: " + e.getMessage() );
                        }

                    }, retrySeconds, TimeUnit.SECONDS );
                }
            }
            else
            {
                if ( readMetadata().getSource() == StoredWordlistDataBean.Source.AutoImport )
                {
                    logger.trace( "source list is from auto-import, but not currently configured for auto-import; clearing stored data" );

                    // clear previous auto-import wl
                    writeMetadata( StoredWordlistDataBean.builder().build() );
                }
            }

            boolean needsBuiltinPopulating = false;
            if ( !readMetadata().isCompleted() )
            {
                needsBuiltinPopulating = true;
                logger.debug( "wordlist stored in database does not have a completed load status, will load built-in wordlist" );
            }
            else if ( StoredWordlistDataBean.Source.BuiltIn == readMetadata().getSource() )
            {
                final String builtInWordlistHash = getBuiltInWordlistHash();
                if ( !builtInWordlistHash.equals( readMetadata().getSha1hash() ) )
                {
                    logger.debug( "wordlist stored in database does not have match checksum with built-in wordlist file, will load built-in wordlist" );
                    needsBuiltinPopulating = true;
                }
            }

            if ( !needsBuiltinPopulating )
            {
                return;
            }

            this.populateBuiltIn();
        }

        protected void populateBuiltIn( )
                throws IOException, PwmUnrecoverableException
        {
            populateImpl( getBuiltInWordlist(), StoredWordlistDataBean.Source.BuiltIn );
        }

        private void populateImpl( final InputStream inputStream, final StoredWordlistDataBean.Source source )
                throws IOException, PwmUnrecoverableException
        {
            if ( inputStream == null )
            {
                throw new NullPointerException( "input stream can not be null for populateImpl()" );
            }

            if ( wlStatus == STATUS.CLOSED )
            {
                return;
            }

            wlStatus = STATUS.OPENING;

            try
            {
                if ( populator != null )
                {
                    populator.cancel();

                    final int maxWaitMs = 1000 * 30;
                    final Instant startWaitTime = Instant.now();
                    while ( populator.isRunning() && TimeDuration.fromCurrent( startWaitTime ).isShorterThan( maxWaitMs ) )
                    {
                        JavaHelper.pause( 1000 );
                    }
                    if ( populator.isRunning() && TimeDuration.fromCurrent( startWaitTime ).isShorterThan( maxWaitMs ) )
                    {
                        throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_UNKNOWN, "unable to abort populator" ) );
                    }
                }

                {
                    // reset the wordlist metadata
                    final StoredWordlistDataBean storedWordlistDataBean = StoredWordlistDataBean.builder()
                            .source( source )
                            .build();
                    writeMetadata( storedWordlistDataBean );
                }

                populator = new Populator( inputStream, source, AbstractWordlist.this, pwmApplication );
                populator.populate();
            }
            catch ( Exception e )
            {
                final ErrorInformation populationError;
                populationError = e instanceof PwmException
                        ? ( ( PwmException ) e ).getErrorInformation()
                        : new ErrorInformation( PwmError.ERROR_UNKNOWN, e.getMessage() );
                logger.error( "error during wordlist population: " + populationError.toDebugStr() );
                throw new PwmUnrecoverableException( populationError );
            }
            finally
            {
                populator = null;
                IOUtils.closeQuietly( inputStream );
            }

            wlStatus = STATUS.OPEN;
        }

        protected InputStream getBuiltInWordlist( ) throws FileNotFoundException, PwmUnrecoverableException
        {
            final ContextManager contextManager = pwmApplication.getPwmEnvironment().getContextManager();
            if ( contextManager != null )
            {
                final String wordlistFilename = pwmApplication.getConfig().readAppProperty( getBuiltInWordlistLocationProperty() );
                return contextManager.getResourceAsStream( wordlistFilename );
            }
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_SERVICE_NOT_AVAILABLE, "unable to locate builtin wordlist file" ) );
        }

        protected String getBuiltInWordlistHash( ) throws IOException, PwmUnrecoverableException
        {

            try ( InputStream inputStream = getBuiltInWordlist() )
            {
                return SecureEngine.hash( inputStream, CHECKSUM_HASH_ALG );
            }
        }

        public boolean populateAutoImport( )
                throws IOException, PwmUnrecoverableException
        {
            autoImportError = null;
            InputStream inputStream = null;
            try
            {
                inputStream = autoImportInputStream();
                populateImpl( inputStream, StoredWordlistDataBean.Source.AutoImport );
                return true;
            }
            catch ( Exception e )
            {
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_UNKNOWN, "error during remote wordlist import: " + e.getMessage() );
                logger.error( errorInformation );
                autoImportError = errorInformation;
            }
            finally
            {
                IOUtils.closeQuietly( inputStream );
            }
            return false;
        }

        String readImportUrlHash( )
        {
            InputStream inputStream = null;
            try
            {
                final Instant startTime = Instant.now();
                logger.debug( "beginning read of auto-import wordlist url hash checksum from '" + wordlistConfiguration.getAutoImportUrl() + "'" );
                inputStream = autoImportInputStream();
                final ChecksumInputStream checksumInputStream = new ChecksumInputStream( CHECKSUM_HASH_ALG, inputStream );
                JavaHelper.copyWhilePredicate( checksumInputStream, new NullOutputStream(), o -> wlStatus != STATUS.CLOSED );
                IOUtils.copy( checksumInputStream, new NullOutputStream() );
                final String hash = JavaHelper.binaryArrayToHex( checksumInputStream.closeAndFinalChecksum() );
                logger.debug( "completed read of auto-import wordlist url hash, value=" + hash + " (" + TimeDuration.fromCurrent( startTime ).asCompactString() + ")" );
                autoImportError = null;
                return hash;
            }
            catch ( Exception e )
            {
                final ErrorInformation errorInformation = new ErrorInformation(
                        PwmError.ERROR_UNKNOWN,
                        "error reading from remote wordlist auto-import url: " + JavaHelper.readHostileExceptionMessage( e )
                );
                logger.error( errorInformation );
                autoImportError = errorInformation;
            }
            finally
            {
                IOUtils.closeQuietly( inputStream );
            }
            return null;
        }

        private InputStream autoImportInputStream( ) throws IOException, PwmUnrecoverableException
        {
            final boolean promiscuous = Boolean.parseBoolean( pwmApplication.getConfig().readAppProperty( AppProperty.HTTP_CLIENT_PROMISCUOUS_WORDLIST_ENABLE ) );
            final PwmHttpClientConfiguration pwmHttpClientConfiguration = PwmHttpClientConfiguration.builder()
                    .promiscuous( promiscuous )
                    .build();
            final PwmHttpClient client = new PwmHttpClient( pwmApplication, null, pwmHttpClientConfiguration );
            return client.streamForUrl( wordlistConfiguration.getAutoImportUrl() );
        }


    }

}
