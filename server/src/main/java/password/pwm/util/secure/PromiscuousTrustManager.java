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

import password.pwm.util.logging.PwmLogger;

import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;

public class PromiscuousTrustManager implements X509TrustManager
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( PromiscuousTrustManager.class );

    private PromiscuousTrustManager()
    {
    }

    public static PromiscuousTrustManager createPromiscuousTrustManager()
    {
        return new PromiscuousTrustManager();
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
                    LOGGER.debug( () -> "promiscuous trusting certificate during authType=" + authType + ", subject=" + cert.getSubjectDN().toString() );
                }
                catch ( final Exception e )
                {
                    LOGGER.error( () -> "error while decoding certificate: " + e.getMessage() );
                    throw new IllegalStateException( e );
                }
            }
        }
    }
}
