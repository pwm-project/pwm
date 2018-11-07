package password.pwm.svc.wordlist;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.NullOutputStream;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.ContextManager;
import password.pwm.http.client.PwmHttpClient;
import password.pwm.http.client.PwmHttpClientConfiguration;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.ChecksumInputStream;
import password.pwm.util.secure.X509Utils;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.function.BooleanSupplier;

class WordlistSource
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( WordlistSource.class );

    private final StreamProvider streamProvider;

    private WordlistSource( final StreamProvider streamProvider )
    {
        this.streamProvider = streamProvider;
    }

    private interface StreamProvider
    {
        InputStream getInputStream() throws IOException, PwmUnrecoverableException;
    }

    static WordlistSource forAutoImport( final PwmApplication pwmApplication, final WordlistConfiguration wordlistConfiguration )
    {
        return new WordlistSource( () ->
        {
            final boolean promiscuous = Boolean.parseBoolean( pwmApplication.getConfig().readAppProperty( AppProperty.HTTP_CLIENT_PROMISCUOUS_WORDLIST_ENABLE ) );
            final PwmHttpClientConfiguration pwmHttpClientConfiguration = PwmHttpClientConfiguration.builder()
                    .trustManager( promiscuous ? new X509Utils.PromiscuousTrustManager() : null )
                    .build();
            final PwmHttpClient client = new PwmHttpClient( pwmApplication, null, pwmHttpClientConfiguration );
            return client.streamForUrl( wordlistConfiguration.getAutoImportUrl() );
        }
        );
    }

    static WordlistSource forBuiltIn(
            final PwmApplication pwmApplication,
            final WordlistConfiguration wordlistConfiguration
    )
    {
        return new WordlistSource( () ->
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
        InputStream inputStream = null;

        try
        {
            final Instant startTime = Instant.now();
            LOGGER.debug( "beginning read of auto-import remote url data" );

            inputStream = this.streamProvider.getInputStream();

            final ChecksumInputStream checksumInputStream = new ChecksumInputStream( AbstractWordlist.CHECKSUM_HASH_ALG, inputStream );
            final long bytes = JavaHelper.copyWhilePredicate( checksumInputStream, new NullOutputStream(), o -> !cancelFlag.getAsBoolean() );

            if ( cancelFlag.getAsBoolean() )
            {
                return null;
            }

            final String hash = JavaHelper.binaryArrayToHex( checksumInputStream.closeAndFinalChecksum() );

            final WordlistSourceInfo wordlistSourceInfo = new WordlistSourceInfo(
                    hash,
                    bytes );

            LOGGER.debug( "completed read of wordlist data: "
                    + JsonUtil.serialize( wordlistSourceInfo )
                    + " (" + TimeDuration.fromCurrent( startTime ).asCompactString() + ")" );
            return wordlistSourceInfo;
        }
        catch ( Exception e )
        {
            final ErrorInformation errorInformation = new ErrorInformation(
                    PwmError.ERROR_INTERNAL,
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
