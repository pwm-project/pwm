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

    private WordlistSource( final WordlistSourceType wordlistSourceType, final StreamProvider streamProvider )
    {
        this.wordlistSourceType = wordlistSourceType;
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
        return new WordlistSource( WordlistSourceType.AutoImport, () ->
        {
            final String urlString = wordlistConfiguration.getAutoImportUrl();
            if ( urlString.startsWith( "http" ) )
            {
                final boolean promiscuous = Boolean.parseBoolean( pwmApplication.getConfig().readAppProperty( AppProperty.HTTP_CLIENT_PROMISCUOUS_WORDLIST_ENABLE ) );
                final PwmHttpClientConfiguration pwmHttpClientConfiguration = PwmHttpClientConfiguration.builder()
                        .trustManager( promiscuous ? new X509Utils.PromiscuousTrustManager() : null )
                        .build();
                final PwmHttpClient client = new PwmHttpClient( pwmApplication, null, pwmHttpClientConfiguration );
                return client.streamForUrl( wordlistConfiguration.getAutoImportUrl() );
            }

            try
            {
                final URL url = new URL( urlString );
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
        return new WordlistSource( WordlistSourceType.BuiltIn, () ->
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
    {
        final int buffersize = 128_1024;
        InputStream inputStream = null;

        try
        {
            final Instant startTime = Instant.now();
            LOGGER.debug( "reading file info for " + this.getWordlistSourceType() + " wordlist" );

            inputStream = this.streamProvider.getInputStream();

            final ChecksumInputStream checksumInputStream = new ChecksumInputStream( AbstractWordlist.CHECKSUM_HASH_ALG, inputStream );
            final CountingInputStream countingInputStream = new CountingInputStream( checksumInputStream );

            final ConditionalTaskExecutor debugOutputter = new ConditionalTaskExecutor(
                    () -> LOGGER.debug( "continuing reading file info for " + getWordlistSourceType() + " wordlist"
                            + " " + StringUtil.formatDiskSizeforDebug( countingInputStream.getByteCount() )
                            + " (" + TimeDuration.compactFromCurrent( startTime ) + ")" ),
                    new ConditionalTaskExecutor.TimeDurationPredicate( TimeDuration.MINUTE )
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

            final String hash = JavaHelper.binaryArrayToHex( checksumInputStream.closeAndFinalChecksum() );

            final WordlistSourceInfo wordlistSourceInfo = new WordlistSourceInfo(
                    hash,
                    bytes
            );

            LOGGER.debug( "completed read of data for " + this.getWordlistSourceType() + " wordlist"
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
            LOGGER.error( errorInformation );
        }
        finally
        {
            IOUtils.closeQuietly( inputStream );
        }
        return null;
    }
}
