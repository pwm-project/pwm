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
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * @author mpieters
 */
public class OTPUrlUtilTest
{

    public OTPUrlUtilTest()
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
     * Test of composeOtpUrl method and decomposeOtpUrl, of class OTPUrlUtil.
     */
    @Test
    public void testComposeAndDecomposeOtpUrl()
    {
        /*
        System.out.println("composeOtpUrl");
        OTPUserRecord otp = new OTPUserRecord("TEST");
        otp.setSecret("2222222222222222");
        String result = OTPUrlUtil.composeOtpUrl(otp);
        System.out.println(result);
        OTPUserRecord xotp = OTPUrlUtil.decomposeOtpUrl(result);
        assertNotNull(xotp);
        assertEquals(otp.getIdentifier(), xotp.getIdentifier());
        assertEquals(otp.getSecret(), xotp.getSecret());
        */
    }

}
