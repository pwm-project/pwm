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

import password.pwm.AppProperty;
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
import password.pwm.http.client.PwmHttpClient;
import password.pwm.http.client.PwmHttpClientConfiguration;
import password.pwm.http.client.PwmHttpClientRequest;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class X509Utils
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( X509Utils.class );

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
        final CertReaderTrustManager certReaderTm = new CertReaderTrustManager( readCertificateFlagsFromConfig( configuration ) );
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
        catch ( Exception e )
        {
            final StringBuilder errorMsg = new StringBuilder();
            errorMsg.append( "unable to read server certificates from host=" );
            errorMsg.append( host ).append( ", port=" ).append( port );
            errorMsg.append( " error: " );
            errorMsg.append( e.getMessage() );
            LOGGER.error( "ServerCertReader: " + errorMsg );
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_CERTIFICATE_ERROR, errorMsg.toString(), new String[]
                    {
                            errorMsg.toString(),
                    }
            );
            throw new PwmOperationalException( errorInformation );
        }
        final List<X509Certificate> certs = certReaderTm.getCertificates();
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
            final URI uri,
            final Configuration configuration
    )
            throws PwmUnrecoverableException
    {
        final CertReaderTrustManager certReaderTrustManager = new CertReaderTrustManager( readCertificateFlagsFromConfig( configuration ) );
        final PwmHttpClientConfiguration pwmHttpClientConfiguration = PwmHttpClientConfiguration.builder()
                .trustManager( certReaderTrustManager )
                .build();
        final PwmHttpClient pwmHttpClient = new PwmHttpClient( pwmApplication, sessionLabel, pwmHttpClientConfiguration );
        final PwmHttpClientRequest request = new PwmHttpClientRequest( HttpMethod.GET, uri.toString(), "", Collections.emptyMap() );

        LOGGER.debug( sessionLabel, () -> "beginning attempt to import certificates via httpclient" );

        ErrorInformation requestError = null;
        try
        {
            pwmHttpClient.makeRequest( request );
        }
        catch ( PwmException e )
        {
            requestError = e.getErrorInformation();
        }

        if ( certReaderTrustManager.getCertificates() != null )
        {
            return certReaderTrustManager.getCertificates();
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
        catch ( Exception e )
        {
            LOGGER.trace( () -> "exception while testing ldap server cert validity against default keystore: " + e.getMessage() );
        }

        return false;
    }

    public static class CertReaderTrustManager implements X509TrustManager
    {
        private final ReadCertificateFlag[] readCertificateFlags;

        private List<X509Certificate> certificates = new ArrayList<>();

        public CertReaderTrustManager( final ReadCertificateFlag... readCertificateFlags )
        {
            this.readCertificateFlags = readCertificateFlags;
        }

        public void checkClientTrusted( final X509Certificate[] chain, final String authType )
                throws CertificateException
        {
            LOGGER.debug( () -> "clientCheckTrusted invoked in CertReaderTrustManager" );
        }

        public X509Certificate[] getAcceptedIssuers( )
        {
            return null;
        }

        public void checkServerTrusted( final X509Certificate[] chain, final String authType )
                throws CertificateException
        {
            final List<X509Certificate> asList = Arrays.asList( chain );
            certificates.addAll( asList );
            final List<Map<String, String>> certDebugInfo = X509Utils.makeDebugInfoMap( certificates );
            LOGGER.debug( () -> "read certificates from remote server: "
                    + JsonUtil.serialize( new ArrayList<>( certDebugInfo ) ) );
        }

        public List<X509Certificate> getCertificates( )
        {
            if ( JavaHelper.enumArrayContainsValue( readCertificateFlags, ReadCertificateFlag.ReadOnlyRootCA ) )
            {
                return Collections.unmodifiableList( identifyRootCACertificate( certificates ) );
            }
            return Collections.unmodifiableList( certificates );
        }
    }

    public static class PromiscuousTrustManager implements X509TrustManager
    {
        private final SessionLabel sessionLabel;

        public PromiscuousTrustManager( final SessionLabel sessionLabel )
        {
            this.sessionLabel = sessionLabel;
        }

        public X509Certificate[] getAcceptedIssuers( )
        {
            return new X509Certificate[ 0 ];
        }

        public void checkClientTrusted( final X509Certificate[] certs, final String authType )
        {
            logMsg( certs, authType );
        }

        public void checkServerTrusted( final X509Certificate[] certs, final String authType )
        {
            logMsg( certs, authType );
        }

        private void logMsg( final X509Certificate[] certs, final String authType )
        {
            if ( certs != null )
            {
                for ( final X509Certificate cert : certs )
                {
                    try
                    {
                        LOGGER.debug( sessionLabel, () -> "promiscuous trusting certificate during authType=" + authType + ", subject=" + cert.getSubjectDN().toString() );
                    }
                    catch ( Exception e )
                    {
                        LOGGER.error( "error while decoding certificate: " + e.getMessage() );
                        throw new IllegalStateException( e );
                    }
                }
            }
        }
    }

    public static class CertMatchingTrustManager implements X509TrustManager
    {
        final List<X509Certificate> trustedCertificates;
        final boolean validateTimestamps;
        final boolean allowSelfSigned;
        final CertificateMatchingMode certificateMatchingMode;

        public CertMatchingTrustManager( final Configuration config, final List<X509Certificate> trustedCertificates )
        {
            this.trustedCertificates = new ArrayList<>( trustedCertificates );
            validateTimestamps = config != null && Boolean.parseBoolean( config.readAppProperty( AppProperty.SECURITY_CERTIFICATES_VALIDATE_TIMESTAMPS ) );
            allowSelfSigned = config != null && Boolean.parseBoolean( config.readAppProperty( AppProperty.SECURITY_CERTIFICATES_ALLOW_SELF_SIGNED ) );
            certificateMatchingMode = config == null
                    ? CertificateMatchingMode.CERTIFICATE_CHAIN
                    : config.readCertificateMatchingMode();
        }

        @Override
        public void checkClientTrusted( final X509Certificate[] x509Certificates, final String s ) throws CertificateException
        {
        }

        @Override
        public void checkServerTrusted( final X509Certificate[] x509Certificates, final String s ) throws CertificateException
        {
            final List<X509Certificate> trustedRootCA = X509Utils.identifyRootCACertificate( trustedCertificates );
            final List<X509Certificate> remoteCertificates = Arrays.asList( x509Certificates );
            if ( trustedCertificates.size() == 1 && trustedRootCA.isEmpty() && remoteCertificates.size() == 1 )
            {
                if ( allowSelfSigned )
                {
                    doValidation( remoteCertificates, trustedCertificates, validateTimestamps );
                    return;
                }
                else
                {
                    final String msg = "unable to trust self-signed certificate due to app property '"
                            + AppProperty.SECURITY_CERTIFICATES_ALLOW_SELF_SIGNED.getKey() + "'";
                    throw new CertificateException( msg );
                }
            }


            switch ( certificateMatchingMode )
            {
                case CERTIFICATE_CHAIN:
                {
                    doValidation( trustedCertificates, remoteCertificates, validateTimestamps );
                    break;
                }

                case CA_ONLY:
                {
                    if ( trustedRootCA.isEmpty() )
                    {
                        final String errorMsg = "no root CA certificates in configuration trust store for this operation";
                        throw new CertificateException( errorMsg );
                    }
                    doValidation(
                            trustedRootCA,
                            X509Utils.identifyRootCACertificate( remoteCertificates ),
                            validateTimestamps
                    );
                    break;
                }

                default:
                    JavaHelper.unhandledSwitchStatement( certificateMatchingMode );
            }
        }

        private static void doValidation(
                final List<X509Certificate> trustedCertificates,
                final List<X509Certificate> certificates,
                final boolean validateTimestamps
        )
                throws CertificateException
        {
            if ( JavaHelper.isEmpty( trustedCertificates ) )
            {
                final String errorMsg = "no certificates in configuration trust store for this operation";
                throw new CertificateException( errorMsg );
            }

            for ( final X509Certificate loopCert : certificates )
            {
                boolean certTrusted = false;
                for ( final X509Certificate storedCert : trustedCertificates )
                {
                    if ( loopCert.equals( storedCert ) )
                    {
                        if ( validateTimestamps )
                        {
                            loopCert.checkValidity();
                        }
                        certTrusted = true;
                    }
                }
                if ( !certTrusted )
                {
                    final String errorMsg = "server certificate {subject=" + loopCert.getSubjectDN().getName() + "} does not match a certificate in the "
                            + PwmConstants.PWM_APP_NAME + " configuration trust store.";
                    throw new CertificateException( errorMsg );
                }
                //LOGGER.trace("trusting configured certificate: " + makeDebugText(loopCert));
            }
        }

        @Override
        public X509Certificate[] getAcceptedIssuers( )
        {
            return new X509Certificate[ 0 ];
        }
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
        catch ( PwmUnrecoverableException | CertificateEncodingException e )
        {
            LOGGER.warn( "error generating hash for certificate: " + e.getMessage() );
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

    private static List<X509Certificate> identifyRootCACertificate( final List<X509Certificate> certificates )
    {
        for ( final X509Certificate certificate : certificates )
        {
            final boolean[] keyUsages = certificate.getKeyUsage();
            if ( certIsRootCA( certificate ) )
            {
                return Collections.singletonList( certificate );
            }
        }
        return Collections.emptyList();
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
        catch ( GeneralSecurityException e )
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
        catch ( CertificateEncodingException e )
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
        catch ( Exception e )
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
