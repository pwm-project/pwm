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
