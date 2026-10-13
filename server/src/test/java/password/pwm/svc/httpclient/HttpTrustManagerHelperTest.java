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

package password.pwm.svc.httpclient;

import org.junit.Assert;
import org.junit.Test;
import password.pwm.util.secure.PromiscuousTrustManager;
import password.pwm.util.secure.PwmTrustManager;

import javax.net.ssl.TrustManager;
import java.util.Collections;

/**
 * Tests for pwm-project/pwm#733: outbound HTTPS callouts configured with
 * {@link PwmHttpClientConfiguration.TrustManagerType#configuredCertificates} but with no
 * certificates actually configured should fall back to the JVM default trust store, rather
 * than installing a trust manager with an empty (trust-nothing) certificate list.
 */
public class HttpTrustManagerHelperTest
{
    private static HttpTrustManagerHelper helperFor( final PwmHttpClientConfiguration clientConfiguration )
    {
        // appConfig is unused by the code paths under test (getDefaultJavaTrustManager ignores it,
        // and TrustManagerSettings.fromConfiguration is null-tolerant), so null is acceptable here.
        return new HttpTrustManagerHelper( null, clientConfiguration );
    }

    @Test
    public void configuredCertificatesWithEmptyListFallsBackToDefaultJava() throws Exception
    {
        final PwmHttpClientConfiguration clientConfiguration = PwmHttpClientConfiguration.builder()
                .trustManagerType( PwmHttpClientConfiguration.TrustManagerType.configuredCertificates )
                .certificates( Collections.emptyList() )
                .build();

        final TrustManager[] trustManagers = helperFor( clientConfiguration ).makeTrustManager();

        Assert.assertTrue( "at least one trust manager expected", trustManagers.length >= 1 );
        Assert.assertFalse( "empty cert list must not produce a (trust-nothing) PwmTrustManager",
                trustManagers[0] instanceof PwmTrustManager );
        Assert.assertFalse( "empty cert list must not produce a PromiscuousTrustManager",
                trustManagers[0] instanceof PromiscuousTrustManager );
    }

    @Test
    public void configuredCertificatesWithNullListFallsBackToDefaultJava() throws Exception
    {
        final PwmHttpClientConfiguration clientConfiguration = PwmHttpClientConfiguration.builder()
                .trustManagerType( PwmHttpClientConfiguration.TrustManagerType.configuredCertificates )
                .certificates( null )
                .build();

        final TrustManager[] trustManagers = helperFor( clientConfiguration ).makeTrustManager();

        Assert.assertTrue( "at least one trust manager expected", trustManagers.length >= 1 );
        Assert.assertFalse( "null cert list must not produce a (trust-nothing) PwmTrustManager",
                trustManagers[0] instanceof PwmTrustManager );
    }

    @Test
    public void emptyConfiguredCertificatesMatchesExplicitDefaultJava() throws Exception
    {
        final TrustManager[] explicitDefault = helperFor( PwmHttpClientConfiguration.builder()
                .trustManagerType( PwmHttpClientConfiguration.TrustManagerType.defaultJava )
                .build() ).makeTrustManager();

        final TrustManager[] emptyConfigured = helperFor( PwmHttpClientConfiguration.builder()
                .trustManagerType( PwmHttpClientConfiguration.TrustManagerType.configuredCertificates )
                .certificates( Collections.emptyList() )
                .build() ).makeTrustManager();

        Assert.assertEquals( "empty configuredCertificates should yield the same trust manager type as defaultJava",
                explicitDefault[0].getClass(), emptyConfigured[0].getClass() );
    }

    @Test
    public void debugTextWithEmptyConfiguredCertificatesDoesNotThrow() throws Exception
    {
        final PwmHttpClientConfiguration clientConfiguration = PwmHttpClientConfiguration.builder()
                .trustManagerType( PwmHttpClientConfiguration.TrustManagerType.configuredCertificates )
                .certificates( null )
                .build();

        // regression: debugText() previously NPE'd on a null/empty certificate list
        final String debugText = helperFor( clientConfiguration ).debugText();
        Assert.assertNotNull( debugText );
    }
}
