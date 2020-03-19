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

package password.pwm.util.secure;

import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.SessionLabel;
import password.pwm.config.Configuration;
import password.pwm.config.option.CertificateMatchingMode;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpMethod;
import password.pwm.http.PwmURL;
import password.pwm.svc.httpclient.PwmHttpClient;
import password.pwm.svc.httpclient.PwmHttpClientConfiguration;
import password.pwm.svc.httpclient.PwmHttpClientRequest;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 *
 */
public class X509Utils
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( X509Utils.class );

    private X509Utils()
    {
    }


    public static List<X509Certificate> readRemoteCertificates(
            final URI uri,
            final Configuration configuration
    )
            throws PwmOperationalException
    {
        final String host = uri.getHost();
        final int port = PwmURL.portForUriSchema( uri );

        return readRemoteCertificates( host, port, configuration );
    }

    public static List<X509Certificate> readRemoteCertificates(
            final String host,
            final int port,
            final Configuration configuration
    )
            throws PwmOperationalException
    {
        LOGGER.debug( () -> "ServerCertReader: beginning certificate read procedure to import certificates from host=" + host + ", port=" + port );
        final CertificateReadingTrustManager certReaderTm = CertificateReadingTrustManager.newCertReaderTrustManager(
                configuration,
                readCertificateFlagsFromConfig( configuration ) );

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

        if ( JavaHelper.isEmpty( certs ) )
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
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final URI uri
    )
            throws PwmUnrecoverableException
    {
        final PwmHttpClientConfiguration pwmHttpClientConfiguration = PwmHttpClientConfiguration.builder()
                .trustManagerType( PwmHttpClientConfiguration.TrustManagerType.promiscuousCertReader )
                .build();
        final PwmHttpClient pwmHttpClient = pwmApplication.getHttpClientService().getPwmHttpClient( pwmHttpClientConfiguration );
        final PwmHttpClientRequest request = PwmHttpClientRequest.builder()
                .method( HttpMethod.GET )
                .url( uri.toString() )
                .build();

        LOGGER.debug( sessionLabel, () -> "beginning attempt to import certificates via httpclient" );

        ErrorInformation requestError = null;
        try
        {
            pwmHttpClient.makeRequest( request, sessionLabel );
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

    private static ReadCertificateFlag[] readCertificateFlagsFromConfig( final Configuration configuration )
    {
        final CertificateMatchingMode mode = configuration.readCertificateMatchingMode();
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
        String result = x509Certificate.getSerialNumber().toString( 16 ).toUpperCase();
        while ( result.length() % 2 != 0 )
        {
            result = "0" + result;
        }
        return result;
    }

    public static String makeDetailText( final X509Certificate x509Certificate )
            throws CertificateEncodingException, PwmUnrecoverableException
    {
        return x509Certificate.toString()
                + "\nMD5: " + hash( x509Certificate, PwmHashAlgorithm.MD5 )
                + "\nSHA1: " + hash( x509Certificate, PwmHashAlgorithm.SHA1 )
                + "\nSHA2-256: " + hash( x509Certificate, PwmHashAlgorithm.SHA256 )
                + "\nSHA2-512: " + hash( x509Certificate, PwmHashAlgorithm.SHA512 )
                + "\n:IsRootCA: " + certIsRootCA( x509Certificate );
    }

    public static String makeDebugTexts( final List<X509Certificate> x509Certificates )
    {
        final StringBuilder sb = new StringBuilder();
        sb.append( "Certificates: " );
        for ( final X509Certificate x509Certificate : x509Certificates )
        {
            sb.append( "[" );
            sb.append( makeDebugText( x509Certificate ) );
            sb.append( "]" );

        }
        return sb.toString();
    }

    public static String makeDebugText( final X509Certificate x509Certificate )
    {
        return "subject=" + x509Certificate.getSubjectDN().getName() + ", serial=" + x509Certificate.getSerialNumber();
    }

    enum CertDebugInfoKey
    {
        subject,
        serial,
        issuer,
        issueDate,
        expireDate,
        md5Hash,
        sha1Hash,
        sha512Hash,
        detail,
    }

    public enum ReadCertificateFlag
    {
        ReadOnlyRootCA
    }

    public enum DebugInfoFlag
    {
        IncludeCertificateDetail
    }

    public static List<Map<String, String>> makeDebugInfoMap( final List<X509Certificate> certificates, final DebugInfoFlag... flags )
    {
        final List<Map<String, String>> returnList = new ArrayList<>();
        if ( certificates != null )
        {
            for ( final X509Certificate cert : certificates )
            {
                returnList.add( makeDebugInfoMap( cert, flags ) );
            }
        }
        return returnList;
    }

    public static Map<String, String> makeDebugInfoMap( final X509Certificate cert, final DebugInfoFlag... flags )
    {
        final Map<String, String> returnMap = new LinkedHashMap<>();
        returnMap.put( CertDebugInfoKey.subject.toString(), cert.getSubjectDN().toString() );
        returnMap.put( CertDebugInfoKey.serial.toString(), X509Utils.hexSerial( cert ) );
        returnMap.put( CertDebugInfoKey.issuer.toString(), cert.getIssuerDN().toString() );
        returnMap.put( CertDebugInfoKey.issueDate.toString(), JavaHelper.toIsoDate( cert.getNotBefore() ) );
        returnMap.put( CertDebugInfoKey.expireDate.toString(), JavaHelper.toIsoDate( cert.getNotAfter() ) );
        try
        {
            returnMap.put( CertDebugInfoKey.md5Hash.toString(), hash( cert, PwmHashAlgorithm.MD5 ) );
            returnMap.put( CertDebugInfoKey.sha1Hash.toString(), hash( cert, PwmHashAlgorithm.SHA1 ) );
            returnMap.put( CertDebugInfoKey.sha512Hash.toString(), hash( cert, PwmHashAlgorithm.SHA512 ) );
            if ( JavaHelper.enumArrayContainsValue( flags, DebugInfoFlag.IncludeCertificateDetail ) )
            {
                returnMap.put( CertDebugInfoKey.detail.toString(), X509Utils.makeDetailText( cert ) );
            }
        }
        catch ( final PwmUnrecoverableException | CertificateEncodingException e )
        {
            LOGGER.warn( () -> "error generating hash for certificate: " + e.getMessage() );
        }
        return returnMap;
    }

    enum KeyDebugInfoKey
    {
        algorithm,
        format,
    }

    public static Map<String, String> makeDebugInfoMap( final PrivateKey key )
    {
        final Map<String, String> returnMap = new LinkedHashMap<>();
        returnMap.put( KeyDebugInfoKey.algorithm.toString(), key.getAlgorithm() );
        returnMap.put( KeyDebugInfoKey.format.toString(), key.getFormat() );
        return returnMap;
    }

    public static X509Certificate certificateFromBase64( final String b64encodedStr )
            throws CertificateException, IOException
    {
        final CertificateFactory certificateFactory = CertificateFactory.getInstance( "X.509" );
        final byte[] certByteValue = StringUtil.base64Decode( b64encodedStr );
        return ( X509Certificate ) certificateFactory.generateCertificate( new ByteArrayInputStream( certByteValue ) );
    }

    public static String certificateToBase64( final X509Certificate certificate )
            throws CertificateEncodingException
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
            if ( certIsRootCA( certificate ) )
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

    private static boolean certIsRootCA( final X509Certificate certificate )
    {
        final int keyCertSignBitPosition = 5;
        final boolean[] keyUsages = certificate.getKeyUsage();
        if ( keyUsages != null && keyUsages.length > keyCertSignBitPosition - 1 )
        {
            if ( keyUsages[keyCertSignBitPosition] )
            {
                return true;
            }
        }

        return false;
    }

    public static TrustManager[] getDefaultJavaTrustManager( final Configuration configuration )
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
            throws PwmUnrecoverableException
    {
        try
        {
            return SecureEngine.hash( new ByteArrayInputStream( certificate.getEncoded() ), pwmHashAlgorithm );
        }
        catch ( final CertificateEncodingException e )
        {
            throw PwmUnrecoverableException.newException( PwmError.ERROR_INTERNAL, "unexpected error encoding certificate: " + e.getMessage() );
        }
    }

    public static Set<X509Certificate> readCertsForListOfLdapUrls( final List<String> ldapUrls, final Configuration configuration )
            throws PwmUnrecoverableException
    {
        final Set<X509Certificate> resultCertificates = new LinkedHashSet<>();
        try
        {
            for ( final String ldapUrlString : ldapUrls )
            {
                final URI ldapURI = new URI( ldapUrlString );
                final List<X509Certificate> certs = X509Utils.readRemoteCertificates( ldapURI, configuration );
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
