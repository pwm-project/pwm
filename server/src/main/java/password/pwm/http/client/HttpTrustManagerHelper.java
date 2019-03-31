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

package password.pwm.http.client;

import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import password.pwm.AppProperty;
import password.pwm.bean.SessionLabel;
import password.pwm.config.Configuration;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.secure.PwmHashAlgorithm;
import password.pwm.util.secure.X509Utils;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.TrustManager;
import java.security.cert.X509Certificate;
import java.util.Iterator;

class HttpTrustManagerHelper
{
    private final Configuration configuration;
    private final SessionLabel sessionLabel;
    private final PwmHttpClientConfiguration pwmHttpClientConfiguration;
    private final TrustManagerType trustManagerType;

    enum TrustManagerType
    {
        promiscuous,
        supplied,
        configuredCertificates,
        defaultJava,
    }

    HttpTrustManagerHelper(
            final Configuration configuration,
            final SessionLabel sessionLabel,
            final PwmHttpClientConfiguration pwmHttpClientConfiguration
    )
    {
        this.configuration = configuration;
        this.sessionLabel = sessionLabel;
        this.pwmHttpClientConfiguration = pwmHttpClientConfiguration;
        this.trustManagerType = figureType();
    }

    TrustManagerType getTrustManagerType()
    {
        return trustManagerType;
    }

    private TrustManagerType figureType()
    {

        final boolean configPromiscuousEnabled = Boolean.parseBoolean( configuration.readAppProperty( AppProperty.SECURITY_HTTP_PROMISCUOUS_ENABLE ) );
        final boolean promiscuousTrustMgrSet = pwmHttpClientConfiguration != null
                && pwmHttpClientConfiguration.getTrustManager() != null
                && X509Utils.PromiscuousTrustManager.class.equals( pwmHttpClientConfiguration.getTrustManager().getClass() );

        if ( configPromiscuousEnabled || promiscuousTrustMgrSet )
        {
            return TrustManagerType.promiscuous;
        }

        // use the client supplied TrustManager
        if ( pwmHttpClientConfiguration.getTrustManager() != null )
        {
            return TrustManagerType.supplied;
        }

        // using configured certificates
        if ( !JavaHelper.isEmpty( pwmHttpClientConfiguration.getCertificates() ) )
        {
            return TrustManagerType.configuredCertificates;
        }

        // use default trust manager
        return TrustManagerType.defaultJava;
    }

    HostnameVerifier hostnameVerifier()
    {
        final TrustManagerType trustManagerType = getTrustManagerType();
        if ( trustManagerType == TrustManagerType.promiscuous )
        {
            return NoopHostnameVerifier.INSTANCE;
        }

        if ( !Boolean.parseBoolean( configuration.readAppProperty( AppProperty.HTTP_CLIENT_ENABLE_HOSTNAME_VERIFICATION ) ) )
        {
            return NoopHostnameVerifier.INSTANCE;
        }

        return new DefaultHostnameVerifier();
    }

    TrustManager[] makeTrustManager(
    )
            throws PwmUnrecoverableException
    {
        final TrustManagerType trustManagerType = getTrustManagerType();

        switch ( trustManagerType )
        {
            case promiscuous:
                return new TrustManager[]
                        {
                                new X509Utils.PromiscuousTrustManager( sessionLabel ),
                        };

            case supplied:
            {
                return new TrustManager[]
                        {
                                pwmHttpClientConfiguration.getTrustManager(),
                        };
            }

            case configuredCertificates:
            {
                return new TrustManager[]
                        {
                                new X509Utils.CertMatchingTrustManager( configuration, pwmHttpClientConfiguration.getCertificates() ),
                        };
            }

            case defaultJava:
            {
                return X509Utils.getDefaultJavaTrustManager( configuration );
            }

            default:
                JavaHelper.unhandledSwitchStatement( trustManagerType );

        }

        throw PwmUnrecoverableException.newException( PwmError.ERROR_INTERNAL, "unknown trust manager type" );
    }

    String debugText() throws PwmUnrecoverableException
    {
        final TrustManagerType type = getTrustManagerType();
        final StringBuilder value = new StringBuilder( "trust manager [" + type );
        if ( TrustManagerType.supplied == type )
        {
            value.append( "=" );
            value.append( pwmHttpClientConfiguration.getTrustManager().getClass().getSimpleName() );
        }
        else if ( TrustManagerType.configuredCertificates == type )
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
