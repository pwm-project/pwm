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

import password.pwm.config.Configuration;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.logging.PwmLogger;

import javax.net.ssl.X509TrustManager;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class CertificateReadingTrustManager implements X509TrustManager
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( CertificateReadingTrustManager.class );

    private final X509Utils.ReadCertificateFlag[] readCertificateFlags;
    private final X509TrustManager wrappedTrustManager;
    private final TrustManagerSettings trustManagerSettings;

    private List<X509Certificate> certificates = new ArrayList<>();

    CertificateReadingTrustManager( final Configuration configuration, final X509TrustManager wrappedTrustManager, final X509Utils.ReadCertificateFlag... readCertificateFlags )
    {
        this.readCertificateFlags = readCertificateFlags;
        this.wrappedTrustManager = wrappedTrustManager;
        this.trustManagerSettings = TrustManagerSettings.fromConfiguration( configuration );
    }

    public static CertificateReadingTrustManager newCertReaderTrustManager(
            final Configuration configuration,
            final X509Utils.ReadCertificateFlag... readCertificateFlags )
    {
        return new CertificateReadingTrustManager( configuration, PromiscuousTrustManager.createPromiscuousTrustManager(), readCertificateFlags );
    }

    public void checkClientTrusted( final X509Certificate[] chain, final String authType )
            throws CertificateException
    {
        wrappedTrustManager.checkClientTrusted(  chain, authType );
    }

    public X509Certificate[] getAcceptedIssuers( )
    {
        return wrappedTrustManager.getAcceptedIssuers();
    }

    public void checkServerTrusted( final X509Certificate[] chain, final String authType )
            throws CertificateException
    {
        certificates = Arrays.asList( chain );
        wrappedTrustManager.checkServerTrusted( chain, authType );
    }

    public List<X509Certificate> getCertificates( )
            throws PwmUnrecoverableException
    {
        if ( JavaHelper.isEmpty( certificates ) )
        {
            final String msg = "remote server did not present any certificates";
            LOGGER.debug( () -> "ServerCertReader: " + msg );
            throw PwmUnrecoverableException.newException( PwmError.ERROR_CERTIFICATE_ERROR, msg );
        }

        final boolean readOnlyRootCA = JavaHelper.enumArrayContainsValue( readCertificateFlags, X509Utils.ReadCertificateFlag.ReadOnlyRootCA );
        if ( readOnlyRootCA )
        {
            final Optional<List<X509Certificate>> rootCA = X509Utils.extractRootCaCertificates( certificates );
            if ( rootCA.isPresent() )
            {
                LOGGER.debug( () -> "ServerCertReader: read " + rootCA.get().size()
                        + " remote CA ROOT certificate(s) from server: " + X509Utils.makeDebugTexts( rootCA.get() ) );
                return Collections.unmodifiableList( rootCA.get() );
            }
            else
            {
                LOGGER.debug( () -> "ServerCertReader: " + "read " + certificates.size()
                        + " certificate(s) from server but none are identified as a ROOT CA certificate." );
                if ( !trustManagerSettings.isAllowSelfSigned() )
                {
                    final String msg = "remote server did not present a signed CA ROOT certificate"
                            + " and self-signed certs are not permitted";
                    LOGGER.debug( () -> "ServerCertReader: " + msg );
                    throw PwmUnrecoverableException.newException( PwmError.ERROR_CERTIFICATE_ERROR, msg );
                }
            }
        }

        LOGGER.debug( () -> "ServerCertReader: read self-signed certificates from remote server: "
                + JsonUtil.serialize( new ArrayList<>( X509Utils.makeDebugInfoMap( certificates ) ) ) );
        return Collections.unmodifiableList( certificates );
    }
}
