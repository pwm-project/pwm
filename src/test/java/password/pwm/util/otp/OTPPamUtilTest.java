/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2015 The PWM Project
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

package password.pwm.util.otp;

import org.junit.*;

import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 *
 * @author mpieters
 */
public class OTPPamUtilTest {
    
    public OTPPamUtilTest() {
    }
    
    @BeforeClass
    public static void setUpClass() {
    }
    
    @AfterClass
    public static void tearDownClass() {
    }
    
    @Before
    public void setUp() {
    }
    
    @After
    public void tearDown() {
    }

    /**
     * Test of splitLines method, of class OTPPamUtil.
     */
    @Test
    public void testSplitLines() {
        System.out.println("splitLines");
        String text = "TUC2JMV7BLJVV6YX\r\n\" WINDOW_SIZE -1\r\n\" TOTP_AUTH\r\n72706699\r\n";
        List<String> result = OTPPamUtil.splitLines(text);
        assertEquals(4, result.size());
    }

    /**
     * Test of decomposePamData method, of class OTPPamUtil.
     */
    @Test
    public void testDecomposePamData() {
        /*
        System.out.println("decomposePamData");
        // TOTP
        String text = "TUC2JMV7BLJVV6YX\r\n\" WINDOW_SIZE -1\r\n\" TOTP_AUTH\r\n72706699\r\n";
        OTPUserRecord otp = OTPPamUtil.decomposePamData(text);
        assertNotNull(otp);
        assertEquals("TUC2JMV7BLJVV6YX", otp.getSecret());
        assertEquals(OTPUserRecord.Type.TOTP, otp.getType());
        // HOTP
        text = "D4GVE762TVEIHYJF\r\n\" HOTP_COUNTER 10\r\n72706699\r\n87839814\r\n";
        otp = OTPPamUtil.decomposePamData(text);
        assertNotNull(otp);
        assertEquals("D4GVE762TVEIHYJF", otp.getSecret());
        assertEquals(OTPUserRecord.Type.HOTP, otp.getType());
        assertEquals(10L, otp.getCurrentCounter());
        assertEquals(2, otp.getRecoveryCodesCount().size());
        */
    }

    /**
     * Test of composePamData method, of class OTPPamUtil.
     */
    @Test
    @SuppressWarnings("empty-statement")
    public void testComposePamData() {
        /*
        System.out.println("composePamData");
        // TOTP
        OTPUserRecord otp = new OTPUserRecord();
        otp.setType(OTPUserRecord.Type.TOTP);
        otp.setSecret("TUC2JMV7BLJVV6YX");
        String[] recoveryCodes = { "72706699", "87839814" };
        otp.setRecoveryCodes(Arrays.asList(recoveryCodes));
        String expResult = "TUC2JMV7BLJVV6YX\n\" TOTP_AUTH\n72706699\n87839814\n";
        String result = OTPPamUtil.composePamData(otp);
        assertEquals(expResult, result);
        // HOTP
        otp = new OTPUserRecord();
        otp.setType(OTPUserRecord.Type.HOTP);
        otp.setSecret("D4GVE762TVEIHYJF");
        otp.setRecoveryCodes(Arrays.asList(recoveryCodes));
        otp.setAttemptCount(10L);
        expResult = "D4GVE762TVEIHYJF\n\" HOTP_COUNTER 10\n72706699\n87839814\n";
        result = OTPPamUtil.composePamData(otp);
        assertEquals(expResult, result);
        // HOTP
        */
    }
    
}
