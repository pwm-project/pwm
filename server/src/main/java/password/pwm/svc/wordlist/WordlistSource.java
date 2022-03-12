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

import org.apache.commons.io.IOUtils;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.bean.SessionLabel;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.ContextManager;
import password.pwm.http.HttpHeader;
import password.pwm.http.HttpMethod;
import password.pwm.svc.httpclient.PwmHttpClient;
import password.pwm.svc.httpclient.PwmHttpClientConfiguration;
import password.pwm.svc.httpclient.PwmHttpClientRequest;
import password.pwm.svc.httpclient.PwmHttpClientResponse;
import password.pwm.util.java.ConditionalTaskExecutor;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

class WordlistSource
{
    private static final Set<HttpHeader> HTTP_INTERESTED_HEADERS = Set.of(
            HttpHeader.ETag,
            HttpHeader.ContentLength,
            HttpHeader.Last_Modified );

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

    static WordlistSource forAutoImport(
            final PwmApplication pwmApplication,
            final WordlistConfiguration wordlistConfiguration
    )
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

    Map<HttpHeader, String> readRemoteHeaders(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final PwmLogger pwmLogger
    )
            throws PwmUnrecoverableException
    {
        final Instant startTime = Instant.now();

        final PwmHttpClient pwmHttpClient = pwmApplication.getHttpClientService().getPwmHttpClient();
        final PwmHttpClientRequest request = PwmHttpClientRequest.builder()
                .method( HttpMethod.HEAD )
                .url( importUrl )
                .build();
        final PwmHttpClientResponse response = pwmHttpClient.makeRequest( request, null );
        final Map<HttpHeader, String> returnResponses = new EnumMap<>( HttpHeader.class );
        for ( final Map.Entry<String, String> entry : response.getHeaders().entrySet() )
        {
            final String headerStrName = entry.getKey();
            HttpHeader.forHttpHeader( headerStrName ).ifPresent( header ->
            {
                if ( HTTP_INTERESTED_HEADERS.contains( header ) )
                {
                    returnResponses.put( header, entry.getValue() );
                }
            } );
        }

        final Map<HttpHeader, String> finalReturnResponses =  Collections.unmodifiableMap( returnResponses );
        pwmLogger.debug( sessionLabel, () -> "read remote header info for " + this.getWordlistSourceType() + " wordlist: "
                + JsonFactory.get().serializeMap( finalReturnResponses ), () -> TimeDuration.fromCurrent( startTime ) );
        return finalReturnResponses;
    }

    WordlistSourceInfo readRemoteWordlistInfo(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final BooleanSupplier cancelFlag,
            final PwmLogger pwmLogger
    )
            throws PwmUnrecoverableException
    {
        final Instant startTime = Instant.now();
        final int processId = CLOSE_COUNTER.getAndIncrement();

        if ( cancelFlag.getAsBoolean() )
        {
            throw new CancellationException();
        }

        pwmLogger.debug( sessionLabel, () -> processIdLabel( processId ) + "begin reading file info for " + this.getWordlistSourceType() + " wordlist" );

        final long bytes;
        final String hash;
        final long lines;

        InputStream inputStream = null;
        WordlistZipReader zipInputStream = null;

        try
        {
            inputStream = this.streamProvider.getInputStream();
            zipInputStream = new WordlistZipReader( inputStream );
            final ConditionalTaskExecutor debugOutputter = makeDebugLoggerExecutor( pwmLogger, processId, sessionLabel, startTime, zipInputStream );

            String nextLine;
            do
            {
                nextLine = zipInputStream.nextLine();

                if ( zipInputStream.getLineCount() % 10_000 == 0 )
                {
                    debugOutputter.conditionallyExecuteTask();
                }

                if ( cancelFlag.getAsBoolean() )
                {
                    throw new CancellationException();
                }
            }
            while ( nextLine != null );
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
            closeStreams( pwmLogger, processId, sessionLabel, inputStream );
            IOUtils.closeQuietly( zipInputStream );
        }

        bytes = zipInputStream.getByteCount();
        hash = JavaHelper.binaryArrayToHex( zipInputStream.getHash() );
        lines = zipInputStream.getLineCount();

        if ( cancelFlag.getAsBoolean() )
        {
            throw new CancellationException();
        }

        final WordlistSourceInfo wordlistSourceInfo = new WordlistSourceInfo( hash, bytes, importUrl, lines );

        pwmLogger.debug( sessionLabel, () -> processIdLabel( processId ) + "completed read of data for " + this.getWordlistSourceType()
                + " wordlist " + StringUtil.formatDiskSizeforDebug( bytes )
                + ", " + JsonFactory.get().serialize( wordlistSourceInfo )
                + " (" + TimeDuration.compactFromCurrent( startTime ) + ")" );

        return wordlistSourceInfo;
    }

    private ConditionalTaskExecutor makeDebugLoggerExecutor(
            final PwmLogger pwmLogger,
            final int processId,
            final SessionLabel sessionLabel,
            final Instant startTime,
            final WordlistZipReader wordlistZipReader
    )
    {
        final Runnable logOutputter = new Runnable()
        {
            @Override
            public void run()
            {
                pwmLogger.debug( sessionLabel, () -> processIdLabel( processId ) + "continuing reading file info for "
                                + getWordlistSourceType() + " wordlist"
                                + " " + StringUtil.formatDiskSize( wordlistZipReader.getByteCount() ) + " read"
                                + bytesPerSecondStr().orElse( "" ),
                        () -> TimeDuration.fromCurrent( startTime ) );
            }

            private Optional<String> bytesPerSecondStr()
            {
                final long bytesPerSecond = wordlistZipReader.getEventRate().intValue();
                if ( bytesPerSecond > 0 )
                {
                    return Optional.of( ", (" + StringUtil.formatDiskSize( bytesPerSecond ) + "/second)" );
                }
                return Optional.empty();
            }
        };

        return ConditionalTaskExecutor.forPeriodicTask(
                logOutputter,
                AbstractWordlist.DEBUG_OUTPUT_FREQUENCY.asDuration() );
    }

    private void closeStreams(
            final PwmLogger pwmLogger,
            final int processId,
            final SessionLabel sessionLabel,
            final InputStream... inputStreams )
    {
        final Instant startClose = Instant.now();
        pwmLogger.trace( sessionLabel, () -> processIdLabel( processId ) + "beginning close of remote wordlist read process" );
        for ( final InputStream inputStream : inputStreams )
        {
            IOUtils.closeQuietly( inputStream );
        }
        pwmLogger.trace( sessionLabel, () -> processIdLabel( processId ) + "completed close of remote wordlist read process",
                () -> TimeDuration.fromCurrent( startClose ) );
    }

    private String processIdLabel( final int processId )
    {
        return "<" + processId + "> ";
    }
}
