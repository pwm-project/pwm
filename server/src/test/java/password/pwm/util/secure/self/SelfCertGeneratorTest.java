/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2020 The PWM Project
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

package password.pwm.util.secure.self;

import org.junit.Assert;
import org.junit.Test;
import password.pwm.PwmConstants;
import password.pwm.util.PasswordData;

import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

public class SelfCertGeneratorTest
{

    @Test
    public void doSelfCertGeneratorTest() throws Exception
    {
        final KeyStore keyStore = SelfCertFactory.generateNewCert( Settings.builder().build(), null, new PasswordData( "password" ), "alias" );
        final Certificate certificate = keyStore.getCertificate( "alias" );
        final String subjectDN = ( ( X509Certificate) certificate ).getSubjectDN().getName();
        Assert.assertEquals( "CN=" + PwmConstants.PWM_APP_NAME.toLowerCase() + ".example.com", subjectDN );
    }
}
