/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package javax.mail.internet;

import junit.framework.Assert;

import org.junit.Test;

/**
 * Tests to ensure the email address parsing from javax.mail.internet.InternetAddress is what we
 * expect.
 */
public class InternetAddressTest {
    @Test(expected = Exception.class)
    public void testParse_null() throws AddressException {
        InternetAddress.parse(null, true);
    }

    @Test
    public void testParse_empty() throws AddressException {
        InternetAddress[] addresses = InternetAddress.parse("", true);
        Assert.assertEquals(0, addresses.length);
    }

    @Test
    public void testParse_single() throws AddressException {
        InternetAddress[] addresses = InternetAddress.parse("fred@flintstones.tv");

        Assert.assertEquals(1, addresses.length);
        Assert.assertEquals("fred@flintstones.tv", addresses[0].getAddress());
    }

    @Test
    public void testParse_multiple() throws AddressException {
        InternetAddress[] addresses = InternetAddress.parse("fred@flintstones.tv,wilma@flinstones.tv, barney@flintstones.tv,   betty@flinstones.tv");

        Assert.assertEquals(4, addresses.length);
        Assert.assertEquals("fred@flintstones.tv", addresses[0].getAddress());
        Assert.assertEquals("wilma@flinstones.tv", addresses[1].getAddress());
        Assert.assertEquals("barney@flintstones.tv", addresses[2].getAddress());
        Assert.assertEquals("betty@flinstones.tv", addresses[3].getAddress());
    }

    @Test
    public void testParse_multipleWithCommasInName() throws AddressException {
        InternetAddress[] addresses = InternetAddress.parse("fred@flintstones.tv,wilma@flinstones.tv, \"Rubble, Barney\"@flintstones.tv,   Betty Rubble <betty@flinstones.tv>");

        Assert.assertEquals(4, addresses.length);
        Assert.assertEquals("fred@flintstones.tv", addresses[0].getAddress());
        Assert.assertEquals("wilma@flinstones.tv", addresses[1].getAddress());
        Assert.assertEquals("\"Rubble, Barney\"@flintstones.tv", addresses[2].getAddress());
        Assert.assertEquals("betty@flinstones.tv", addresses[3].getAddress());
    }
}
