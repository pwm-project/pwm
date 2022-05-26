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

package password.pwm.util.secure;

import password.pwm.PwmConstants;
import password.pwm.PwmDomain;
import password.pwm.bean.SessionLabel;
import password.pwm.config.AppConfig;
import password.pwm.config.option.CertificateMatchingMode;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmInternalException;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpMethod;
import password.pwm.http.PwmURL;
import password.pwm.svc.httpclient.PwmHttpClient;
import password.pwm.svc.httpclient.PwmHttpClientConfiguration;
import password.pwm.svc.httpclient.PwmHttpClientRequest;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 *
 */
public class X509Utils
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( X509Utils.class );

    public enum ReadCertificateFlag
    {
        ReadOnlyRootCA
    }

    public enum DebugInfoFlag
    {
        IncludeCertificateDetail
    }


    enum KeyDebugInfoKey
    {
        algorithm,
        format,
    }

    private X509Utils()
    {
    }

    public static List<X509Certificate> readRemoteCertificates(
            final URI uri,
            final AppConfig appConfig
    )
            throws PwmOperationalException
    {
        final String host = uri.getHost();
        final int port = PwmURL.portForUriSchema( uri );

        return readRemoteCertificates( host, port, appConfig );
    }

    public static List<X509Certificate> readRemoteCertificates(
            final String host,
            final int port,
            final AppConfig appConfig
    )
            throws PwmOperationalException
    {
        LOGGER.debug( () -> "ServerCertReader: beginning certificate read procedure to import certificates from host=" + host + ", port=" + port );
        final CertificateReadingTrustManager certReaderTm = CertificateReadingTrustManager.newCertReaderTrustManager(
                appConfig,
                readCertificateFlagsFromConfig( appConfig ) );

        try
        {
            // use custom trust manager to read the certificates
            final SSLContext ctx = SSLContext.getInstance( "TLS" );
            ctx.init( null, new TrustManager[]
                            {
                                    certReaderTm,
                            },
                    new SecureRandom() );
            final SSLSocketFactory factory = ctx.getSocketFactory();
            final SSLSocket sslSock = ( SSLSocket ) factory.createSocket( host, port );
            LOGGER.debug( () -> "ServerCertReader: socket established to host=" + host + ", port=" + port );
            if ( !sslSock.isConnected() )
            {
                throw PwmUnrecoverableException.newException( PwmError.ERROR_CERTIFICATE_ERROR, "unable to connect to " + host + ":" + port );
            }
            LOGGER.debug( () -> "ServerCertReader: connected to host=" + host + ", port=" + port );
            sslSock.startHandshake();
            LOGGER.debug( () -> "ServerCertReader: handshake completed to host=" + host + ", port=" + port );
            sslSock.close();
            LOGGER.debug( () -> "ServerCertReader: certificate information read from host=" + host + ", port=" + port );
        }
        catch ( final Exception e )
        {
            final StringBuilder errorMsg = new StringBuilder();
            errorMsg.append( "unable to read server certificates from host=" );
            errorMsg.append( host ).append( ", port=" ).append( port );
            errorMsg.append( " error: " );
            errorMsg.append( JavaHelper.readHostileExceptionMessage( e ) );
            LOGGER.error( () -> "ServerCertReader: " + errorMsg );
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_CERTIFICATE_ERROR, errorMsg.toString(), new String[]
                    {
                            errorMsg.toString(),
                    }
            );
            throw new PwmOperationalException( errorInformation );
        }

        final List<X509Certificate> certs;
        try
        {
            certs = certReaderTm.getCertificates();
        }
        catch ( final PwmUnrecoverableException e )
        {
            throw new PwmOperationalException( e.getErrorInformation() );
        }

        if ( CollectionUtil.isEmpty( certs ) )
        {
            LOGGER.debug( () -> "ServerCertReader: unable to read certificates: null returned from CertReaderTrustManager.getCertificates()" );
        }
        else
        {
            for ( final X509Certificate certificate : certs )
            {
                LOGGER.debug( () -> "ServerCertReader: read x509 Certificate from host=" + host + ", port=" + port + ": \n" + certificate.toString() );
            }
        }
        LOGGER.debug( () -> "ServerCertReader: process completed" );
        return certs == null ? Collections.emptyList() : certs;
    }

    public static List<X509Certificate> readRemoteHttpCertificates(
            final PwmDomain pwmDomain,
            final SessionLabel sessionLabel,
            final URI uri
    )
            throws PwmUnrecoverableException
    {
        final PwmHttpClientConfiguration pwmHttpClientConfiguration = PwmHttpClientConfiguration.builder()
                .trustManagerType( PwmHttpClientConfiguration.TrustManagerType.promiscuousCertReader )
                .build();
        final PwmHttpClient pwmHttpClient = pwmDomain.getHttpClientService().getPwmHttpClient( pwmHttpClientConfiguration, sessionLabel );
        final PwmHttpClientRequest request = PwmHttpClientRequest.builder()
                .method( HttpMethod.GET )
                .url( uri.toString() )
                .build();

        LOGGER.debug( sessionLabel, () -> "beginning attempt to import certificates via httpclient" );

        ErrorInformation requestError = null;
        try
        {
            pwmHttpClient.makeRequest( request );
        }
        catch ( final PwmException e )
        {
            requestError = e.getErrorInformation();
        }

        if ( pwmHttpClient.readServerCertificates() != null )
        {
            return pwmHttpClient.readServerCertificates();
        }

        {
            final ErrorInformation finalError = requestError;
            LOGGER.debug( sessionLabel, () -> "unable to read certificates from remote server via httpclient, error: " + finalError );
        }

        if ( requestError == null )
        {
            final String msg = "unable to read certificates via httpclient; check log files for more details";
            throw PwmUnrecoverableException.newException( PwmError.ERROR_CERTIFICATE_ERROR, msg );
        }

        throw new PwmUnrecoverableException( requestError );
    }

    private static ReadCertificateFlag[] readCertificateFlagsFromConfig( final AppConfig appConfig )
    {
        final CertificateMatchingMode mode = appConfig.readCertificateMatchingMode();
        return mode == CertificateMatchingMode.CA_ONLY
                ? Collections.singletonList( X509Utils.ReadCertificateFlag.ReadOnlyRootCA ).toArray( new X509Utils.ReadCertificateFlag[0] )
                : new X509Utils.ReadCertificateFlag[0];
    }

    public static boolean testIfLdapServerCertsInDefaultKeystore( final URI serverURI )
    {
        final String ldapHost = serverURI.getHost();
        final int ldapPort = serverURI.getPort();

        try
        {
            // use default socket factory to test if certs work with it
            final SSLSocketFactory factory = ( SSLSocketFactory ) SSLSocketFactory.getDefault();
            final SSLSocket sslSock = ( SSLSocket ) factory.createSocket( ldapHost, ldapPort );
            if ( !sslSock.isConnected() )
            {
                throw PwmUnrecoverableException.newException( PwmError.ERROR_CERTIFICATE_ERROR, "unable to connect to " + serverURI );
            }
            try ( OutputStream outputStream = sslSock.getOutputStream() )
            {
                outputStream.write( "data!".getBytes( PwmConstants.DEFAULT_CHARSET ) );
            }
            sslSock.close();
            return true;
        }
        catch ( final Exception e )
        {
            LOGGER.trace( () -> "exception while testing ldap server cert validity against default keystore: " + e.getMessage() );
        }

        return false;
    }

    public static String hexSerial( final X509Certificate x509Certificate )
    {
        final StringBuilder result = new StringBuilder( x509Certificate.getSerialNumber().toString( 16 ).toUpperCase() );
        while ( result.length() % 2 != 0 )
        {
            result.insert( 0, "0" );
        }
        return result.toString();
    }

    public static String makeDetailText( final X509Certificate x509Certificate )
    {
        final StringBuilder sb = new StringBuilder();
        X509CertInfo.makeDebugInfoMapImpl( x509Certificate ).forEach( ( key, value ) ->
        {
            sb.append( key );
            sb.append( ": " );
            sb.append( value );
            sb.append( "\n" );
        } );

        sb.append( x509Certificate );
        sb.append( "\n" );
        return sb.toString();
    }

    public static String makeDebugTexts( final List<X509Certificate> x509Certificates )
    {
        return x509Certificates.stream()
                .map( x509Certificate -> '[' + makeDebugText( x509Certificate ) + ']' )
                .collect( Collectors.joining( "", "Certificates: ", "" ) );
    }

    public static String makeDebugText( final X509Certificate x509Certificate )
    {
        return "subject=" + X509CertDataParser.readCertSubject( x509Certificate ) + ", serial=" + x509Certificate.getSerialNumber();
    }

    public static List<X509Certificate> certificatesFromBase64s( final Collection<String> b64certificates )
    {
        final Function<String, Optional<X509Certificate>> mapFunction = s ->
        {
            try
            {
                return Optional.of( certificateFromBase64( s ) );
            }
            catch ( final Exception e )
            {
                LOGGER.error( () -> "error decoding certificate from b64: " + e.getMessage() );
            }
            return Optional.empty();
        };

        return b64certificates
                .stream()
                .map( mapFunction )
                .flatMap( Optional::stream )
                .collect( Collectors.toList() );
    }

    public static List<String> certificatesToBase64s( final Collection<X509Certificate> certificates )
    {
        final Function<X509Certificate, Optional<String>> mapFunction = s ->
        {
            try
            {
                return Optional.of( certificateToBase64( s ) );
            }
            catch ( final Exception e )
            {
                LOGGER.error( () -> "error encoding certificate to b64: " + e.getMessage() );
            }
            return Optional.empty();
        };

        return certificates
                .stream()
                .map( mapFunction )
                .flatMap( Optional::stream )
                .collect( Collectors.toList() );
    }

    public static void outputKeystore(
            final KeyStore keyStore,
            final File keyStoreFile,
            final String password
    )
            throws CertificateException, KeyStoreException, IOException, NoSuchAlgorithmException
    {
        final ByteArrayOutputStream outputContents = new ByteArrayOutputStream();
        keyStore.store( outputContents, password.toCharArray() );

        if ( keyStoreFile.exists() )
        {
            Files.delete( keyStoreFile.toPath() );
        }

        try ( OutputStream fileOutputStream = Files.newOutputStream( keyStoreFile.toPath() ) )
        {
            fileOutputStream.write( outputContents.toByteArray() );
        }
    }


    public static X509Certificate certificateFromBase64( final String b64encodedStr )
            throws CertificateException, IOException
    {
        final CertificateFactory certificateFactory = CertificateFactory.getInstance( "X.509" );
        final byte[] certByteValue = StringUtil.base64Decode( b64encodedStr );
        return ( X509Certificate ) certificateFactory.generateCertificate( new ByteArrayInputStream( certByteValue ) );
    }

    public static String certificateToBase64( final X509Certificate certificate )
            throws CertificateEncodingException, PwmUnrecoverableException
    {
        return StringUtil.base64Encode( certificate.getEncoded() );
    }

    static Optional<List<X509Certificate>> extractRootCaCertificates(
            final List<X509Certificate> certificates
    )
    {
        Objects.requireNonNull( certificates );

        final List<X509Certificate> returnList = new ArrayList<>( );

        for ( final X509Certificate certificate : certificates )
        {
            if ( X509CertDataParser.certIsSigningKey( certificate ) )
            {
                returnList.add( certificate );
            }
        }

        if ( !returnList.isEmpty() )
        {
            return Optional.of( Collections.unmodifiableList( returnList ) );
        }

        return Optional.empty();
    }

    public static TrustManager[] getDefaultJavaTrustManager( final AppConfig appConfig )
            throws PwmUnrecoverableException
    {
        try
        {
            final TrustManagerFactory tmf = TrustManagerFactory.getInstance( TrustManagerFactory.getDefaultAlgorithm() );
            tmf.init( (KeyStore) null );
            return tmf.getTrustManagers();
        }
        catch ( final GeneralSecurityException e )
        {
            final String errorMsg = "unexpected error loading default java TrustManager: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INTERNAL, errorMsg );
            throw new PwmUnrecoverableException( errorInformation );
        }
    }

    public static String hash( final X509Certificate certificate, final PwmHashAlgorithm pwmHashAlgorithm )
    {
        try
        {
            return SecureEngine.hash( new ByteArrayInputStream( certificate.getEncoded() ), pwmHashAlgorithm );
        }
        catch ( final CertificateEncodingException e )
        {
            throw new PwmInternalException( new ErrorInformation( PwmError.ERROR_INTERNAL,
                    "unexpected error encoding certificate: " + e.getMessage() ) );
        }
    }

    public static Set<X509Certificate> readCertsForListOfLdapUrls( final List<String> ldapUrls, final AppConfig appConfig )
            throws PwmUnrecoverableException
    {
        final Set<X509Certificate> resultCertificates = new LinkedHashSet<>();
        try
        {
            for ( final String ldapUrlString : ldapUrls )
            {
                final URI ldapURI = new URI( ldapUrlString );
                final List<X509Certificate> certs = X509Utils.readRemoteCertificates( ldapURI, appConfig );
                if ( certs != null )
                {
                    resultCertificates.addAll( certs );
                }
            }
        }
        catch ( final Exception e )
        {
            if ( e instanceof PwmException )
            {
                throw new PwmUnrecoverableException( ( ( PwmException ) e ).getErrorInformation() );
            }
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INTERNAL, "error importing certificates: " + e.getMessage() );
            throw new PwmUnrecoverableException( errorInformation );
        }
        return Collections.unmodifiableSet( resultCertificates );
    }
}
