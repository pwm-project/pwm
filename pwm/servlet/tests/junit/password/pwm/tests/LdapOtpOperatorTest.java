/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package password.pwm.tests;

import java.io.File;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import junit.framework.TestCase;
import org.codehaus.jettison.json.JSONTokener;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

import password.pwm.PwmApplication;
import password.pwm.config.Configuration;
import password.pwm.config.ConfigurationReader;
import password.pwm.util.operations.otp.LdapOtpOperator;
import password.pwm.util.otp.OTPUserConfiguration;

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
        final PwmApplication pwmApplication = new PwmApplication(config, PwmApplication.MODE.RUNNING, configFileLocation);
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
     */
    @Test
    public void testEncodeDecode() throws NoSuchAlgorithmException, InvalidKeyException {
        OTPUserConfiguration otp = new OTPUserConfiguration("dummy@example.com");
        otp.init(false, 5);
        String encoded = operator.composeOtpAttribute(otp);
        OTPUserConfiguration decoded = operator.decomposeOtpAttribute(encoded);
        System.err.println("1: "+encoded);
        System.err.println("2: "+operator.composeOtpAttribute(decoded));
        assert otp.equals(decoded);
    }
}