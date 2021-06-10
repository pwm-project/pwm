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

package password.pwm.svc.httpclient;

import lombok.Value;
import password.pwm.AppProperty;
import password.pwm.config.Configuration;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.secure.CertificateReadingTrustManager;
import password.pwm.util.secure.PromiscuousTrustManager;
import password.pwm.util.secure.PwmHashAlgorithm;
import password.pwm.util.secure.PwmTrustManager;
import password.pwm.util.secure.X509Utils;

import javax.net.ssl.TrustManager;
import java.security.cert.X509Certificate;
import java.util.Iterator;

@Value
class HttpTrustManagerHelper
{
    private final Configuration appConfig;
    private final PwmHttpClientConfiguration pwmHttpClientConfiguration;
    private final PwmHttpClientConfiguration.TrustManagerType trustManagerType;

    HttpTrustManagerHelper(
            final Configuration appConfig,
            final PwmHttpClientConfiguration pwmHttpClientConfiguration
    )
    {
        this.appConfig = appConfig;
        this.pwmHttpClientConfiguration = pwmHttpClientConfiguration;
        this.trustManagerType = pwmHttpClientConfiguration.getTrustManagerType();
    }

    PwmHttpClientConfiguration.TrustManagerType getTrustManagerType()
    {
        return trustManagerType;
    }

    boolean hostnameVerificationEnabled()
    {
        final PwmHttpClientConfiguration.TrustManagerType trustManagerType = getTrustManagerType();
        if ( trustManagerType == PwmHttpClientConfiguration.TrustManagerType.promiscuous )
        {
            return false;
        }

        final Configuration appConfig = getAppConfig();
        if ( !Boolean.parseBoolean( appConfig.readAppProperty( AppProperty.HTTP_CLIENT_ENABLE_HOSTNAME_VERIFICATION ) ) )
        {
            return false;
        }

        return true;
    }

    TrustManager[] makeTrustManager(
    )
            throws PwmUnrecoverableException
    {
        final PwmHttpClientConfiguration.TrustManagerType trustManagerType = getTrustManagerType();

        switch ( trustManagerType )
        {
            case promiscuous:
                return new TrustManager[]
                        {
                                PromiscuousTrustManager.createPromiscuousTrustManager( ),
                        };

            case promiscuousCertReader:
                return new TrustManager[]
                        {
                                CertificateReadingTrustManager.newCertReaderTrustManager( appConfig ),
                        };

            case configuredCertificates:
            {
                return new TrustManager[]
                        {
                                PwmTrustManager.createPwmTrustManager( appConfig, pwmHttpClientConfiguration.getCertificates() ),
                        };
            }

            case defaultJava:
            {
                return X509Utils.getDefaultJavaTrustManager( appConfig );
            }

            default:
                JavaHelper.unhandledSwitchStatement( trustManagerType );

        }

        throw PwmUnrecoverableException.newException( PwmError.ERROR_INTERNAL, "unknown trust manager type" );
    }

    String debugText() throws PwmUnrecoverableException
    {
        final PwmHttpClientConfiguration.TrustManagerType type = getTrustManagerType();
        final StringBuilder value = new StringBuilder( "trust manager [" + type );

        if ( PwmHttpClientConfiguration.TrustManagerType.configuredCertificates == type )
        {
            value.append( "=" );
            for ( final Iterator<X509Certificate> iterator = pwmHttpClientConfiguration.getCertificates().iterator(); iterator.hasNext(); )
            {
                final X509Certificate certificate = iterator.next();
                value.append( X509Utils.hash( certificate, PwmHashAlgorithm.SHA1 ) );
                if ( iterator.hasNext() )
                {
                    value.append( "," );
                }
            }
        }
        value.append( "]" );
        return value.toString();
    }
}
