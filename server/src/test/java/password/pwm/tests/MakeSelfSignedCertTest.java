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

package password.pwm.tests;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.Assert;
import org.junit.Test;
import password.pwm.util.secure.HttpsServerCertificateManager;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

public class MakeSelfSignedCertTest
{
    private static final Provider BC_PROVIDER = new BouncyCastleProvider();

    @Test
    public void testSelfSignedCert() throws Exception
    {
        Security.addProvider( BC_PROVIDER );

        final KeyPairGenerator kpGen = KeyPairGenerator.getInstance( "RSA", "BC" );
        kpGen.initialize( 2048, new SecureRandom() );
        final KeyPair keyPair = kpGen.generateKeyPair();


        final String cnName = "test.myname.com";
        final long futureSeconds = ( TimeUnit.DAYS.toMillis( 2 * 365 ) ) / 1000;

        final X509Certificate storedCertData = HttpsServerCertificateManager.SelfCertGenerator.generateV3Certificate( keyPair, cnName, futureSeconds );
        Assert.assertNotNull( storedCertData );
        Assert.assertEquals( storedCertData.getSubjectDN().getName(), storedCertData.getIssuerDN().getName() );
    }
}
