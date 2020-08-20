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

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.CountingInputStream;
import org.apache.commons.io.output.NullOutputStream;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.ContextManager;
import password.pwm.svc.httpclient.PwmHttpClient;
import password.pwm.svc.httpclient.PwmHttpClientConfiguration;
import password.pwm.util.java.ConditionalTaskExecutor;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.DigestInputStream;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

class WordlistSource
{
    private static final AtomicInteger CLOSE_COUNTER = new AtomicInteger();

    private final WordlistSourceType wordlistSourceType;
    private final StreamProvider streamProvider;
    private final String importUrl;

    private WordlistSource(
            final WordlistSourceType wordlistSourceType,
            final String importUrl,
            final StreamProvider streamProvider
    )
    {
        this.wordlistSourceType = wordlistSourceType;
        this.importUrl = importUrl;
        this.streamProvider = streamProvider;
    }

    private WordlistSourceType getWordlistSourceType()
    {
        return wordlistSourceType;
    }

    private interface StreamProvider
    {
        InputStream getInputStream() throws IOException, PwmUnrecoverableException;
    }

    static WordlistSource forAutoImport( final PwmApplication pwmApplication, final WordlistConfiguration wordlistConfiguration )
    {
        final String importUrl = wordlistConfiguration.getAutoImportUrl();
        return new WordlistSource( WordlistSourceType.AutoImport, importUrl, () ->
        {
            if ( importUrl.startsWith( "http" ) )
            {
                final boolean promiscuous = Boolean.parseBoolean( pwmApplication.getConfig().readAppProperty( AppProperty.HTTP_CLIENT_PROMISCUOUS_WORDLIST_ENABLE ) );
                final PwmHttpClientConfiguration pwmHttpClientConfiguration = PwmHttpClientConfiguration.builder()
                        .trustManagerType( promiscuous ? PwmHttpClientConfiguration.TrustManagerType.promiscuous : PwmHttpClientConfiguration.TrustManagerType.defaultJava )
                        .build();
                final PwmHttpClient client = pwmApplication.getHttpClientService().getPwmHttpClient( pwmHttpClientConfiguration );
                return client.streamForUrl( wordlistConfiguration.getAutoImportUrl() );
            }

            try
            {
                final URL url = new URL( importUrl );
                return url.openStream();
            }
            catch ( final IOException e )
            {
                final String msg = "unable to open auto-import URL: " + e.getMessage();
                throw PwmUnrecoverableException.newException( PwmError.ERROR_WORDLIST_IMPORT_ERROR, msg );
            }
        }
        );
    }

    static WordlistSource forBuiltIn(
            final PwmApplication pwmApplication,
            final WordlistConfiguration wordlistConfiguration
    )
    {
        return new WordlistSource( WordlistSourceType.BuiltIn, null, () ->
        {
            final ContextManager contextManager = pwmApplication.getPwmEnvironment().getContextManager();
            final String wordlistFilename = pwmApplication.getConfig().readAppProperty( wordlistConfiguration.getBuiltInWordlistLocationProperty() );
            final InputStream inputStream;
            if ( contextManager != null )
            {
                inputStream = contextManager.getResourceAsStream( wordlistFilename );
            }
            else
            {
                inputStream = new URL( wordlistFilename ).openStream();
            }

            if ( inputStream == null )
            {
                throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_SERVICE_NOT_AVAILABLE, "unable to locate builtin wordlist file" ) );
            }
            return inputStream;
        }
        );
    }

    WordlistZipReader getZipWordlistReader()
            throws IOException, PwmUnrecoverableException
    {
        return new WordlistZipReader( this.streamProvider.getInputStream() );
    }

    WordlistSourceInfo readRemoteWordlistInfo(
            final PwmApplication pwmApplication,
            final BooleanSupplier cancelFlag,
            final PwmLogger pwmLogger
    )
            throws PwmUnrecoverableException
    {
        final Instant startTime = Instant.now();

        if ( cancelFlag.getAsBoolean() )
        {
            return null;
        }

        pwmLogger.debug( () -> "begin reading file info for " + this.getWordlistSourceType() + " wordlist" );

        final long bytes;
        final String hash;

        InputStream inputStream = null;
        DigestInputStream checksumInputStream = null;
        CountingInputStream countingInputStream = null;

        try
        {
            inputStream = this.streamProvider.getInputStream();
            checksumInputStream = pwmApplication.getSecureService().digestInputStream( WordlistConfiguration.HASH_ALGORITHM, inputStream );
            countingInputStream = new CountingInputStream( checksumInputStream );
            final ConditionalTaskExecutor debugOutputter = makeDebugLoggerExecutor( pwmLogger, startTime, countingInputStream );

            JavaHelper.copyWhilePredicate(
                    countingInputStream,
                    new NullOutputStream(),
                    WordlistConfiguration.STREAM_BUFFER_SIZE,
                    o -> !cancelFlag.getAsBoolean(),
                    debugOutputter );

            bytes = countingInputStream.getByteCount();
            hash = JavaHelper.byteArrayToHexString( checksumInputStream.getMessageDigest().digest() );
        }
        catch ( final IOException e )
        {
            final ErrorInformation errorInformation = new ErrorInformation(
                    PwmError.ERROR_WORDLIST_IMPORT_ERROR,
                    "error reading from remote wordlist auto-import url: " + JavaHelper.readHostileExceptionMessage( e )
            );
            throw new PwmUnrecoverableException( errorInformation );
        }
        finally
        {
            closeStreams( pwmLogger, countingInputStream, checksumInputStream, inputStream );
        }

        if ( cancelFlag.getAsBoolean() )
        {
            return null;
        }

        final WordlistSourceInfo wordlistSourceInfo = new WordlistSourceInfo( hash, bytes, importUrl );

        pwmLogger.debug( () -> "completed read of data for " + this.getWordlistSourceType() + " wordlist"
                + " " + StringUtil.formatDiskSizeforDebug( bytes )
                + ", " + JsonUtil.serialize( wordlistSourceInfo )
                + " (" + TimeDuration.compactFromCurrent( startTime ) + ")" );

        return wordlistSourceInfo;
    }

    private ConditionalTaskExecutor makeDebugLoggerExecutor(
            final PwmLogger pwmLogger,
            final Instant startTime,
            final CountingInputStream countingInputStream
            )
    {
        return new ConditionalTaskExecutor(
                () -> pwmLogger.debug( () -> "continuing reading file info for " + getWordlistSourceType() + " wordlist"
                                + " " + StringUtil.formatDiskSizeforDebug( countingInputStream.getByteCount() ),
                        () -> TimeDuration.fromCurrent( startTime ) ),
                new ConditionalTaskExecutor.TimeDurationPredicate( AbstractWordlist.DEBUG_OUTPUT_FREQUENCY )
        );
    }

    private void closeStreams( final PwmLogger pwmLogger, final InputStream... inputStreams )
    {
        // use a thread to close the io tasks as the close will block until the tcp socket is closed
        final Thread closerThread = new Thread( () ->
        {
            final int counter = CLOSE_COUNTER.incrementAndGet();

            final Instant startClose = Instant.now();
            pwmLogger.trace( () -> "beginning close of remote wordlist read process [" + counter + "]" );
            for ( final InputStream inputStream : inputStreams )
            {
                IOUtils.closeQuietly( inputStream );
            }
            pwmLogger.trace( () -> "completed close of remote wordlist read process [" + counter + "]",
                    () -> TimeDuration.fromCurrent( startClose ) );
        } );
        closerThread.setDaemon( true );
        closerThread.setName( Thread.currentThread().getName() + "-import-close-io" );
        closerThread.start();
    }
}
