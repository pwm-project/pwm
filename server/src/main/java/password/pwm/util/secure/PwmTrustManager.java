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
import password.pwm.PwmConstants;
import password.pwm.config.Configuration;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.logging.PwmLogger;

import javax.net.ssl.X509TrustManager;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
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
        final Optional<List<X509Certificate>> rootCa = X509Utils.extractRootCaCertificates( trustedCertificates );

        if ( JavaHelper.isEmpty( trustedCertificates ) )
        {
            final String errorMsg = "no ROOT certificates in configuration trust store for this operation";
            throw new CertificateException( errorMsg );
        }

        if ( rootCa.isPresent() )
        {
            for ( final X509Certificate presentedCert : presentedCertificates )
            {
                try
                {
                    doRootCaValidation( rootCa.get(), presentedCert );
                }
                catch ( final PwmUnrecoverableException e )
                {
                    throw new CertificateException( e.getMessage() );
                }
            }
            return;
        }

        doSelfSignedValidation( trustedCertificates, presentedCertificates );
    }

    private static void doRootCaValidation(
            final List<X509Certificate> rootCertificates,
            final X509Certificate testCertificate
    )
            throws PwmUnrecoverableException
    {
        boolean passed = false;
        final StringBuilder errorText = new StringBuilder(  );
        for ( final X509Certificate rootCA : rootCertificates )
        {
            if ( !passed )
            {
                try
                {
                    testCertificate.verify( rootCA.getPublicKey() );
                    passed = true;
                }
                catch ( final NoSuchAlgorithmException | SignatureException | NoSuchProviderException | InvalidKeyException | CertificateException e )
                {
                    final String msg = "server certificate " + X509Utils.makeDebugText( testCertificate )
                            + " is not trusted by ROOT CA " + X509Utils.makeDebugText( rootCA );
                    LOGGER.trace( () -> msg );
                    errorText.append( msg ).append( "  " );
                }
            }
        }

        if ( !passed )
        {
            final String errorMsg = "server certificate " + X509Utils.makeDebugText( testCertificate )
                    + " is not signed by configured ROOT CA certificate(s): " + errorText.toString();
            throw PwmUnrecoverableException.newException( PwmError.ERROR_CERTIFICATE_ERROR, errorMsg );
        }
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
