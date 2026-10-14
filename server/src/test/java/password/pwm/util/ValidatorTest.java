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

package password.pwm.util;

import org.junit.Assert;
import org.junit.Test;

/**
 * Input sanitizing warnings must never include the values being sanitized, since the input may be a
 * password field or a credential-bearing header.
 */
public class ValidatorTest
{
    private static final String SECRET = "MySecretPassw0rd<script>";

    @Test
    public void sanitizeInputLogMessageOmitsValues()
    {
        final String newValue = "MySecretPassw0rd";
        final String message = Validator.makeSanitizeInputLogMessage( SECRET, newValue, "<script>" );

        Assert.assertFalse( message.contains( SECRET ) );
        Assert.assertFalse( message.contains( newValue ) );
        Assert.assertTrue( message.contains( "length " + SECRET.length() + " changed to " + newValue.length() ) );
        Assert.assertTrue( message.contains( "pattern='<script>'" ) );
    }

    @Test
    public void sanitizeHeaderLogMessageOmitsValues()
    {
        final String header = "Basic c2VjcmV0OnBhc3N3b3Jk\r\n";
        final String stripped = "Basic c2VjcmV0OnBhc3N3b3Jk";
        final String message = Validator.makeSanitizeHeaderLogMessage( header, stripped );

        Assert.assertFalse( message.contains( stripped ) );
        Assert.assertTrue( message.contains( "length " + header.length() + " changed to " + stripped.length() ) );
    }

    @Test
    public void sanitizeLogMessagesHandleNull()
    {
        Assert.assertTrue( Validator.makeSanitizeInputLogMessage( null, null, "x" ).contains( "length 0 changed to 0" ) );
        Assert.assertTrue( Validator.makeSanitizeHeaderLogMessage( null, null ).contains( "length 0 changed to 0" ) );
    }

    @Test
    public void sanitizeInputValueStillStripsWithoutConfig()
    {
        // behavior unchanged: with no configuration only the length limit applies
        Assert.assertEquals( "abc", Validator.sanitizeInputValue( null, "abcdef", 3 ) );
        Assert.assertEquals( "", Validator.sanitizeInputValue( null, null, 10 ) );
    }
}
