/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2021 The PWM Project
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

package password.pwm.svc.email;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;

/**
 * Tests to ensure the email address parsing from javax.mail.internet.InternetAddress is what we
 * expect.
 */
public class InternetAddressTest
{
    @Test
    public void testParseNull() throws AddressException
    {
        Assertions.assertThrows( Exception.class, () ->
        {
            InternetAddress.parse( null, true );
        } );
    }

    @Test
    public void testParseEmpty() throws AddressException
    {
        final InternetAddress[] addresses = InternetAddress.parse( "", true );
        Assertions.assertEquals( 0, addresses.length );
    }

    @Test
    public void testParseSingle() throws AddressException
    {
        final InternetAddress[] addresses = InternetAddress.parse( "fred@flintstones.tv" );

        Assertions.assertEquals( 1, addresses.length );
        Assertions.assertEquals( "fred@flintstones.tv", addresses[0].getAddress() );
    }

    @Test
    public void testParseMultiple() throws AddressException
    {
        final InternetAddress[] addresses = InternetAddress.parse( "fred@flintstones.tv,wilma@flinstones.tv, barney@flintstones.tv,   betty@flinstones.tv" );

        Assertions.assertEquals( 4, addresses.length );
        Assertions.assertEquals( "fred@flintstones.tv", addresses[0].getAddress() );
        Assertions.assertEquals( "wilma@flinstones.tv", addresses[1].getAddress() );
        Assertions.assertEquals( "barney@flintstones.tv", addresses[2].getAddress() );
        Assertions.assertEquals( "betty@flinstones.tv", addresses[3].getAddress() );
    }

    @Test
    public void testParseMultipleWithCommasInName() throws AddressException
    {
        final InternetAddress[] addresses = InternetAddress.parse(
                "fred@flintstones.tv,wilma@flinstones.tv, \"Rubble, Barney\"@flintstones.tv,   Betty Rubble <betty@flinstones.tv>" );

        Assertions.assertEquals( 4, addresses.length );
        Assertions.assertEquals( "fred@flintstones.tv", addresses[0].getAddress() );
        Assertions.assertEquals( "wilma@flinstones.tv", addresses[1].getAddress() );
        Assertions.assertEquals( "\"Rubble, Barney\"@flintstones.tv", addresses[2].getAddress() );
        Assertions.assertEquals( "betty@flinstones.tv", addresses[3].getAddress() );
    }
}
