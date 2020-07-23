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

import org.junit.Test;

import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ResourceBundle;

public class PromiscuousTrustManagerTest
{

    @Test
    public void createPromiscuousTrustManager() throws CertificateException, IOException
    {
        final ResourceBundle bundle = ResourceBundle.getBundle( PromiscuousTrustManagerTest.class.getName() );
        final String cert1data = bundle.getString( "cert1" );
        final X509Certificate cert1 = X509Utils.certificateFromBase64( cert1data );

        final X509TrustManager trustManager = PromiscuousTrustManager.createPromiscuousTrustManager();
        final X509Certificate[] certificates = new X509Certificate[]
                {
                        cert1,
                };
        trustManager.checkServerTrusted( certificates, "test" );
    }
}
