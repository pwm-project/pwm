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

package password.pwm.config;

import org.junit.Assert;
import org.junit.Test;
import password.pwm.config.value.StoredValueEncoder;
import password.pwm.util.secure.PwmSecurityKey;

import java.util.Optional;

public class StoredValueEncoderTest
{
    @Test
    public void encode() throws Exception
    {
        final PwmSecurityKey pwmSecurityKey = new PwmSecurityKey( "SuperSecretKeyValue" );

        final String encodedValue = StoredValueEncoder.encode( "password", StoredValueEncoder.Mode.ENCODED, pwmSecurityKey );
        final Optional<String> decodedValue = StoredValueEncoder.decode( encodedValue, StoredValueEncoder.Mode.ENCODED, pwmSecurityKey );
        Assert.assertTrue( decodedValue.isPresent() );
        Assert.assertEquals( "password", decodedValue.get() );
        Assert.assertNotEquals( "password", encodedValue );
        Assert.assertTrue( encodedValue.startsWith( StoredValueEncoder.Mode.ENCODED.getPrefix() ) );
    }
}
