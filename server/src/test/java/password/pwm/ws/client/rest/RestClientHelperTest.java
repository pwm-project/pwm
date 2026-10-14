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

package password.pwm.ws.client.rest;

import org.junit.Assert;
import org.junit.Test;

/**
 * Outbound rest call bodies (e.g. external password rule checks) can contain passwords and must not be
 * written to the debug log.
 */
public class RestClientHelperTest
{
    @Test
    public void beginCallLogMessageOmitsBody()
    {
        final String body = "{\"password\":\"MySecretPassw0rd\"}";
        final String message = RestClientHelper.makeBeginCallLogMessage( "https://rules.example.com/check", body );

        Assert.assertFalse( message.contains( "MySecretPassw0rd" ) );
        Assert.assertTrue( message.contains( "https://rules.example.com/check" ) );
        Assert.assertTrue( message.contains( "[" + body.length() + " chars]" ) );
    }

    @Test
    public void beginCallLogMessageHandlesNullBody()
    {
        Assert.assertTrue( RestClientHelper.makeBeginCallLogMessage( "https://x", null ).contains( "[0 chars]" ) );
    }
}
