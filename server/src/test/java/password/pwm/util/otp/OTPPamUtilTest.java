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

package password.pwm.util.otp;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import password.pwm.util.operations.otp.OTPPamUtil;

import java.util.List;

/**
 * @author mpieters
 */
public class OTPPamUtilTest
{

    public OTPPamUtilTest()
    {
    }

    @BeforeClass
    public static void setUpClass()
    {
    }

    @AfterClass
    public static void tearDownClass()
    {
    }

    @Before
    public void setUp()
    {
    }

    @After
    public void tearDown()
    {
    }

    /**
     * Test of splitLines method, of class OTPPamUtil.
     */
    @Test
    public void testSplitLines()
    {
        final String text = "TUC2JMV7BLJVV6YX\r\n\" WINDOW_SIZE -1\r\n\" TOTP_AUTH\r\n72706699\r\n";
        final List<String> result = OTPPamUtil.splitLines( text );
        Assert.assertEquals( 4, result.size() );
    }

    /**
     * Test of decomposePamData method, of class OTPPamUtil.
     */
    @Test
    public void testDecomposePamData()
    {
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
    @SuppressWarnings( "empty-statement" )
    public void testComposePamData()
    {
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
