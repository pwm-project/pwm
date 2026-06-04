/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2023 The PWM Project
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
import password.pwm.PwmConstants;
import password.pwm.config.Configuration;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.logging.PwmLogger;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class PwmTrustManager implements X509TrustManager
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( PwmTrustManager.class );

    private final List<X509Certificate> trustedCertificates;
    private final TrustManagerSettings settings;

    private PwmTrustManager( final TrustManagerSettings trustManagerSettings, final List<X509Certificate> trustedCertificates )
    {
        this.trustedCertificates = new ArrayList<>( trustedCertificates );
        this.settings = trustManagerSettings;
    }

    public static PwmTrustManager createPwmTrustManager( final Configuration config, final List<X509Certificate> trustedCertificates )
    {
        final TrustManagerSettings trustManagerSettings = TrustManagerSettings.fromConfiguration( config );

        return new PwmTrustManager( trustManagerSettings, trustedCertificates );
    }

    public static PwmTrustManager createPwmTrustManager( final TrustManagerSettings trustManagerSettings, final List<X509Certificate> trustedCertificates )
    {
        return new PwmTrustManager( trustManagerSettings, trustedCertificates );
    }

    @Override
    public void checkClientTrusted( final X509Certificate[] x509Certificates, final String s ) throws CertificateException
    {
    }

    @Override
    public void checkServerTrusted( final X509Certificate[] x509Certificates, final String s ) throws CertificateException
    {
        switch ( settings.getCertificateMatchingMode() )
        {
            case CA_ONLY:
                doRootCaValidation( trustedCertificates, Arrays.asList( x509Certificates ) );
                break;

            case CERTIFICATE_CHAIN:
                doSelfSignedValidation( trustedCertificates, Arrays.asList( x509Certificates ) );
                break;

            default:
                JavaHelper.unhandledSwitchStatement( settings.getCertificateMatchingMode() );
        }
    }

    private void doRootCaValidation(
            final List<X509Certificate> trustedCertificates,
            final List<X509Certificate> presentedCertificates
    )
            throws CertificateException
    {
        if ( JavaHelper.isEmpty( trustedCertificates ) )
        {
            final String errorMsg = "no ROOT certificates in configuration trust store for this operation";
            throw new CertificateException( errorMsg );
        }

        final Optional<List<X509Certificate>> rootCa = X509Utils.extractRootCaCertificates( trustedCertificates );

        if ( rootCa.isPresent() )
        {
            validateUsingCaCertificates( rootCa.get(), presentedCertificates );
            return;
        }

        doSelfSignedValidation( trustedCertificates, presentedCertificates );
    }

    /**
     * Validate the presented certificate chain against the configured CA certificate(s) using a standard
     * PKIX certification-path validator (RFC 5280).  The configured CA certificate(s) are used as trust
     * anchors and the intermediate certificate(s) supplied by the remote server are used to build the path.
     *
     * <p>This replaces an earlier flat, per-certificate signature check which required every certificate
     * presented by the server to be <em>directly</em> signed by a configured certificate.  That earlier
     * behavior meant that, for a typical {@code leaf -> intermediate -> root} chain, configuring only the
     * root CA was insufficient (the leaf is signed by the intermediate, not the root) and every intermediate
     * had to be configured explicitly.  Proper path building lets a configured root CA validate the full
     * chain using the server-supplied intermediate(s).  See pwm-project/pwm issue 735.</p>
     */
    private void validateUsingCaCertificates(
            final List<X509Certificate> caCertificates,
            final List<X509Certificate> presentedCertificates
    )
            throws CertificateException
    {
        if ( JavaHelper.isEmpty( presentedCertificates ) )
        {
            throw new CertificateException( "no certificates were presented by the remote server" );
        }

        final X509TrustManager delegateTrustManager;
        try
        {
            final KeyStore keyStore = KeyStore.getInstance( KeyStore.getDefaultType() );
            keyStore.load( null, null );

            int index = 0;
            for ( final X509Certificate caCertificate : caCertificates )
            {
                keyStore.setCertificateEntry( "ca-" + index, caCertificate );
                index++;
            }

            // explicitly request PKIX (rather than getDefaultAlgorithm(), which can be overridden to the
            // legacy SunX509 via the ssl.TrustManagerFactory.algorithm security property) so that proper
            // RFC 5280 certification-path building is always used.
            final TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance( "PKIX" );
            trustManagerFactory.init( keyStore );

            delegateTrustManager = firstX509TrustManager( trustManagerFactory.getTrustManagers() );
        }
        catch ( final GeneralSecurityException | IOException e )
        {
            throw new CertificateException( "unable to initialize PKIX trust manager for configured CA certificate(s): " + e.getMessage() );
        }

        try
        {
            final X509Certificate[] chain = presentedCertificates.toArray( new X509Certificate[0] );
            final String authType = chain[0].getPublicKey().getAlgorithm();
            delegateTrustManager.checkServerTrusted( chain, authType );
        }
        catch ( final CertificateException e )
        {
            final String errorMsg = "server certificate chain is not trusted by configured ROOT CA certificate(s): " + e.getMessage();
            LOGGER.trace( () -> errorMsg );
            throw new CertificateException( errorMsg );
        }
    }

    private static X509TrustManager firstX509TrustManager( final TrustManager[] trustManagers ) throws CertificateException
    {
        if ( trustManagers != null )
        {
            for ( final TrustManager trustManager : trustManagers )
            {
                if ( trustManager instanceof X509TrustManager )
                {
                    return ( X509TrustManager ) trustManager;
                }
            }
        }
        throw new CertificateException( "no X509TrustManager available for configured CA certificate(s)" );
    }

    private void doSelfSignedValidation(
            final List<X509Certificate> trustedCertificates,
            final List<X509Certificate> presentedCertificates
    )
            throws CertificateException
    {
        if ( !settings.isAllowSelfSigned() )
        {
            final String msg = "unable to trust self-signed certificate due to app property '"
                    + AppProperty.SECURITY_CERTIFICATES_ALLOW_SELF_SIGNED.getKey() + "'";
            throw new CertificateException( msg );
        }

        for ( final X509Certificate loopCert : presentedCertificates )
        {
            boolean certTrusted = false;
            for ( final X509Certificate storedCert : trustedCertificates )
            {
                if ( loopCert.equals( storedCert ) )
                {
                    if ( settings.isValidateTimestamps() )
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
