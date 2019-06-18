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

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.CountingInputStream;
import org.apache.commons.io.output.NullOutputStream;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.ContextManager;
import password.pwm.http.client.PwmHttpClient;
import password.pwm.http.client.PwmHttpClientConfiguration;
import password.pwm.util.java.ConditionalTaskExecutor;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.ChecksumInputStream;
import password.pwm.util.secure.X509Utils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.Instant;
import java.util.function.BooleanSupplier;

class WordlistSource
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( WordlistSource.class );

    private final WordlistSourceType wordlistSourceType;
    private final StreamProvider streamProvider;
    private final String importUrl;

    private WordlistSource( final WordlistSourceType wordlistSourceType, final String importUrl, final StreamProvider streamProvider )
    {
        this.wordlistSourceType = wordlistSourceType;
        this.importUrl = importUrl;
        this.streamProvider = streamProvider;
    }

    public WordlistSourceType getWordlistSourceType()
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
                        .trustManager( promiscuous ? new X509Utils.PromiscuousTrustManager( null ) : null )
                        .build();
                final PwmHttpClient client = new PwmHttpClient( pwmApplication, null, pwmHttpClientConfiguration );
                return client.streamForUrl( wordlistConfiguration.getAutoImportUrl() );
            }

            try
            {
                final URL url = new URL( importUrl );
                return url.openStream();
            }
            catch ( IOException e )
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
            if ( contextManager != null )
            {
                final String wordlistFilename = pwmApplication.getConfig().readAppProperty( wordlistConfiguration.getBuiltInWordlistLocationProperty() );
                return contextManager.getResourceAsStream( wordlistFilename );
            }
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_SERVICE_NOT_AVAILABLE, "unable to locate builtin wordlist file" ) );
        }
        );
    }

    WordlistZipReader getZipWordlistReader()
            throws IOException, PwmUnrecoverableException
    {
        return new WordlistZipReader( this.streamProvider.getInputStream() );
    }

    WordlistSourceInfo readRemoteWordlistInfo(
            final BooleanSupplier cancelFlag
    )
            throws PwmUnrecoverableException
    {
        final int buffersize = 128_1024;
        InputStream inputStream = null;

        try
        {
            final Instant startTime = Instant.now();
            LOGGER.debug( () -> "reading file info for " + this.getWordlistSourceType() + " wordlist" );

            inputStream = this.streamProvider.getInputStream();

            final ChecksumInputStream checksumInputStream = new ChecksumInputStream( inputStream );
            final CountingInputStream countingInputStream = new CountingInputStream( checksumInputStream );

            final ConditionalTaskExecutor debugOutputter = new ConditionalTaskExecutor(
                    () -> LOGGER.debug( () -> "continuing reading file info for " + getWordlistSourceType() + " wordlist"
                            + " " + StringUtil.formatDiskSizeforDebug( countingInputStream.getByteCount() )
                            + " (" + TimeDuration.compactFromCurrent( startTime ) + ")" ),
                    new ConditionalTaskExecutor.TimeDurationPredicate( AbstractWordlist.DEBUG_OUTPUT_FREQUENCY )
            );

            final long bytes = JavaHelper.copyWhilePredicate(
                    countingInputStream,
                    new NullOutputStream(),
                    buffersize, o -> !cancelFlag.getAsBoolean(),
                    debugOutputter );

            if ( cancelFlag.getAsBoolean() )
            {
                return null;
            }

            final String hash = checksumInputStream.checksum();

            final WordlistSourceInfo wordlistSourceInfo = new WordlistSourceInfo(
                    hash,
                    bytes,
                    importUrl
            );

            LOGGER.debug( () -> "completed read of data for " + this.getWordlistSourceType() + " wordlist"
                    + " " + StringUtil.formatDiskSizeforDebug( countingInputStream.getByteCount() )
                    + ", " + JsonUtil.serialize( wordlistSourceInfo )
                    + " (" + TimeDuration.compactFromCurrent( startTime ) + ")" );
            return wordlistSourceInfo;
        }
        catch ( Exception e )
        {
            final ErrorInformation errorInformation = new ErrorInformation(
                    PwmError.ERROR_WORDLIST_IMPORT_ERROR,
                    "error reading from remote wordlist auto-import url: " + JavaHelper.readHostileExceptionMessage( e )
            );
            throw new PwmUnrecoverableException( errorInformation );
        }
        finally
        {
            IOUtils.closeQuietly( inputStream );
        }
    }
}
