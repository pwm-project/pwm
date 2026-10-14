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

package password.pwm.util.operations;

import org.junit.Assert;
import org.junit.Test;
import password.pwm.PwmConstants;
import password.pwm.svc.httpclient.PwmHttpClientConfiguration;

import java.util.Collections;

/**
 * Action values are usually macro expanded (e.g. {@code @User:Password@}), so web service request bodies
 * must be masked in debug output and ldap action values must never be logged.
 */
public class ActionExecutorTest
{
    @Test
    public void webActionClientMasksBodyWithoutCertificates()
    {
        final PwmHttpClientConfiguration config = ActionExecutor.makeWebActionClientConfiguration( null );

        Assert.assertTrue( config.isMaskBodyDebugOutput() );
        Assert.assertEquals( PwmHttpClientConfiguration.TrustManagerType.defaultJava, config.getTrustManagerType() );
    }

    @Test
    public void webActionClientMasksBodyWithCertificates()
    {
        final PwmHttpClientConfiguration config = ActionExecutor.makeWebActionClientConfiguration( Collections.emptyList() );

        Assert.assertTrue( config.isMaskBodyDebugOutput() );
        Assert.assertEquals( PwmHttpClientConfiguration.TrustManagerType.configuredCertificates, config.getTrustManagerType() );
        Assert.assertEquals( Collections.emptyList(), config.getCertificates() );
    }

    @Test
    public void ldapActionLogMessageOmitsValue()
    {
        final String message = ActionExecutor.makeLdapActionLogMessage( "replaced", "uid=jdoe,ou=people,o=example", "userPassword" );

        Assert.assertEquals( "replaced attribute on user uid=jdoe,ou=people,o=example (userPassword="
                + PwmConstants.LOG_REMOVED_VALUE_REPLACEMENT + ")", message );
    }
}
