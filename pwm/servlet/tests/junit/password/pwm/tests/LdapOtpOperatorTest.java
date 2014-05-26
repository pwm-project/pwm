/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2014 The PWM Project
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

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package password.pwm.tests;

import junit.framework.TestCase;
import org.junit.*;
import password.pwm.PwmApplication;
import password.pwm.config.Configuration;
import password.pwm.config.ConfigurationReader;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.operations.otp.LdapOtpOperator;

import java.io.File;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 *
 * @author mpieters
 */
public class LdapOtpOperatorTest extends TestCase {
    
    private LdapOtpOperator operator;
    private Configuration config;
    
    public LdapOtpOperatorTest() {
    }
    
    @BeforeClass
    public static void setUpClass() {
    }
    
    @AfterClass
    public static void tearDownClass() {
    }
    
    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        //TestHelper.setupLogging();
        //final File fileLocation = new File(password.pwm.tests.TestHelper.getParameter("pwmDBlocation"));
        final File configFileLocation = new File(password.pwm.tests.TestHelper.getParameter("pwmConfigurationLocation"));
        final ConfigurationReader reader = new ConfigurationReader(configFileLocation);
        final PwmApplication pwmApplication = new PwmApplication(config, PwmApplication.MODE.RUNNING, configFileLocation, true);
        config = reader.getConfiguration();
        operator = new LdapOtpOperator(pwmApplication);
    }
    
    @After
    @Override
    public void tearDown() {
    }

    /**
     * 
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeyException 
     * @throws password.pwm.error.PwmUnrecoverableException 
     */
    @Test
    public void testEncodeDecode() throws NoSuchAlgorithmException, InvalidKeyException, PwmUnrecoverableException {
        /*
        OTPUserRecord otp = new OTPUserRecord("dummy@example.com");
        otp.init(false, 5);
        String encoded = operator.composeOtpAttribute(otp);
        OTPUserRecord decoded = operator.decomposeOtpAttribute(encoded);
        System.err.println("1: "+encoded);
        System.err.println("2: "+operator.composeOtpAttribute(decoded));
        assert otp.equals(decoded);
        */
    }
}